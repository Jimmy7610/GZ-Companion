package se.jimmyeliasson.gzcompanion.guide.progress;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.jimmyeliasson.gzcompanion.guide.model.GuideCompletionSource;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

/**
 * Robust JSON-backed GuideProgressStore located in config/gzcompanion/guide-progress.json.
 * Implements atomic writes and corruption recovery.
 */
public class JsonGuideProgressStore implements GuideProgressStore {
    private static final Logger LOGGER = LoggerFactory.getLogger(JsonGuideProgressStore.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path filePath;

    public JsonGuideProgressStore(Path filePath) {
        this.filePath = filePath;
    }

    public static JsonGuideProgressStore createDefault() {
        Path configDir = Path.of("config", "gzcompanion");
        return new JsonGuideProgressStore(configDir.resolve("guide-progress.json"));
    }

    @Override
    public synchronized GuideProgressData load() {
        if (!Files.exists(filePath)) {
            return GuideProgressData.empty();
        }

        try (FileReader reader = new FileReader(filePath.toFile())) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            int schemaVersion = root.has("schemaVersion") ? root.get("schemaVersion").getAsInt() : 1;
            if (schemaVersion > GuideProgressData.CURRENT_SCHEMA) {
                LOGGER.warn("Unsupported future guide progress schema version: {}. Returning empty progress.", schemaVersion);
                return GuideProgressData.empty();
            }

            Map<String, ContextProgress> contexts = new HashMap<>();
            if (root.has("contexts") && root.get("contexts").isJsonObject()) {
                JsonObject ctxObj = root.getAsJsonObject("contexts");
                for (Map.Entry<String, JsonElement> entry : ctxObj.entrySet()) {
                    String ctxKey = entry.getKey();
                    if (!entry.getValue().isJsonObject()) continue;
                    JsonObject cObj = entry.getValue().getAsJsonObject();

                    String selectedStepId = cObj.has("selectedStepId") && !cObj.get("selectedStepId").isJsonNull()
                            ? cObj.get("selectedStepId").getAsString() : null;

                    Map<String, StepCompletionRecord> steps = new HashMap<>();
                    if (cObj.has("completedSteps") && cObj.get("completedSteps").isJsonObject()) {
                        JsonObject sObj = cObj.getAsJsonObject("completedSteps");
                        for (Map.Entry<String, JsonElement> sEntry : sObj.entrySet()) {
                            String stepId = sEntry.getKey();
                            if (!sEntry.getValue().isJsonObject()) continue;
                            JsonObject recObj = sEntry.getValue().getAsJsonObject();

                            String sourceStr = recObj.has("source") ? recObj.get("source").getAsString() : "MANUAL";
                            GuideCompletionSource source;
                            try {
                                source = GuideCompletionSource.valueOf(sourceStr);
                            } catch (Exception e) {
                                source = GuideCompletionSource.MANUAL;
                            }
                            long completedAt = recObj.has("completedAtMs") ? recObj.get("completedAtMs").getAsLong() : 0L;
                            steps.put(stepId, new StepCompletionRecord(stepId, source, completedAt));
                        }
                    }
                    contexts.put(ctxKey, new ContextProgress(selectedStepId, steps));
                }
            }

            return new GuideProgressData(schemaVersion, contexts);
        } catch (Exception e) {
            LOGGER.error("Failed to parse guide progress file {}. Preserving corrupt file.", filePath, e);
            backupCorruptFile();
            return GuideProgressData.empty();
        }
    }

    @Override
    public synchronized void save(GuideProgressData data) {
        if (data == null) return;
        try {
            Path parent = filePath.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }

            Path tmpFile = filePath.resolveSibling(filePath.getFileName().toString() + ".tmp");
            try (FileWriter writer = new FileWriter(tmpFile.toFile())) {
                GSON.toJson(data, writer);
            }

            Files.move(tmpFile, filePath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOGGER.error("Failed to save guide progress data to {}", filePath, e);
        }
    }

    @Override
    public synchronized void resetContext(GuideContext context) {
        if (context == null) return;
        GuideProgressData current = load();
        Map<String, ContextProgress> updated = new HashMap<>(current.contexts());
        updated.remove(context.getStorageKey());
        save(new GuideProgressData(current.schemaVersion(), updated));
    }

    private void backupCorruptFile() {
        try {
            if (Files.exists(filePath)) {
                Path corruptPath = filePath.resolveSibling(filePath.getFileName().toString() + ".corrupt." + System.currentTimeMillis());
                Files.move(filePath, corruptPath, StandardCopyOption.REPLACE_EXISTING);
                LOGGER.info("Moved corrupt guide progress file to {}", corruptPath);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to backup corrupt guide progress file", e);
        }
    }
}