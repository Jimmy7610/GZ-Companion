package se.jimmyeliasson.gzcompanion.settings;

/**
 * Local, global (never world/server-bound) GZ Companion settings. Every default here is chosen
 * to preserve the exact behavior that was already shipping and human-QA-approved BEFORE this
 * setting existed - so installing this milestone never silently changes what a returning player
 * sees.
 */
public record CompanionSettings(
    boolean companionNotificationsEnabled,
    boolean gameZoneToastsEnabled,
    boolean showTechnicalIds,
    boolean showUnverifiedKnowledge,
    boolean useLastKnownChestDataInPlanners
) {
    public static final int CURRENT_SCHEMA = 1;

    public static CompanionSettings defaults() {
        return new CompanionSettings(true, true, true, true, true);
    }
}
