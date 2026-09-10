package se.jimmyeliasson.gzcompanion.building.storage;

/**
 * Semantic runtime readiness status of the Building Planner plan store, mirroring
 * {@code SettlementPlannerStatus}/{@code ChestManagerStatus}.
 */
public enum BuildingPlanStatus {
    LOADED("Laddad", true),
    UNAVAILABLE("Ej tillgänglig", false),
    ERROR("Fel vid inläsning", false),
    INCOMPATIBLE("Inkompatibelt schema", false);

    private final String displayName;
    private final boolean available;

    BuildingPlanStatus(String displayName, boolean available) {
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
