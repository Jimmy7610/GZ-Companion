package se.jimmyeliasson.gzcompanion.bounty;

/**
 * Freshness/health of the cached {@link BountySnapshot} - mirrors {@code LeaderboardStatus}
 * exactly, including the same "never silently upgrade to LIVE" discipline. See
 * docs/BOUNTY-BOARD.md's "cache / stale policy" section.
 */
public enum BountyStatus {
    /** Never fetched this session - no entries to show yet. */
    IDLE,
    /** A fetch is currently in flight. Prior cached entries (if any) remain visible underneath. */
    LOADING,
    /** Fetched within the last cache window - safe to label LIVE. A LOADED snapshot may legitimately
     * have zero entries (no active bounties right now) - that is success, never an error. */
    LOADED,
    /** Cached entries (possibly zero) exist but are older than the cache window - must be labeled
     * "CACHAD", never LIVE. */
    STALE,
    /** No cached entries exist AND the most recent fetch failed at the network level (offline,
     * timeout, non-2xx, refused host, oversized response). */
    UNAVAILABLE,
    /** No cached entries exist AND the most recent fetch failed for an unexpected local reason. */
    ERROR,
    /** The endpoint was reachable but its structure no longer matches what this Companion version
     * can parse - GameZone likely changed the public contract. */
    INCOMPATIBLE;

    /** Whether this status has a real (possibly empty) successful result safe to render. */
    public boolean hasUsableData() {
        return this == LOADED || this == STALE;
    }

    /** Whether this status may ever be labeled "LIVE" - only a fetch that succeeded within the cache window. */
    public boolean isLive() {
        return this == LOADED;
    }
}
