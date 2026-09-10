package se.jimmyeliasson.gzcompanion.building;

import se.jimmyeliasson.gzcompanion.building.storage.BuildingPlan;
import se.jimmyeliasson.gzcompanion.building.storage.BuildingPlanData;
import se.jimmyeliasson.gzcompanion.building.storage.BuildingPlanStatus;
import se.jimmyeliasson.gzcompanion.building.storage.BuildingPlanStore;
import se.jimmyeliasson.gzcompanion.building.storage.BuildingRequirementKey;

import java.util.List;
import java.util.UUID;

/**
 * Runtime coordinator for local Building Planner plans, isolated per
 * GameZone-server-vs-singleplayer context exactly like {@code ChestManager}/
 * {@code SettlementPlannerManager}. Never claims to know GameZone's actual server-side
 * license/build/approve state - every checklist item here is a local planning note only.
 */
public class BuildingPlanManager {
    private final BuildingPlanStore store;
    private BuildingPlanData data;
    private BuildingPlanStatus status = BuildingPlanStatus.UNAVAILABLE;

    public BuildingPlanManager(BuildingPlanStore store) {
        this.store = store;
    }

    public void initialize() {
        try {
            var result = store.load();
            if (result == null) {
                this.data = BuildingPlanData.empty();
                this.status = BuildingPlanStatus.ERROR;
                return;
            }
            switch (result.outcome()) {
                case NOT_FOUND, LOADED, CORRUPT_RECOVERED -> {
                    this.data = result.data();
                    this.status = BuildingPlanStatus.LOADED;
                }
                case INCOMPATIBLE_SCHEMA -> {
                    this.data = BuildingPlanData.empty();
                    this.status = BuildingPlanStatus.INCOMPATIBLE;
                }
                case ERROR -> {
                    this.data = BuildingPlanData.empty();
                    this.status = BuildingPlanStatus.ERROR;
                }
            }
        } catch (Exception e) {
            this.data = BuildingPlanData.empty();
            this.status = BuildingPlanStatus.ERROR;
        }
    }

    public BuildingPlanStatus getStatus() {
        return status;
    }

    private boolean requireLoaded() {
        return status == BuildingPlanStatus.LOADED;
    }

    public List<BuildingPlan> getPlans(String contextKey) {
        if (contextKey == null || data == null) return List.of();
        return data.getPlans(contextKey);
    }

    public List<BuildingPlan> getPlansForBuilding(String contextKey, String buildingId) {
        return getPlans(contextKey).stream().filter(p -> p.buildingId().equals(buildingId)).toList();
    }

    public String createPlan(String contextKey, String buildingId, String planName, int width, int depth, int height, long nowMs) {
        if (!requireLoaded() || contextKey == null || buildingId == null) return null;
        String id = UUID.randomUUID().toString();
        BuildingPlan plan = new BuildingPlan(id, buildingId, planName, width, depth, height, java.util.EnumSet.noneOf(BuildingRequirementKey.class), nowMs);
        data = data.withPlanAdded(contextKey, plan);
        store.save(data);
        return id;
    }

    public boolean renamePlan(String contextKey, String planId, String newName) {
        return mutatePlan(contextKey, planId, plan -> plan.withRenamed(newName));
    }

    public boolean setDimensions(String contextKey, String planId, int width, int depth, int height) {
        return mutatePlan(contextKey, planId, plan -> plan.withDimensions(width, depth, height));
    }

    public boolean toggleRequirement(String contextKey, String planId, BuildingRequirementKey key) {
        return mutatePlan(contextKey, planId, plan -> plan.withToggledRequirement(key));
    }

    public boolean deletePlan(String contextKey, String planId) {
        if (!requireLoaded() || contextKey == null || planId == null) return false;
        data = data.withPlanRemoved(contextKey, planId);
        store.save(data);
        return true;
    }

    /** Deletes every local building plan for one context. Used by the Settings tab's "Rensa Byggplaner" action. */
    public boolean clearContext(String contextKey) {
        if (!requireLoaded() || contextKey == null) return false;
        data = data.withPlans(contextKey, List.of());
        store.save(data);
        return true;
    }

    private boolean mutatePlan(String contextKey, String planId, java.util.function.UnaryOperator<BuildingPlan> mutator) {
        if (!requireLoaded() || contextKey == null || planId == null) return false;
        BuildingPlan existing = getPlans(contextKey).stream().filter(p -> p.id().equals(planId)).findFirst().orElse(null);
        if (existing == null) return false;
        data = data.withPlanReplaced(contextKey, mutator.apply(existing));
        store.save(data);
        return true;
    }
}
