package se.jimmyeliasson.gzcompanion.settlement.storage;

/**
 * Local-first persistence boundary for the Settlement Companion planner state.
 */
public interface SettlementPlannerStore {
    SettlementPlannerLoadResult load();

    void save(SettlementPlannerData data);
}
