package se.jimmyeliasson.gzcompanion.leaderboard;

import java.util.List;

/**
 * The outcome of one {@link LeaderboardSource#fetch(LeaderboardDefinition)} call. A sealed result
 * rather than a thrown exception - {@link LeaderboardManager} needs to store SOME outcome for a
 * board no matter what went wrong, and a sealed return type makes every call site handle all three
 * cases explicitly rather than risking an uncaught exception taking down an unrelated board's cache.
 */
public sealed interface LeaderboardFetchResult {
    record Success(List<LeaderboardEntry> entries) implements LeaderboardFetchResult {
        public Success {
            entries = entries == null ? List.of() : List.copyOf(entries);
        }
    }

    /** Network-level failure: offline, timeout, non-2xx, refused host, oversized response. */
    record Unavailable(String reason) implements LeaderboardFetchResult {}

    /** The page was reachable but its structure no longer matches what this parser understands. */
    record Incompatible(String reason) implements LeaderboardFetchResult {}
}
