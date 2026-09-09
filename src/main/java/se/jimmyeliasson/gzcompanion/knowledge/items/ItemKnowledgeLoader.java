package se.jimmyeliasson.gzcompanion.knowledge.items;

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
 * Loads the bundled GameZone custom-item/lore knowledge from
 * {@code gamezone-pack/item-overrides.json}.
 */
public final class ItemKnowledgeLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(ItemKnowledgeLoader.class);
    private static final String DEFAULT_RESOURCE_PATH = "/gamezone-pack/item-overrides.json";
    private static final int CURRENT_SCHEMA = 1;

    private final String resourcePath;

    public ItemKnowledgeLoader() {
        this(DEFAULT_RESOURCE_PATH);
    }

    public ItemKnowledgeLoader(String resourcePath) {
        this.resourcePath = resourcePath;
    }

    public KnowledgeLoadResult<ItemKnowledgeBase> load() {
        JsonObject root;
        try (InputStream stream = ItemKnowledgeLoader.class.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                LOGGER.error("Item knowledge resource not found: {}", resourcePath);
                return KnowledgeLoadResult.error(ItemKnowledgeBase.empty());
            }
            try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                JsonElement element = JsonParser.parseReader(reader);
                if (element == null || !element.isJsonObject()) {
                    LOGGER.error("Item knowledge file {} did not contain a JSON object.", resourcePath);
                    return KnowledgeLoadResult.error(ItemKnowledgeBase.empty());
                }
                root = element.getAsJsonObject();
            }
        } catch (Exception e) {
            LOGGER.error("Failed to read item knowledge file {}: {}", resourcePath, e.getMessage());
            return KnowledgeLoadResult.error(ItemKnowledgeBase.empty());
        }

        int schemaVersion = (root.has("schemaVersion") && !root.get("schemaVersion").isJsonNull())
                ? root.get("schemaVersion").getAsInt() : 1;
        if (schemaVersion < 1) {
            LOGGER.error("Item knowledge file declares invalid schemaVersion {}.", schemaVersion);
            return KnowledgeLoadResult.error(ItemKnowledgeBase.empty());
        }
        if (schemaVersion > CURRENT_SCHEMA) {
            LOGGER.warn("Item knowledge schemaVersion {} is newer than this build supports ({}).", schemaVersion, CURRENT_SCHEMA);
            return KnowledgeLoadResult.incompatibleSchema(ItemKnowledgeBase.empty());
        }

        List<String> warnings = new ArrayList<>();
        List<CustomItemKnowledge> items = parseItems(root, warnings);
        return KnowledgeLoadResult.loaded(new ItemKnowledgeBase(items, warnings));
    }

    private List<CustomItemKnowledge> parseItems(JsonObject root, List<String> warnings) {
        List<CustomItemKnowledge> result = new ArrayList<>();
        if (!root.has("items") || !root.get("items").isJsonArray()) return result;

        Set<String> seenIds = new HashSet<>();
        for (JsonElement el : root.getAsJsonArray("items")) {
            if (!el.isJsonObject()) continue;
            try {
                JsonObject obj = el.getAsJsonObject();

                String id = getString(obj, "id", null);
                if (id == null || id.isBlank()) {
                    warnings.add("Hoppar över föremål utan giltigt id.");
                    continue;
                }
                if (!seenIds.add(id)) {
                    warnings.add("Dubblerat föremåls-id ignorerat: " + id);
                    continue;
                }

                String displayName = getString(obj, "displayName", null);
                if (displayName == null || displayName.isBlank()) {
                    warnings.add("Hoppar över föremål '" + id + "' utan displayName.");
                    continue;
                }

                String baseMinecraftItemId = getString(obj, "baseMinecraftItemId", null);
                String description = getString(obj, "description", null);
                List<String> lore = readStringArray(obj, "lore");
                String category = getString(obj, "category", null);
                String tier = getString(obj, "tier", null);
                String culture = getString(obj, "culture", null);
                String serial = getString(obj, "serial", null);
                List<String> enchants = readStringArray(obj, "enchants");
                String specialEffect = getString(obj, "specialEffect", null);
                String acquisitionNotes = getString(obj, "acquisitionNotes", null);
                String craftingReferenceId = getString(obj, "craftingReferenceId", null);
                String releaseStatus = getString(obj, "releaseStatus", null);
                VerificationMetadata verification = parseVerification(obj);

                result.add(new CustomItemKnowledge(id, displayName, baseMinecraftItemId, description, lore,
                        category, tier, culture, serial, enchants, specialEffect, acquisitionNotes,
                        craftingReferenceId, releaseStatus, verification));
            } catch (Exception ex) {
                warnings.add("Hoppar över felformad föremålspost: " + ex.getMessage());
            }
        }
        return result;
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

    private List<String> readStringArray(JsonObject obj, String key) {
        List<String> result = new ArrayList<>();
        if (obj.has(key) && obj.get(key).isJsonArray()) {
            for (JsonElement el : obj.getAsJsonArray(key)) {
                if (el.isJsonPrimitive()) result.add(el.getAsString());
            }
        }
        return result;
    }

    private static String getString(JsonObject obj, String key, String fallback) {
        if (obj.has(key) && !obj.get(key).isJsonNull() && obj.get(key).isJsonPrimitive()) {
            return obj.get(key).getAsString();
        }
        return fallback;
    }
}
