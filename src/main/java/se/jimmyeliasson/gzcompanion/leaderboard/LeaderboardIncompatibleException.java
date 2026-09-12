package se.jimmyeliasson.gzcompanion.leaderboard;

/**
 * Thrown by {@link LeaderboardHtmlParser} when the fetched page's structure doesn't match what this
 * Companion version knows how to read AT ALL (the expected container marker is entirely absent) -
 * as opposed to an individual malformed row, which is silently skipped rather than failing the
 * whole board. This is the one signal that maps to {@link LeaderboardStatus#INCOMPATIBLE}: GameZone
 * most likely changed its site's markup and this parser needs an update, not a transient network
 * problem.
 */
public final class LeaderboardIncompatibleException extends Exception {
    public LeaderboardIncompatibleException(String message) {
        super(message);
    }
}
