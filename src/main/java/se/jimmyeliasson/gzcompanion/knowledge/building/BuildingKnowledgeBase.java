package se.jimmyeliasson.gzcompanion.knowledge.building;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Immutable, loaded-once GameZone Building System 1.0 knowledge. All search/lookup runs purely
 * in memory against data parsed once at load time.
 */
public final class BuildingKnowledgeBase {
    private final List<SettlementBuilding> buildings;
    private final GlobalBuildingRules globalRules;
    private final List<String> loadWarnings;
    private final Map<String, SettlementBuilding> byId;

    public BuildingKnowledgeBase(List<SettlementBuilding> buildings, GlobalBuildingRules globalRules, List<String> loadWarnings) {
        this.buildings = buildings != null ? List.copyOf(buildings) : List.of();
        this.globalRules = globalRules != null ? globalRules : GlobalBuildingRules.empty();
        this.loadWarnings = loadWarnings != null ? List.copyOf(loadWarnings) : List.of();

        Map<String, SettlementBuilding> map = new LinkedHashMap<>();
        for (SettlementBuilding building : this.buildings) {
            map.put(building.id(), building);
        }
        this.byId = Map.copyOf(map);
    }

    public static BuildingKnowledgeBase empty() {
        return new BuildingKnowledgeBase(List.of(), GlobalBuildingRules.empty(), List.of());
    }

    public List<SettlementBuilding> buildings() {
        return buildings;
    }

    public GlobalBuildingRules globalRules() {
        return globalRules;
    }

    public List<String> loadWarnings() {
        return loadWarnings;
    }

    public int size() {
        return buildings.size();
    }

    public Optional<SettlementBuilding> byId(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    /** Local-only search across name, id, and bonus text. Never touches disk or network. */
    public List<SettlementBuilding> search(String query) {
        String q = (query != null && !query.isBlank()) ? query.trim().toLowerCase(Locale.ROOT) : null;
        if (q == null) return buildings;

        List<SettlementBuilding> result = new ArrayList<>();
        for (SettlementBuilding building : buildings) {
            String haystack = (building.name() + ' ' + building.id() + ' ' + building.mainBonus()).toLowerCase(Locale.ROOT);
            if (haystack.contains(q)) {
                result.add(building);
            }
        }
        return result;
    }
}
