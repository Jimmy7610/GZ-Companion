package se.jimmyeliasson.gzcompanion.knowledge.crafting;

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
 * Loads the bundled GameZone crafting override knowledge from
 * {@code gamezone-pack/crafting-overrides.json}. Never registers, intercepts, or modifies any
 * actual Minecraft recipe - this is read-only reference data.
 */
public final class CraftingKnowledgeLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(CraftingKnowledgeLoader.class);
    private static final String DEFAULT_RESOURCE_PATH = "/gamezone-pack/crafting-overrides.json";
    private static final int CURRENT_SCHEMA = 1;
    private static final int MAX_GRID_DIMENSION = 3;

    private final String resourcePath;

    public CraftingKnowledgeLoader() {
        this(DEFAULT_RESOURCE_PATH);
    }

    public CraftingKnowledgeLoader(String resourcePath) {
        this.resourcePath = resourcePath;
    }

    public KnowledgeLoadResult<CraftingKnowledgeBase> load() {
        JsonObject root;
        try (InputStream stream = CraftingKnowledgeLoader.class.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                LOGGER.error("Crafting override resource not found: {}", resourcePath);
                return KnowledgeLoadResult.error(CraftingKnowledgeBase.empty());
            }
            try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                JsonElement element = JsonParser.parseReader(reader);
                if (element == null || !element.isJsonObject()) {
                    LOGGER.error("Crafting override file {} did not contain a JSON object.", resourcePath);
                    return KnowledgeLoadResult.error(CraftingKnowledgeBase.empty());
                }
                root = element.getAsJsonObject();
            }
        } catch (Exception e) {
            LOGGER.error("Failed to read crafting override file {}: {}", resourcePath, e.getMessage());
            return KnowledgeLoadResult.error(CraftingKnowledgeBase.empty());
        }

        int schemaVersion = (root.has("schemaVersion") && !root.get("schemaVersion").isJsonNull())
                ? root.get("schemaVersion").getAsInt() : 1;
        if (schemaVersion < 1) {
            LOGGER.error("Crafting override file declares invalid schemaVersion {}.", schemaVersion);
            return KnowledgeLoadResult.error(CraftingKnowledgeBase.empty());
        }
        if (schemaVersion > CURRENT_SCHEMA) {
            LOGGER.warn("Crafting override schemaVersion {} is newer than this build supports ({}).", schemaVersion, CURRENT_SCHEMA);
            return KnowledgeLoadResult.incompatibleSchema(CraftingKnowledgeBase.empty());
        }

        List<String> warnings = new ArrayList<>();
        List<GameZoneCraftingEntry> entries = parseRecipes(root, warnings);
        return KnowledgeLoadResult.loaded(new CraftingKnowledgeBase(entries, warnings));
    }

    private List<GameZoneCraftingEntry> parseRecipes(JsonObject root, List<String> warnings) {
        List<GameZoneCraftingEntry> result = new ArrayList<>();
        if (!root.has("recipes") || !root.get("recipes").isJsonArray()) return result;

        Set<String> seenIds = new HashSet<>();
        for (JsonElement el : root.getAsJsonArray("recipes")) {
            if (!el.isJsonObject()) continue;
            try {
                JsonObject obj = el.getAsJsonObject();

                String id = getString(obj, "id", null);
                if (id == null || id.isBlank()) {
                    warnings.add("Hoppar över recept utan giltigt id.");
                    continue;
                }
                if (!seenIds.add(id)) {
                    warnings.add("Dubblerat recept-id ignorerat: " + id);
                    continue;
                }

                String outputItemId = getString(obj, "outputItemId", null);
                if (outputItemId == null || outputItemId.isBlank()) {
                    warnings.add("Hoppar över recept '" + id + "' utan outputItemId.");
                    continue;
                }

                int outputCount = obj.has("outputCount") && obj.get("outputCount").isJsonPrimitive()
                        ? Math.max(1, obj.get("outputCount").getAsInt()) : 1;

                RecipeKind kind = parseKind(getString(obj, "kind", null));
                if (kind == null) {
                    warnings.add("Hoppar över recept '" + id + "' med okänt/saknat kind.");
                    continue;
                }

                RecipeKnowledgeSource source = parseSource(getString(obj, "source", null));
                if (source == null) {
                    warnings.add("Hoppar över recept '" + id + "' med okänd/saknad source.");
                    continue;
                }

                int width = 0;
                int height = 0;
                List<IngredientRef> grid = List.of();
                List<IngredientRef> ingredients = List.of();

                if (kind == RecipeKind.SHAPED) {
                    width = obj.has("width") && obj.get("width").isJsonPrimitive() ? obj.get("width").getAsInt() : 0;
                    height = obj.has("height") && obj.get("height").isJsonPrimitive() ? obj.get("height").getAsInt() : 0;
                    if (width < 1 || width > MAX_GRID_DIMENSION || height < 1 || height > MAX_GRID_DIMENSION) {
                        warnings.add("Hoppar över recept '" + id + "': ogiltig rutnätsstorlek " + width + "x" + height + ".");
                        continue;
                    }
                    grid = parseIngredientGrid(obj, width * height);
                    if (grid == null) {
                        warnings.add("Hoppar över recept '" + id + "': rutnätet matchar inte width*height.");
                        continue;
                    }
                } else {
                    ingredients = parseIngredientList(obj, "ingredients");
                    if (ingredients.isEmpty()) {
                        warnings.add("Hoppar över formlöst recept '" + id + "' utan ingredienser.");
                        continue;
                    }
                }

                String notes = getString(obj, "notes", null);
                VerificationMetadata verification = parseVerification(obj);

                result.add(new GameZoneCraftingEntry(id, outputItemId, outputCount, kind, width, height,
                        grid, ingredients, source, notes, verification));
            } catch (Exception ex) {
                warnings.add("Hoppar över felformad receptpost: " + ex.getMessage());
            }
        }
        return result;
    }

    /** Returns null if the JSON grid array's length doesn't match the declared width*height. */
    private List<IngredientRef> parseIngredientGrid(JsonObject obj, int expectedSize) {
        List<IngredientRef> grid = new ArrayList<>();
        if (!obj.has("grid") || !obj.get("grid").isJsonArray()) return null;
        JsonArray arr = obj.getAsJsonArray("grid");
        if (arr.size() != expectedSize) return null;
        for (JsonElement el : arr) {
            grid.add(parseIngredientRef(el));
        }
        return grid;
    }

    private List<IngredientRef> parseIngredientList(JsonObject obj, String key) {
        List<IngredientRef> result = new ArrayList<>();
        if (!obj.has(key) || !obj.get(key).isJsonArray()) return result;
        for (JsonElement el : obj.getAsJsonArray(key)) {
            IngredientRef ref = parseIngredientRef(el);
            if (ref != null && !ref.isEmpty()) result.add(ref);
        }
        return result;
    }

    private IngredientRef parseIngredientRef(JsonElement el) {
        if (el == null || el.isJsonNull() || !el.isJsonObject()) return IngredientRef.ofItems();
        JsonObject obj = el.getAsJsonObject();
        List<String> itemIds = new ArrayList<>();
        if (obj.has("itemIds") && obj.get("itemIds").isJsonArray()) {
            for (JsonElement idEl : obj.getAsJsonArray("itemIds")) {
                if (idEl.isJsonPrimitive() && !idEl.getAsString().isBlank()) itemIds.add(idEl.getAsString());
            }
        }
        String tag = getString(obj, "tag", null);
        return new IngredientRef(itemIds, tag);
    }

    private VerificationMetadata parseVerification(JsonObject obj) {
        if (!obj.has("verification") || !obj.get("verification").isJsonObject()) {
            return VerificationMetadata.UNVERIFIED_DEFAULT;
        }
        JsonObject v = obj.getAsJsonObject("verification");
        return VerificationMetadata.of(
                getString(v, "status", null),
                getString(v, "sourceName", null),
                getString(v, "sourceReference", null),
                getString(v, "lastVerified", null));
    }

    private RecipeKind parseKind(String raw) {
        if (raw == null) return null;
        try {
            return RecipeKind.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private RecipeKnowledgeSource parseSource(String raw) {
        if (raw == null) return null;
        try {
            return RecipeKnowledgeSource.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String getString(JsonObject obj, String key, String fallback) {
        if (obj.has(key) && !obj.get(key).isJsonNull() && obj.get(key).isJsonPrimitive()) {
            return obj.get(key).getAsString();
        }
        return fallback;
    }
}
