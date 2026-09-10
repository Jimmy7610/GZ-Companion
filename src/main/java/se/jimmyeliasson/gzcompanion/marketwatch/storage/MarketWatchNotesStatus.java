package se.jimmyeliasson.gzcompanion.marketwatch.storage;

/**
 * Semantic runtime readiness status of the MarketWatch notes store, mirroring
 * {@code BuildingPlanStatus}/{@code SettlementPlannerStatus}/{@code ChestManagerStatus}.
 */
public enum MarketWatchNotesStatus {
    LOADED("Laddad", true),
    UNAVAILABLE("Ej tillgänglig", false),
    ERROR("Fel vid inläsning", false),
    INCOMPATIBLE("Inkompatibelt schema", false);

    private final String displayName;
    private final boolean available;

    MarketWatchNotesStatus(String displayName, boolean available) {
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
