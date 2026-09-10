package se.jimmyeliasson.gzcompanion.settings;

import java.util.function.UnaryOperator;

/**
 * Runtime coordinator for global GZ Companion settings. Every mutation persists immediately -
 * there is no separate "Save" step, matching the pattern already established for Settlement/
 * Building/MarketWatch local state.
 */
public class SettingsManager {
    private final SettingsStore store;
    private CompanionSettings settings = CompanionSettings.defaults();
    private SettingsStatus status = SettingsStatus.UNAVAILABLE;

    public SettingsManager(SettingsStore store) {
        this.store = store;
    }

    public void initialize() {
        try {
            SettingsLoadResult result = store.load();
            if (result == null) {
                this.settings = CompanionSettings.defaults();
                this.status = SettingsStatus.ERROR;
                return;
            }
            switch (result.outcome()) {
                case NOT_FOUND, LOADED, CORRUPT_RECOVERED -> {
                    this.settings = result.settings();
                    this.status = SettingsStatus.LOADED;
                }
                case INCOMPATIBLE_SCHEMA -> {
                    this.settings = CompanionSettings.defaults();
                    this.status = SettingsStatus.INCOMPATIBLE;
                }
                case ERROR -> {
                    this.settings = CompanionSettings.defaults();
                    this.status = SettingsStatus.ERROR;
                }
            }
        } catch (Exception e) {
            this.settings = CompanionSettings.defaults();
            this.status = SettingsStatus.ERROR;
        }
    }

    public SettingsStatus getStatus() {
        return status;
    }

    public CompanionSettings getSettings() {
        return settings;
    }

    private boolean requireLoaded() {
        return status == SettingsStatus.LOADED;
    }

    public boolean setCompanionNotificationsEnabled(boolean enabled) {
        return mutate(s -> new CompanionSettings(enabled, s.gameZoneToastsEnabled(), s.showTechnicalIds(), s.showUnverifiedKnowledge(), s.useLastKnownChestDataInPlanners()));
    }

    public boolean setGameZoneToastsEnabled(boolean enabled) {
        return mutate(s -> new CompanionSettings(s.companionNotificationsEnabled(), enabled, s.showTechnicalIds(), s.showUnverifiedKnowledge(), s.useLastKnownChestDataInPlanners()));
    }

    public boolean setShowTechnicalIds(boolean enabled) {
        return mutate(s -> new CompanionSettings(s.companionNotificationsEnabled(), s.gameZoneToastsEnabled(), enabled, s.showUnverifiedKnowledge(), s.useLastKnownChestDataInPlanners()));
    }

    public boolean setShowUnverifiedKnowledge(boolean enabled) {
        return mutate(s -> new CompanionSettings(s.companionNotificationsEnabled(), s.gameZoneToastsEnabled(), s.showTechnicalIds(), enabled, s.useLastKnownChestDataInPlanners()));
    }

    public boolean setUseLastKnownChestDataInPlanners(boolean enabled) {
        return mutate(s -> new CompanionSettings(s.companionNotificationsEnabled(), s.gameZoneToastsEnabled(), s.showTechnicalIds(), s.showUnverifiedKnowledge(), enabled));
    }

    public boolean resetToDefaults() {
        if (!requireLoaded()) return false;
        settings = CompanionSettings.defaults();
        store.save(settings);
        return true;
    }

    private boolean mutate(UnaryOperator<CompanionSettings> mutator) {
        if (!requireLoaded()) return false;
        settings = mutator.apply(settings);
        store.save(settings);
        return true;
    }
}
