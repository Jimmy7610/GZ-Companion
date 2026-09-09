package se.jimmyeliasson.gzcompanion.chest.model;

/**
 * Semantic runtime readiness status of the Chest Manager, mirroring the Guide Engine's
 * {@code GuideLoadStatus} pattern so FeatureManager can drive real module readiness.
 */
public enum ChestManagerStatus {
    LOADED("Laddad", true),
    UNAVAILABLE("Ej tillgänglig", false),
    ERROR("Fel vid inläsning", false),
    INCOMPATIBLE("Inkompatibelt schema", false);

    private final String displayName;
    private final boolean available;

    ChestManagerStatus(String displayName, boolean available) {
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
