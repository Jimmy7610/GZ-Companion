package se.jimmyeliasson.gzcompanion.settlement.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JsonSettlementPlannerStoreTest {

    @TempDir
    Path tempDir;

    private Path storeFile() {
        return tempDir.resolve("settlement-planner.json");
    }

    @Test
    @DisplayName("load() on a missing file returns NOT_FOUND with an empty, usable dataset")
    void loadMissingFileReturnsNotFound() {
        JsonSettlementPlannerStore store = new JsonSettlementPlannerStore(storeFile());
        SettlementPlannerLoadResult result = store.load();
        assertEquals(SettlementPlannerLoadResult.Outcome.NOT_FOUND, result.outcome());
        assertTrue(result.isUsable());
    }

    @Test
    @DisplayName("save() then load() round-trips level selections, owned amounts, and members exactly")
    void saveThenLoadRoundTrips() {
        JsonSettlementPlannerStore store = new JsonSettlementPlannerStore(storeFile());
        SettlementPlannerProfile profile = SettlementPlannerProfile.empty()
                .withCurrentLevel(3)
                .withTargetLevel(10)
                .withOwnedAmount("minecraft:oak_log", 42)
                .withMember(new MemberNote("m1", "Alice", "Byggare - ansvarar för centrum"));

        SettlementPlannerData data = SettlementPlannerData.empty().withProfile("server:gamezone.se", profile);
        store.save(data);

        SettlementPlannerLoadResult result = store.load();
        assertEquals(SettlementPlannerLoadResult.Outcome.LOADED, result.outcome());

        SettlementPlannerProfile loaded = result.data().getProfile("server:gamezone.se");
        assertEquals(3, loaded.currentLevel());
        assertEquals(10, loaded.targetLevel());
        assertEquals(42, loaded.ownedAmount("minecraft:oak_log"));
        assertEquals(1, loaded.members().size());
        assertEquals("Alice", loaded.members().get(0).playerName());
    }

    @Test
    @DisplayName("Two different context keys are stored and loaded in isolation from each other")
    void contextIsolation() {
        JsonSettlementPlannerStore store = new JsonSettlementPlannerStore(storeFile());
        SettlementPlannerData data = SettlementPlannerData.empty()
                .withProfile("server:gamezone.se", SettlementPlannerProfile.empty().withCurrentLevel(5))
                .withProfile("singleplayer:testworld", SettlementPlannerProfile.empty().withCurrentLevel(20));
        store.save(data);

        SettlementPlannerData loaded = store.load().data();
        assertEquals(5, loaded.getProfile("server:gamezone.se").currentLevel());
        assertEquals(20, loaded.getProfile("singleplayer:testworld").currentLevel());
        // A context that was never written must never see another context's data.
        assertNull(loaded.getProfile("server:other.example").currentLevel());
    }

    @Test
    @DisplayName("A future schema version is reported as incompatible and the file is left untouched")
    void futureSchemaIsIncompatibleAndUntouched() throws Exception {
        Path file = storeFile();
        Files.writeString(file, "{\"schemaVersion\": 999, \"profiles\": {}}");
        String before = Files.readString(file);

        JsonSettlementPlannerStore store = new JsonSettlementPlannerStore(file);
        SettlementPlannerLoadResult result = store.load();

        assertEquals(SettlementPlannerLoadResult.Outcome.INCOMPATIBLE_SCHEMA, result.outcome());
        assertFalse(result.isUsable());
        assertEquals(before, Files.readString(file), "An incompatible-schema file must never be modified.");
    }

    @Test
    @DisplayName("A corrupt (non-JSON-object) file is backed up and recovered as an empty dataset")
    void corruptFileIsBackedUpAndRecovered() throws Exception {
        Path file = storeFile();
        Files.writeString(file, "not valid json {{{");

        JsonSettlementPlannerStore store = new JsonSettlementPlannerStore(file);
        SettlementPlannerLoadResult result = store.load();

        assertEquals(SettlementPlannerLoadResult.Outcome.CORRUPT_RECOVERED, result.outcome());
        assertTrue(result.isUsable());

        boolean backupExists;
        try (var stream = Files.list(tempDir)) {
            backupExists = stream.anyMatch(p -> p.getFileName().toString().contains(".corrupt."));
        }
        assertTrue(backupExists, "The corrupt file must be preserved as a backup, not deleted.");
    }

    @Test
    @DisplayName("A malformed single profile entry is skipped without discarding the rest of the file")
    void malformedProfileEntryIsSkipped() throws Exception {
        Path file = storeFile();
        Files.writeString(file, "{\"schemaVersion\": 1, \"profiles\": {"
                + "\"server:good\": {\"currentLevel\": 4, \"ownedItemAmounts\": {}, \"members\": []},"
                + "\"server:bad\": \"not-an-object-would-be-skipped-by-outer-check\""
                + "}}");

        JsonSettlementPlannerStore store = new JsonSettlementPlannerStore(file);
        SettlementPlannerLoadResult result = store.load();

        assertEquals(SettlementPlannerLoadResult.Outcome.LOADED, result.outcome());
        assertEquals(4, result.data().getProfile("server:good").currentLevel());
    }
}
