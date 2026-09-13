package se.jimmyeliasson.gzcompanion.gamezone.net;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The ONE shared background-execution and HTTP transport layer for every "public GameZone web
 * data" feature (Leaderboards today; Bounty Board/Chronicles/Live Relics/Settlement Explorer/etc.
 * later). A single {@code CompanionSession}-owned instance of this class is handed to every such
 * feature's source/manager instead of each one constructing its own {@link ExecutorService}/
 * {@link HttpClient} - see docs/PERFORMANCE-AUDIT-ALPHA4.md's "2026-09-13 follow-up" section for
 * the audit finding this fixes: after only two live-network features (Leaderboards, the updater),
 * three separate {@code HttpClient} instances and two separate custom executors already existed.
 * Left unchecked, every future GameZone module would add one more of each - this class is the
 * permanent fix, enforced structurally by {@code ThreadingInfrastructureRulesTest}'s allow-list.
 *
 * <p><b>Deliberately NOT shared with the updater.</b> {@code UpdateManager}/{@code
 * GitHubReleaseSource}/{@code HttpUpdateByteSource} keep their own executor and {@code HttpClient}
 * on purpose - the updater talks to a completely different host (GitHub) with different
 * requirements (redirect-following, multi-minute installer download timeouts, independent 45-minute
 * scheduling) that have nothing to do with GameZone's own public web pages. Merging them would only
 * couple two unrelated systems together for no real thread-count benefit.
 *
 * <p><b>Lazy by design.</b> Neither the shared executor nor the shared {@link HttpClient} is created
 * in this class's constructor - each is created on its OWN first use, the first time any GameZone
 * live-data feature actually needs it. This closes the exact gap the audit found in the pre-fix
 * {@code GameZoneLeaderboardSource}, which built its {@code HttpClient} eagerly at {@code
 * CompanionSession} construction (mod startup) even though Leaderboards might never be opened in
 * that session. With this class, merely starting Companion (or even opening every OTHER tab)
 * creates neither the worker thread nor the HTTP transport - proven by {@code
 * GameZoneLiveDataRuntimeTest} and {@code LeaderboardManagerTest}.
 *
 * <p><b>Security/privacy posture preserved.</b> This class only supplies the shared, generic
 * transport (connect timeout, no automatic redirect-following, a static User-Agent). Each module
 * remains responsible for its OWN endpoint-specific policy - the allow-listed host, per-request
 * timeout, response-size ceiling, and response parsing - exactly as {@link
 * se.jimmyeliasson.gzcompanion.leaderboard.GameZoneLeaderboardSource} already did and continues to
 * do after migrating onto this shared transport. Nothing here ever attaches a cookie, session, or
 * Minecraft player/world identity to a request.
 */
public final class GameZoneLiveDataRuntime {
    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(10);

    private final String userAgent;
    private final Duration connectTimeout;

    private final Object lock = new Object();
    private ExecutorService executor;
    private HttpClient httpClient;

    public GameZoneLiveDataRuntime(String companionVersion) {
        this(companionVersion, DEFAULT_CONNECT_TIMEOUT);
    }

    /** Test-only seam - a shorter connect timeout lets tests point at a local mock server and fail
     * fast on a deliberately-closed port, instead of waiting out the real 10s production timeout. */
    GameZoneLiveDataRuntime(String companionVersion, Duration connectTimeout) {
        this.userAgent = "GZ-Companion/" + companionVersion + " (+https://github.com/Jimmy7610/GZ-Companion)";
        this.connectTimeout = connectTimeout;
    }

    /**
     * The one shared daemon executor for GameZone public live-data work. Created on first call,
     * never before - constructing this class, or any feature's manager/source that merely HOLDS a
     * reference to it, never creates the thread by itself.
     */
    public ExecutorService executor() {
        synchronized (lock) {
            if (executor == null) {
                executor = Executors.newSingleThreadExecutor(runnable -> {
                    Thread thread = new Thread(runnable, "gzcompanion-gamezone-live");
                    thread.setDaemon(true);
                    return thread;
                });
            }
            return executor;
        }
    }

    /**
     * The one shared {@link HttpClient} for GameZone public live-data requests - HTTPS-capable,
     * never automatically follows redirects (a redirect away from the intended GameZone page is
     * always treated as a failure by the caller, never silently trusted), a bounded connect
     * timeout, and reused for every request from every GameZone live-data module. Created on first
     * call, never before.
     */
    public HttpClient httpClient() {
        synchronized (lock) {
            if (httpClient == null) {
                httpClient = HttpClient.newBuilder()
                        .connectTimeout(connectTimeout)
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build();
            }
            return httpClient;
        }
    }

    /** The single static User-Agent every GameZone live-data request must send - carries this
     * feature family's name/version, never any player/session identity. */
    public String userAgent() {
        return userAgent;
    }

    /** Test-only introspection - never used by production code. Public (rather than
     * package-private) specifically so tests for every feature that consumes this shared runtime
     * (Leaderboards today, others later) can assert laziness from their own package, without each
     * one needing a same-package test-only wrapper. */
    public boolean isExecutorInitializedForTesting() {
        synchronized (lock) {
            return executor != null;
        }
    }

    /** Test-only introspection - never used by production code. See {@link
     * #isExecutorInitializedForTesting()} for why this is public. */
    public boolean isHttpClientInitializedForTesting() {
        synchronized (lock) {
            return httpClient != null;
        }
    }
}
