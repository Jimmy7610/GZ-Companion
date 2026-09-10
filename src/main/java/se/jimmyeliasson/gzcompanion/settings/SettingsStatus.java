package se.jimmyeliasson.gzcompanion.settings;

/**
 * Semantic runtime readiness status of the Settings store, mirroring
 * {@code MarketWatchNotesStatus}/{@code BuildingPlanStatus}/{@code ChestManagerStatus}.
 */
public enum SettingsStatus {
    LOADED("Laddad", true),
    UNAVAILABLE("Ej tillgänglig", false),
    ERROR("Fel vid inläsning", false),
    INCOMPATIBLE("Inkompatibelt schema", false);

    private final String displayName;
    private final boolean available;

    SettingsStatus(String displayName, boolean available) {
        this.displayName = displayName;
        this.available = available;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isAvailable() {
        return available;
    }
}
