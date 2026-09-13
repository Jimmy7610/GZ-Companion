package se.jimmyeliasson.gzcompanion.bounty;

import se.jimmyeliasson.gzcompanion.gamezone.net.GameZoneLiveDataRuntime;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;

/**
 * Cache and fetch state machine for GZ Companion's Bounty Board - the single source of truth
 * {@code BountiesTabComponent} reads from and triggers refreshes through. Mirrors {@code
 * LeaderboardManager}'s already-proven design, simplified because there is exactly ONE bounty
 * registry (not one per board) - see docs/BOUNTY-BOARD.md.
 *
 * <p>Never starts a background poll on its own - the registry is fetched ONLY when Bounties is
 * actually opened (see {@link #ensureFresh}), per this project's permanent "idle cost must not
 * grow with feature count" rule. All network I/O happens on the shared {@link
 * GameZoneLiveDataRuntime} worker - {@link #ensureFresh} and {@link #manualRefresh} both return
 * immediately; render code only ever calls {@link #getSnapshot()}, a cheap volatile read.
 *
 * <h2>Scheduling - "at most one fetch, coalesce the rest"</h2>
 * Unlike Leaderboards (many independently-keyed boards, so a request for a DIFFERENT board while
 * one is active needs a pending slot to remember it), Bounty Board has only one homogeneous
 * target: the whole registry. There is therefore nothing a pending request could ever refer to
 * that isn't already exactly what the active fetch is already going to produce - so a request
 * while one is already active simply coalesces (a no-op), with no pending slot, no queue at all.
 * This matches the task's own explicit direction: "If refresh is requested while already active:
 * coalesce it" (not "queue it").
 *
 * <h2>Shared runtime / bounded submission</h2>
 * Never constructs its own executor or {@code HttpClient} - holds a {@link GameZoneLiveDataRuntime}
 * and only calls {@link GameZoneLiveDataRuntime#submit} at the moment a fetch actually starts. If
 * the shared runtime's bounded queue rejects the job, the snapshot reverts to its previous state
 * (never left stuck LOADING) and {@link #lastFetchAttemptAt} is NOT stamped (a rejected job never
 * touched the network, so it must not count against the 60s auto-refresh floor and block a genuine
 * retry).
 */
public final class BountyManager {
    /** Automatic (tab-opened/render-loop) refreshes never happen more often than this. */
    public static final Duration AUTO_REFRESH_INTERVAL = Duration.ofSeconds(60);
    /** A manual "Uppdatera" click may bypass the automatic interval, but not more often than this. */
    public static final Duration MANUAL_REFRESH_COOLDOWN = Duration.ofSeconds(12);

    /** Identifies which adapter/parsing generation produced the cached data - surfaced in
     * diagnostics only; bump this if the public JSON contract this reads against changes. */
    public static final String ADAPTER_VERSION = "gamezonemc.se-api-bounties-v1";

    private final BountySource source;
    private final GameZoneLiveDataRuntime runtime;
    private final Supplier<Instant> clock;

    /** Guards {@link #activeFetch} and the read-modify-write of {@link #snapshot} during a
     * transition - a plain monitor is sufficient since every critical section is a handful of
     * field reads/writes plus (at most) submitting a job, which itself never blocks. */
    private final Object lock = new Object();
    private volatile BountySnapshot snapshot = BountySnapshot.idle();
    private boolean activeFetch;

    private volatile Instant lastFetchAttemptAt;
    private volatile Instant lastSuccessfulRefreshAt;
    private volatile String lastErrorCategory;

    public BountyManager(BountySource source, GameZoneLiveDataRuntime runtime) {
        this(source, runtime, Instant::now);
    }

    /** Test-only seam - an injectable clock lets tests deterministically prove cooldown-EXPIRY
     * behavior without ever sleeping a real 60/12 seconds. */
    BountyManager(BountySource source, GameZoneLiveDataRuntime runtime, Supplier<Instant> clock) {
        this.source = source;
        this.runtime = runtime;
        this.clock = clock;
    }

    /** Cheap, non-blocking read - never null. */
    public BountySnapshot getSnapshot() {
        return snapshot;
    }

    /**
     * Requests a background fetch only if the registry has never been fetched, or its last fetch
     * ATTEMPT (success or failure) is older than {@link #AUTO_REFRESH_INTERVAL}. Safe to call every
     * render frame the tab is visible - the interval check makes repeated calls a cheap no-op.
     */
    public void ensureFresh() {
        Instant lastAttempt = lastFetchAttemptAt;
        if (lastAttempt != null && Duration.between(lastAttempt, clock.get()).compareTo(AUTO_REFRESH_INTERVAL) < 0) {
            return;
        }
        requestFetch();
    }

    /**
     * The "Uppdatera" button's action - bypasses {@link #AUTO_REFRESH_INTERVAL} but still enforces
     * its own shorter {@link #MANUAL_REFRESH_COOLDOWN}. Returns false (a no-op) if the cooldown
     * hasn't elapsed, a fetch is already active, or the shared runtime rejects the submission.
     */
    public boolean manualRefresh() {
        if (!canManualRefresh()) return false;
        return requestFetch();
    }

    /** Whether {@link #manualRefresh} would actually do anything right now - drives the button's enabled state. */
    public boolean canManualRefresh() {
        Instant lastAttempt = lastFetchAttemptAt;
        boolean cooldownElapsed = lastAttempt == null || Duration.between(lastAttempt, clock.get()).compareTo(MANUAL_REFRESH_COOLDOWN) >= 0;
        if (!cooldownElapsed) return false;
        synchronized (lock) {
            return !activeFetch;
        }
    }

    /**
     * @return {@code true} iff the shared runtime actually accepted the submission (a fetch is now
     * genuinely in flight); {@code false} if a fetch was already active (coalesced) or the shared
     * runtime's bounded queue rejected it.
     */
    private boolean requestFetch() {
        BountySnapshot previous;
        synchronized (lock) {
            if (activeFetch) {
                return false; // already in flight - coalesce, no pending slot needed (see class doc comment)
            }
            previous = snapshot;
            activeFetch = true;
            snapshot = new BountySnapshot(BountyStatus.LOADING, previous.entries(), previous.fetchedAt(), null);
        }

        boolean accepted = runtime.submit(() -> runFetch(previous));
        if (accepted) {
            lastFetchAttemptAt = clock.get();
        } else {
            synchronized (lock) {
                activeFetch = false;
                snapshot = previous; // revert - never leave stuck LOADING, never stamp a fake attempt timestamp
            }
        }
        return accepted;
    }

    private void runFetch(BountySnapshot previous) {
        BountyFetchResult result = source.fetch();
        BountySnapshot next = applyResult(previous, result);
        synchronized (lock) {
            snapshot = next;
            activeFetch = false;
        }
    }

    private BountySnapshot applyResult(BountySnapshot previous, BountyFetchResult result) {
        boolean hadUsablePrior = previous.status().hasUsableData();
        return switch (result) {
            case BountyFetchResult.Success success -> {
                lastSuccessfulRefreshAt = clock.get();
                lastErrorCategory = null;
                yield new BountySnapshot(BountyStatus.LOADED, success.entries(), clock.get(), null);
            }
            case BountyFetchResult.Unavailable unavailable -> {
                lastErrorCategory = "UNAVAILABLE";
                yield hadUsablePrior
                        ? new BountySnapshot(BountyStatus.STALE, previous.entries(), previous.fetchedAt(), unavailable.reason())
                        : new BountySnapshot(BountyStatus.UNAVAILABLE, List.of(), null, unavailable.reason());
            }
            case BountyFetchResult.Incompatible incompatible -> {
                lastErrorCategory = "INCOMPATIBLE";
                yield hadUsablePrior
                        ? new BountySnapshot(BountyStatus.STALE, previous.entries(), previous.fetchedAt(), incompatible.reason())
                        : new BountySnapshot(BountyStatus.INCOMPATIBLE, List.of(), null, incompatible.reason());
            }
        };
    }

    /** Diagnostics only (see docs/BOUNTY-BOARD.md) - never surfaces clue text, exact payloads, or
     * player identity, only summary metadata. */
    public record DiagnosticsSummary(int activeBountyCount, Instant lastSuccessfulRefreshAt, String lastErrorCategory, String adapterVersion) {}

    public DiagnosticsSummary getDiagnosticsSummary() {
        return new DiagnosticsSummary(snapshot.entries().size(), lastSuccessfulRefreshAt, lastErrorCategory, ADAPTER_VERSION);
    }
}
