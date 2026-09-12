package se.jimmyeliasson.gzcompanion.leaderboard;

/**
 * Fetches ONE board's current top-10 (or, for a {@link LeaderboardDefinition#isServerStat()} board,
 * its single current value) from wherever it legitimately comes from. Never throws - every failure
 * mode is represented in {@link LeaderboardFetchResult}, so {@link LeaderboardManager} can update a
 * board's cache unconditionally without a try/catch scattered at every call site, and so one board's
 * failure can never propagate into another's.
 */
public interface LeaderboardSource {
    LeaderboardFetchResult fetch(LeaderboardDefinition definition);
}
