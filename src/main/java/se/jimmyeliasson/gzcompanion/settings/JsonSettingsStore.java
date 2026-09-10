package se.jimmyeliasson.gzcompanion.settings;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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

/**
 * Robust JSON-backed {@link SettingsStore} located at config/gzcompanion/settings.json. Mirrors
 * {@code JsonMarketWatchNotesStore}/{@code JsonBuildingPlanStore}/{@code JsonChestIndexStore}:
 * atomic writes, corruption backup and recovery, and safe rejection of unsupported future schema
 * versions. Unlike the other stores, this holds exactly one flat settings object - never a
 * per-world/per-server map, since these are deliberately global preferences.
 */
public class JsonSettingsStore implements SettingsStore {
    private static final Logger LOGGER = LoggerFactory.getLogger(JsonSettingsStore.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path filePath;

    public JsonSettingsStore(Path filePath) {
        this.filePath = filePath;
    }

    public static JsonSettingsStore createDefault() {
        Path configDir = Path.of("config", "gzcompanion");
        return new JsonSettingsStore(configDir.resolve("settings.json"));
    }

    @Override
    public synchronized SettingsLoadResult load() {
        if (!Files.exists(filePath)) {
            return SettingsLoadResult.notFound();
        }

        try (FileReader reader = new FileReader(filePath.toFile())) {
            JsonElement rootElement = JsonParser.parseReader(reader);
            if (rootElement == null || !rootElement.isJsonObject()) {
                LOGGER.warn("Settings file did not contain a JSON object. Backing up and starting fresh.");
                backupCorruptFile();
                return SettingsLoadResult.corruptRecovered();
            }
            JsonObject root = rootElement.getAsJsonObject();

            int schemaVersion = (root.has("schemaVersion") && !root.get("schemaVersion").isJsonNull())
                    ? root.get("schemaVersion").getAsInt() : 1;
            if (schemaVersion < 1) {
                LOGGER.warn("Settings file declares an invalid schemaVersion {}. Backing up and starting fresh.", schemaVersion);
                backupCorruptFile();
                return SettingsLoadResult.corruptRecovered();
            }
            if (schemaVersion > CompanionSettings.CURRENT_SCHEMA) {
                LOGGER.warn("Unsupported future settings schema version: {} (this build understands up to {}). Leaving the file untouched.",
                        schemaVersion, CompanionSettings.CURRENT_SCHEMA);
                return SettingsLoadResult.incompatibleSchema();
            }

            CompanionSettings defaults = CompanionSettings.defaults();
            CompanionSettings settings = new CompanionSettings(
                    getBool(root, "companionNotificationsEnabled", defaults.companionNotificationsEnabled()),
                    getBool(root, "gameZoneToastsEnabled", defaults.gameZoneToastsEnabled()),
                    getBool(root, "showTechnicalIds", defaults.showTechnicalIds()),
                    getBool(root, "showUnverifiedKnowledge", defaults.showUnverifiedKnowledge()),
                    getBool(root, "useLastKnownChestDataInPlanners", defaults.useLastKnownChestDataInPlanners())
            );
            return SettingsLoadResult.loaded(settings);
        } catch (Exception e) {
            LOGGER.error("Failed to parse settings file {}. Preserving corrupt file.", filePath, e);
            backupCorruptFile();
            return SettingsLoadResult.corruptRecovered();
        }
    }

    private static boolean getBool(JsonObject obj, String key, boolean fallback) {
        return (obj.has(key) && !obj.get(key).isJsonNull() && obj.get(key).isJsonPrimitive()) ? obj.get(key).getAsBoolean() : fallback;
    }

    @Override
    public synchronized void save(CompanionSettings settings) {
        if (settings == null) return;
        try {
            Path parent = filePath.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }

            JsonObject root = new JsonObject();
            root.addProperty("schemaVersion", CompanionSettings.CURRENT_SCHEMA);
            root.addProperty("companionNotificationsEnabled", settings.companionNotificationsEnabled());
            root.addProperty("gameZoneToastsEnabled", settings.gameZoneToastsEnabled());
            root.addProperty("showTechnicalIds", settings.showTechnicalIds());
            root.addProperty("showUnverifiedKnowledge", settings.showUnverifiedKnowledge());
            root.addProperty("useLastKnownChestDataInPlanners", settings.useLastKnownChestDataInPlanners());

            Path tmpFile = filePath.resolveSibling(filePath.getFileName().toString() + ".tmp");
            try (FileWriter writer = new FileWriter(tmpFile.toFile())) {
                GSON.toJson(root, writer);
            }

            Files.move(tmpFile, filePath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOGGER.error("Failed to save settings to {}", filePath, e);
        }
    }

    private void backupCorruptFile() {
        try {
            if (Files.exists(filePath)) {
                Path corruptPath = filePath.resolveSibling(filePath.getFileName().toString() + ".corrupt." + System.currentTimeMillis());
                Files.move(filePath, corruptPath, StandardCopyOption.REPLACE_EXISTING);
                LOGGER.info("Moved corrupt settings file to {}", corruptPath);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to backup corrupt settings file", e);
        }
    }
}
