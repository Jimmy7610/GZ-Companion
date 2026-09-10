package se.jimmyeliasson.gzcompanion.settlement.storage;

/**
 * Semantic runtime readiness status of the Settlement planner store, mirroring
 * {@code ChestManagerStatus}'s pattern so {@code FeatureManager} can drive real module readiness.
 */
public enum SettlementPlannerStatus {
    LOADED("Laddad", true),
    UNAVAILABLE("Ej tillgänglig", false),
    ERROR("Fel vid inläsning", false),
    INCOMPATIBLE("Inkompatibelt schema", false);

    private final String displayName;
    private final boolean available;

    SettlementPlannerStatus(String displayName, boolean available) {
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
