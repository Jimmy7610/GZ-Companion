package se.jimmyeliasson.gzcompanion.leaderboard;

import java.time.Instant;
import java.util.List;

/**
 * One board's cached state, as held by {@link LeaderboardManager}. {@code entries} may be non-empty
 * even when {@code status} is {@link LeaderboardStatus#STALE}, {@link LeaderboardStatus#UNAVAILABLE},
 * or {@link LeaderboardStatus#ERROR} - a failed refresh never clears a prior successful one (see
 * "graceful degradation" in docs/LEADERBOARDS.md); the UI decides what to show from
 * {@link LeaderboardStatus#hasUsableData()}, never from {@code status == LOADED} alone.
 *
 * @param definition   the board this snapshot is for.
 * @param status       current freshness/health - see {@link LeaderboardStatus}.
 * @param entries      cached rows, most-recently-successful fetch. Empty (never null) until a fetch
 *                     has ever succeeded.
 * @param fetchedAt    when {@code entries} was captured. Null exactly when {@code entries} is empty.
 * @param errorMessage a short, non-technical-enough-to-show-if-needed reason for the current
 *                     non-LOADED status. Null when status is IDLE or LOADED.
 */
public record LeaderboardSnapshot(
        LeaderboardDefinition definition,
        LeaderboardStatus status,
        List<LeaderboardEntry> entries,
        Instant fetchedAt,
        String errorMessage
) {
    public LeaderboardSnapshot {
        entries = entries == null ? List.of() : List.copyOf(entries);
    }

    public static LeaderboardSnapshot idle(LeaderboardDefinition definition) {
        return new LeaderboardSnapshot(definition, LeaderboardStatus.IDLE, List.of(), null, null);
    }

    public boolean hasEntries() {
        return !entries.isEmpty();
    }
}
