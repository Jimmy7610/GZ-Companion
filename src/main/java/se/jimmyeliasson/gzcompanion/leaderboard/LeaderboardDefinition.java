package se.jimmyeliasson.gzcompanion.leaderboard;

/**
 * Static metadata for one of GameZone's published leaderboards - everything Companion needs to
 * fetch and label it, discovered once from the real public site and kept in exactly one place,
 * {@link GameZoneLeaderboardRegistry} (see docs/LEADERBOARDS.md). Never scattered across UI code.
 *
 * @param id             Companion's stable internal identifier. For {@link #isServerStat()} boards
 *                        this is Companion's own invention (GameZone's site exposes no per-board
 *                        page/slug for them); for every other board it is the EXACT slug GameZone's
 *                        own site uses in {@code https://www.gamezonemc.se/leaderboards/<id>} - kept
 *                        identical deliberately so the URL a human clicks "Visa hela tabellen" to
 *                        reach on the real site is exactly the one Companion fetches.
 * @param group          which of the 4 top-level families this board belongs to.
 * @param title          GameZone's own Swedish board title (e.g. "Rikaste spelare").
 * @param description    GameZone's own short description line shown under the title.
 * @param valueLabel     the column header GameZone uses for this board's primary metric (e.g. "Coins").
 * @param isServerStat   true for the 5 SERVERN boards, which are a single aggregate value (no rank
 *                        list, no per-board page) rather than a ranked top-N table - see
 *                        {@link LeaderboardEntry} doc comment and docs/LEADERBOARDS.md.
 */
public record LeaderboardDefinition(
        String id,
        LeaderboardGroup group,
        String title,
        String description,
        String valueLabel,
        boolean isServerStat
) {
    public LeaderboardDefinition {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id must not be blank");
        if (group == null) throw new IllegalArgumentException("group must not be null");
        if (title == null || title.isBlank()) throw new IllegalArgumentException("title must not be blank");
    }
}
