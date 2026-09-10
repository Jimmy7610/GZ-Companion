package se.jimmyeliasson.gzcompanion.building.storage;

/**
 * The fixed set of LOCAL planning checklist items a {@link BuildingPlan} tracks completion for.
 * These are never claims of server-side completion - see {@code docs/BUILDING-PLANNER.md}.
 */
public enum BuildingRequirementKey {
    LICENSE("Licens"),
    LEVEL("Settlement-nivå"),
    SIZE("Storlek"),
    WALLS("Väggar"),
    ROOF("Tak"),
    SPECIAL("Specialkrav");

    private final String displayName;

    BuildingRequirementKey(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
