package se.jimmyeliasson.gzcompanion.leaderboard;

/**
 * The 4 top-level leaderboard families GameZone's public leaderboard page groups its 27 boards
 * into (as of 2026-09-13 - see docs/LEADERBOARDS.md for the discovery process and full board
 * list). Deliberately NOT one enum constant per board: GameZone can add/remove individual boards
 * without any Companion code change (see {@link GameZoneLeaderboardRegistry}), but the 4 families
 * are a stable, coarse UI concept (the group selector tabs) unlikely to change as often.
 */
public enum LeaderboardGroup {
    SPELARE("Spelare"),
    SETTLEMENTS("Settlements"),
    FORETAG("Företag"),
    SERVERN("Servern");

    private final String displayName;

    LeaderboardGroup(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
