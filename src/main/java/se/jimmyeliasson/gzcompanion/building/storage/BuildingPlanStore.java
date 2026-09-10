package se.jimmyeliasson.gzcompanion.building.storage;

/**
 * Local-first persistence boundary for local Building Planner plans.
 */
public interface BuildingPlanStore {
    BuildingPlanLoadResult load();

    void save(BuildingPlanData data);
}
