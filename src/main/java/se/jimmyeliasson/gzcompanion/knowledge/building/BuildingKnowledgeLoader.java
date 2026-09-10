package se.jimmyeliasson.gzcompanion.knowledge.building;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.jimmyeliasson.gzcompanion.knowledge.common.KnowledgeLoadResult;
import se.jimmyeliasson.gzcompanion.knowledge.common.VerificationMetadata;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Loads the bundled GameZone Building System 1.0 catalog from {@code gamezone-pack/buildings.json}
 * (a read-only classpath resource). Mirrors {@code CommandKnowledgeLoader}/
 * {@code SettlementKnowledgeLoader} exactly.
 */
public final class BuildingKnowledgeLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(BuildingKnowledgeLoader.class);
    private static final String DEFAULT_RESOURCE_PATH = "/gamezone-pack/buildings.json";
    private static final int CURRENT_SCHEMA = 1;

    private final String resourcePath;

    public BuildingKnowledgeLoader() {
        this(DEFAULT_RESOURCE_PATH);
    }

    public BuildingKnowledgeLoader(String resourcePath) {
        this.resourcePath = resourcePath;
    }

    public KnowledgeLoadResult<BuildingKnowledgeBase> load() {
        JsonObject root;
        try (InputStream stream = BuildingKnowledgeLoader.class.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                LOGGER.error("Building catalog resource not found: {}", resourcePath);
                return KnowledgeLoadResult.error(BuildingKnowledgeBase.empty());
            }
            try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                JsonElement element = JsonParser.parseReader(reader);
                if (element == null || !element.isJsonObject()) {
                    LOGGER.error("Building catalog {} did not contain a JSON object.", resourcePath);
                    return KnowledgeLoadResult.error(BuildingKnowledgeBase.empty());
                }
                root = element.getAsJsonObject();
            }
        } catch (Exception e) {
            LOGGER.error("Failed to read building catalog {}: {}", resourcePath, e.getMessage());
            return KnowledgeLoadResult.error(BuildingKnowledgeBase.empty());
        }

        int schemaVersion = getInt(root, "schemaVersion", 1);
        if (schemaVersion < 1) {
            LOGGER.error("Building catalog declares invalid schemaVersion {}.", schemaVersion);
            return KnowledgeLoadResult.error(BuildingKnowledgeBase.empty());
        }
        if (schemaVersion > CURRENT_SCHEMA) {
            LOGGER.warn("Building catalog schemaVersion {} is newer than this build supports ({}).", schemaVersion, CURRENT_SCHEMA);
            return KnowledgeLoadResult.incompatibleSchema(BuildingKnowledgeBase.empty());
        }

        List<String> warnings = new ArrayList<>();
        GlobalBuildingRules globalRules = parseGlobalRules(root, warnings);
        List<SettlementBuilding> buildings = parseBuildings(root, warnings);

        return KnowledgeLoadResult.loaded(new BuildingKnowledgeBase(buildings, globalRules, warnings));
    }

    private GlobalBuildingRules parseGlobalRules(JsonObject root, List<String> warnings) {
        if (!root.has("globalRules") || !root.get("globalRules").isJsonObject()) {
            warnings.add("Globala byggregler saknas i Rule Pack-filen.");
            return GlobalBuildingRules.empty();
        }
        try {
            JsonObject obj = root.getAsJsonObject("globalRules");
            int minWall = getInt(obj, "minWallCoveragePercent", 0);
            int minRoof = getInt(obj, "minRoofCoveragePercent", 0);
            boolean mustBeInside = obj.has("mustBeFullyInsideTerritory") && obj.get("mustBeFullyInsideTerritory").isJsonPrimitive()
                    && obj.get("mustBeFullyInsideTerritory").getAsBoolean();
            String process = getString(obj, "process", null);
            String note = getString(obj, "note", null);
            VerificationMetadata verification = parseVerification(obj);
            return new GlobalBuildingRules(minWall, minRoof, mustBeInside, process, note, verification);
        } catch (Exception ex) {
            warnings.add("Kunde inte tolka globala byggregler: " + ex.getMessage());
            return GlobalBuildingRules.empty();
        }
    }

    private List<SettlementBuilding> parseBuildings(JsonObject root, List<String> warnings) {
        List<SettlementBuilding> result = new ArrayList<>();
        if (!root.has("buildings") || !root.get("buildings").isJsonArray()) return result;

        Set<String> seenIds = new HashSet<>();
        for (JsonElement el : root.getAsJsonArray("buildings")) {
            if (!el.isJsonObject()) continue;
            try {
                JsonObject obj = el.getAsJsonObject();
                String id = getString(obj, "id", null);
                if (id == null || id.isBlank()) {
                    warnings.add("Hoppar över byggnad utan giltigt id.");
                    continue;
                }
                if (!seenIds.add(id)) {
                    warnings.add("Dubblerat byggnads-id ignorerat: " + id);
                    continue;
                }

                String name = getString(obj, "name", id);
                int levelRequirement = getInt(obj, "levelRequirement", 0);
                long licenseCost = obj.has("licenseCost") && obj.get("licenseCost").isJsonPrimitive() ? obj.get("licenseCost").getAsLong() : 0L;
                String mainBonus = getString(obj, "mainBonus", "");
                List<BuildingRequirement> specialRequirements = parseRequirements(obj, warnings, id);
                Integer minWidth = getNullableInt(obj, "minWidth");
                Integer minDepth = getNullableInt(obj, "minDepth");
                Integer minHeight = getNullableInt(obj, "minHeight");
                VerificationMetadata verification = parseVerification(obj);

                result.add(new SettlementBuilding(id, name, levelRequirement, licenseCost, mainBonus,
                        specialRequirements, minWidth, minDepth, minHeight, verification));
            } catch (Exception ex) {
                warnings.add("Hoppar över felformad byggnadspost: " + ex.getMessage());
            }
        }
        return result;
    }

    private List<BuildingRequirement> parseRequirements(JsonObject buildingObj, List<String> warnings, String buildingId) {
        List<BuildingRequirement> result = new ArrayList<>();
        if (!buildingObj.has("specialRequirements") || !buildingObj.get("specialRequirements").isJsonArray()) return result;

        JsonArray arr = buildingObj.getAsJsonArray("specialRequirements");
        for (JsonElement el : arr) {
            if (!el.isJsonObject()) continue;
            try {
                JsonObject obj = el.getAsJsonObject();
                String itemId = getString(obj, "itemId", null);
                String displayName = getString(obj, "displayName", null);
                int count = getInt(obj, "count", 0);
                String note = getString(obj, "note", null);
                result.add(new BuildingRequirement(itemId, displayName, count, note));
            } catch (Exception ex) {
                warnings.add("Hoppar över felformat specialkrav på byggnad " + buildingId + ": " + ex.getMessage());
            }
        }
        return result;
    }

    private VerificationMetadata parseVerification(JsonObject obj) {
        if (!obj.has("verification") || !obj.get("verification").isJsonObject()) {
            return VerificationMetadata.UNVERIFIED_DEFAULT;
        }
        JsonObject v = obj.getAsJsonObject("verification");
        String status = getString(v, "status", null);
        String sourceName = getString(v, "sourceName", null);
        String sourceReference = getString(v, "sourceReference", null);
        String lastVerified = getString(v, "lastVerified", null);
        return VerificationMetadata.of(status, sourceName, sourceReference, lastVerified);
    }

    private static int getInt(JsonObject obj, String key, int fallback) {
        return (obj.has(key) && !obj.get(key).isJsonNull() && obj.get(key).isJsonPrimitive()) ? obj.get(key).getAsInt() : fallback;
    }

    private static Integer getNullableInt(JsonObject obj, String key) {
        return (obj.has(key) && !obj.get(key).isJsonNull() && obj.get(key).isJsonPrimitive()) ? obj.get(key).getAsInt() : null;
    }

    private static String getString(JsonObject obj, String key, String fallback) {
        if (obj.has(key) && !obj.get(key).isJsonNull() && obj.get(key).isJsonPrimitive()) {
            return obj.get(key).getAsString();
        }
        return fallback;
    }
}
