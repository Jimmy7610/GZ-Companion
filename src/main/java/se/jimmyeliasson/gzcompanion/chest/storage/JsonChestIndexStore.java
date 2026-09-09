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
    public synchronized ChestIndexData load() {
        if (!Files.exists(filePath)) {
            return ChestIndexData.empty();
        }

        try (FileReader reader = new FileReader(filePath.toFile())) {
            JsonElement rootElement = JsonParser.parseReader(reader);
            if (rootElement == null || !rootElement.isJsonObject()) {
                LOGGER.warn("Chest index file did not contain a JSON object. Returning empty index.");
                return ChestIndexData.empty();
            }
            JsonObject root = rootElement.getAsJsonObject();

            int schemaVersion = root.has("schemaVersion") ? root.get("schemaVersion").getAsInt() : 1;
            if (schemaVersion > ChestIndexData.CURRENT_SCHEMA) {
                LOGGER.warn("Unsupported future chest index schema version: {}. Returning empty index.", schemaVersion);
                return ChestIndexData.empty();
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

            return new ChestIndexData(schemaVersion, contexts);
        } catch (Exception e) {
            LOGGER.error("Failed to parse chest index file {}. Preserving corrupt file.", filePath, e);
            backupCorruptFile();
            return ChestIndexData.empty();
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
        boolean partnerUnknown = obj.has("partnerUnknown") && obj.get("partnerUnknown").getAsBoolean();

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
        return new StoredContainer(id, label, partner, partnerUnknown, lastOpenedAtMs, slots);
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
        obj.addProperty("partnerUnknown", container.partnerUnknown());
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
