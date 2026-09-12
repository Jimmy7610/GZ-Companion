package se.jimmyeliasson.gzcompanion.settings;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JsonSettingsStoreTest {

    @TempDir
    Path tempDir;

    private Path storeFile() {
        return tempDir.resolve("settings.json");
    }

    @Test
    @DisplayName("load() on a missing file returns NOT_FOUND with conservative defaults")
    void loadMissingFileReturnsDefaults() {
        JsonSettingsStore store = new JsonSettingsStore(storeFile());
        SettingsLoadResult result = store.load();
        assertEquals(SettingsLoadResult.Outcome.NOT_FOUND, result.outcome());
        assertEquals(CompanionSettings.defaults(), result.settings());
    }

    @Test
    @DisplayName("save() then load() round-trips every field exactly")
    void saveThenLoadRoundTrips() {
        JsonSettingsStore store = new JsonSettingsStore(storeFile());
        CompanionSettings settings = new CompanionSettings(false, true, false, true, false, List.of("Kalle92", "AnnaCraft"));
        store.save(settings);

        SettingsLoadResult result = store.load();
        assertEquals(SettingsLoadResult.Outcome.LOADED, result.outcome());
        assertEquals(settings, result.settings());
    }

    @Test
    @DisplayName("A settings file saved before favoritePlayers existed (migration) still loads, defaulting to an empty favorites list")
    void migrationFromPreviousSaveWithoutFavoritePlayers() throws Exception {
        Path file = storeFile();
        Files.writeString(file, "{\"schemaVersion\": 1, \"showTechnicalIds\": false}");

        JsonSettingsStore store = new JsonSettingsStore(file);
        SettingsLoadResult result = store.load();

        assertEquals(SettingsLoadResult.Outcome.LOADED, result.outcome());
        assertEquals(List.of(), result.settings().favoritePlayers());
        assertFalse(result.settings().showTechnicalIds(), "Pre-existing fields must still load correctly alongside the new one.");
    }

    @Test
    @DisplayName("Corrupted favoritePlayers data (wrong type, non-string entries) degrades safely rather than crashing")
    void corruptedFavoritesDataDegradesSafely() throws Exception {
        Path file = storeFile();
        Files.writeString(file, "{\"schemaVersion\": 1, \"favoritePlayers\": [\"Kalle92\", 42, null, \"\", \"   \", {\"nested\":true}, \"AnnaCraft\"]}");

        JsonSettingsStore store = new JsonSettingsStore(file);
        SettingsLoadResult result = store.load();

        assertEquals(SettingsLoadResult.Outcome.LOADED, result.outcome());
        assertEquals(List.of("Kalle92", "AnnaCraft"), result.settings().favoritePlayers(),
                "Malformed entries must be skipped individually - never crash the whole settings load.");
    }

    @Test
    @DisplayName("A favoritePlayers value that isn't a JSON array at all degrades to an empty list rather than crashing")
    void favoritePlayersWrongTopLevelTypeDegradesSafely() throws Exception {
        Path file = storeFile();
        Files.writeString(file, "{\"schemaVersion\": 1, \"favoritePlayers\": \"not-an-array\"}");

        JsonSettingsStore store = new JsonSettingsStore(file);
        SettingsLoadResult result = store.load();

        assertEquals(SettingsLoadResult.Outcome.LOADED, result.outcome());
        assertEquals(List.of(), result.settings().favoritePlayers());
    }

    @Test
    @DisplayName("A future schema version is reported as incompatible and the file is left untouched")
    void futureSchemaIsIncompatible() throws Exception {
        Path file = storeFile();
        Files.writeString(file, "{\"schemaVersion\": 999}");
        String before = Files.readString(file);

        JsonSettingsStore store = new JsonSettingsStore(file);
        SettingsLoadResult result = store.load();

        assertEquals(SettingsLoadResult.Outcome.INCOMPATIBLE_SCHEMA, result.outcome());
        assertEquals(before, Files.readString(file));
    }

    @Test
    @DisplayName("A corrupt file is backed up and recovered as conservative defaults")
    void corruptFileIsRecovered() throws Exception {
        Path file = storeFile();
        Files.writeString(file, "not valid json {{{");

        JsonSettingsStore store = new JsonSettingsStore(file);
        SettingsLoadResult result = store.load();

        assertEquals(SettingsLoadResult.Outcome.CORRUPT_RECOVERED, result.outcome());
        assertTrue(result.isUsable());
        assertEquals(CompanionSettings.defaults(), result.settings());
    }

    @Test
    @DisplayName("A partially-written file falls back to conservative defaults for any missing field")
    void partialFileFallsBackPerField() throws Exception {
        Path file = storeFile();
        Files.writeString(file, "{\"schemaVersion\": 1, \"showTechnicalIds\": false}");

        JsonSettingsStore store = new JsonSettingsStore(file);
        SettingsLoadResult result = store.load();

        assertEquals(SettingsLoadResult.Outcome.LOADED, result.outcome());
        assertFalse(result.settings().showTechnicalIds());
        assertTrue(result.settings().companionNotificationsEnabled(), "Missing fields must fall back to the conservative default.");
    }
}
