package se.jimmyeliasson.gzcompanion.settings;

import se.jimmyeliasson.gzcompanion.ui.theme.UiTheme;

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
    List<String> favoritePlayers,
    boolean hideUnverifiedChatWarning,
    UiTheme uiTheme
) {
    public static final int CURRENT_SCHEMA = 1;

    public CompanionSettings {
        favoritePlayers = favoritePlayers != null ? List.copyOf(favoritePlayers) : List.of();
        uiTheme = uiTheme != null ? uiTheme : UiTheme.DEFAULT;
    }

    /**
     * Pre-theme seven-value shape; {@code uiTheme} defaults to {@link UiTheme#DEFAULT} (Minecraft),
     * so settings saved before themes existed look exactly as before. No schema bump: a missing
     * {@code uiTheme} key simply loads as the default.
     */
    public CompanionSettings(boolean companionNotificationsEnabled, boolean gameZoneToastsEnabled, boolean showTechnicalIds,
                             boolean showUnverifiedKnowledge, boolean useLastKnownChestDataInPlanners, List<String> favoritePlayers,
                             boolean hideUnverifiedChatWarning) {
        this(companionNotificationsEnabled, gameZoneToastsEnabled, showTechnicalIds, showUnverifiedKnowledge,
                useLastKnownChestDataInPlanners, favoritePlayers, hideUnverifiedChatWarning, UiTheme.DEFAULT);
    }

    /**
     * Pre-existing six-value shape; {@code hideUnverifiedChatWarning} defaults to {@code false}
     * (vanilla behavior). Like {@code favoritePlayers} before it, the new key needed no schema bump:
     * a settings.json without it simply loads with the default.
     */
    public CompanionSettings(boolean companionNotificationsEnabled, boolean gameZoneToastsEnabled, boolean showTechnicalIds,
                             boolean showUnverifiedKnowledge, boolean useLastKnownChestDataInPlanners, List<String> favoritePlayers) {
        this(companionNotificationsEnabled, gameZoneToastsEnabled, showTechnicalIds, showUnverifiedKnowledge,
                useLastKnownChestDataInPlanners, favoritePlayers, false);
    }

    public static CompanionSettings defaults() {
        return new CompanionSettings(true, true, true, true, true, List.of(), false, UiTheme.DEFAULT);
    }

    public CompanionSettings withHideUnverifiedChatWarning(boolean hide) {
        return new CompanionSettings(companionNotificationsEnabled, gameZoneToastsEnabled, showTechnicalIds, showUnverifiedKnowledge,
                useLastKnownChestDataInPlanners, favoritePlayers, hide, uiTheme);
    }

    public CompanionSettings withUiTheme(UiTheme theme) {
        return new CompanionSettings(companionNotificationsEnabled, gameZoneToastsEnabled, showTechnicalIds, showUnverifiedKnowledge,
                useLastKnownChestDataInPlanners, favoritePlayers, hideUnverifiedChatWarning, theme);
    }
}
