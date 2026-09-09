package se.jimmyeliasson.gzcompanion.chest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import se.jimmyeliasson.gzcompanion.chest.model.ChestSlotEntry;
import se.jimmyeliasson.gzcompanion.chest.model.StorageKind;
import se.jimmyeliasson.gzcompanion.chest.model.StoragePosition;
import se.jimmyeliasson.gzcompanion.chest.model.StoredContainer;
import se.jimmyeliasson.gzcompanion.chest.model.StoredContainerId;
import se.jimmyeliasson.gzcompanion.chest.storage.ChestIndexData;
import se.jimmyeliasson.gzcompanion.chest.storage.ChestIndexLoadResult;
import se.jimmyeliasson.gzcompanion.chest.storage.ContextContainers;
import se.jimmyeliasson.gzcompanion.chest.storage.JsonChestIndexStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JsonChestIndexStoreTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Should round-trip save and reload the chest index atomically")
    void testSaveAndLoadRoundTrip() {
        Path storePath = tempDir.resolve("chest-index.json");
        JsonChestIndexStore store = new JsonChestIndexStore(storePath);

        StoredContainerId id = new StoredContainerId("player1@@server:play.gamezonemc.se", "minecraft:overworld",
                new StoragePosition(120, 64, -32), StorageKind.CHEST);
        StoredContainer container = new StoredContainer(id, "Gruvbas", null, false, 5000L,
                List.of(new ChestSlotEntry(0, "minecraft:iron_ingot", 32), new ChestSlotEntry(1, "minecraft:torch", 18)));

        ChestIndexData data = ChestIndexData.empty().withContext(id.contextStorageKey(),
                new ContextContainers(Map.of(id.asStableKey(), container)));

        store.save(data);
        assertTrue(Files.exists(storePath));

        ChestIndexLoadResult result = store.load();
        assertEquals(ChestIndexLoadResult.Outcome.LOADED, result.outcome());
        assertTrue(result.isUsable());

        StoredContainer reloadedContainer = result.data().getContext(id.contextStorageKey()).containers().get(id.asStableKey());
        assertNotNull(reloadedContainer);
        assertEquals("Gruvbas", reloadedContainer.label());
        assertEquals(2, reloadedContainer.slots().size());
        assertEquals(StorageKind.CHEST, reloadedContainer.kind());
        assertEquals(new StoragePosition(120, 64, -32), reloadedContainer.anchor());
    }

    @Test
    @DisplayName("Should report NOT_FOUND with an empty, usable index when no file exists yet")
    void testNotFoundOutcome() {
        Path storePath = tempDir.resolve("chest-index.json");
        JsonChestIndexStore store = new JsonChestIndexStore(storePath);

        ChestIndexLoadResult result = store.load();
        assertEquals(ChestIndexLoadResult.Outcome.NOT_FOUND, result.outcome());
        assertTrue(result.isUsable());
        assertTrue(result.data().contexts().isEmpty());
    }

    @Test
    @DisplayName("Should not leave a leftover .tmp file after a successful save")
    void testAtomicSaveLeavesNoTempFile() {
        Path storePath = tempDir.resolve("chest-index.json");
        JsonChestIndexStore store = new JsonChestIndexStore(storePath);
        store.save(ChestIndexData.empty());

        Path tmp = storePath.resolveSibling(storePath.getFileName().toString() + ".tmp");
        assertFalse(Files.exists(tmp), "No .tmp file should remain after a successful atomic save");
        assertTrue(Files.exists(storePath));
    }

    @Test
    @DisplayName("Should recover gracefully from a corrupted chest index file and back it up")
    void testCorruptionRecovery() throws IOException {
        Path storePath = tempDir.resolve("chest-index.json");
        Files.writeString(storePath, "{ not valid json at all !!!");

        JsonChestIndexStore store = new JsonChestIndexStore(storePath);
        ChestIndexLoadResult result = store.load();

        assertEquals(ChestIndexLoadResult.Outcome.CORRUPT_RECOVERED, result.outcome());
        assertTrue(result.isUsable());
        assertTrue(result.data().contexts().isEmpty());

        boolean backupFound = Files.list(tempDir).anyMatch(p -> p.getFileName().toString().contains(".corrupt."));
        assertTrue(backupFound, "Corrupt file should be preserved with a timestamped backup");
    }

    @Test
    @DisplayName("Should report INCOMPATIBLE_SCHEMA for an unsupported future schema, as a non-usable outcome")
    void testUnsupportedFutureSchemaRejected() throws IOException {
        Path storePath = tempDir.resolve("chest-index.json");
        Files.writeString(storePath, "{ \"schemaVersion\": 999, \"contexts\": {} }");

        JsonChestIndexStore store = new JsonChestIndexStore(storePath);
        ChestIndexLoadResult result = store.load();

        assertEquals(ChestIndexLoadResult.Outcome.INCOMPATIBLE_SCHEMA, result.outcome());
        assertFalse(result.isUsable(), "An incompatible future schema must never be treated as usable/loaded");
        assertTrue(result.data().contexts().isEmpty());
    }

    @Test
    @DisplayName("Should never move, delete, or overwrite a future-schema file on load")
    void testFutureSchemaFileIsNeverTouched() throws IOException {
        Path storePath = tempDir.resolve("chest-index.json");
        String futureContent = "{ \"schemaVersion\": 999, \"contexts\": { \"someFutureShape\": \"unrecognized-by-this-client\" } }";
        Files.writeString(storePath, futureContent);
        byte[] originalBytes = Files.readAllBytes(storePath);

        JsonChestIndexStore store = new JsonChestIndexStore(storePath);
        store.load();

        assertTrue(Files.exists(storePath), "The future-schema file must still exist at its original path");
        assertArrayEquals(originalBytes, Files.readAllBytes(storePath), "The future-schema file's bytes must be completely untouched");

        boolean noCorruptBackupCreated = Files.list(tempDir).noneMatch(p -> p.getFileName().toString().contains(".corrupt."));
        assertTrue(noCorruptBackupCreated, "A future schema is not corruption - it must not be backed up or moved");
    }

    @Test
    @DisplayName("Should tolerate unknown extra fields without failing to load")
    void testUnknownFieldsTolerated() throws IOException {
        Path storePath = tempDir.resolve("chest-index.json");
        String json = "{"
                + "\"schemaVersion\": 1,"
                + "\"futureTopLevelField\": \"ignored\","
                + "\"contexts\": {"
                + "  \"ctx1\": {"
                + "    \"futureContextField\": true,"
                + "    \"containers\": {"
                + "      \"k1\": {"
                + "        \"kind\": \"CHEST\","
                + "        \"dimension\": \"minecraft:overworld\","
                + "        \"anchor\": {\"x\": 1, \"y\": 2, \"z\": 3},"
                + "        \"partnerUnknown\": false,"
                + "        \"lastOpenedAtMs\": 42,"
                + "        \"slots\": [],"
                + "        \"futureSlotField\": \"ignored\""
                + "      }"
                + "    }"
                + "  }"
                + "}"
                + "}";
        Files.writeString(storePath, json);

        JsonChestIndexStore store = new JsonChestIndexStore(storePath);
        ChestIndexLoadResult result = store.load();

        assertEquals(ChestIndexLoadResult.Outcome.LOADED, result.outcome());
        assertEquals(1, result.data().getContext("ctx1").containers().size());
    }

    @Test
    @DisplayName("Should isolate containers stored under different contexts")
    void testContextsIsolated() {
        Path storePath = tempDir.resolve("chest-index.json");
        JsonChestIndexStore store = new JsonChestIndexStore(storePath);

        StoredContainerId idA = new StoredContainerId("ctxA", "minecraft:overworld", new StoragePosition(1, 1, 1), StorageKind.BARREL);
        StoredContainerId idB = new StoredContainerId("ctxB", "minecraft:overworld", new StoragePosition(1, 1, 1), StorageKind.BARREL);

        StoredContainer containerA = new StoredContainer(idA, null, null, false, 1L, List.of());
        StoredContainer containerB = new StoredContainer(idB, null, null, false, 1L, List.of());

        ChestIndexData data = ChestIndexData.empty()
                .withContext("ctxA", new ContextContainers(Map.of(idA.asStableKey(), containerA)))
                .withContext("ctxB", new ContextContainers(Map.of(idB.asStableKey(), containerB)));
        store.save(data);

        ChestIndexData reloaded = store.load().data();
        assertEquals(1, reloaded.getContext("ctxA").containers().size());
        assertEquals(1, reloaded.getContext("ctxB").containers().size());
        assertTrue(reloaded.getContext("ctxNonExistent").containers().isEmpty());
    }
}
