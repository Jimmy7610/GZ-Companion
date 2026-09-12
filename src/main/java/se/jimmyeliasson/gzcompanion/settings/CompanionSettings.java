package se.jimmyeliasson.gzcompanion.settings;

import java.util.List;

/**
 * Local, global (never world/server-bound) GZ Companion settings. Every default here is chosen
 * to preserve the exact behavior that was already shipping and human-QA-approved BEFORE this
 * setting existed - so installing this milestone never silently changes what a returning player
 * sees.
 *
 * <p>{@code favoritePlayers} is the only piece of Online-tab state persisted anywhere - just the
 * player names the user chose to favorite, in their original casing, nothing else (no online
 * snapshots, ping history, or join/leave timestamps are ever stored - see docs/ONLINE-PLAYERS.md).
 * Adding this field required no schema bump: a missing key on load simply defaults to an empty
 * list (see {@code JsonSettingsStore.getStringList}), so settings.json files saved before this
 * field existed keep loading exactly as before.
 */
public record CompanionSettings(
    boolean companionNotificationsEnabled,
    boolean gameZoneToastsEnabled,
    boolean showTechnicalIds,
    boolean showUnverifiedKnowledge,
    boolean useLastKnownChestDataInPlanners,
    List<String> favoritePlayers
) {
    public static final int CURRENT_SCHEMA = 1;

    public CompanionSettings {
        favoritePlayers = favoritePlayers != null ? List.copyOf(favoritePlayers) : List.of();
    }

    public static CompanionSettings defaults() {
        return new CompanionSettings(true, true, true, true, true, List.of());
    }
}
