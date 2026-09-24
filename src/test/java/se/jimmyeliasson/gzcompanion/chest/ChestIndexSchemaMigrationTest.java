package se.jimmyeliasson.gzcompanion.chest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import se.jimmyeliasson.gzcompanion.chest.model.ChestManagerStatus;
import se.jimmyeliasson.gzcompanion.chest.model.ChestSlotEntry;
import se.jimmyeliasson.gzcompanion.chest.model.PreviousSnapshot;
import se.jimmyeliasson.gzcompanion.chest.model.StorageKind;
import se.jimmyeliasson.gzcompanion.chest.model.StorageMetadata;
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

/**
 * Kistor 2.0 schema v1 -> v2 migration: real v1 chest-index.json files (including the pre-shape
 * legacy {@code partnerUnknown} records) must load losslessly with safe defaults for every new
 * field, and a future schema must still fail closed without the file ever being touched.
 */
class ChestIndexSchemaMigrationTest {

    @TempDir
    Path tempDir;

    private static final String CTX = "11111111-2222-3333-4444-555555555555@@server:play.gamezonemc.se";

    /** A realistic schema-v1 file as written by alpha.6: label, double chest, legacy record, barrel. */
    private static final String V1_FIXTURE = """
            {
              "schemaVersion": 1,
              "contexts": {
                "11111111-2222-3333-4444-555555555555@@server:play.gamezonemc.se": {
                  "containers": {
                    "a": {
                      "kind": "CHEST",
                      "label": "Materiallager",
                      "dimension": "minecraft:overworld",
                      "anchor": { "x": 120, "y": 64, "z": -32 },
                      "partner": { "x": 121, "y": 64, "z": -32 },
                      "shape": "DOUBLE",
                      "lastOpenedAtMs": 1726000000000,
                      "slots": [
                        { "slot": 0, "itemId": "minecraft:iron_ingot", "count": 64 },
                        { "slot": 1, "itemId": "minecraft:iron_ingot", "count": 32 },
                        { "slot": 30, "itemId": "minecraft:stone", "count": 64 }
                      ]
                    },
                    "b": {
                      "kind": "CHEST",
                      "dimension": "minecraft:overworld",
                      "anchor": { "x": 10, "y": 70, "z": 10 },
                      "partnerUnknown": true,
                      "lastOpenedAtMs": 1725000000000,
                      "slots": [ { "slot": 3, "itemId": "minecraft:diamond", "count": 4 } ]
                    },
                    "c": {
                      "kind": "BARREL",
                      "label": "Gruvbas",
                      "dimension": "minecraft:the_nether",
                      "anchor": { "x": -5, "y": 40, "z": 7 },
                      "lastOpenedAtMs": 1724000000000,
                      "slots": []
                    }
                  }
                }
              }
            }
            """;

    private Path writeFixture(String content) throws IOException {
        Path file = tempDir.resolve("chest-index.json");
        Files.writeString(file, content);
        return file;
    }

    private static StoredContainer byAnchor(ContextContainers ctx, int x, int y, int z) {
        return ctx.containers().values().stream()
                .filter(c -> c.anchor().equals(new StoragePosition(x, y, z)))
                .findFirst().orElseThrow();
    }

    @Test
    @DisplayName("v1 file loads losslessly: labels, ids, positions, shape, partner, lastOpened and slots are all preserved")
    void v1LoadsLosslessly() throws IOException {
        JsonChestIndexStore store = new JsonChestIndexStore(writeFixture(V1_FIXTURE));
        ChestIndexLoadResult result = store.load();

        assertEquals(ChestIndexLoadResult.Outcome.LOADED, result.outcome());
        ContextContainers ctx = result.data().getContext(CTX);
        assertEquals(3, ctx.containers().size());

        StoredContainer a = byAnchor(ctx, 120, 64, -32);
        assertEquals("Materiallager", a.label());
        assertEquals(StorageKind.CHEST, a.kind());
        assertEquals(StorageShape.DOUBLE, a.shape());
        assertEquals(new StoragePosition(121, 64, -32), a.partner());
        assertEquals(1726000000000L, a.lastOpenedAtMs());
        assertEquals(List.of(new ChestSlotEntry(0, "minecraft:iron_ingot", 64), new ChestSlotEntry(1, "minecraft:iron_ingot", 32),
                new ChestSlotEntry(30, "minecraft:stone", 64)), a.slots());
        assertEquals(new StoredContainerId(CTX, "minecraft:overworld", new StoragePosition(120, 64, -32), StorageKind.CHEST), a.id());

        StoredContainer b = byAnchor(ctx, 10, 70, 10);
        assertEquals(StorageShape.UNKNOWN, b.shape(), "Legacy partnerUnknown=true must still migrate to UNKNOWN");
        assertNull(b.label());

        StoredContainer c = byAnchor(ctx, -5, 40, 7);
        assertEquals("minecraft:the_nether", c.dimensionKey());
        assertEquals(StorageShape.NOT_APPLICABLE, c.shape());
        assertTrue(c.slots().isEmpty());
    }

    @Test
    @DisplayName("v1 records get safe Kistor 2.0 defaults: not favorite, no group, no note, no previous snapshot")
    void v1GetsSafeDefaults() throws IOException {
        ChestIndexLoadResult result = new JsonChestIndexStore(writeFixture(V1_FIXTURE)).load();
        for (StoredContainer c : result.data().getContext(CTX).containers().values()) {
            assertEquals(StorageMetadata.EMPTY, c.metadata());
            assertFalse(c.favorite());
            assertNull(c.group());
            assertNull(c.locationNote());
            assertFalse(c.hasPreviousSnapshot());
            assertNull(c.previousSnapshot());
        }
    }

    @Test
    @DisplayName("Loading a v1 file never rewrites it on disk; it is migrated in memory and tagged with the current schema")
    void loadingNeverRewritesV1File() throws IOException {
        Path file = writeFixture(V1_FIXTURE);
        ChestManager manager = new ChestManager(new JsonChestIndexStore(file));
        manager.initialize();

        assertEquals(ChestManagerStatus.LOADED, manager.getStatus());
        assertEquals(V1_FIXTURE, Files.readString(file), "Merely loading must not touch the file");
        assertEquals(ChestIndexData.CURRENT_SCHEMA, manager.getDiagnostics(CTX).schemaVersion());
        assertEquals(3, manager.getIndexedCount(CTX));
    }

    @Test
    @DisplayName("The first save after a v1 load writes schema v2 and keeps every v1 field intact")
    void firstSaveWritesV2PreservingEverything() throws IOException {
        Path file = writeFixture(V1_FIXTURE);
        ChestManager manager = new ChestManager(new JsonChestIndexStore(file));
        manager.initialize();

        StoredContainerId aId = new StoredContainerId(CTX, "minecraft:overworld", new StoragePosition(120, 64, -32), StorageKind.CHEST);
        assertTrue(manager.setFavorite(CTX, aId, true));

        JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        assertEquals(2, root.get("schemaVersion").getAsInt());

        ChestManager reloaded = new ChestManager(new JsonChestIndexStore(file));
        reloaded.initialize();
        assertEquals(3, reloaded.getIndexedCount(CTX));
        StoredContainer a = reloaded.getContainer(CTX, aId).orElseThrow();
        assertTrue(a.favorite());
        assertEquals("Materiallager", a.label());
        assertEquals(StorageShape.DOUBLE, a.shape());
        assertEquals(3, a.slots().size());
        StoredContainer b = reloaded.getContainers(CTX).stream().filter(c -> c.anchor().x() == 10).findFirst().orElseThrow();
        assertEquals(StorageShape.UNKNOWN, b.shape(), "Legacy-migrated shape survives the v2 rewrite as an explicit field");
    }

    @Test
    @DisplayName("v2 metadata and the previous snapshot round-trip through save/load")
    void v2RoundTrip() {
        Path file = tempDir.resolve("chest-index.json");
        JsonChestIndexStore store = new JsonChestIndexStore(file);
        StoredContainerId id = new StoredContainerId(CTX, "minecraft:overworld", new StoragePosition(1, 2, 3), StorageKind.BARREL);
        StoredContainer container = new StoredContainer(id, "Värdesaker", null, StorageShape.NOT_APPLICABLE, 9000L,
                List.of(new ChestSlotEntry(0, "minecraft:diamond", 17)),
                new StorageMetadata(true, "Min bas", "Källaren bakom smedjan"),
                new PreviousSnapshot(5000L, List.of(new ChestSlotEntry(0, "minecraft:diamond", 29))));
        store.save(ChestIndexData.empty().withContext(CTX, new ContextContainers(Map.of(id.asStableKey(), container))));

        StoredContainer loaded = store.load().data().getContext(CTX).containers().get(id.asStableKey());
        assertEquals(container, loaded);
    }

    @Test
    @DisplayName("Malformed Kistor 2.0 fields fall back to defaults instead of discarding the storage entry; unknown fields are tolerated")
    void malformedNewFieldsAreTolerated() throws IOException {
        String content = """
                { "schemaVersion": 2, "contexts": { "%s": { "containers": { "x": {
                  "kind": "CHEST", "dimension": "minecraft:overworld", "anchor": {"x":1,"y":2,"z":3}, "shape": "SINGLE",
                  "lastOpenedAtMs": 100, "slots": [ {"slot":0,"itemId":"minecraft:torch","count":5} ],
                  "favorite": {"not": "a boolean"}, "group": ["nope"], "locationNote": 42,
                  "previous": "not-an-object", "someFutureField": {"deep": true}
                } } } } }
                """.formatted(CTX);
        ChestIndexLoadResult result = new JsonChestIndexStore(writeFixture(content)).load();
        assertEquals(ChestIndexLoadResult.Outcome.LOADED, result.outcome());
        StoredContainer c = result.data().getContext(CTX).containers().values().iterator().next();
        assertFalse(c.favorite());
        assertNull(c.group());
        assertEquals("42", c.locationNote(), "A primitive note value is read as text");
        assertNull(c.previousSnapshot());
        assertEquals(1, c.slots().size());
    }

    @Test
    @DisplayName("A future schema (v3) still fails closed: INCOMPATIBLE, no mutation, and the file is never overwritten")
    void futureSchemaStillFailsClosed() throws IOException {
        String future = "{ \"schemaVersion\": 3, \"contexts\": { \"x\": { \"containers\": {} } }, \"newTopLevel\": 1 }";
        Path file = writeFixture(future);
        ChestManager manager = new ChestManager(new JsonChestIndexStore(file));
        manager.initialize();

        assertEquals(ChestManagerStatus.INCOMPATIBLE, manager.getStatus());
        StoredContainerId id = new StoredContainerId(CTX, "minecraft:overworld", new StoragePosition(0, 0, 0), StorageKind.CHEST);
        assertFalse(manager.setFavorite(CTX, id, true));
        assertFalse(manager.setGroup(CTX, id, "Min bas"));
        assertFalse(manager.setLocationNote(CTX, id, "x"));
        assertFalse(manager.clearContext(CTX));
        assertTrue(manager.endCapture(1L).isEmpty());
        assertEquals(future, Files.readString(file), "An incompatible future file must never be modified");
    }
}
