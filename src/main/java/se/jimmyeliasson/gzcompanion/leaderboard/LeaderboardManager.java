package se.jimmyeliasson.gzcompanion.leaderboard;

import java.time.Duration;
import java.time.Instant;
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
    private final Map<String, Boolean> inFlight = new ConcurrentHashMap<>();
    private final ExecutorService executor;

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
     * Triggers a background fetch only if this board has never been fetched, or its last fetch
     * ATTEMPT (success or failure - a failing board must not be hammered every frame either) is
     * older than {@link #AUTO_REFRESH_INTERVAL}. Safe to call every render frame the board is
     * visible - the interval/in-flight checks make repeated calls a cheap no-op.
     */
    public void ensureFresh(LeaderboardDefinition definition) {
        if (definition == null) return;
        Instant lastAttempt = lastFetchAttemptAt.get(definition.id());
        if (lastAttempt != null && Duration.between(lastAttempt, clock.get()).compareTo(AUTO_REFRESH_INTERVAL) < 0) {
            return;
        }
        triggerFetch(definition);
    }

    /**
     * The "Uppdatera" button's action - bypasses {@link #AUTO_REFRESH_INTERVAL} but still enforces
     * its own shorter {@link #MANUAL_REFRESH_COOLDOWN} to prevent spam-clicking. Returns false
     * (a no-op) if the cooldown hasn't elapsed yet or a fetch is already in flight.
     */
    public boolean manualRefresh(LeaderboardDefinition definition) {
        if (definition == null) return false;
        if (!canManualRefresh(definition)) return false;
        return triggerFetch(definition);
    }

    /** Whether {@link #manualRefresh} would actually do anything right now - drives the button's enabled state. */
    public boolean canManualRefresh(LeaderboardDefinition definition) {
        if (definition == null) return false;
        if (Boolean.TRUE.equals(inFlight.get(definition.id()))) return false;
        Instant lastAttempt = lastFetchAttemptAt.get(definition.id());
        return lastAttempt == null || Duration.between(lastAttempt, clock.get()).compareTo(MANUAL_REFRESH_COOLDOWN) >= 0;
    }

    /** @return true if a fetch was actually submitted (false only when one was already in flight - concurrent refreshes are coalesced). */
    private boolean triggerFetch(LeaderboardDefinition definition) {
        String id = definition.id();
        if (Boolean.TRUE.equals(inFlight.putIfAbsent(id, Boolean.TRUE))) {
            return false; // already in flight - coalesce, never start a second concurrent request for the same board
        }
        lastFetchAttemptAt.put(id, clock.get());
        LeaderboardSnapshot previous = snapshots.get(id);
        snapshots.put(id, new LeaderboardSnapshot(definition, LeaderboardStatus.LOADING,
                previous != null ? previous.entries() : java.util.List.of(),
                previous != null ? previous.fetchedAt() : null, null));

        executor.execute(() -> {
            try {
                LeaderboardFetchResult result = source.fetch(definition);
                snapshots.put(id, applyResult(definition, previous, result));
            } finally {
                inFlight.remove(id);
            }
        });
        return true;
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
                        : new LeaderboardSnapshot(definition, LeaderboardStatus.UNAVAILABLE, java.util.List.of(), null, unavailable.reason());
            }
            case LeaderboardFetchResult.Incompatible incompatible -> {
                lastErrorCategory = "INCOMPATIBLE";
                yield hadPriorEntries
                        ? new LeaderboardSnapshot(definition, LeaderboardStatus.STALE, previous.entries(), previous.fetchedAt(), incompatible.reason())
                        : new LeaderboardSnapshot(definition, LeaderboardStatus.INCOMPATIBLE, java.util.List.of(), null, incompatible.reason());
            }
        };
    }

    /** Diagnostics only (see docs/LEADERBOARDS.md) - never surfaces full fetched player data, only summary metadata. */
    public record DiagnosticsSummary(int cachedBoardCount, Instant lastSuccessfulRefreshAt, String lastErrorCategory, String adapterVersion) {}

    public DiagnosticsSummary getDiagnosticsSummary() {
        return new DiagnosticsSummary(snapshots.size(), lastSuccessfulRefreshAt, lastErrorCategory, ADAPTER_VERSION);
    }
}
