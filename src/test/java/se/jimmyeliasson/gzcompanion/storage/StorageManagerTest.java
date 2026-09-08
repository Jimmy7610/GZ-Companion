package se.jimmyeliasson.gzcompanion.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class StorageManagerTest {

    @Test
    @DisplayName("Should initialize and write default configuration")
    void testCreateDefaults(@TempDir Path tempDir) {
        StorageManager manager = new StorageManager(tempDir);
        CompanionConfig config = manager.getConfig();

        assertNotNull(config);
        assertEquals("sv_se", config.getLanguage());
        assertTrue(config.isEnableTranslucentUi());

        Path configFile = tempDir.resolve("config").resolve("gzcompanion").resolve("config.json");
        assertTrue(Files.exists(configFile), "config.json must be created");
    }

    @Test
    @DisplayName("Should safely recover and backup when config file contains malformed JSON")
    void testRecoverMalformedConfig(@TempDir Path tempDir) throws IOException {
        Path configDir = tempDir.resolve("config").resolve("gzcompanion");
        Files.createDirectories(configDir);
        Path configFile = configDir.resolve("config.json");
        Files.writeString(configFile, "{ invalid json content !!!");

        StorageManager manager = new StorageManager(tempDir);
        CompanionConfig recovered = manager.getConfig();

        assertNotNull(recovered);
        assertEquals(1, recovered.getSchemaVersion());

        Path backupFile = configDir.resolve("config.json.corrupt.bak");
        assertTrue(Files.exists(backupFile), "Backup file should be created");
    }
}
