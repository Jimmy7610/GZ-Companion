package se.jimmyeliasson.gzcompanion.building.storage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Versioned root schema for {@code config/gzcompanion/building-plans.json}. Isolated per
 * GameZone-server-vs-singleplayer context exactly like Chest Manager / Settlement planner.
 */
public record BuildingPlanData(int schemaVersion, Map<String, List<BuildingPlan>> plansByContext) {
    public static final int CURRENT_SCHEMA = 1;

    public BuildingPlanData {
        plansByContext = plansByContext != null ? new HashMap<>(plansByContext) : new HashMap<>();
    }

    public static BuildingPlanData empty() {
        return new BuildingPlanData(CURRENT_SCHEMA, new HashMap<>());
    }

    public List<BuildingPlan> getPlans(String contextKey) {
        return plansByContext.getOrDefault(contextKey, List.of());
    }

    public BuildingPlanData withPlans(String contextKey, List<BuildingPlan> updated) {
        Map<String, List<BuildingPlan>> copy = new HashMap<>(plansByContext);
        copy.put(contextKey, List.copyOf(updated));
        return new BuildingPlanData(schemaVersion, copy);
    }

    public BuildingPlanData withPlanAdded(String contextKey, BuildingPlan plan) {
        List<BuildingPlan> updated = new ArrayList<>(getPlans(contextKey));
        updated.add(plan);
        return withPlans(contextKey, updated);
    }

    public BuildingPlanData withPlanReplaced(String contextKey, BuildingPlan plan) {
        List<BuildingPlan> updated = new ArrayList<>();
        for (BuildingPlan existing : getPlans(contextKey)) {
            updated.add(existing.id().equals(plan.id()) ? plan : existing);
        }
        return withPlans(contextKey, updated);
    }

    public BuildingPlanData withPlanRemoved(String contextKey, String planId) {
        List<BuildingPlan> updated = new ArrayList<>();
        for (BuildingPlan existing : getPlans(contextKey)) {
            if (!existing.id().equals(planId)) updated.add(existing);
        }
        return withPlans(contextKey, updated);
    }
}
