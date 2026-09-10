package se.jimmyeliasson.gzcompanion.settings;

/**
 * Local-first persistence boundary for GZ Companion's global settings.
 */
public interface SettingsStore {
    SettingsLoadResult load();

    void save(CompanionSettings settings);
}
