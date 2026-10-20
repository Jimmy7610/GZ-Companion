package se.jimmyeliasson.gzcompanion.usage;

import se.jimmyeliasson.gzcompanion.gamezone.net.GameZoneLiveDataRuntime;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * The anonymous active-installation counter: at most one small heartbeat per day, containing only
 * the random installation id and the GZ Companion version (see {@link HeartbeatPayload} and
 * docs/PRIVACY.md).
 *
 * <ul>
 *   <li><b>Inactive without an endpoint.</b> With no {@link HeartbeatTransport} (today: no
 *       production endpoint, see {@link HeartbeatEndpoint}) {@link #onClientActive} returns
 *       immediately - no id file, no thread, no network.</li>
 *   <li><b>Never on the render/tick thread.</b> {@link #onClientActive} only submits one job to the
 *       shared {@link GameZoneLiveDataRuntime}; reading/creating the id, the throttle check and the
 *       request all run on that worker.</li>
 *   <li><b>Throttled.</b> After a success, the next heartbeat is due after {@link #SUCCESS_INTERVAL}
 *       (24 h); after a failed attempt, no new attempt for {@link #RETRY_AFTER_FAILURE} (6 h). The
 *       attempt time is persisted BEFORE sending, so even a crash mid-request cannot cause a retry
 *       loop. At most one attempt per game session.</li>
 *   <li><b>One id, one heartbeat.</b> Concurrent calls are coalesced (single in-flight job), and the
 *       id is loaded-or-created under a lock, so concurrency can never create two ids.</li>
 *   <li><b>Harmless failures.</b> Nothing here throws; network errors only leave the throttle state.</li>
 * </ul>
 */
public final class UsageHeartbeatService {
    public static final Duration SUCCESS_INTERVAL = Duration.ofHours(24);
    public static final Duration RETRY_AFTER_FAILURE = Duration.ofHours(6);

    private final InstallationIdentityStore store;
    private final HeartbeatTransport transport;
    private final GameZoneLiveDataRuntime runtime;
    private final Supplier<String> version;
    private final Supplier<Instant> clock;

    private final AtomicBoolean inFlight = new AtomicBoolean(false);
    private final AtomicBoolean attemptedThisSession = new AtomicBoolean(false);
    private final Object identityLock = new Object();
    private InstallationIdentity identity;

    /**
     * @param transport null = no endpoint configured: the service is inactive.
     */
    public UsageHeartbeatService(InstallationIdentityStore store, HeartbeatTransport transport,
                                 GameZoneLiveDataRuntime runtime, Supplier<String> version) {
        this(store, transport, runtime, version, Instant::now);
    }

    UsageHeartbeatService(InstallationIdentityStore store, HeartbeatTransport transport,
                          GameZoneLiveDataRuntime runtime, Supplier<String> version, Supplier<Instant> clock) {
        this.store = store;
        this.transport = transport;
        this.runtime = runtime;
        this.version = version;
        this.clock = clock;
    }

    /** The production wiring: active only once {@link HeartbeatEndpoint#PRODUCTION} is set. */
    public static UsageHeartbeatService createDefault(GameZoneLiveDataRuntime runtime, Supplier<String> version) {
        return createDefault(runtime, version, false);
    }

    /**
     * @param developmentEnvironment true when running from the development workspace ({@code runClient},
     *        client game tests) - the counter is then inactive, so developer and automated test runs can
     *        never add installations to the real statistics. Decided by the caller, so this package
     *        never touches Fabric or Minecraft code.
     */
    public static UsageHeartbeatService createDefault(GameZoneLiveDataRuntime runtime, Supplier<String> version, boolean developmentEnvironment) {
        HeartbeatTransport transport = HeartbeatEndpoint.PRODUCTION
                .filter(HeartbeatEndpoint::isValid)
                .filter(uri -> !developmentEnvironment)
                .<HeartbeatTransport>map(uri -> new HttpHeartbeatTransport(runtime, uri))
                .orElse(null);
        return new UsageHeartbeatService(JsonInstallationIdentityStore.createDefault(), transport, runtime, version);
    }

    /** Whether the counter can ever send anything (an endpoint is configured). */
    public boolean isActive() {
        return transport != null;
    }

    /**
     * Called when the player is actively using the game (joined a world/server). Returns
     * immediately; the work happens on the shared worker, at most once per session.
     */
    public void onClientActive() {
        if (!isActive()) return;
        if (attemptedThisSession.get() || !inFlight.compareAndSet(false, true)) return;
        boolean accepted = runtime.submit(() -> {
            try {
                runOnce();
            } finally {
                inFlight.set(false);
            }
        });
        if (!accepted) inFlight.set(false); // shared queue full: harmless, a later join tries again
    }

    /** One throttled heartbeat attempt. Package-private for tests; never throws. */
    void runOnce() {
        try {
            if (!attemptedThisSession.compareAndSet(false, true)) return;
            InstallationIdentity current = loadOrCreateIdentity();
            Instant now = clock.get();
            if (!isDue(current, now)) return;
            current = current.withAttemptAt(now);
            persist(current);
            HeartbeatPayload payload = new HeartbeatPayload(current.installationId(), version.get());
            if (transport.send(payload)) {
                persist(current.withSuccessAt(clock.get()));
            }
        } catch (Exception ignored) {
            // The counter must never affect the game; a failure simply waits for the next due time.
        }
    }

    /** Due if never accepted or the last success is 24 h old - and no attempt within the last 6 h. */
    static boolean isDue(InstallationIdentity identity, Instant now) {
        Instant success = identity.lastSuccessAt();
        if (success != null && Duration.between(success, now).compareTo(SUCCESS_INTERVAL) < 0) return false;
        Instant attempt = identity.lastAttemptAt();
        return attempt == null || Duration.between(attempt, now).compareTo(RETRY_AFTER_FAILURE) >= 0;
    }

    /** The persisted identity, creating and saving a fresh random one exactly once if none exists. */
    InstallationIdentity loadOrCreateIdentity() {
        synchronized (identityLock) {
            if (identity == null) {
                Optional<InstallationIdentity> stored = store.load();
                if (stored.isPresent()) {
                    identity = stored.get();
                } else {
                    identity = InstallationIdentity.generate();
                    store.save(identity);
                }
            }
            return identity;
        }
    }

    private void persist(InstallationIdentity updated) {
        synchronized (identityLock) {
            identity = updated;
            store.save(updated);
        }
    }
}
