package se.jimmyeliasson.gzcompanion.leaderboard;

/**
 * One ranked row, GameZone's own semantics preserved exactly rather than forced into a single
 * numeric primitive (a settlement's "Bäst ticket-differens" and a player's "Mest spelad tid" don't
 * share a shape) - see docs/LEADERBOARDS.md's "board-specific values" section.
 *
 * <p>{@code primaryValue}/{@code secondaryValue} are GameZone's own pre-formatted display text
 * (e.g. {@code "28 297 019 coins"}, {@code "11 d 13 h"}) - never reformatted/reparsed into a number
 * by Companion, so GameZone's own grouping/units/locale are shown byte-for-byte. A board like
 * Monsterjägare that publishes a combined "primary • secondary" string has already been split by
 * {@link LeaderboardHtmlParser} before this record is constructed.
 *
 * @param rank          1-based position. Always &gt;= 1.
 * @param displayName   the player/settlement/company/server name GameZone shows for this row.
 * @param primaryValue  GameZone's own formatted value text for this board's main metric.
 * @param primaryLabel  the unit/column label for {@code primaryValue} (e.g. {@code "Coins"}, {@code "Level"}).
 * @param secondaryValue optional extra value some boards legitimately publish (e.g. Monsterjägare's
 *                        total bounty coins earned) - null when the board has none.
 * @param secondaryLabel optional label for {@code secondaryValue} - null exactly when it is.
 */
public record LeaderboardEntry(
        int rank,
        String displayName,
        String primaryValue,
        String primaryLabel,
        String secondaryValue,
        String secondaryLabel
) {
    public LeaderboardEntry {
        if (rank < 1) {
            throw new IllegalArgumentException("rank must be >= 1, was " + rank);
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("displayName must not be blank");
        }
        if (primaryValue == null || primaryValue.isBlank()) {
            throw new IllegalArgumentException("primaryValue must not be blank");
        }
        // Labels come from GameZoneLeaderboardRegistry's own valueLabel, not scraped per-row - a
        // board whose label is genuinely uninformative may legitimately pass "" (never null).
        if (primaryLabel == null) {
            throw new IllegalArgumentException("primaryLabel must not be null (blank is allowed)");
        }
        if ((secondaryValue == null) != (secondaryLabel == null)) {
            throw new IllegalArgumentException("secondaryValue and secondaryLabel must both be present or both be absent");
        }
    }

    public boolean hasSecondaryValue() {
        return secondaryValue != null;
    }

    /** Convenience constructor for the common case of no secondary value. */
    public static LeaderboardEntry of(int rank, String displayName, String primaryValue, String primaryLabel) {
        return new LeaderboardEntry(rank, displayName, primaryValue, primaryLabel, null, null);
    }
}
