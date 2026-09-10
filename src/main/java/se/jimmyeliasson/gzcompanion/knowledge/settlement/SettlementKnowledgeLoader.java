package se.jimmyeliasson.gzcompanion.knowledge.settlement;

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
 * Loads the bundled current "Settlement Levels 1.0" progression ({@code settlement-levels.json})
 * and the foundation/production-category facts ({@code settlements.json}) into one combined,
 * read-only {@link SettlementCatalog}.
 *
 * <p>The two files are parsed independently: a malformed/incompatible {@code settlements.json}
 * never hides an otherwise-valid level progression, and vice versa. The overall
 * {@link KnowledgeLoadResult.Outcome} is driven by the levels file, since the 50-level
 * progression is this module's primary deliverable; the foundation/category data is merged in on
 * a best-effort basis with its own load warnings when it fails.
 */
public final class SettlementKnowledgeLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(SettlementKnowledgeLoader.class);
    private static final String DEFAULT_LEVELS_RESOURCE_PATH = "/gamezone-pack/settlement-levels.json";
    private static final String DEFAULT_FOUNDATION_RESOURCE_PATH = "/gamezone-pack/settlements.json";
    private static final int CURRENT_SCHEMA = 1;

    private final String levelsResourcePath;
    private final String foundationResourcePath;

    public SettlementKnowledgeLoader() {
        this(DEFAULT_LEVELS_RESOURCE_PATH, DEFAULT_FOUNDATION_RESOURCE_PATH);
    }

    public SettlementKnowledgeLoader(String levelsResourcePath, String foundationResourcePath) {
        this.levelsResourcePath = levelsResourcePath;
        this.foundationResourcePath = foundationResourcePath;
    }

    public KnowledgeLoadResult<SettlementCatalog> load() {
        List<String> warnings = new ArrayList<>();

        JsonObject levelsRoot = readJsonObject(levelsResourcePath, warnings);
        if (levelsRoot == null) {
            return KnowledgeLoadResult.error(SettlementCatalog.empty());
        }

        int schemaVersion = getInt(levelsRoot, "schemaVersion", 1);
        if (schemaVersion < 1) {
            LOGGER.error("Settlement levels file declares invalid schemaVersion {}.", schemaVersion);
            return KnowledgeLoadResult.error(SettlementCatalog.empty());
        }
        if (schemaVersion > CURRENT_SCHEMA) {
            LOGGER.warn("Settlement levels schemaVersion {} is newer than this build supports ({}).", schemaVersion, CURRENT_SCHEMA);
            return KnowledgeLoadResult.incompatibleSchema(SettlementCatalog.empty());
        }

        List<SettlementLevel> levels = parseLevels(levelsRoot, warnings);

        SettlementFoundation foundation = null;
        List<ProductionCategory> categories = List.of();
        VerificationMetadata categoriesVerification = VerificationMetadata.UNVERIFIED_DEFAULT;

        JsonObject foundationRoot = readJsonObject(foundationResourcePath, warnings);
        if (foundationRoot != null) {
            int foundationSchema = getInt(foundationRoot, "schemaVersion", 1);
            if (foundationSchema < 1) {
                warnings.add("settlements.json har ett ogiltigt schemaVersion och ignoreras.");
            } else if (foundationSchema > CURRENT_SCHEMA) {
                warnings.add("settlements.json har ett nyare schema än denna version stödjer och ignoreras.");
            } else {
                try {
                    foundation = parseFoundation(foundationRoot);
                } catch (Exception ex) {
                    warnings.add("Kunde inte tolka grundläggande settlement-data: " + ex.getMessage());
                }
                categories = parseCategories(foundationRoot, warnings);
                categoriesVerification = parseVerification(foundationRoot, "productionCategoriesVerification");
            }
        }

        return KnowledgeLoadResult.loaded(new SettlementCatalog(levels, foundation, categories, categoriesVerification, warnings));
    }

    private JsonObject readJsonObject(String resourcePath, List<String> warnings) {
        try (InputStream stream = SettlementKnowledgeLoader.class.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                LOGGER.error("Settlement resource not found: {}", resourcePath);
                warnings.add("Kunde inte hitta resursen " + resourcePath + ".");
                return null;
            }
            try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                JsonElement element = JsonParser.parseReader(reader);
                if (element == null || !element.isJsonObject()) {
                    LOGGER.error("Settlement resource {} did not contain a JSON object.", resourcePath);
                    warnings.add("Resursen " + resourcePath + " innehöll inte ett giltigt JSON-objekt.");
                    return null;
                }
                return element.getAsJsonObject();
            }
        } catch (Exception e) {
            LOGGER.error("Failed to read settlement resource {}: {}", resourcePath, e.getMessage());
            warnings.add("Fel vid inläsning av " + resourcePath + ": " + e.getMessage());
            return null;
        }
    }

    private List<SettlementLevel> parseLevels(JsonObject root, List<String> warnings) {
        List<SettlementLevel> result = new ArrayList<>();
        if (!root.has("levels") || !root.get("levels").isJsonArray()) return result;

        Set<Integer> seenLevels = new HashSet<>();
        for (JsonElement el : root.getAsJsonArray("levels")) {
            if (!el.isJsonObject()) continue;
            try {
                JsonObject obj = el.getAsJsonObject();
                if (!obj.has("level") || !obj.get("level").isJsonPrimitive()) {
                    warnings.add("Hoppar över settlement-nivå utan giltigt nivånummer.");
                    continue;
                }
                int levelNum = obj.get("level").getAsInt();
                if (!seenLevels.add(levelNum)) {
                    warnings.add("Dubblerad settlement-nivå ignorerad: " + levelNum);
                    continue;
                }

                String name = getString(obj, "name", "Nivå " + levelNum);
                long coinCost = obj.has("coinCost") && obj.get("coinCost").isJsonPrimitive() ? obj.get("coinCost").getAsLong() : 0L;
                String requiredBuildingName = getString(obj, "requiredBuildingName", null);
                String unlockedBuildingName = getString(obj, "unlockedBuildingName", null);
                String unlockedBuildingBonus = getString(obj, "unlockedBuildingBonus", null);
                List<ItemRequirement> items = parseItems(obj, warnings, levelNum);
                VerificationMetadata verification = parseVerification(obj, "verification");

                result.add(new SettlementLevel(levelNum, name, coinCost, items, requiredBuildingName,
                        unlockedBuildingName, unlockedBuildingBonus, verification));
            } catch (Exception ex) {
                warnings.add("Hoppar över felformad settlement-nivå: " + ex.getMessage());
            }
        }
        result.sort((a, b) -> Integer.compare(a.level(), b.level()));
        return result;
    }

    private List<ItemRequirement> parseItems(JsonObject levelObj, List<String> warnings, int levelNum) {
        List<ItemRequirement> result = new ArrayList<>();
        if (!levelObj.has("items") || !levelObj.get("items").isJsonArray()) return result;

        for (JsonElement el : levelObj.getAsJsonArray("items")) {
            if (!el.isJsonObject()) continue;
            try {
                JsonObject obj = el.getAsJsonObject();
                String itemId = getString(obj, "itemId", null);
                String displayName = getString(obj, "displayName", null);
                int count = obj.has("count") && obj.get("count").isJsonPrimitive() ? obj.get("count").getAsInt() : 0;
                Integer distinctVariantsRequired = (obj.has("distinctVariantsRequired") && obj.get("distinctVariantsRequired").isJsonPrimitive())
                        ? obj.get("distinctVariantsRequired").getAsInt() : null;
                result.add(new ItemRequirement(itemId, displayName, count, distinctVariantsRequired));
            } catch (Exception ex) {
                warnings.add("Hoppar över felformat materialkrav på nivå " + levelNum + ": " + ex.getMessage());
            }
        }
        return result;
    }

    private SettlementFoundation parseFoundation(JsonObject root) {
        if (!root.has("foundation") || !root.get("foundation").isJsonObject()) return null;
        JsonObject obj = root.getAsJsonObject("foundation");

        String creationCommand = getString(obj, "creationCommand", null);
        long creationCostCoins = obj.has("creationCostCoins") && obj.get("creationCostCoins").isJsonPrimitive() ? obj.get("creationCostCoins").getAsLong() : 0L;
        int startLevel = obj.has("startLevel") && obj.get("startLevel").isJsonPrimitive() ? obj.get("startLevel").getAsInt() : 1;
        String startLevelName = getString(obj, "startLevelName", null);
        long renameCostCoins = obj.has("renameCostCoins") && obj.get("renameCostCoins").isJsonPrimitive() ? obj.get("renameCostCoins").getAsLong() : 0L;
        long categoryChangeCostCoins = obj.has("categoryChangeCostCoins") && obj.get("categoryChangeCostCoins").isJsonPrimitive() ? obj.get("categoryChangeCostCoins").getAsLong() : 0L;
        String note = getString(obj, "note", null);
        VerificationMetadata verification = parseVerification(obj, "verification");

        return new SettlementFoundation(creationCommand, creationCostCoins, startLevel, startLevelName,
                renameCostCoins, categoryChangeCostCoins, note, verification);
    }

    private List<ProductionCategory> parseCategories(JsonObject root, List<String> warnings) {
        List<ProductionCategory> result = new ArrayList<>();
        if (!root.has("productionCategories") || !root.get("productionCategories").isJsonArray()) return result;

        Set<String> seenIds = new HashSet<>();
        JsonArray arr = root.getAsJsonArray("productionCategories");
        for (JsonElement el : arr) {
            if (!el.isJsonObject()) continue;
            try {
                JsonObject obj = el.getAsJsonObject();
                String id = getString(obj, "id", null);
                if (id == null || id.isBlank()) {
                    warnings.add("Hoppar över produktionskategori utan giltigt id.");
                    continue;
                }
                if (!seenIds.add(id)) {
                    warnings.add("Dubblerad produktionskategori ignorerad: " + id);
                    continue;
                }
                String displayName = getString(obj, "displayName", id);
                String description = getString(obj, "description", "");
                result.add(new ProductionCategory(id, displayName, description));
            } catch (Exception ex) {
                warnings.add("Hoppar över felformad produktionskategori: " + ex.getMessage());
            }
        }
        return result;
    }

    private VerificationMetadata parseVerification(JsonObject obj, String key) {
        if (!obj.has(key) || !obj.get(key).isJsonObject()) {
            return VerificationMetadata.UNVERIFIED_DEFAULT;
        }
        JsonObject v = obj.getAsJsonObject(key);
        String status = getString(v, "status", null);
        String sourceName = getString(v, "sourceName", null);
        String sourceReference = getString(v, "sourceReference", null);
        String lastVerified = getString(v, "lastVerified", null);
        return VerificationMetadata.of(status, sourceName, sourceReference, lastVerified);
    }

    private static int getInt(JsonObject obj, String key, int fallback) {
        return (obj.has(key) && !obj.get(key).isJsonNull() && obj.get(key).isJsonPrimitive()) ? obj.get(key).getAsInt() : fallback;
    }

    private static String getString(JsonObject obj, String key, String fallback) {
        if (obj.has(key) && !obj.get(key).isJsonNull() && obj.get(key).isJsonPrimitive()) {
            return obj.get(key).getAsString();
        }
        return fallback;
    }
}
