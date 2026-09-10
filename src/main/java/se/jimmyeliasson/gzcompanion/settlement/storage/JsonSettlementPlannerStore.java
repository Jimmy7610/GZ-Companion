package se.jimmyeliasson.gzcompanion.settlement.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 * Robust JSON-backed {@link SettlementPlannerStore} located at
 * config/gzcompanion/settlement-planner.json. Mirrors {@code JsonChestIndexStore}: atomic writes,
 * corruption backup and recovery, and safe rejection of unsupported future schema versions.
 *
 * <p>Only local planning choices are ever written here (a chosen current/target level, manually
 * entered owned-material counts, and free-text local member notes) - never GameZone server state.
 */
public class JsonSettlementPlannerStore implements SettlementPlannerStore {
    private static final Logger LOGGER = LoggerFactory.getLogger(JsonSettlementPlannerStore.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path filePath;

    public JsonSettlementPlannerStore(Path filePath) {
        this.filePath = filePath;
    }

    public static JsonSettlementPlannerStore createDefault() {
        Path configDir = Path.of("config", "gzcompanion");
        return new JsonSettlementPlannerStore(configDir.resolve("settlement-planner.json"));
    }

    @Override
    public synchronized SettlementPlannerLoadResult load() {
        if (!Files.exists(filePath)) {
            return SettlementPlannerLoadResult.notFound();
        }

        try (FileReader reader = new FileReader(filePath.toFile())) {
            JsonElement rootElement = JsonParser.parseReader(reader);
            if (rootElement == null || !rootElement.isJsonObject()) {
                LOGGER.warn("Settlement planner file did not contain a JSON object. Backing up and starting fresh.");
                backupCorruptFile();
                return SettlementPlannerLoadResult.corruptRecovered();
            }
            JsonObject root = rootElement.getAsJsonObject();

            int schemaVersion = (root.has("schemaVersion") && !root.get("schemaVersion").isJsonNull())
                    ? root.get("schemaVersion").getAsInt() : 1;
            if (schemaVersion < 1) {
                LOGGER.warn("Settlement planner file declares an invalid schemaVersion {}. Backing up and starting fresh.", schemaVersion);
                backupCorruptFile();
                return SettlementPlannerLoadResult.corruptRecovered();
            }
            if (schemaVersion > SettlementPlannerData.CURRENT_SCHEMA) {
                LOGGER.warn("Unsupported future settlement planner schema version: {} (this build understands up to {}). Leaving the file untouched.",
                        schemaVersion, SettlementPlannerData.CURRENT_SCHEMA);
                return SettlementPlannerLoadResult.incompatibleSchema();
            }

            Map<String, SettlementPlannerProfile> profiles = new HashMap<>();
            if (root.has("profiles") && root.get("profiles").isJsonObject()) {
                JsonObject profilesObj = root.getAsJsonObject("profiles");
                for (Map.Entry<String, JsonElement> entry : profilesObj.entrySet()) {
                    if (!entry.getValue().isJsonObject()) continue;
                    try {
                        profiles.put(entry.getKey(), parseProfile(entry.getValue().getAsJsonObject()));
                    } catch (Exception ex) {
                        LOGGER.warn("Skipping malformed settlement planner profile '{}': {}", entry.getKey(), ex.getMessage());
                    }
                }
            }

            return SettlementPlannerLoadResult.loaded(new SettlementPlannerData(schemaVersion, profiles));
        } catch (Exception e) {
            LOGGER.error("Failed to parse settlement planner file {}. Preserving corrupt file.", filePath, e);
            backupCorruptFile();
            return SettlementPlannerLoadResult.corruptRecovered();
        }
    }

    private SettlementPlannerProfile parseProfile(JsonObject obj) {
        Integer currentLevel = (obj.has("currentLevel") && obj.get("currentLevel").isJsonPrimitive()) ? obj.get("currentLevel").getAsInt() : null;
        Integer targetLevel = (obj.has("targetLevel") && obj.get("targetLevel").isJsonPrimitive()) ? obj.get("targetLevel").getAsInt() : null;

        Map<String, Integer> owned = new HashMap<>();
        if (obj.has("ownedItemAmounts") && obj.get("ownedItemAmounts").isJsonObject()) {
            JsonObject ownedObj = obj.getAsJsonObject("ownedItemAmounts");
            for (Map.Entry<String, JsonElement> entry : ownedObj.entrySet()) {
                if (entry.getValue().isJsonPrimitive()) {
                    try {
                        owned.put(entry.getKey(), entry.getValue().getAsInt());
                    } catch (Exception ignored) {
                        // Skip malformed amount, keep the rest.
                    }
                }
            }
        }

        List<MemberNote> members = new ArrayList<>();
        if (obj.has("members") && obj.get("members").isJsonArray()) {
            for (JsonElement el : obj.getAsJsonArray("members")) {
                if (!el.isJsonObject()) continue;
                try {
                    JsonObject m = el.getAsJsonObject();
                    String id = m.has("id") && !m.get("id").isJsonNull() ? m.get("id").getAsString() : null;
                    if (id == null || id.isBlank()) continue;
                    String playerName = m.has("playerName") && !m.get("playerName").isJsonNull() ? m.get("playerName").getAsString() : null;
                    String note = m.has("note") && !m.get("note").isJsonNull() ? m.get("note").getAsString() : null;
                    members.add(new MemberNote(id, playerName, note));
                } catch (Exception ignored) {
                    // Skip malformed member entry, keep the rest.
                }
            }
        }

        return new SettlementPlannerProfile(currentLevel, targetLevel, owned, members);
    }

    @Override
    public synchronized void save(SettlementPlannerData data) {
        if (data == null) return;
        try {
            Path parent = filePath.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }

            JsonObject root = serialize(data);

            Path tmpFile = filePath.resolveSibling(filePath.getFileName().toString() + ".tmp");
            try (FileWriter writer = new FileWriter(tmpFile.toFile())) {
                GSON.toJson(root, writer);
            }

            Files.move(tmpFile, filePath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOGGER.error("Failed to save settlement planner data to {}", filePath, e);
        }
    }

    private JsonObject serialize(SettlementPlannerData data) {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", data.schemaVersion());

        JsonObject profilesObj = new JsonObject();
        for (Map.Entry<String, SettlementPlannerProfile> entry : data.profiles().entrySet()) {
            profilesObj.add(entry.getKey(), serializeProfile(entry.getValue()));
        }
        root.add("profiles", profilesObj);
        return root;
    }

    private JsonObject serializeProfile(SettlementPlannerProfile profile) {
        JsonObject obj = new JsonObject();
        if (profile.currentLevel() != null) obj.addProperty("currentLevel", profile.currentLevel());
        if (profile.targetLevel() != null) obj.addProperty("targetLevel", profile.targetLevel());

        JsonObject ownedObj = new JsonObject();
        for (Map.Entry<String, Integer> entry : profile.ownedItemAmounts().entrySet()) {
            ownedObj.addProperty(entry.getKey(), entry.getValue());
        }
        obj.add("ownedItemAmounts", ownedObj);

        JsonArray membersArr = new JsonArray();
        for (MemberNote member : profile.members()) {
            JsonObject m = new JsonObject();
            m.addProperty("id", member.id());
            m.addProperty("playerName", member.playerName());
            m.addProperty("note", member.note());
            membersArr.add(m);
        }
        obj.add("members", membersArr);
        return obj;
    }

    private void backupCorruptFile() {
        try {
            if (Files.exists(filePath)) {
                Path corruptPath = filePath.resolveSibling(filePath.getFileName().toString() + ".corrupt." + System.currentTimeMillis());
                Files.move(filePath, corruptPath, StandardCopyOption.REPLACE_EXISTING);
                LOGGER.info("Moved corrupt settlement planner file to {}", corruptPath);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to backup corrupt settlement planner file", e);
        }
    }
}
