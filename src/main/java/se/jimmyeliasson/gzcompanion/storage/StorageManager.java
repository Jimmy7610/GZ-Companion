package se.jimmyeliasson.gzcompanion.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Local-first, safe configuration manager.
 * Stores config in config/gzcompanion/config.json with automatic backup on malformed data.
 */
public class StorageManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(StorageManager.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path configDir;
    private final Path configFile;
    private CompanionConfig activeConfig;

    public StorageManager(Path rootDir) {
        this.configDir = rootDir.resolve("config").resolve("gzcompanion");
        this.configFile = this.configDir.resolve("config.json");
        this.activeConfig = loadConfig();
    }

    public synchronized CompanionConfig loadConfig() {
        if (!Files.exists(configFile)) {
            CompanionConfig defaults = new CompanionConfig();
            saveConfig(defaults);
            return defaults;
        }

        try (Reader reader = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
            CompanionConfig loaded = GSON.fromJson(reader, CompanionConfig.class);
            if (loaded != null) {
                this.activeConfig = loaded;
                return loaded;
            }
        } catch (Exception e) {
            LOGGER.warn("Kunde inte l\u00E4sa config.json, \u00E5terst\u00E4ller till standard och sparar backup: {}", e.getMessage());
            try {
                Path backup = configDir.resolve("config.json.corrupt.bak");
                Files.copy(configFile, backup, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {}
        }

        CompanionConfig fallback = new CompanionConfig();
        saveConfig(fallback);
        this.activeConfig = fallback;
        return fallback;
    }

    public synchronized boolean saveConfig(CompanionConfig config) {
        try {
            Files.createDirectories(configDir);
            try (Writer writer = Files.newBufferedWriter(configFile, StandardCharsets.UTF_8)) {
                GSON.toJson(config, writer);
            }
            this.activeConfig = config;
            return true;
        } catch (Exception e) {
            LOGGER.error("Misslyckades med att spara config.json: {}", e.getMessage());
            return false;
        }
    }

    public CompanionConfig getConfig() {
        return activeConfig != null ? activeConfig : new CompanionConfig();
    }
}