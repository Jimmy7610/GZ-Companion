package se.jimmyeliasson.gzcompanion.chest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import se.jimmyeliasson.gzcompanion.chest.model.ChestSlotEntry;
import se.jimmyeliasson.gzcompanion.chest.model.StorageKind;
import se.jimmyeliasson.gzcompanion.chest.model.StoragePosition;
import se.jimmyeliasson.gzcompanion.chest.model.StoredContainer;
import se.jimmyeliasson.gzcompanion.chest.model.StoredContainerId;
import se.jimmyeliasson.gzcompanion.chest.storage.JsonChestIndexStore;

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
        manager.recordPendingInteraction(context, dimension, StorageKind.CHEST, pos, null, false, atMs);
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

        managerLeftClick.recordPendingInteraction(CTX_A, DIM_OVERWORLD, StorageKind.CHEST, left, right, true, 1000L);
        assertTrue(managerLeftClick.tryBeginCapture(CTX_A, DIM_OVERWORLD, CHEST_MENU_KINDS, 1050L));
        managerLeftClick.updateCaptureSlots(List.of(new ChestSlotEntry(0, "minecraft:bread", 1)), 1100L);
        managerLeftClick.endCapture(1200L);

        ChestManager managerRightClick = new ChestManager(new JsonChestIndexStore(tempDir.resolve("chest-index-2.json")));
        managerRightClick.initialize();
        managerRightClick.recordPendingInteraction(CTX_A, DIM_OVERWORLD, StorageKind.CHEST, right, left, true, 1000L);
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

        manager.recordPendingInteraction(CTX_A, DIM_OVERWORLD, StorageKind.CHEST, pos, null, false, 1000L);
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

        manager.recordPendingInteraction(CTX_A, DIM_OVERWORLD, StorageKind.CHEST, pos, null, false, 1000L);
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
        manager.recordPendingInteraction(CTX_A, DIM_OVERWORLD, StorageKind.BARREL, new StoragePosition(0, 0, 0), null, false, 1000L);
        assertTrue(manager.tryBeginCapture(CTX_A, DIM_OVERWORLD, CHEST_MENU_KINDS, 1500L));
    }

    @Test
    @DisplayName("A stale interaction beyond the correlation window is rejected")
    void testStaleInteractionRejected() {
        ChestManager manager = newManager();
        manager.recordPendingInteraction(CTX_A, DIM_OVERWORLD, StorageKind.CHEST, new StoragePosition(0, 0, 0), null, false, 1000L);
        long stale = 1000L + ChestManager.PENDING_INTERACTION_WINDOW_MS + 1;
        assertFalse(manager.tryBeginCapture(CTX_A, DIM_OVERWORLD, CHEST_MENU_KINDS, stale));
    }

    @Test
    @DisplayName("A dimension mismatch between interaction and menu open is rejected")
    void testDimensionMismatchRejected() {
        ChestManager manager = newManager();
        manager.recordPendingInteraction(CTX_A, DIM_OVERWORLD, StorageKind.CHEST, new StoragePosition(0, 0, 0), null, false, 1000L);
        assertFalse(manager.tryBeginCapture(CTX_A, DIM_NETHER, CHEST_MENU_KINDS, 1050L));
    }

    @Test
    @DisplayName("A context mismatch between interaction and menu open is rejected")
    void testContextMismatchRejected() {
        ChestManager manager = newManager();
        manager.recordPendingInteraction(CTX_A, DIM_OVERWORLD, StorageKind.CHEST, new StoragePosition(0, 0, 0), null, false, 1000L);
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
        manager.recordPendingInteraction(CTX_A, DIM_OVERWORLD, StorageKind.HOPPER, new StoragePosition(0, 0, 0), null, false, 1000L);
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
}
