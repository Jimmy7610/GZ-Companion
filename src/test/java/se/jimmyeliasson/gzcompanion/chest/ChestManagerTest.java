package se.jimmyeliasson.gzcompanion.chest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import se.jimmyeliasson.gzcompanion.chest.model.ChestManagerStatus;
import se.jimmyeliasson.gzcompanion.chest.model.ChestSlotEntry;
import se.jimmyeliasson.gzcompanion.chest.model.ChestSortMode;
import se.jimmyeliasson.gzcompanion.chest.model.ChestTypeFilter;
import se.jimmyeliasson.gzcompanion.chest.model.StorageKind;
import se.jimmyeliasson.gzcompanion.chest.model.StoragePosition;
import se.jimmyeliasson.gzcompanion.chest.model.StorageShape;
import se.jimmyeliasson.gzcompanion.chest.model.StoredContainer;
import se.jimmyeliasson.gzcompanion.chest.model.StoredContainerId;
import se.jimmyeliasson.gzcompanion.chest.storage.ChestIndexData;
import se.jimmyeliasson.gzcompanion.chest.storage.ChestIndexLoadResult;
import se.jimmyeliasson.gzcompanion.chest.storage.ChestIndexStore;
import se.jimmyeliasson.gzcompanion.chest.storage.JsonChestIndexStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ChestManagerTest {

    @TempDir
    Path tempDir;

    private static final String CTX_A = "player1@@server:play.gamezonemc.se";
    private static final String CTX_B = "player1@@singleplayer:world1";
    private static final String DIM_OVERWORLD = "minecraft:overworld";
    private static final String DIM_NETHER = "minecraft:the_nether";
    private static final Set<StorageKind> CHEST_MENU_KINDS = EnumSet.of(StorageKind.CHEST, StorageKind.TRAPPED_CHEST, StorageKind.BARREL);

    private ChestManager newManager() {
        ChestManager manager = new ChestManager(new JsonChestIndexStore(tempDir.resolve("chest-index.json")));
        manager.initialize();
        return manager;
    }

    private void openAndCloseChest(ChestManager manager, String context, String dimension, StoragePosition pos, List<ChestSlotEntry> slots, long atMs) {
        manager.recordPendingInteraction(context, dimension, StorageKind.CHEST, pos, null, StorageShape.SINGLE, atMs);
        boolean began = manager.tryBeginCapture(context, dimension, CHEST_MENU_KINDS, atMs + 50);
        assertTrue(began, "Capture should begin for a recent, matching physical interaction");
        manager.updateCaptureSlots(slots, atMs + 100);
        manager.endCapture(atMs + 200);
    }

    // ------------------------------------------------------------------
    // Storage model
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Reopening the same physical chest updates the existing record instead of duplicating it")
    void testSamePhysicalStorageUpdatesNotDuplicates() {
        ChestManager manager = newManager();
        StoragePosition pos = new StoragePosition(100, 64, 200);

        openAndCloseChest(manager, CTX_A, DIM_OVERWORLD, pos, List.of(new ChestSlotEntry(0, "minecraft:iron_ingot", 10)), 1000L);
        assertEquals(1, manager.getIndexedCount(CTX_A));

        openAndCloseChest(manager, CTX_A, DIM_OVERWORLD, pos, List.of(new ChestSlotEntry(0, "minecraft:iron_ingot", 32)), 5000L);
        assertEquals(1, manager.getIndexedCount(CTX_A), "Reopening the same chest must update, not duplicate");

        StoredContainer container = manager.getContainers(CTX_A).get(0);
        assertEquals(32, container.slots().get(0).count());
    }

    @Test
    @DisplayName("Same X/Y/Z in different contexts (world/server) are isolated entries")
    void testDifferentContextsSameXyzAreDifferentEntries() {
        ChestManager manager = newManager();
        StoragePosition pos = new StoragePosition(10, 65, 10);

        openAndCloseChest(manager, CTX_A, DIM_OVERWORLD, pos, List.of(new ChestSlotEntry(0, "minecraft:oak_log", 4)), 1000L);
        openAndCloseChest(manager, CTX_B, DIM_OVERWORLD, pos, List.of(new ChestSlotEntry(0, "minecraft:stone", 4)), 1000L);

        assertEquals(1, manager.getIndexedCount(CTX_A));
        assertEquals(1, manager.getIndexedCount(CTX_B));
        assertTrue(manager.getContainers(CTX_B).stream().noneMatch(c -> c.slots().stream().anyMatch(s -> s.itemId().equals("minecraft:oak_log"))));
    }

    @Test
    @DisplayName("Same X/Y/Z in different dimensions are isolated entries")
    void testDifferentDimensionsSameXyzAreDifferentEntries() {
        ChestManager manager = newManager();
        StoragePosition pos = new StoragePosition(0, 70, 0);

        openAndCloseChest(manager, CTX_A, DIM_OVERWORLD, pos, List.of(new ChestSlotEntry(0, "minecraft:cobblestone", 5)), 1000L);
        openAndCloseChest(manager, CTX_A, DIM_NETHER, pos, List.of(new ChestSlotEntry(0, "minecraft:netherrack", 5)), 1000L);

        assertEquals(2, manager.getIndexedCount(CTX_A), "Same coordinates in a different dimension must be a separate entry");
    }

    @Test
    @DisplayName("Double chest anchor canonicalizes regardless of which half was clicked")
    void testDoubleChestCanonicalAnchorRegardlessOfClickedHalf() {
        ChestManager managerLeftClick = newManager();
        StoragePosition left = new StoragePosition(5, 64, 5);
        StoragePosition right = new StoragePosition(6, 64, 5);

        managerLeftClick.recordPendingInteraction(CTX_A, DIM_OVERWORLD, StorageKind.CHEST, left, right, StorageShape.DOUBLE, 1000L);
        assertTrue(managerLeftClick.tryBeginCapture(CTX_A, DIM_OVERWORLD, CHEST_MENU_KINDS, 1050L));
        managerLeftClick.updateCaptureSlots(List.of(new ChestSlotEntry(0, "minecraft:bread", 1)), 1100L);
        managerLeftClick.endCapture(1200L);

        ChestManager managerRightClick = new ChestManager(new JsonChestIndexStore(tempDir.resolve("chest-index-2.json")));
        managerRightClick.initialize();
        managerRightClick.recordPendingInteraction(CTX_A, DIM_OVERWORLD, StorageKind.CHEST, right, left, StorageShape.DOUBLE, 1000L);
        assertTrue(managerRightClick.tryBeginCapture(CTX_A, DIM_OVERWORLD, CHEST_MENU_KINDS, 1050L));
        managerRightClick.updateCaptureSlots(List.of(new ChestSlotEntry(0, "minecraft:bread", 1)), 1100L);
        managerRightClick.endCapture(1200L);

        StoredContainerId idFromLeft = managerLeftClick.getContainers(CTX_A).get(0).id();
        StoredContainerId idFromRight = managerRightClick.getContainers(CTX_A).get(0).id();
        assertEquals(idFromLeft.asStableKey(), idFromRight.asStableKey(), "Clicking either half of a double chest must resolve to the same identity");
        assertEquals(left, idFromLeft.anchor(), "Canonical anchor must be the lower-ordered position");
    }

    // ------------------------------------------------------------------
    // Snapshot / fingerprint behavior
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Repeated identical fingerprint while open is a no-op and still persists correctly on close")
    void testRepeatedUnchangedFingerprintIsNoOp() {
        ChestManager manager = newManager();
        StoragePosition pos = new StoragePosition(1, 64, 1);
        List<ChestSlotEntry> slots = List.of(new ChestSlotEntry(0, "minecraft:torch", 4));

        manager.recordPendingInteraction(CTX_A, DIM_OVERWORLD, StorageKind.CHEST, pos, null, StorageShape.SINGLE, 1000L);
        manager.tryBeginCapture(CTX_A, DIM_OVERWORLD, CHEST_MENU_KINDS, 1050L);
        manager.updateCaptureSlots(slots, 1100L);
        manager.updateCaptureSlots(slots, 1120L);
        manager.updateCaptureSlots(slots, 1140L);
        manager.endCapture(1200L);

        StoredContainer container = manager.getContainers(CTX_A).get(0);
        assertEquals(1, container.slots().size());
        assertEquals(4, container.slots().get(0).count());
    }

    @Test
    @DisplayName("Changed visible storage contents while open update the finalized snapshot")
    void testChangedContentsUpdateSnapshot() {
        ChestManager manager = newManager();
        StoragePosition pos = new StoragePosition(2, 64, 2);

        manager.recordPendingInteraction(CTX_A, DIM_OVERWORLD, StorageKind.CHEST, pos, null, StorageShape.SINGLE, 1000L);
        manager.tryBeginCapture(CTX_A, DIM_OVERWORLD, CHEST_MENU_KINDS, 1050L);
        manager.updateCaptureSlots(List.of(new ChestSlotEntry(0, "minecraft:coal", 10)), 1100L);
        manager.updateCaptureSlots(List.of(new ChestSlotEntry(0, "minecraft:coal", 3), new ChestSlotEntry(1, "minecraft:iron_ingot", 5)), 1150L);
        manager.endCapture(1200L);

        StoredContainer container = manager.getContainers(CTX_A).get(0);
        assertEquals(2, container.slots().size());
    }

    @Test
    @DisplayName("Only top-level slot data is stored; no nested container structure exists on the record")
    void testTopLevelSlotDataOnly() {
        ChestSlotEntry entry = new ChestSlotEntry(0, "minecraft:shulker_box", 1);
        assertEquals("minecraft:shulker_box", entry.itemId());
        assertEquals(1, entry.count());
        assertEquals(0, entry.slotIndex());
    }

    // ------------------------------------------------------------------
    // Correlation
    // ------------------------------------------------------------------

    @Test
    @DisplayName("A recent matching physical interaction is accepted")
    void testRecentMatchingInteractionAccepted() {
        ChestManager manager = newManager();
        manager.recordPendingInteraction(CTX_A, DIM_OVERWORLD, StorageKind.BARREL, new StoragePosition(0, 0, 0), null, StorageShape.NOT_APPLICABLE, 1000L);
        assertTrue(manager.tryBeginCapture(CTX_A, DIM_OVERWORLD, CHEST_MENU_KINDS, 1500L));
    }

    @Test
    @DisplayName("A stale interaction beyond the correlation window is rejected")
    void testStaleInteractionRejected() {
        ChestManager manager = newManager();
        manager.recordPendingInteraction(CTX_A, DIM_OVERWORLD, StorageKind.CHEST, new StoragePosition(0, 0, 0), null, StorageShape.SINGLE, 1000L);
        long stale = 1000L + ChestManager.PENDING_INTERACTION_WINDOW_MS + 1;
        assertFalse(manager.tryBeginCapture(CTX_A, DIM_OVERWORLD, CHEST_MENU_KINDS, stale));
    }

    @Test
    @DisplayName("A dimension mismatch between interaction and menu open is rejected")
    void testDimensionMismatchRejected() {
        ChestManager manager = newManager();
        manager.recordPendingInteraction(CTX_A, DIM_OVERWORLD, StorageKind.CHEST, new StoragePosition(0, 0, 0), null, StorageShape.SINGLE, 1000L);
        assertFalse(manager.tryBeginCapture(CTX_A, DIM_NETHER, CHEST_MENU_KINDS, 1050L));
    }

    @Test
    @DisplayName("A context mismatch between interaction and menu open is rejected")
    void testContextMismatchRejected() {
        ChestManager manager = newManager();
        manager.recordPendingInteraction(CTX_A, DIM_OVERWORLD, StorageKind.CHEST, new StoragePosition(0, 0, 0), null, StorageShape.SINGLE, 1000L);
        assertFalse(manager.tryBeginCapture(CTX_B, DIM_OVERWORLD, CHEST_MENU_KINDS, 1050L));
    }

    @Test
    @DisplayName("An unsupported block never records a pending interaction, so no menu can correlate against it")
    void testUnsupportedBlockNeverCorrelates() {
        ChestManager manager = newManager();
        // Simulates a furnace/anvil/etc. right-click: the controller would never call
        // recordPendingInteraction for it at all.
        assertFalse(manager.tryBeginCapture(CTX_A, DIM_OVERWORLD, CHEST_MENU_KINDS, 1050L));
        assertEquals(0, manager.getIndexedCount(CTX_A));
    }

    @Test
    @DisplayName("A structurally compatible menu whose kind does not match the clicked block is rejected")
    void testCompatibleMenuWithoutMatchingPhysicalKindRejected() {
        ChestManager manager = newManager();
        // Player clicked a Hopper, but a ChestMenu-family screen opened - kinds don't intersect.
        manager.recordPendingInteraction(CTX_A, DIM_OVERWORLD, StorageKind.HOPPER, new StoragePosition(0, 0, 0), null, StorageShape.NOT_APPLICABLE, 1000L);
        assertFalse(manager.tryBeginCapture(CTX_A, DIM_OVERWORLD, CHEST_MENU_KINDS, 1050L));
        assertEquals(0, manager.getIndexedCount(CTX_A));
    }

    @Test
    @DisplayName("A plugin/virtual container-style GUI without any preceding block interaction is never indexed")
    void testVirtualGuiWithoutInteractionNeverIndexed() {
        ChestManager manager = newManager();
        // No recordPendingInteraction call at all - simulates a server/plugin opening a
        // ChestMenu-shaped GUI (e.g. a shop) with no real block interaction behind it.
        boolean began = manager.tryBeginCapture(CTX_A, DIM_OVERWORLD, CHEST_MENU_KINDS, System.currentTimeMillis());
        assertFalse(began);
        assertEquals(0, manager.getIndexedCount(CTX_A));
    }

    // ------------------------------------------------------------------
    // Search
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Search matches by item ID, coordinate text, and empty query returns everything")
    void testSearchBehavior() {
        ChestManager manager = newManager();
        openAndCloseChest(manager, CTX_A, DIM_OVERWORLD, new StoragePosition(120, 64, -32),
                List.of(new ChestSlotEntry(0, "minecraft:iron_ingot", 32)), 1000L);
        openAndCloseChest(manager, CTX_A, DIM_OVERWORLD, new StoragePosition(1, 64, 1),
                List.of(new ChestSlotEntry(0, "minecraft:oak_planks", 64)), 2000L);

        assertEquals(2, manager.search(CTX_A, "").size());
        assertEquals(2, manager.search(CTX_A, null).size());

        assertEquals(1, manager.search(CTX_A, "iron_ingot").size());
        assertEquals(1, manager.search(CTX_A, "iron").size(), "Fallback display name search should match 'Iron Ingot'");
        assertEquals(1, manager.search(CTX_A, "120 64 -32").size());
        assertEquals(0, manager.search(CTX_A, "diamond").size());
    }

    @Test
    @DisplayName("Search never returns results from a different context")
    void testSearchNeverCrossesContexts() {
        ChestManager manager = newManager();
        openAndCloseChest(manager, CTX_A, DIM_OVERWORLD, new StoragePosition(1, 1, 1), List.of(new ChestSlotEntry(0, "minecraft:diamond", 1)), 1000L);
        assertEquals(0, manager.search(CTX_B, "diamond").size());
    }

    // ------------------------------------------------------------------
    // Forget / labels
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Forgetting a container removes only that entry, locally")
    void testForgetRemovesOnlyRequestedEntry() {
        ChestManager manager = newManager();
        openAndCloseChest(manager, CTX_A, DIM_OVERWORLD, new StoragePosition(1, 1, 1), List.of(new ChestSlotEntry(0, "minecraft:dirt", 1)), 1000L);
        openAndCloseChest(manager, CTX_A, DIM_OVERWORLD, new StoragePosition(2, 2, 2), List.of(new ChestSlotEntry(0, "minecraft:sand", 1)), 2000L);

        assertEquals(2, manager.getIndexedCount(CTX_A));
        StoredContainerId toForget = manager.getContainers(CTX_A).stream()
                .filter(c -> c.anchor().equals(new StoragePosition(1, 1, 1))).findFirst().orElseThrow().id();

        assertTrue(manager.forgetContainer(CTX_A, toForget));
        assertEquals(1, manager.getIndexedCount(CTX_A));
        assertFalse(manager.forgetContainer(CTX_A, toForget), "Forgetting an already-removed entry should report false");
    }

    // ------------------------------------------------------------------
    // Final capture & data-safety hardening
    // ------------------------------------------------------------------

    @Test
    @DisplayName("The final updateCaptureSlots call before endCapture wins over earlier calls in the same session")
    void testFinalSlotUpdateWins() {
        ChestManager manager = newManager();
        StoragePosition pos = new StoragePosition(9, 64, 9);

        manager.recordPendingInteraction(CTX_A, DIM_OVERWORLD, StorageKind.CHEST, pos, null, StorageShape.SINGLE, 1000L);
        manager.tryBeginCapture(CTX_A, DIM_OVERWORLD, CHEST_MENU_KINDS, 1050L);
        // Immediate open-time snapshot.
        manager.updateCaptureSlots(List.of(new ChestSlotEntry(0, "minecraft:coal", 10)), 1060L);
        // A tick update.
        manager.updateCaptureSlots(List.of(new ChestSlotEntry(0, "minecraft:coal", 5), new ChestSlotEntry(1, "minecraft:torch", 3)), 1100L);
        // The true final read taken right before endCapture.
        manager.updateCaptureSlots(List.of(new ChestSlotEntry(0, "minecraft:diamond", 1)), 1190L);
        manager.endCapture(1200L);

        StoredContainer container = manager.getContainers(CTX_A).get(0);
        assertEquals(1, container.slots().size());
        assertEquals("minecraft:diamond", container.slots().get(0).itemId());
        assertEquals(1190L, container.lastOpenedAtMs());
    }

    @Test
    @DisplayName("A capture session that never received a single legitimate snapshot creates no record")
    void testNoUpdateCaptureNeverCreatesEmptyRecord() {
        ChestManager manager = newManager();
        StoragePosition pos = new StoragePosition(11, 64, 11);

        manager.recordPendingInteraction(CTX_A, DIM_OVERWORLD, StorageKind.CHEST, pos, null, StorageShape.SINGLE, 1000L);
        assertTrue(manager.tryBeginCapture(CTX_A, DIM_OVERWORLD, CHEST_MENU_KINDS, 1050L));
        // No updateCaptureSlots call at all.
        manager.endCapture(1100L);

        assertEquals(0, manager.getIndexedCount(CTX_A), "No snapshot was ever taken - nothing should be indexed");
    }

    @Test
    @DisplayName("A capture session with no snapshot never wipes an existing non-empty record")
    void testNoUpdateCaptureNeverWipesExistingRecord() {
        ChestManager manager = newManager();
        StoragePosition pos = new StoragePosition(12, 64, 12);

        openAndCloseChest(manager, CTX_A, DIM_OVERWORLD, pos, List.of(new ChestSlotEntry(0, "minecraft:gold_ingot", 7)), 1000L);
        assertEquals(1, manager.getIndexedCount(CTX_A));

        // A second session correlates and begins, but for some reason never captures a snapshot.
        manager.recordPendingInteraction(CTX_A, DIM_OVERWORLD, StorageKind.CHEST, pos, null, StorageShape.SINGLE, 5000L);
        assertTrue(manager.tryBeginCapture(CTX_A, DIM_OVERWORLD, CHEST_MENU_KINDS, 5050L));
        manager.endCapture(5100L);

        assertEquals(1, manager.getIndexedCount(CTX_A), "The existing record must still exist");
        StoredContainer container = manager.getContainers(CTX_A).get(0);
        assertEquals(1, container.slots().size());
        assertEquals(7, container.slots().get(0).count(), "The existing non-empty snapshot must be untouched");
        assertEquals(1100L, container.lastOpenedAtMs(), "lastOpenedAtMs must not advance when no legitimate snapshot was captured (unchanged from the first session's real snapshot at atMs+100)");
    }

    @Test
    @DisplayName("Reopening the same chest with identical contents still updates lastOpenedAtMs and stays one record")
    void testUnchangedReopenUpdatesLastOpenedAtMs() {
        ChestManager manager = newManager();
        StoragePosition pos = new StoragePosition(13, 64, 13);
        List<ChestSlotEntry> sameSlots = List.of(new ChestSlotEntry(0, "minecraft:emerald", 2));

        openAndCloseChest(manager, CTX_A, DIM_OVERWORLD, pos, sameSlots, 1000L);
        StoredContainer first = manager.getContainers(CTX_A).get(0);
        assertEquals(1, manager.getIndexedCount(CTX_A));

        openAndCloseChest(manager, CTX_A, DIM_OVERWORLD, pos, List.of(new ChestSlotEntry(0, "minecraft:emerald", 2)), 5000L);

        assertEquals(1, manager.getIndexedCount(CTX_A), "Reopening the same chest must never duplicate the record");
        StoredContainer second = manager.getContainers(CTX_A).get(0);
        assertEquals(second.id().asStableKey(), first.id().asStableKey());
        assertEquals(1, second.slots().size());
        assertEquals(2, second.slots().get(0).count());
        assertTrue(second.lastOpenedAtMs() > first.lastOpenedAtMs(), "lastOpenedAtMs must reflect the second, later opening");
    }

    // ------------------------------------------------------------------
    // Schema compatibility & fail-closed status gating
    // ------------------------------------------------------------------

    /** A fake store returning a fixed load outcome, to simulate INCOMPATIBLE/ERROR without touching disk. */
    private static final class FixedResultStore implements ChestIndexStore {
        private final ChestIndexLoadResult result;
        private boolean saveCalled = false;

        FixedResultStore(ChestIndexLoadResult result) {
            this.result = result;
        }

        @Override
        public ChestIndexLoadResult load() {
            return result;
        }

        @Override
        public void save(ChestIndexData data) {
            saveCalled = true;
        }
    }

    @Test
    @DisplayName("An incompatible future schema produces ChestManagerStatus.INCOMPATIBLE, not LOADED")
    void testFutureSchemaProducesIncompatibleStatus() {
        FixedResultStore store = new FixedResultStore(ChestIndexLoadResult.incompatibleSchema());
        ChestManager manager = new ChestManager(store);
        manager.initialize();

        assertEquals(ChestManagerStatus.INCOMPATIBLE, manager.getStatus());
        assertFalse(manager.getStatus().isAvailable());
    }

    @Test
    @DisplayName("Capture and mutation operations are rejected while status is INCOMPATIBLE, and never save")
    void testCaptureAndMutationRejectedWhileIncompatible() {
        FixedResultStore store = new FixedResultStore(ChestIndexLoadResult.incompatibleSchema());
        ChestManager manager = new ChestManager(store);
        manager.initialize();

        StoragePosition pos = new StoragePosition(0, 0, 0);
        manager.recordPendingInteraction(CTX_A, DIM_OVERWORLD, StorageKind.CHEST, pos, null, StorageShape.SINGLE, 1000L);
        assertFalse(manager.tryBeginCapture(CTX_A, DIM_OVERWORLD, CHEST_MENU_KINDS, 1050L),
                "A pending interaction must not even be recorded, let alone begin a capture, while incompatible");

        assertFalse(manager.forgetContainer(CTX_A, new StoredContainerId(CTX_A, DIM_OVERWORLD, pos, StorageKind.CHEST)));
        assertFalse(manager.setLabel(CTX_A, new StoredContainerId(CTX_A, DIM_OVERWORLD, pos, StorageKind.CHEST), "label"));

        assertFalse(store.saveCalled, "No save must ever occur while the manager is not LOADED");
        assertEquals(0, manager.getIndexedCount(CTX_A));
    }

    @Test
    @DisplayName("Capture and mutation operations are rejected while status is ERROR")
    void testCaptureAndMutationRejectedWhileError() {
        FixedResultStore store = new FixedResultStore(ChestIndexLoadResult.error());
        ChestManager manager = new ChestManager(store);
        manager.initialize();

        assertEquals(ChestManagerStatus.ERROR, manager.getStatus());

        StoragePosition pos = new StoragePosition(0, 0, 0);
        manager.recordPendingInteraction(CTX_A, DIM_OVERWORLD, StorageKind.CHEST, pos, null, StorageShape.SINGLE, 1000L);
        assertFalse(manager.tryBeginCapture(CTX_A, DIM_OVERWORLD, CHEST_MENU_KINDS, 1050L));
        assertFalse(store.saveCalled);
    }

    @Test
    @DisplayName("A future-schema chest-index.json on disk is never overwritten, and Chest Manager reports INCOMPATIBLE end-to-end")
    void testFutureSchemaFileNotOverwrittenEndToEnd() throws IOException {
        Path storePath = tempDir.resolve("chest-index.json");
        String futureContent = "{ \"schemaVersion\": 999, \"contexts\": {} }";
        Files.writeString(storePath, futureContent);
        byte[] originalBytes = Files.readAllBytes(storePath);

        ChestManager manager = new ChestManager(new JsonChestIndexStore(storePath));
        manager.initialize();
        assertEquals(ChestManagerStatus.INCOMPATIBLE, manager.getStatus());

        // Attempting to use the manager must not touch the file either.
        StoragePosition pos = new StoragePosition(1, 2, 3);
        manager.recordPendingInteraction(CTX_A, DIM_OVERWORLD, StorageKind.CHEST, pos, null, StorageShape.SINGLE, 1000L);
        manager.tryBeginCapture(CTX_A, DIM_OVERWORLD, CHEST_MENU_KINDS, 1050L);
        manager.forgetContainer(CTX_A, new StoredContainerId(CTX_A, DIM_OVERWORLD, pos, StorageKind.CHEST));

        assertArrayEquals(originalBytes, Files.readAllBytes(storePath), "The future-schema file on disk must remain byte-identical");
    }

    // ------------------------------------------------------------------
    // Storage shape (single / double / unknown / not applicable)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("A confirmed SINGLE chest never shows ambiguous double-chest text")
    void testSingleChestShape() {
        ChestManager manager = newManager();
        StoragePosition pos = new StoragePosition(20, 64, 20);
        manager.recordPendingInteraction(CTX_A, DIM_OVERWORLD, StorageKind.CHEST, pos, null, StorageShape.SINGLE, 1000L);
        manager.tryBeginCapture(CTX_A, DIM_OVERWORLD, CHEST_MENU_KINDS, 1050L);
        manager.updateCaptureSlots(List.of(new ChestSlotEntry(0, "minecraft:dirt", 1)), 1100L);
        manager.endCapture(1200L);

        StoredContainer container = manager.getContainers(CTX_A).get(0);
        assertEquals(StorageShape.SINGLE, container.shape());
        assertNull(container.partner());
        assertFalse(container.isDoubleWide());
    }

    @Test
    @DisplayName("A resolved double chest half records DOUBLE shape and the partner position")
    void testDoubleChestShape() {
        ChestManager manager = newManager();
        StoragePosition left = new StoragePosition(21, 64, 21);
        StoragePosition right = new StoragePosition(22, 64, 21);
        manager.recordPendingInteraction(CTX_A, DIM_OVERWORLD, StorageKind.CHEST, left, right, StorageShape.DOUBLE, 1000L);
        manager.tryBeginCapture(CTX_A, DIM_OVERWORLD, CHEST_MENU_KINDS, 1050L);
        manager.updateCaptureSlots(List.of(new ChestSlotEntry(0, "minecraft:dirt", 1)), 1100L);
        manager.endCapture(1200L);

        StoredContainer container = manager.getContainers(CTX_A).get(0);
        assertEquals(StorageShape.DOUBLE, container.shape());
        assertEquals(right, container.partner());
        assertTrue(container.isDoubleWide());
    }

    @Test
    @DisplayName("A chest-family block whose double/single state cannot be resolved records UNKNOWN, not a guess")
    void testUnresolvedChestShapeIsUnknownNotSingle() {
        ChestManager manager = newManager();
        StoragePosition pos = new StoragePosition(23, 64, 23);
        // Simulates a LEFT/RIGHT chest half whose partner position could not be determined -
        // this must NEVER collapse into SINGLE (that would be the exact reported bug).
        manager.recordPendingInteraction(CTX_A, DIM_OVERWORLD, StorageKind.CHEST, pos, null, StorageShape.UNKNOWN, 1000L);
        manager.tryBeginCapture(CTX_A, DIM_OVERWORLD, CHEST_MENU_KINDS, 1050L);
        manager.updateCaptureSlots(List.of(new ChestSlotEntry(0, "minecraft:dirt", 1)), 1100L);
        manager.endCapture(1200L);

        StoredContainer container = manager.getContainers(CTX_A).get(0);
        assertEquals(StorageShape.UNKNOWN, container.shape());
        assertNull(container.partner());
        assertNotEquals(StorageShape.SINGLE, container.shape(), "UNKNOWN must never collapse into SINGLE");
    }

    @Test
    @DisplayName("Non-chest storage kinds always record NOT_APPLICABLE shape")
    void testNonChestKindsAreShapeNotApplicable() {
        ChestManager manager = newManager();
        StoragePosition pos = new StoragePosition(24, 64, 24);
        manager.recordPendingInteraction(CTX_A, DIM_OVERWORLD, StorageKind.BARREL, pos, null, StorageShape.NOT_APPLICABLE, 1000L);
        manager.tryBeginCapture(CTX_A, DIM_OVERWORLD, CHEST_MENU_KINDS, 1050L);
        manager.updateCaptureSlots(List.of(new ChestSlotEntry(0, "minecraft:dirt", 1)), 1100L);
        manager.endCapture(1200L);

        StoredContainer container = manager.getContainers(CTX_A).get(0);
        assertEquals(StorageShape.NOT_APPLICABLE, container.shape());
    }

    // ------------------------------------------------------------------
    // Local labels
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Setting and clearing a local label works and is sanitized")
    void testSetAndClearLabel() {
        ChestManager manager = newManager();
        StoragePosition pos = new StoragePosition(30, 64, 30);
        openAndCloseChest(manager, CTX_A, DIM_OVERWORLD, pos, List.of(new ChestSlotEntry(0, "minecraft:dirt", 1)), 1000L);
        StoredContainerId id = manager.getContainers(CTX_A).get(0).id();

        assertTrue(manager.setLabel(CTX_A, id, "  Gruvbas  "));
        assertEquals("Gruvbas", manager.getContainer(CTX_A, id).orElseThrow().label(), "Label must be trimmed");

        assertTrue(manager.setLabel(CTX_A, id, ""));
        assertNull(manager.getContainer(CTX_A, id).orElseThrow().label(), "An empty label save must clear the label");

        assertTrue(manager.setLabel(CTX_A, id, "   "));
        assertNull(manager.getContainer(CTX_A, id).orElseThrow().label(), "A whitespace-only label must also clear the label");
    }

    @Test
    @DisplayName("A local label survives a full reload from disk")
    void testLabelSurvivesReload() {
        Path storePath = tempDir.resolve("chest-index.json");
        StoragePosition pos = new StoragePosition(31, 64, 31);

        ChestManager first = new ChestManager(new JsonChestIndexStore(storePath));
        first.initialize();
        openAndCloseChest(first, CTX_A, DIM_OVERWORLD, pos, List.of(new ChestSlotEntry(0, "minecraft:iron_ingot", 5)), 1000L);
        StoredContainerId id = first.getContainers(CTX_A).get(0).id();
        assertTrue(first.setLabel(CTX_A, id, "Gruvbas"));

        ChestManager second = new ChestManager(new JsonChestIndexStore(storePath));
        second.initialize();
        assertEquals("Gruvbas", second.getContainer(CTX_A, id).orElseThrow().label());
    }

    // ------------------------------------------------------------------
    // Search / filter / sort
    // ------------------------------------------------------------------

    private ChestManager buildSearchFixture() {
        ChestManager manager = newManager();
        openAndCloseChest(manager, CTX_A, DIM_OVERWORLD, new StoragePosition(1, 64, 1),
                List.of(new ChestSlotEntry(0, "minecraft:iron_ingot", 32)), 3000L);
        manager.setLabel(CTX_A, manager.getContainers(CTX_A).stream()
                .filter(c -> c.anchor().equals(new StoragePosition(1, 64, 1))).findFirst().orElseThrow().id(), "Gruvbas");

        StoragePosition barrelPos = new StoragePosition(2, 64, 2);
        manager.recordPendingInteraction(CTX_A, DIM_OVERWORLD, StorageKind.BARREL, barrelPos, null, StorageShape.NOT_APPLICABLE, 2000L);
        manager.tryBeginCapture(CTX_A, DIM_OVERWORLD, CHEST_MENU_KINDS, 2050L);
        manager.updateCaptureSlots(List.of(new ChestSlotEntry(0, "minecraft:oak_planks", 64)), 2100L);
        manager.endCapture(2200L);

        openAndCloseChest(manager, CTX_A, DIM_OVERWORLD, new StoragePosition(3, 64, 3),
                List.of(new ChestSlotEntry(0, "minecraft:stick", 8)), 1000L);
        return manager;
    }

    @Test
    @DisplayName("An empty query with default filter/sort returns everything, RECENT-first")
    void testSearchNoQueryReturnsAllRecentFirst() {
        ChestManager manager = buildSearchFixture();
        List<StoredContainer> result = manager.search(CTX_A, "", ChestTypeFilter.ALL, ChestSortMode.RECENT);
        assertEquals(3, result.size());
        assertEquals(new StoragePosition(1, 64, 1), result.get(0).anchor(), "Most recently opened (3000L) must be first");
    }

    @Test
    @DisplayName("A query matching nothing returns an empty list, not an error")
    void testSearchNoResult() {
        ChestManager manager = buildSearchFixture();
        assertTrue(manager.search(CTX_A, "totally_unmatched_query_xyz").isEmpty());
    }

    @Test
    @DisplayName("Search matches a local label")
    void testSearchMatchesLabel() {
        ChestManager manager = buildSearchFixture();
        List<StoredContainer> result = manager.search(CTX_A, "gruvbas");
        assertEquals(1, result.size());
        assertEquals("Gruvbas", result.get(0).label());
    }

    @Test
    @DisplayName("A type filter restricts results to matching storage kinds only")
    void testTypeFilterRestrictsResults() {
        ChestManager manager = buildSearchFixture();
        List<StoredContainer> chestsOnly = manager.search(CTX_A, "", ChestTypeFilter.CHEST, ChestSortMode.RECENT);
        assertEquals(2, chestsOnly.size());
        assertTrue(chestsOnly.stream().allMatch(c -> c.kind() == StorageKind.CHEST));

        List<StoredContainer> barrelsOnly = manager.search(CTX_A, "", ChestTypeFilter.BARREL, ChestSortMode.RECENT);
        assertEquals(1, barrelsOnly.size());
        assertEquals(StorageKind.BARREL, barrelsOnly.get(0).kind());
    }

    @Test
    @DisplayName("A type filter combined with a search query applies both")
    void testTypeFilterCombinedWithSearch() {
        ChestManager manager = buildSearchFixture();
        // "Gruvbas" is a CHEST; filtering to BARREL while searching for it must yield nothing.
        List<StoredContainer> result = manager.search(CTX_A, "gruvbas", ChestTypeFilter.BARREL, ChestSortMode.RECENT);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("NAME sort orders by label-or-type-name, case-insensitive")
    void testNameSort() {
        ChestManager manager = buildSearchFixture();
        List<StoredContainer> result = manager.search(CTX_A, "", ChestTypeFilter.ALL, ChestSortMode.NAME);
        List<String> names = result.stream().map(c -> c.label() != null ? c.label() : c.kind().getDisplayName()).toList();
        List<String> sortedCopy = new java.util.ArrayList<>(names);
        sortedCopy.sort(String.CASE_INSENSITIVE_ORDER);
        assertEquals(sortedCopy, names, "Results must already be in case-insensitive name order");
    }

    @Test
    @DisplayName("TYPE sort groups by storage kind")
    void testTypeSortGroupsByKind() {
        ChestManager manager = buildSearchFixture();
        List<StoredContainer> result = manager.search(CTX_A, "", ChestTypeFilter.ALL, ChestSortMode.TYPE);
        for (int i = 1; i < result.size(); i++) {
            assertTrue(result.get(i - 1).kind().name().compareTo(result.get(i).kind().name()) <= 0, "TYPE sort must keep kinds grouped in order");
        }
    }

    // ------------------------------------------------------------------
    // Capture cleanup / pending-interaction hygiene
    // ------------------------------------------------------------------

    @Test
    @DisplayName("clearTransientCaptureState discards a pending interaction without indexing anything")
    void testClearTransientCaptureStateDiscardsPendingInteraction() {
        ChestManager manager = newManager();
        manager.recordPendingInteraction(CTX_A, DIM_OVERWORLD, StorageKind.CHEST, new StoragePosition(0, 0, 0), null, StorageShape.SINGLE, 1000L);
        manager.clearTransientCaptureState();

        assertFalse(manager.tryBeginCapture(CTX_A, DIM_OVERWORLD, CHEST_MENU_KINDS, 1050L), "A cleared pending interaction must not correlate");
        assertEquals(0, manager.getIndexedCount(CTX_A));
    }

    @Test
    @DisplayName("clearTransientCaptureState during an active capture never persists a guessed final snapshot")
    void testClearTransientCaptureStateDuringActiveCaptureNeverPersists() {
        ChestManager manager = newManager();
        StoragePosition pos = new StoragePosition(0, 0, 0);
        manager.recordPendingInteraction(CTX_A, DIM_OVERWORLD, StorageKind.CHEST, pos, null, StorageShape.SINGLE, 1000L);
        assertTrue(manager.tryBeginCapture(CTX_A, DIM_OVERWORLD, CHEST_MENU_KINDS, 1050L));
        manager.updateCaptureSlots(List.of(new ChestSlotEntry(0, "minecraft:diamond", 3)), 1100L);

        // Simulates leaving the world/server mid-capture (disconnect) - no final snapshot read.
        manager.clearTransientCaptureState();
        assertFalse(manager.isCaptureActive());

        // A subsequent unrelated endCapture call (defensively, in case one still fired) must be a no-op.
        manager.endCapture(1200L);
        assertEquals(0, manager.getIndexedCount(CTX_A), "No guessed/fake snapshot must ever be persisted");
    }

    // ------------------------------------------------------------------
    // Reopen / update semantics (explicit end-to-end regression)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Open, move items, close immediately: exactly one record, final contents match state at close, senast öppnad updates")
    void testOpenMoveItemsCloseImmediatelyEndToEnd() {
        ChestManager manager = newManager();
        StoragePosition pos = new StoragePosition(40, 64, 40);

        manager.recordPendingInteraction(CTX_A, DIM_OVERWORLD, StorageKind.CHEST, pos, null, StorageShape.SINGLE, 1000L);
        assertTrue(manager.tryBeginCapture(CTX_A, DIM_OVERWORLD, CHEST_MENU_KINDS, 1050L));
        // Immediate open-time snapshot (controller reads right away).
        manager.updateCaptureSlots(List.of(new ChestSlotEntry(0, "minecraft:iron_ingot", 10)), 1060L);
        // Player moves items around.
        manager.updateCaptureSlots(List.of(new ChestSlotEntry(0, "minecraft:iron_ingot", 4), new ChestSlotEntry(1, "minecraft:gold_ingot", 2)), 1090L);
        // Controller's true final read immediately before endCapture.
        manager.updateCaptureSlots(List.of(new ChestSlotEntry(0, "minecraft:iron_ingot", 4), new ChestSlotEntry(1, "minecraft:gold_ingot", 2)), 1095L);
        manager.endCapture(1100L);

        assertEquals(1, manager.getIndexedCount(CTX_A));
        StoredContainer container = manager.getContainers(CTX_A).get(0);
        assertEquals(2, container.slots().size());
        // The final read (1095L) was identical to the previous tick's read and is therefore a
        // no-op by design - lastOpenedAtMs correctly reflects the last ACTUAL change (1090L).
        assertEquals(1090L, container.lastOpenedAtMs());
    }

    // ------------------------------------------------------------------
    // clearContext (M-Settings "Rensa Kistor-index" support)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("clearContext removes every indexed container for one context but leaves other contexts untouched")
    void testClearContextIsolatedToOneContext() {
        ChestManager manager = newManager();
        openAndCloseChest(manager, CTX_A, DIM_OVERWORLD, new StoragePosition(1, 64, 1), List.of(new ChestSlotEntry(0, "minecraft:iron_ingot", 1)), 1000L);
        openAndCloseChest(manager, CTX_B, DIM_OVERWORLD, new StoragePosition(2, 64, 2), List.of(new ChestSlotEntry(0, "minecraft:gold_ingot", 1)), 1000L);

        assertTrue(manager.clearContext(CTX_A));
        assertEquals(0, manager.getIndexedCount(CTX_A));
        assertEquals(1, manager.getIndexedCount(CTX_B), "Clearing one context must never affect another.");
    }

    @Test
    @DisplayName("clearContext before initialize() is refused")
    void testClearContextRefusedBeforeInitialize() {
        ChestManager manager = new ChestManager(new JsonChestIndexStore(tempDir.resolve("uninitialized.json")));
        assertFalse(manager.clearContext(CTX_A));
    }
}
