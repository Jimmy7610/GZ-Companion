package se.jimmyeliasson.gzcompanion.advisor;

/**
 * Plain, Minecraft-API-free snapshot of the real local state the Advisor reasons over: the
 * Guide's current objective, the Settlement planner's chosen level range and missing-material
 * count, an active Building plan's incomplete checklist, whether MarketWatch has local notes,
 * and whether the client is currently connected to GameZone. Every field must be legitimately
 * available local/session data - never invented, never world-scanned.
 */
public record AdvisorContext(
    String guideObjectiveTitle,
    boolean guideComplete,
    Integer settlementCurrentLevel,
    Integer settlementTargetLevel,
    int settlementMissingMaterialsCount,
    String activeBuildingPlanDisplayName,
    int buildingPlanIncompleteChecklistCount,
    boolean marketWatchHasNotes,
    boolean connectedToGameZone
) {
    public static AdvisorContext empty() {
        return new AdvisorContext(null, false, null, null, 0, null, 0, false, false);
    }
}
