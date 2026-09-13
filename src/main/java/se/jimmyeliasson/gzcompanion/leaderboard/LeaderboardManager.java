package se.jimmyeliasson.gzcompanion.leaderboard;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * Per-board cache and fetch state machine for GZ Companion's Leaderboards tab - the single source
 * of truth {@code LeaderboardsTabComponent} reads from and triggers refreshes through, exactly
 * mirroring {@code UpdateManager}'s "one authoritative background-executor-backed manager, render
 * code only ever reads a cheap snapshot" pattern, but keyed per board id since boards are fetched
 * and cached independently of each other.
 *
 * <p>Unlike {@code UpdateManager}, this never starts a background poll on its own - Leaderboards
 * data is fetched ONLY when a board is actually opened/selected (see {@link #ensureFresh}), per
 * this feature's explicit "fetch only when required" network policy (docs/LEADERBOARDS.md).
 *
 * <p>All network I/O happens on a dedicated single-thread background executor - {@link #ensureFresh}
 * and {@link #manualRefresh} both return immediately; render code only ever calls
 * {@link #getSnapshot(String)}, a cheap map read, never networking itself.
 *
 * <h2>Request scheduling - "latest relevant request wins" (2026-09-13 follow-up fix)</h2>
 * An earlier version submitted one executor job per newly-selected uncached board. Since the
 * executor is single-threaded, only one fetch ever ran at a time, but rapidly cycling the selector
 * (A -&gt; B -&gt; C -&gt; D while A was still fetching) queued B, C, and D as separate jobs that
 * ALL eventually ran, even though the player only cared about D by the time A finished - wasted
 * requests to GameZone, and D's own data was delayed behind two obsolete fetches.
 *
 * <p>This class now tracks at most ONE {@link #activeFetch active fetch} and at most ONE
 * {@link #pendingRequest pending request} at a time, both guarded by {@link #scheduleLock} - never
 * an executor-level queue of many jobs:
 * <ul>
 *   <li>A request for the board already {@link #activeFetch} is coalesced (a no-op) - the same
 *       "don't start a duplicate fetch for what's already in flight" guarantee as before.</li>
 *   <li>A request while a DIFFERENT board is active becomes the pending slot - overwriting whatever
 *       was pending before, so only the LATEST request survives (this is what fixes B/C/D above:
 *       each new auto-request for a different board simply replaces the previous pending one).</li>
 *   <li>A MANUAL request always claims the pending slot, even over a pending AUTO request (a
 *       deliberate "Uppdatera" click is a stronger signal of intent than merely having cycled past
 *       a board). An AUTO request, however, never displaces an already-pending MANUAL one - a mere
 *       selector cycle must not silently cancel a click the player just made.</li>
 *   <li>When the active fetch completes, the pending slot (if any) is atomically taken and
 *       immediately started as the new active fetch - a self-perpetuating chain, never a poll loop
 *       and never more than one executor job in flight or queued at a time.</li>
 *   <li>A board sitting in the pending slot is NEVER marked {@link LeaderboardStatus#LOADING} -
 *       only the board that actually becomes {@link #activeFetch} is, so a superseded board (B, C
 *       above) is never left stuck showing "loading" for a fetch that never happened.</li>
 * </ul>
 */
public final class LeaderboardManager {
    /** Automatic (board-opened/selector-cycled) refreshes never happen more often than this per board. */
    public static final Duration AUTO_REFRESH_INTERVAL = Duration.ofSeconds(60);
    /** A manual "Uppdatera" click may bypass the automatic interval, but not more often than this. */
    public static final Duration MANUAL_REFRESH_COOLDOWN = Duration.ofSeconds(12);

    /** Identifies which adapter/parsing generation produced the cached data - surfaced in diagnostics
     * only (see docs/LEADERBOARDS.md); bump this if the HTML contract this reads against changes. */
    public static final String ADAPTER_VERSION = "gamezonemc.se-html-v1";

    private final LeaderboardSource source;
    private final Supplier<Instant> clock;
    private final Map<String, LeaderboardSnapshot> snapshots = new ConcurrentHashMap<>();
    private final Map<String, Instant> lastFetchAttemptAt = new ConcurrentHashMap<>();
    private final ExecutorService executor;

    /** Guards {@link #activeFetch} and {@link #pendingRequest} - see class doc comment. A plain
     * monitor is sufficient: every critical section is a handful of field reads/writes plus (at
     * most) submitting a job to the executor, which itself never blocks. */
    private final Object scheduleLock = new Object();
    private LeaderboardDefinition activeFetch;
    private PendingRequest pendingRequest;

    private record PendingRequest(LeaderboardDefinition definition, boolean manual) {}

    private volatile Instant lastSuccessfulRefreshAt;
    private volatile String lastErrorCategory;

    public LeaderboardManager(LeaderboardSource source) {
        this(source, Instant::now);
    }

    /** Test-only seam - an injectable clock lets tests deterministically prove cooldown-EXPIRY
     * behavior (not just "still within window") without ever sleeping a real 60/12 seconds. */
    LeaderboardManager(LeaderboardSource source, Supplier<Instant> clock) {
        this.source = source;
        this.clock = clock;
        this.executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "gzcompanion-leaderboards");
            thread.setDaemon(true);
            return thread;
        });
    }

    /** Cheap, non-blocking read - never null; a never-fetched board returns {@link LeaderboardSnapshot#idle}. */
    public LeaderboardSnapshot getSnapshot(LeaderboardDefinition definition) {
        if (definition == null) return null;
        return snapshots.getOrDefault(definition.id(), LeaderboardSnapshot.idle(definition));
    }

    /**
     * Requests a background fetch only if this board has never been fetched, or its last fetch
     * ATTEMPT (success or failure - a failing board must not be hammered every frame either) is
     * older than {@link #AUTO_REFRESH_INTERVAL}. Safe to call every render frame the board is
     * visible - the interval check makes repeated calls for an already-fresh board a cheap no-op,
     * and the scheduling policy above (never a growing queue) makes rapid selector cycling safe.
     */
    public void ensureFresh(LeaderboardDefinition definition) {
        if (definition == null) return;
        Instant lastAttempt = lastFetchAttemptAt.get(definition.id());
        if (lastAttempt != null && Duration.between(lastAttempt, clock.get()).compareTo(AUTO_REFRESH_INTERVAL) < 0) {
            return;
        }
        requestFetch(definition, false);
    }

    /**
     * The "Uppdatera" button's action - bypasses {@link #AUTO_REFRESH_INTERVAL} but still enforces
     * its own shorter {@link #MANUAL_REFRESH_COOLDOWN} to prevent spam-clicking. Returns false
     * (a no-op) if the cooldown hasn't elapsed, the board is already the active fetch, or it is
     * already the pending request.
     */
    public boolean manualRefresh(LeaderboardDefinition definition) {
        if (definition == null) return false;
        if (!canManualRefresh(definition)) return false;
        return requestFetch(definition, true);
    }

    /** Whether {@link #manualRefresh} would actually do anything right now - drives the button's enabled state. */
    public boolean canManualRefresh(LeaderboardDefinition definition) {
        if (definition == null) return false;
        Instant lastAttempt = lastFetchAttemptAt.get(definition.id());
        boolean cooldownElapsed = lastAttempt == null || Duration.between(lastAttempt, clock.get()).compareTo(MANUAL_REFRESH_COOLDOWN) >= 0;
        if (!cooldownElapsed) return false;
        synchronized (scheduleLock) {
            if (definition.equals(activeFetch)) return false;
            if (pendingRequest != null && definition.equals(pendingRequest.definition())) return false;
        }
        return true;
    }

    /**
     * Implements the "latest relevant request wins" policy - see class doc comment. Never creates
     * more than one pending slot; never touches the executor's own queue directly (it always holds
     * at most the one currently-running job).
     *
     * @return true if this request was actually accepted (became the active fetch OR the new
     *         pending request); false only when it was redundant (already active, already pending,
     *         or a mere auto-cycle that a pending manual request takes priority over).
     */
    private boolean requestFetch(LeaderboardDefinition definition, boolean manual) {
        synchronized (scheduleLock) {
            if (definition.equals(activeFetch)) {
                return false; // already the one in flight right now - coalesce
            }
            if (activeFetch == null) {
                startFetchLocked(definition);
                return true;
            }
            if (manual) {
                pendingRequest = new PendingRequest(definition, true); // manual intent always claims the pending slot
                return true;
            }
            if (pendingRequest == null || !pendingRequest.manual()) {
                pendingRequest = new PendingRequest(definition, false); // auto only ever supersedes a pending AUTO (or nothing)
                return true;
            }
            return false; // a manual request is already pending - a mere auto-cycle must not displace it
        }
    }

    /** Must be called while holding {@link #scheduleLock}. Marks {@code definition} as the active
     * fetch, transitions its snapshot to LOADING (preserving any prior entries), and submits the
     * real fetch to the executor. */
    private void startFetchLocked(LeaderboardDefinition definition) {
        activeFetch = definition;
        String id = definition.id();
        lastFetchAttemptAt.put(id, clock.get());
        LeaderboardSnapshot previous = snapshots.get(id);
        snapshots.put(id, new LeaderboardSnapshot(definition, LeaderboardStatus.LOADING,
                previous != null ? previous.entries() : List.of(),
                previous != null ? previous.fetchedAt() : null, null));

        executor.execute(() -> runFetchThenAdvance(definition, previous));
    }

    /** Runs the real fetch for {@code definition} (the current active fetch), applies its result,
     * then atomically hands off to whatever is pending (if anything) - a self-perpetuating chain
     * that never leaves more than one job active or queued. */
    private void runFetchThenAdvance(LeaderboardDefinition definition, LeaderboardSnapshot previous) {
        LeaderboardFetchResult result = source.fetch(definition);
        snapshots.put(definition.id(), applyResult(definition, previous, result));

        synchronized (scheduleLock) {
            activeFetch = null;
            if (pendingRequest != null) {
                LeaderboardDefinition next = pendingRequest.definition();
                pendingRequest = null;
                startFetchLocked(next);
            }
        }
    }

    private LeaderboardSnapshot applyResult(LeaderboardDefinition definition, LeaderboardSnapshot previous, LeaderboardFetchResult result) {
        boolean hadPriorEntries = previous != null && previous.hasEntries();
        return switch (result) {
            case LeaderboardFetchResult.Success success -> {
                lastSuccessfulRefreshAt = clock.get();
                lastErrorCategory = null;
                yield new LeaderboardSnapshot(definition, LeaderboardStatus.LOADED, success.entries(), clock.get(), null);
            }
            case LeaderboardFetchResult.Unavailable unavailable -> {
                lastErrorCategory = "UNAVAILABLE";
                yield hadPriorEntries
                        ? new LeaderboardSnapshot(definition, LeaderboardStatus.STALE, previous.entries(), previous.fetchedAt(), unavailable.reason())
                        : new LeaderboardSnapshot(definition, LeaderboardStatus.UNAVAILABLE, List.of(), null, unavailable.reason());
            }
            case LeaderboardFetchResult.Incompatible incompatible -> {
                lastErrorCategory = "INCOMPATIBLE";
                yield hadPriorEntries
                        ? new LeaderboardSnapshot(definition, LeaderboardStatus.STALE, previous.entries(), previous.fetchedAt(), incompatible.reason())
                        : new LeaderboardSnapshot(definition, LeaderboardStatus.INCOMPATIBLE, List.of(), null, incompatible.reason());
            }
        };
    }

    /** Diagnostics only (see docs/LEADERBOARDS.md) - never surfaces full fetched player data, only summary metadata. */
    public record DiagnosticsSummary(int cachedBoardCount, Instant lastSuccessfulRefreshAt, String lastErrorCategory, String adapterVersion) {}

    public DiagnosticsSummary getDiagnosticsSummary() {
        return new DiagnosticsSummary(snapshots.size(), lastSuccessfulRefreshAt, lastErrorCategory, ADAPTER_VERSION);
    }
}
