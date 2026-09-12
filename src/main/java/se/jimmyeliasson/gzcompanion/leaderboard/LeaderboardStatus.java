package se.jimmyeliasson.gzcompanion.leaderboard;

/**
 * Freshness/health of one cached {@link LeaderboardSnapshot}. Deliberately fine-grained so the UI
 * can distinguish "never tried yet" from "tried and the site is unreachable" from "tried and the
 * site's structure no longer matches what this Companion version understands" - each needs
 * different, honest copy (see docs/LEADERBOARDS.md's "source-change behavior" section), and none of
 * them may ever be silently upgraded to LIVE.
 */
public enum LeaderboardStatus {
    /** Never fetched this session - no entries to show yet. */
    IDLE,
    /** A fetch is currently in flight. Prior cached entries (if any) remain visible underneath. */
    LOADING,
    /** Fetched within the last cache window - safe to label LIVE. */
    LOADED,
    /** Cached entries exist but are older than the cache window - must be labeled "SENAST HÄMTADE", never LIVE. */
    STALE,
    /** No cached entries exist AND the most recent fetch failed at the network level (offline, timeout, non-2xx). */
    UNAVAILABLE,
    /** No cached entries exist AND the most recent fetch failed for an unexpected local reason. */
    ERROR,
    /** The page was reachable but its structure no longer matches what this Companion version can parse - GameZone likely changed its site. */
    INCOMPATIBLE;

    /** Whether this status has real entries safe to render (LOADED or a not-yet-expired STALE cache). */
    public boolean hasUsableData() {
        return this == LOADED || this == STALE;
    }

    /** Whether this status may ever be labeled "LIVE" - only a fetch that succeeded within the cache window. */
    public boolean isLive() {
        return this == LOADED;
    }
}
