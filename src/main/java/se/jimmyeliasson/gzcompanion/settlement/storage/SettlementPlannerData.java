package se.jimmyeliasson.gzcompanion.settlement.storage;

import java.util.HashMap;
import java.util.Map;

/**
 * Versioned root schema for {@code config/gzcompanion/settlement-planner.json}. Mirrors
 * {@code ChestIndexData}'s per-context isolation exactly.
 */
public record SettlementPlannerData(int schemaVersion, Map<String, SettlementPlannerProfile> profiles) {
    public static final int CURRENT_SCHEMA = 1;

    public SettlementPlannerData {
        profiles = profiles != null ? new HashMap<>(profiles) : new HashMap<>();
    }

    public static SettlementPlannerData empty() {
        return new SettlementPlannerData(CURRENT_SCHEMA, new HashMap<>());
    }

    public SettlementPlannerProfile getProfile(String contextKey) {
        return profiles.getOrDefault(contextKey, SettlementPlannerProfile.empty());
    }

    public SettlementPlannerData withProfile(String contextKey, SettlementPlannerProfile updated) {
        Map<String, SettlementPlannerProfile> copy = new HashMap<>(profiles);
        copy.put(contextKey, updated);
        return new SettlementPlannerData(schemaVersion, copy);
    }
}
