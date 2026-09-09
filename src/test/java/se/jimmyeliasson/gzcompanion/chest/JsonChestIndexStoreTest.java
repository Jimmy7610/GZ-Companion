package se.jimmyeliasson.gzcompanion.chest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import se.jimmyeliasson.gzcompanion.chest.model.ChestSlotEntry;
import se.jimmyeliasson.gzcompanion.chest.model.StorageKind;
import se.jimmyeliasson.gzcompanion.chest.model.StoragePosition;
import se.jimmyeliasson.gzcompanion.chest.model.StorageShape;
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
        StoredContainer container = new StoredContainer(id, "Gruvbas", null, StorageShape.SINGLE, 5000L,
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

        StoredContainer containerA = new StoredContainer(idA, null, null, StorageShape.NOT_APPLICABLE, 1L, List.of());
        StoredContainer containerB = new StoredContainer(idB, null, null, StorageShape.NOT_APPLICABLE, 1L, List.of());

        ChestIndexData data = ChestIndexData.empty()
                .withContext("ctxA", new ContextContainers(Map.of(idA.asStableKey(), containerA)))
                .withContext("ctxB", new ContextContainers(Map.of(idB.asStableKey(), containerB)));
        store.save(data);

        ChestIndexData reloaded = store.load().data();
        assertEquals(1, reloaded.getContext("ctxA").containers().size());
        assertEquals(1, reloaded.getContext("ctxB").containers().size());
        assertTrue(reloaded.getContext("ctxNonExistent").containers().isEmpty());
    }

    // ------------------------------------------------------------------
    // StorageShape legacy migration (backward compatibility, no data loss)
    // ------------------------------------------------------------------

    private String legacyContainerJson(String kind, String partnerJson, String partnerUnknown) {
        return "{"
                + "\"schemaVersion\": 1,"
                + "\"contexts\": { \"ctx1\": { \"containers\": { \"k1\": {"
                + "  \"kind\": \"" + kind + "\","
                + "  \"dimension\": \"minecraft:overworld\","
                + "  \"anchor\": {\"x\": 1, \"y\": 2, \"z\": 3},"
                + (partnerJson != null ? "\"partner\": " + partnerJson + "," : "")
                + "  \"partnerUnknown\": " + partnerUnknown + ","
                + "  \"lastOpenedAtMs\": 42,"
                + "  \"slots\": []"
                + "} } } } }";
    }

    @Test
    @DisplayName("Legacy record with a partner position migrates to DOUBLE shape")
    void testLegacyMigrationPartnerPresentBecomesDouble() throws IOException {
        Path storePath = tempDir.resolve("chest-index.json");
        Files.writeString(storePath, legacyContainerJson("CHEST", "{\"x\":4,\"y\":2,\"z\":3}", "false"));

        StoredContainer container = new JsonChestIndexStore(storePath).load().data().getContext("ctx1").containers().values().stream().findFirst().orElse(null);
        assertNotNull(container);
        assertEquals(StorageShape.DOUBLE, container.shape());
        assertEquals(new StoragePosition(4, 2, 3), container.partner());
    }

    @Test
    @DisplayName("Legacy record with no partner and partnerUnknown=true migrates to UNKNOWN shape")
    void testLegacyMigrationPartnerUnknownBecomesUnknown() throws IOException {
        Path storePath = tempDir.resolve("chest-index.json");
        Files.writeString(storePath, legacyContainerJson("CHEST", null, "true"));

        StoredContainer container = new JsonChestIndexStore(storePath).load().data().getContext("ctx1").containers().values().stream().findFirst().orElse(null);
        assertNotNull(container);
        assertEquals(StorageShape.UNKNOWN, container.shape());
        assertNull(container.partner());
    }

    @Test
    @DisplayName("Legacy chest-family record with no partner and partnerUnknown=false migrates to SINGLE shape")
    void testLegacyMigrationChestFamilyBecomesSingle() throws IOException {
        Path storePath = tempDir.resolve("chest-index.json");
        Files.writeString(storePath, legacyContainerJson("TRAPPED_CHEST", null, "false"));

        StoredContainer container = new JsonChestIndexStore(storePath).load().data().getContext("ctx1").containers().values().stream().findFirst().orElse(null);
        assertNotNull(container);
        assertEquals(StorageShape.SINGLE, container.shape());
    }

    @Test
    @DisplayName("Legacy non-chest record with no partner migrates to NOT_APPLICABLE shape")
    void testLegacyMigrationNonChestBecomesNotApplicable() throws IOException {
        Path storePath = tempDir.resolve("chest-index.json");
        Files.writeString(storePath, legacyContainerJson("BARREL", null, "false"));

        StoredContainer container = new JsonChestIndexStore(storePath).load().data().getContext("ctx1").containers().values().stream().findFirst().orElse(null);
        assertNotNull(container);
        assertEquals(StorageShape.NOT_APPLICABLE, container.shape());
    }

    @Test
    @DisplayName("An explicit new-format shape field round-trips exactly")
    void testExplicitShapeFieldRoundTrips() {
        Path storePath = tempDir.resolve("chest-index.json");
        JsonChestIndexStore store = new JsonChestIndexStore(storePath);

        StoredContainerId id = new StoredContainerId("ctx1", "minecraft:overworld", new StoragePosition(1, 1, 1), StorageKind.CHEST);
        StoredContainer container = new StoredContainer(id, null, null, StorageShape.SINGLE, 1L, List.of());
        store.save(ChestIndexData.empty().withContext("ctx1", new ContextContainers(Map.of(id.asStableKey(), container))));

        StoredContainer reloaded = store.load().data().getContext("ctx1").containers().get(id.asStableKey());
        assertEquals(StorageShape.SINGLE, reloaded.shape());
    }

    @Test
    @DisplayName("An unparseable future shape value safely falls back to the legacy mapping instead of crashing")
    void testUnknownFutureShapeValueFallsBackSafely() throws IOException {
        Path storePath = tempDir.resolve("chest-index.json");
        String json = "{"
                + "\"schemaVersion\": 1,"
                + "\"contexts\": { \"ctx1\": { \"containers\": { \"k1\": {"
                + "  \"kind\": \"CHEST\","
                + "  \"dimension\": \"minecraft:overworld\","
                + "  \"anchor\": {\"x\": 1, \"y\": 2, \"z\": 3},"
                + "  \"shape\": \"TRIPLE_WIDE_FUTURE_SHAPE\","
                + "  \"partnerUnknown\": true,"
                + "  \"lastOpenedAtMs\": 42,"
                + "  \"slots\": []"
                + "} } } } }";
        Files.writeString(storePath, json);

        ChestIndexLoadResult result = new JsonChestIndexStore(storePath).load();
        assertEquals(ChestIndexLoadResult.Outcome.LOADED, result.outcome(), "An unknown shape value must not corrupt the whole file");
        StoredContainer container = result.data().getContext("ctx1").containers().values().stream().findFirst().orElse(null);
        assertNotNull(container);
        assertEquals(StorageShape.UNKNOWN, container.shape(), "Falls back to the legacy partnerUnknown mapping");
    }

    // ------------------------------------------------------------------
    // Persistence robustness audit (Part Q)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("A negative or zero schemaVersion is treated as malformed, not a valid old version")
    void testNegativeSchemaVersionTreatedAsCorrupt() throws IOException {
        Path storePath = tempDir.resolve("chest-index.json");
        Files.writeString(storePath, "{ \"schemaVersion\": -1, \"contexts\": {} }");

        ChestIndexLoadResult result = new JsonChestIndexStore(storePath).load();
        assertEquals(ChestIndexLoadResult.Outcome.CORRUPT_RECOVERED, result.outcome());
        assertTrue(result.data().contexts().isEmpty());
    }

    @Test
    @DisplayName("A null schemaVersion field defaults safely instead of throwing")
    void testNullSchemaVersionDefaultsSafely() throws IOException {
        Path storePath = tempDir.resolve("chest-index.json");
        Files.writeString(storePath, "{ \"schemaVersion\": null, \"contexts\": {} }");

        ChestIndexLoadResult result = new JsonChestIndexStore(storePath).load();
        assertTrue(result.isUsable());
    }

    @Test
    @DisplayName("A malformed individual container entry is skipped, leaving valid siblings intact")
    void testMalformedSingleEntrySkippedSiblingsIntact() throws IOException {
        Path storePath = tempDir.resolve("chest-index.json");
        String json = "{"
                + "\"schemaVersion\": 1,"
                + "\"contexts\": { \"ctx1\": { \"containers\": {"
                + "  \"bad\": { \"kind\": \"CHEST\", \"dimension\": \"minecraft:overworld\", \"anchor\": {\"x\": \"not-a-number\", \"y\": 2, \"z\": 3}, \"slots\": [] },"
                + "  \"good\": { \"kind\": \"CHEST\", \"dimension\": \"minecraft:overworld\", \"anchor\": {\"x\": 9, \"y\": 9, \"z\": 9}, \"shape\": \"SINGLE\", \"lastOpenedAtMs\": 1, \"slots\": [] }"
                + "} } } }";
        Files.writeString(storePath, json);

        ChestIndexLoadResult result = new JsonChestIndexStore(storePath).load();
        assertEquals(ChestIndexLoadResult.Outcome.LOADED, result.outcome());
        assertEquals(1, result.data().getContext("ctx1").containers().size(), "Only the malformed entry should be skipped");
    }

    @Test
    @DisplayName("An unknown StorageKind causes that single entry to be skipped safely")
    void testUnknownStorageKindSkipsEntry() throws IOException {
        Path storePath = tempDir.resolve("chest-index.json");
        String json = "{"
                + "\"schemaVersion\": 1,"
                + "\"contexts\": { \"ctx1\": { \"containers\": {"
                + "  \"k1\": { \"kind\": \"FUTURE_UNKNOWN_KIND\", \"dimension\": \"minecraft:overworld\", \"anchor\": {\"x\": 1, \"y\": 1, \"z\": 1}, \"slots\": [] }"
                + "} } } }";
        Files.writeString(storePath, json);

        ChestIndexLoadResult result = new JsonChestIndexStore(storePath).load();
        assertEquals(ChestIndexLoadResult.Outcome.LOADED, result.outcome());
        assertTrue(result.data().getContext("ctx1").containers().isEmpty());
    }

    @Test
    @DisplayName("Negative or zero slot counts are never persisted as usable slots")
    void testNegativeOrZeroSlotCountsExcluded() throws IOException {
        Path storePath = tempDir.resolve("chest-index.json");
        String json = "{"
                + "\"schemaVersion\": 1,"
                + "\"contexts\": { \"ctx1\": { \"containers\": {"
                + "  \"k1\": { \"kind\": \"CHEST\", \"dimension\": \"minecraft:overworld\", \"anchor\": {\"x\": 1, \"y\": 1, \"z\": 1}, \"shape\": \"SINGLE\", \"lastOpenedAtMs\": 1, \"slots\": ["
                + "    {\"slot\": 0, \"itemId\": \"minecraft:dirt\", \"count\": -5},"
                + "    {\"slot\": 1, \"itemId\": \"minecraft:dirt\", \"count\": 0},"
                + "    {\"slot\": 2, \"itemId\": \"minecraft:dirt\", \"count\": 3}"
                + "  ] }"
                + "} } } }";
        Files.writeString(storePath, json);

        StoredContainer container = new JsonChestIndexStore(storePath).load().data().getContext("ctx1").containers().values().stream().findFirst().orElse(null);
        assertNotNull(container);
        assertEquals(1, container.slots().size(), "Only the single valid positive-count slot must survive");
        assertEquals(3, container.slots().get(0).count());
    }

    // ------------------------------------------------------------------
    // Full context and dimension isolation across a reload (Part S / T)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Two different worlds each keep exactly their own indexed chest across a full manager reload")
    void testContextIsolationFullReloadNoLeakage() {
        Path storePath = tempDir.resolve("chest-index.json");
        String worldA = "playerX@@singleplayer:worldA";
        String worldB = "playerX@@singleplayer:worldB";
        StoragePosition samePos = new StoragePosition(10, 64, 10);

        ChestManager writer = new ChestManager(new JsonChestIndexStore(storePath));
        writer.initialize();
        java.util.Set<StorageKind> chestKinds = java.util.EnumSet.of(StorageKind.CHEST, StorageKind.TRAPPED_CHEST, StorageKind.BARREL);

        writer.recordPendingInteraction(worldA, "minecraft:overworld", StorageKind.CHEST, samePos, null, StorageShape.SINGLE, 1000L);
        writer.tryBeginCapture(worldA, "minecraft:overworld", chestKinds, 1050L);
        writer.updateCaptureSlots(List.of(new ChestSlotEntry(0, "minecraft:oak_log", 1)), 1100L);
        writer.endCapture(1200L);

        writer.recordPendingInteraction(worldB, "minecraft:overworld", StorageKind.CHEST, samePos, null, StorageShape.SINGLE, 2000L);
        writer.tryBeginCapture(worldB, "minecraft:overworld", chestKinds, 2050L);
        writer.updateCaptureSlots(List.of(new ChestSlotEntry(0, "minecraft:stone", 1)), 2100L);
        writer.endCapture(2200L);

        ChestManager reader = new ChestManager(new JsonChestIndexStore(storePath));
        reader.initialize();

        assertEquals(1, reader.getIndexedCount(worldA));
        assertEquals(1, reader.getIndexedCount(worldB));
        assertEquals("minecraft:oak_log", reader.getContainers(worldA).get(0).slots().get(0).itemId());
        assertEquals("minecraft:stone", reader.getContainers(worldB).get(0).slots().get(0).itemId());
    }

    @Test
    @DisplayName("The same coordinates in Overworld and Nether remain separate entries across a full manager reload")
    void testDimensionIsolationFullReloadNoLeakage() {
        Path storePath = tempDir.resolve("chest-index.json");
        String ctx = "playerX@@singleplayer:worldA";
        StoragePosition samePos = new StoragePosition(10, 64, 10);
        java.util.Set<StorageKind> chestKinds = java.util.EnumSet.of(StorageKind.CHEST, StorageKind.TRAPPED_CHEST, StorageKind.BARREL);

        ChestManager writer = new ChestManager(new JsonChestIndexStore(storePath));
        writer.initialize();

        writer.recordPendingInteraction(ctx, "minecraft:overworld", StorageKind.CHEST, samePos, null, StorageShape.SINGLE, 1000L);
        writer.tryBeginCapture(ctx, "minecraft:overworld", chestKinds, 1050L);
        writer.updateCaptureSlots(List.of(new ChestSlotEntry(0, "minecraft:oak_log", 1)), 1100L);
        writer.endCapture(1200L);

        writer.recordPendingInteraction(ctx, "minecraft:the_nether", StorageKind.CHEST, samePos, null, StorageShape.SINGLE, 2000L);
        writer.tryBeginCapture(ctx, "minecraft:the_nether", chestKinds, 2050L);
        writer.updateCaptureSlots(List.of(new ChestSlotEntry(0, "minecraft:netherrack", 1)), 2100L);
        writer.endCapture(2200L);

        ChestManager reader = new ChestManager(new JsonChestIndexStore(storePath));
        reader.initialize();

        assertEquals(2, reader.getIndexedCount(ctx), "Overworld and Nether at the same coordinates must remain two entries");
        assertTrue(reader.getContainers(ctx).stream().anyMatch(c -> "minecraft:overworld".equals(c.dimensionKey())));
        assertTrue(reader.getContainers(ctx).stream().anyMatch(c -> "minecraft:the_nether".equals(c.dimensionKey())));
    }
}
