package se.jimmyeliasson.gzcompanion.chest.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.jimmyeliasson.gzcompanion.chest.model.ChestSlotEntry;
import se.jimmyeliasson.gzcompanion.chest.model.StorageKind;
import se.jimmyeliasson.gzcompanion.chest.model.StoragePosition;
import se.jimmyeliasson.gzcompanion.chest.model.StorageShape;
import se.jimmyeliasson.gzcompanion.chest.model.StoredContainer;
import se.jimmyeliasson.gzcompanion.chest.model.StoredContainerId;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Robust JSON-backed {@link ChestIndexStore} located at config/gzcompanion/chest-index.json.
 * Mirrors {@code JsonGuideProgressStore}: atomic writes, corruption backup and recovery,
 * and safe rejection of unsupported future schema versions.
 *
 * Only "last known" locally observed data is ever written here. No telemetry, no cloud, no
 * server communication of any kind.
 */
public class JsonChestIndexStore implements ChestIndexStore {
    private static final Logger LOGGER = LoggerFactory.getLogger(JsonChestIndexStore.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path filePath;

    public JsonChestIndexStore(Path filePath) {
        this.filePath = filePath;
    }

    public static JsonChestIndexStore createDefault() {
        Path configDir = Path.of("config", "gzcompanion");
        return new JsonChestIndexStore(configDir.resolve("chest-index.json"));
    }

    @Override
    public synchronized ChestIndexLoadResult load() {
        if (!Files.exists(filePath)) {
            return ChestIndexLoadResult.notFound();
        }

        try (FileReader reader = new FileReader(filePath.toFile())) {
            JsonElement rootElement = JsonParser.parseReader(reader);
            if (rootElement == null || !rootElement.isJsonObject()) {
                LOGGER.warn("Chest index file did not contain a JSON object. Backing up and starting fresh.");
                backupCorruptFile();
                return ChestIndexLoadResult.corruptRecovered();
            }
            JsonObject root = rootElement.getAsJsonObject();

            int schemaVersion = (root.has("schemaVersion") && !root.get("schemaVersion").isJsonNull())
                    ? root.get("schemaVersion").getAsInt() : 1;
            if (schemaVersion < 1) {
                // A schema version of zero or negative is not a valid past version - it means the
                // file is malformed/tampered, not merely old. Treat it the same as corruption.
                LOGGER.warn("Chest index file declares an invalid schemaVersion {}. Backing up and starting fresh.", schemaVersion);
                backupCorruptFile();
                return ChestIndexLoadResult.corruptRecovered();
            }
            if (schemaVersion > ChestIndexData.CURRENT_SCHEMA) {
                // Deliberately do NOT move, delete, or overwrite the file: an older client must
                // never risk data loss against an index written by a newer version.
                LOGGER.warn("Unsupported future chest index schema version: {} (this build understands up to {}). " +
                        "Leaving the file untouched; Chest Manager will report INCOMPATIBLE.", schemaVersion, ChestIndexData.CURRENT_SCHEMA);
                return ChestIndexLoadResult.incompatibleSchema();
            }

            Map<String, ContextContainers> contexts = new HashMap<>();
            if (root.has("contexts") && root.get("contexts").isJsonObject()) {
                JsonObject ctxObj = root.getAsJsonObject("contexts");
                for (Map.Entry<String, JsonElement> ctxEntry : ctxObj.entrySet()) {
                    String contextKey = ctxEntry.getKey();
                    if (!ctxEntry.getValue().isJsonObject()) continue;
                    contexts.put(contextKey, parseContextContainers(contextKey, ctxEntry.getValue().getAsJsonObject()));
                }
            }

            return ChestIndexLoadResult.loaded(new ChestIndexData(schemaVersion, contexts));
        } catch (Exception e) {
            LOGGER.error("Failed to parse chest index file {}. Preserving corrupt file.", filePath, e);
            backupCorruptFile();
            return ChestIndexLoadResult.corruptRecovered();
        }
    }

    private ContextContainers parseContextContainers(String contextKey, JsonObject ctxObj) {
        Map<String, StoredContainer> containers = new HashMap<>();
        if (ctxObj.has("containers") && ctxObj.get("containers").isJsonObject()) {
            JsonObject containersObj = ctxObj.getAsJsonObject("containers");
            for (Map.Entry<String, JsonElement> entry : containersObj.entrySet()) {
                if (!entry.getValue().isJsonObject()) continue;
                try {
                    StoredContainer container = parseContainer(contextKey, entry.getValue().getAsJsonObject());
                    if (container != null) {
                        containers.put(container.id().asStableKey(), container);
                    }
                } catch (Exception ex) {
                    LOGGER.warn("Skipping malformed chest index entry '{}' in context '{}': {}", entry.getKey(), contextKey, ex.getMessage());
                }
            }
        }
        return new ContextContainers(containers);
    }

    private StoredContainer parseContainer(String contextKey, JsonObject obj) {
        String kindStr = obj.has("kind") ? obj.get("kind").getAsString() : null;
        StorageKind kind;
        try {
            kind = kindStr != null ? StorageKind.valueOf(kindStr) : null;
        } catch (IllegalArgumentException e) {
            kind = null;
        }
        if (kind == null) return null;

        String dimensionKey = obj.has("dimension") && !obj.get("dimension").isJsonNull()
                ? obj.get("dimension").getAsString() : "minecraft:overworld";

        StoragePosition anchor = parsePosition(obj.getAsJsonObject("anchor"));
        if (anchor == null) return null;

        StoragePosition partner = obj.has("partner") && obj.get("partner").isJsonObject()
                ? parsePosition(obj.getAsJsonObject("partner")) : null;
        StorageShape shape = resolveShape(obj, kind, partner);

        String label = obj.has("label") && !obj.get("label").isJsonNull() ? obj.get("label").getAsString() : null;
        long lastOpenedAtMs = obj.has("lastOpenedAtMs") ? obj.get("lastOpenedAtMs").getAsLong() : 0L;

        List<ChestSlotEntry> slots = new ArrayList<>();
        if (obj.has("slots") && obj.get("slots").isJsonArray()) {
            JsonArray slotsArr = obj.getAsJsonArray("slots");
            for (JsonElement slotEl : slotsArr) {
                if (!slotEl.isJsonObject()) continue;
                JsonObject slotObj = slotEl.getAsJsonObject();
                int slotIndex = slotObj.has("slot") ? slotObj.get("slot").getAsInt() : -1;
                String itemId = slotObj.has("itemId") && !slotObj.get("itemId").isJsonNull() ? slotObj.get("itemId").getAsString() : null;
                int count = slotObj.has("count") ? slotObj.get("count").getAsInt() : 0;
                if (slotIndex >= 0 && itemId != null && count > 0) {
                    slots.add(new ChestSlotEntry(slotIndex, itemId, count));
                }
            }
        }

        StoredContainerId id = new StoredContainerId(contextKey, dimensionKey, anchor, kind);
        return new StoredContainer(id, label, partner, shape, lastOpenedAtMs, slots);
    }

    /**
     * Resolves the physical {@link StorageShape} of an entry, supporting BOTH the new explicit
     * {@code "shape"} field and legacy schema-v1 records written before it existed (which only
     * had {@code "partner"} and a boolean {@code "partnerUnknown"}). Existing data from real
     * gameplay is never destroyed or rewritten merely because it was loaded once under this
     * mapping.
     *
     * <p>Legacy mapping (conservative, documented):
     * <ul>
     *   <li>{@code partner != null} -&gt; {@code DOUBLE}</li>
     *   <li>{@code partner == null && partnerUnknown == true} -&gt; {@code UNKNOWN}</li>
     *   <li>{@code partner == null && partnerUnknown == false} -&gt; {@code SINGLE} for a
     *       chest-family kind, {@code NOT_APPLICABLE} otherwise</li>
     * </ul>
     *
     * <p>An explicit {@code "shape"} value that is present but unparseable (e.g. written by a
     * future version under a name this build doesn't know) safely falls back to the same legacy
     * mapping rather than crashing or guessing a specific shape.
     */
    private StorageShape resolveShape(JsonObject obj, StorageKind kind, StoragePosition partner) {
        if (obj.has("shape") && !obj.get("shape").isJsonNull()) {
            try {
                return StorageShape.valueOf(obj.get("shape").getAsString());
            } catch (Exception ignored) {
                // Fall through to the legacy mapping below.
            }
        }

        boolean legacyPartnerUnknown = obj.has("partnerUnknown") && !obj.get("partnerUnknown").isJsonNull()
                && obj.get("partnerUnknown").getAsBoolean();

        if (partner != null) {
            return StorageShape.DOUBLE;
        }
        if (legacyPartnerUnknown) {
            return StorageShape.UNKNOWN;
        }
        return kind.isChestFamily() ? StorageShape.SINGLE : StorageShape.NOT_APPLICABLE;
    }

    private StoragePosition parsePosition(JsonObject posObj) {
        if (posObj == null) return null;
        if (!posObj.has("x") || !posObj.has("y") || !posObj.has("z")) return null;
        return new StoragePosition(posObj.get("x").getAsInt(), posObj.get("y").getAsInt(), posObj.get("z").getAsInt());
    }

    @Override
    public synchronized void save(ChestIndexData data) {
        if (data == null) return;
        try {
            Path parent = filePath.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }

            JsonObject root = serializeIndex(data);

            Path tmpFile = filePath.resolveSibling(filePath.getFileName().toString() + ".tmp");
            try (FileWriter writer = new FileWriter(tmpFile.toFile())) {
                GSON.toJson(root, writer);
            }

            Files.move(tmpFile, filePath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOGGER.error("Failed to save chest index data to {}", filePath, e);
        }
    }

    private JsonObject serializeIndex(ChestIndexData data) {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", data.schemaVersion());

        JsonObject contextsObj = new JsonObject();
        for (Map.Entry<String, ContextContainers> ctxEntry : data.contexts().entrySet()) {
            JsonObject ctxObj = new JsonObject();
            JsonObject containersObj = new JsonObject();
            for (Map.Entry<String, StoredContainer> entry : ctxEntry.getValue().containers().entrySet()) {
                containersObj.add(entry.getKey(), serializeContainer(entry.getValue()));
            }
            ctxObj.add("containers", containersObj);
            contextsObj.add(ctxEntry.getKey(), ctxObj);
        }
        root.add("contexts", contextsObj);
        return root;
    }

    private JsonObject serializeContainer(StoredContainer container) {
        JsonObject obj = new JsonObject();
        obj.addProperty("kind", container.kind().name());
        if (container.label() != null) {
            obj.addProperty("label", container.label());
        }
        obj.addProperty("dimension", container.dimensionKey());
        obj.add("anchor", serializePosition(container.anchor()));
        if (container.partner() != null) {
            obj.add("partner", serializePosition(container.partner()));
        }
        obj.addProperty("shape", container.shape().name());
        obj.addProperty("lastOpenedAtMs", container.lastOpenedAtMs());

        JsonArray slotsArr = new JsonArray();
        for (ChestSlotEntry slot : container.slots()) {
            JsonObject slotObj = new JsonObject();
            slotObj.addProperty("slot", slot.slotIndex());
            slotObj.addProperty("itemId", slot.itemId());
            slotObj.addProperty("count", slot.count());
            slotsArr.add(slotObj);
        }
        obj.add("slots", slotsArr);
        return obj;
    }

    private JsonObject serializePosition(StoragePosition pos) {
        JsonObject obj = new JsonObject();
        obj.addProperty("x", pos.x());
        obj.addProperty("y", pos.y());
        obj.addProperty("z", pos.z());
        return obj;
    }

    private void backupCorruptFile() {
        try {
            if (Files.exists(filePath)) {
                Path corruptPath = filePath.resolveSibling(filePath.getFileName().toString() + ".corrupt." + System.currentTimeMillis());
                Files.move(filePath, corruptPath, StandardCopyOption.REPLACE_EXISTING);
                LOGGER.info("Moved corrupt chest index file to {}", corruptPath);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to backup corrupt chest index file", e);
        }
    }
}
