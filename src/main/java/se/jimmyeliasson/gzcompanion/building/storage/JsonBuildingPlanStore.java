package se.jimmyeliasson.gzcompanion.building.storage;

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
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Robust JSON-backed {@link BuildingPlanStore} located at
 * config/gzcompanion/building-plans.json. Mirrors {@code JsonChestIndexStore}/
 * {@code JsonSettlementPlannerStore}: atomic writes, corruption backup and recovery, and safe
 * rejection of unsupported future schema versions.
 */
public class JsonBuildingPlanStore implements BuildingPlanStore {
    private static final Logger LOGGER = LoggerFactory.getLogger(JsonBuildingPlanStore.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path filePath;

    public JsonBuildingPlanStore(Path filePath) {
        this.filePath = filePath;
    }

    public static JsonBuildingPlanStore createDefault() {
        Path configDir = Path.of("config", "gzcompanion");
        return new JsonBuildingPlanStore(configDir.resolve("building-plans.json"));
    }

    @Override
    public synchronized BuildingPlanLoadResult load() {
        if (!Files.exists(filePath)) {
            return BuildingPlanLoadResult.notFound();
        }

        try (FileReader reader = new FileReader(filePath.toFile())) {
            JsonElement rootElement = JsonParser.parseReader(reader);
            if (rootElement == null || !rootElement.isJsonObject()) {
                LOGGER.warn("Building plan file did not contain a JSON object. Backing up and starting fresh.");
                backupCorruptFile();
                return BuildingPlanLoadResult.corruptRecovered();
            }
            JsonObject root = rootElement.getAsJsonObject();

            int schemaVersion = (root.has("schemaVersion") && !root.get("schemaVersion").isJsonNull())
                    ? root.get("schemaVersion").getAsInt() : 1;
            if (schemaVersion < 1) {
                LOGGER.warn("Building plan file declares an invalid schemaVersion {}. Backing up and starting fresh.", schemaVersion);
                backupCorruptFile();
                return BuildingPlanLoadResult.corruptRecovered();
            }
            if (schemaVersion > BuildingPlanData.CURRENT_SCHEMA) {
                LOGGER.warn("Unsupported future building plan schema version: {} (this build understands up to {}). Leaving the file untouched.",
                        schemaVersion, BuildingPlanData.CURRENT_SCHEMA);
                return BuildingPlanLoadResult.incompatibleSchema();
            }

            Map<String, List<BuildingPlan>> plansByContext = new HashMap<>();
            if (root.has("plansByContext") && root.get("plansByContext").isJsonObject()) {
                JsonObject contextsObj = root.getAsJsonObject("plansByContext");
                for (Map.Entry<String, JsonElement> entry : contextsObj.entrySet()) {
                    if (!entry.getValue().isJsonArray()) continue;
                    plansByContext.put(entry.getKey(), parsePlans(entry.getValue().getAsJsonArray(), entry.getKey()));
                }
            }

            return BuildingPlanLoadResult.loaded(new BuildingPlanData(schemaVersion, plansByContext));
        } catch (Exception e) {
            LOGGER.error("Failed to parse building plan file {}. Preserving corrupt file.", filePath, e);
            backupCorruptFile();
            return BuildingPlanLoadResult.corruptRecovered();
        }
    }

    private List<BuildingPlan> parsePlans(JsonArray arr, String contextKey) {
        List<BuildingPlan> result = new ArrayList<>();
        for (JsonElement el : arr) {
            if (!el.isJsonObject()) continue;
            try {
                JsonObject obj = el.getAsJsonObject();
                String id = obj.has("id") && !obj.get("id").isJsonNull() ? obj.get("id").getAsString() : null;
                String buildingId = obj.has("buildingId") && !obj.get("buildingId").isJsonNull() ? obj.get("buildingId").getAsString() : null;
                if (id == null || id.isBlank() || buildingId == null || buildingId.isBlank()) continue;

                String planName = obj.has("planName") && !obj.get("planName").isJsonNull() ? obj.get("planName").getAsString() : null;
                int width = obj.has("width") && obj.get("width").isJsonPrimitive() ? obj.get("width").getAsInt() : 0;
                int depth = obj.has("depth") && obj.get("depth").isJsonPrimitive() ? obj.get("depth").getAsInt() : 0;
                int height = obj.has("height") && obj.get("height").isJsonPrimitive() ? obj.get("height").getAsInt() : 0;
                long createdAtMs = obj.has("createdAtMs") && obj.get("createdAtMs").isJsonPrimitive() ? obj.get("createdAtMs").getAsLong() : 0L;

                EnumSet<BuildingRequirementKey> completed = EnumSet.noneOf(BuildingRequirementKey.class);
                if (obj.has("completed") && obj.get("completed").isJsonArray()) {
                    for (JsonElement keyEl : obj.getAsJsonArray("completed")) {
                        if (!keyEl.isJsonPrimitive()) continue;
                        try {
                            completed.add(BuildingRequirementKey.valueOf(keyEl.getAsString()));
                        } catch (IllegalArgumentException ignored) {
                            // Skip an unknown/future checklist key rather than failing the whole plan.
                        }
                    }
                }

                result.add(new BuildingPlan(id, buildingId, planName, width, depth, height, completed, createdAtMs));
            } catch (Exception ex) {
                LOGGER.warn("Skipping malformed building plan entry in context '{}': {}", contextKey, ex.getMessage());
            }
        }
        return result;
    }

    @Override
    public synchronized void save(BuildingPlanData data) {
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
            LOGGER.error("Failed to save building plan data to {}", filePath, e);
        }
    }

    private JsonObject serialize(BuildingPlanData data) {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", data.schemaVersion());

        JsonObject contextsObj = new JsonObject();
        for (Map.Entry<String, List<BuildingPlan>> entry : data.plansByContext().entrySet()) {
            JsonArray arr = new JsonArray();
            for (BuildingPlan plan : entry.getValue()) {
                arr.add(serializePlan(plan));
            }
            contextsObj.add(entry.getKey(), arr);
        }
        root.add("plansByContext", contextsObj);
        return root;
    }

    private JsonObject serializePlan(BuildingPlan plan) {
        JsonObject obj = new JsonObject();
        obj.addProperty("id", plan.id());
        obj.addProperty("buildingId", plan.buildingId());
        obj.addProperty("planName", plan.planName());
        obj.addProperty("width", plan.width());
        obj.addProperty("depth", plan.depth());
        obj.addProperty("height", plan.height());
        obj.addProperty("createdAtMs", plan.createdAtMs());

        JsonArray completedArr = new JsonArray();
        for (BuildingRequirementKey key : plan.completed()) {
            completedArr.add(key.name());
        }
        obj.add("completed", completedArr);
        return obj;
    }

    private void backupCorruptFile() {
        try {
            if (Files.exists(filePath)) {
                Path corruptPath = filePath.resolveSibling(filePath.getFileName().toString() + ".corrupt." + System.currentTimeMillis());
                Files.move(filePath, corruptPath, StandardCopyOption.REPLACE_EXISTING);
                LOGGER.info("Moved corrupt building plan file to {}", corruptPath);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to backup corrupt building plan file", e);
        }
    }
}
