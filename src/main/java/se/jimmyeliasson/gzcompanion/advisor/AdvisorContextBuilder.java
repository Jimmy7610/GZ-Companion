package se.jimmyeliasson.gzcompanion.advisor;

import se.jimmyeliasson.gzcompanion.building.storage.BuildingPlan;
import se.jimmyeliasson.gzcompanion.building.storage.BuildingRequirementKey;
import se.jimmyeliasson.gzcompanion.core.CompanionSession;
import se.jimmyeliasson.gzcompanion.guide.GuideEngine;
import se.jimmyeliasson.gzcompanion.guide.model.GuideStep;
import se.jimmyeliasson.gzcompanion.guide.progress.GuideContext;
import se.jimmyeliasson.gzcompanion.knowledge.settlement.ItemRequirement;
import se.jimmyeliasson.gzcompanion.knowledge.settlement.LevelRangeSummary;
import se.jimmyeliasson.gzcompanion.knowledge.settlement.SettlementCatalog;
import se.jimmyeliasson.gzcompanion.settlement.storage.SettlementPlannerProfile;

import java.util.Comparator;
import java.util.List;

/**
 * Bridges the pure {@link AdvisorEngine}/{@link AdvisorContext} to the real, legitimately
 * available {@link CompanionSession} state. This is the ONLY class in the {@code advisor}
 * package allowed to touch the session/knowledge/storage layers - {@link AdvisorContext} and
 * {@link AdvisorEngine} stay plain-data and pure for testability.
 */
public final class AdvisorContextBuilder {
    private AdvisorContextBuilder() {}

    public static AdvisorContext build(CompanionSession session) {
        if (session == null) return AdvisorContext.empty();

        String guideObjectiveTitle = null;
        boolean guideComplete = false;
        GuideEngine guideEngine = session.getGuideEngine();
        if (guideEngine != null) {
            GuideContext guideContext = session.getCurrentGuideContext();
            guideComplete = guideEngine.isRequiredGuideComplete(guideContext);
            GuideStep nextStep = guideEngine.getActiveOrNextStep(guideContext);
            guideObjectiveTitle = nextStep != null ? nextStep.title() : null;
        }

        String contextKey = session.getCurrentStorageContext();

        Integer currentLevel = null;
        Integer targetLevel = null;
        int missingMaterialsCount = 0;
        if (session.getSettlementCatalogStatus().isAvailable()) {
            SettlementPlannerProfile settlementProfile = session.getSettlementPlannerManager().getProfile(contextKey);
            currentLevel = settlementProfile.currentLevel();
            targetLevel = settlementProfile.targetLevel();
            if (currentLevel != null && targetLevel != null && targetLevel > currentLevel) {
                SettlementCatalog catalog = session.getSettlementCatalog();
                LevelRangeSummary summary = catalog.levelRange(currentLevel, targetLevel);
                for (ItemRequirement req : summary.mergedItems()) {
                    int owned = settlementProfile.ownedAmount(req.mergeKey());
                    if (owned < req.count()) missingMaterialsCount++;
                }
            }
        }

        String activeBuildingPlanDisplayName = null;
        int incompleteChecklistCount = 0;
        if (session.getBuildingKnowledgeStatus().isAvailable()) {
            List<BuildingPlan> plans = session.getBuildingPlanManager().getPlans(contextKey);
            BuildingPlan mostRecentIncomplete = plans.stream()
                    .filter(p -> p.completed().size() < BuildingRequirementKey.values().length)
                    .max(Comparator.comparingLong(BuildingPlan::createdAtMs))
                    .orElse(null);
            if (mostRecentIncomplete != null) {
                incompleteChecklistCount = BuildingRequirementKey.values().length - mostRecentIncomplete.completed().size();
                activeBuildingPlanDisplayName = session.getBuildingKnowledgeBase().byId(mostRecentIncomplete.buildingId())
                        .map(b -> b.name())
                        .orElse(mostRecentIncomplete.planName());
            }
        }

        boolean connectedToGameZone = session.getBridge().isConnectedToGameZone();

        return new AdvisorContext(guideObjectiveTitle, guideComplete, currentLevel, targetLevel,
                missingMaterialsCount, activeBuildingPlanDisplayName, incompleteChecklistCount,
                false, connectedToGameZone);
    }
}
