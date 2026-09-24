package se.jimmyeliasson.gzcompanion.chest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import se.jimmyeliasson.gzcompanion.chest.index.ChestSnapshotDiff;
import se.jimmyeliasson.gzcompanion.chest.model.ChestCaptureEvent;
import se.jimmyeliasson.gzcompanion.chest.model.ChestGroupFilter;
import se.jimmyeliasson.gzcompanion.chest.model.ChestSlotEntry;
import se.jimmyeliasson.gzcompanion.chest.model.ChestSortMode;
import se.jimmyeliasson.gzcompanion.chest.model.ChestTypeFilter;
import se.jimmyeliasson.gzcompanion.chest.model.StorageKind;
import se.jimmyeliasson.gzcompanion.chest.model.StorageMetadata;
import se.jimmyeliasson.gzcompanion.chest.model.StoragePosition;
import se.jimmyeliasson.gzcompanion.chest.model.StorageShape;
import se.jimmyeliasson.gzcompanion.chest.model.StoredContainer;
import se.jimmyeliasson.gzcompanion.chest.model.StoredContainerId;
import se.jimmyeliasson.gzcompanion.chest.storage.JsonChestIndexStore;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Kistor 2.0 additions to ChestManager: local metadata, previous snapshot, capture events, revision. */
class ChestManagerKistor2Test {

    @TempDir
    Path tempDir;

    static final String CTX = "p@@server:play.gamezonemc.se";
    static final String OTHER_CTX = "p@@singleplayer:world1";
    static final String OW = "minecraft:overworld";
    static final Set<StorageKind> CHEST_MENU = EnumSet.of(StorageKind.CHEST, StorageKind.TRAPPED_CHEST, StorageKind.BARREL);

    private ChestManager newManager() {
        ChestManager m = new ChestManager(new JsonChestIndexStore(tempDir.resolve("chest-index.json")));
        m.initialize();
        return m;
    }

    private ChestManager reload() {
        return newManager();
    }

    static Optional<ChestCaptureEvent> open(ChestManager m, String ctx, StoragePosition pos, List<ChestSlotEntry> slots, long at) {
        m.recordPendingInteraction(ctx, OW, StorageKind.CHEST, pos, null, StorageShape.SINGLE, at);
        assertTrue(m.tryBeginCapture(ctx, OW, CHEST_MENU, at + 10));
        m.updateCaptureSlots(slots, at + 20);
        return m.endCapture(at + 30);
    }

    static StoredContainerId id(String ctx, StoragePosition pos) {
        return new StoredContainerId(ctx, OW, pos, StorageKind.CHEST);
    }

    @Test
    @DisplayName("Favorite, group and location note persist across a reload and can be cleared again")
    void metadataPersistsAndClears() {
        ChestManager m = newManager();
        StoragePosition pos = new StoragePosition(1, 64, 1);
        open(m, CTX, pos, List.of(new ChestSlotEntry(0, "minecraft:stone", 10)), 1000L);
        StoredContainerId id = id(CTX, pos);

        assertTrue(m.setFavorite(CTX, id, true));
        assertTrue(m.setGroup(CTX, id, "Min bas"));
        assertTrue(m.setLocationNote(CTX, id, "Källaren bakom smedjan"));

        StoredContainer reloaded = reload().getContainer(CTX, id).orElseThrow();
        assertTrue(reloaded.favorite());
        assertEquals("Min bas", reloaded.group());
        assertEquals("Källaren bakom smedjan", reloaded.locationNote());

        ChestManager m2 = reload();
        assertTrue(m2.setFavorite(CTX, id, false));
        assertTrue(m2.setGroup(CTX, id, "   "));
        assertTrue(m2.setLocationNote(CTX, id, null));
        StoredContainer cleared = reload().getContainer(CTX, id).orElseThrow();
        assertEquals(StorageMetadata.EMPTY, cleared.metadata());
    }

    @Test
    @DisplayName("Text sanitization: control characters collapse to spaces, whitespace is trimmed, lengths are capped")
    void sanitizationRules() {
        ChestManager m = newManager();
        StoragePosition pos = new StoragePosition(2, 64, 2);
        open(m, CTX, pos, List.of(), 1000L);
        StoredContainerId id = id(CTX, pos);

        m.setLocationNote(CTX, id, "  rad ett\nrad\ttvå  " + "x".repeat(200));
        String note = m.getContainer(CTX, id).orElseThrow().locationNote();
        assertTrue(note.startsWith("rad ett rad två"));
        assertTrue(note.length() <= StorageMetadata.MAX_NOTE_LENGTH);
        assertFalse(note.contains("\n"));

        m.setGroup(CTX, id, "g".repeat(100));
        assertEquals(StorageMetadata.MAX_GROUP_LENGTH, m.getContainer(CTX, id).orElseThrow().group().length());

        m.setLabel(CTX, id, "L".repeat(100));
        assertEquals(ChestManager.MAX_LABEL_LENGTH, m.getContainer(CTX, id).orElseThrow().label().length());
    }

    @Test
    @DisplayName("Assigning an existing group ignoring case reuses its spelling; groups are listed per context, sorted")
    void groupsReuseExistingSpelling() {
        ChestManager m = newManager();
        StoragePosition a = new StoragePosition(1, 1, 1);
        StoragePosition b = new StoragePosition(2, 1, 1);
        StoragePosition c = new StoragePosition(3, 1, 1);
        open(m, CTX, a, List.of(), 1000L);
        open(m, CTX, b, List.of(), 2000L);
        open(m, OTHER_CTX, c, List.of(), 3000L);

        m.setGroup(CTX, id(CTX, a), "Min bas");
        m.setGroup(CTX, id(CTX, b), "min BAS");
        m.setGroup(OTHER_CTX, id(OTHER_CTX, c), "Gruvbas");

        assertEquals("Min bas", m.getContainer(CTX, id(CTX, b)).orElseThrow().group());
        assertEquals(List.of("Min bas"), m.getGroups(CTX), "Groups never leak from another context");
        assertEquals(List.of("Gruvbas"), m.getGroups(OTHER_CTX));
    }

    @Test
    @DisplayName("Group filter and group/note text search only match the current context's storage")
    void groupFilterAndSearch() {
        ChestManager m = newManager();
        StoragePosition a = new StoragePosition(1, 1, 1);
        StoragePosition b = new StoragePosition(2, 1, 1);
        open(m, CTX, a, List.of(), 1000L);
        open(m, CTX, b, List.of(), 2000L);
        m.setGroup(CTX, id(CTX, a), "Settlementlager");
        m.setLocationNote(CTX, id(CTX, b), "Bakom smedjan");

        assertEquals(1, m.search(CTX, "", ChestTypeFilter.ALL, ChestSortMode.RECENT, ChestGroupFilter.named("settlementlager")).size());
        assertEquals(1, m.search(CTX, "", ChestTypeFilter.ALL, ChestSortMode.RECENT, ChestGroupFilter.UNGROUPED).size());
        assertEquals(id(CTX, a), m.search(CTX, "settlement").get(0).id());
        assertEquals(id(CTX, b), m.search(CTX, "smedjan").get(0).id());
        assertTrue(m.search(OTHER_CTX, "smedjan").isEmpty());
    }

    @Test
    @DisplayName("Reopening a known storage keeps exactly one previous snapshot, label and metadata; the diff reflects the change")
    void previousSnapshotRollsOverBounded() {
        ChestManager m = newManager();
        StoragePosition pos = new StoragePosition(5, 64, 5);
        StoredContainerId id = id(CTX, pos);

        open(m, CTX, pos, List.of(new ChestSlotEntry(0, "minecraft:iron_ingot", 10)), 1000L);
        assertFalse(m.getContainer(CTX, id).orElseThrow().hasPreviousSnapshot(), "A first opening has no previous snapshot");
        m.setLabel(CTX, id, "Materiallager");
        m.setFavorite(CTX, id, true);

        open(m, CTX, pos, List.of(new ChestSlotEntry(0, "minecraft:iron_ingot", 74), new ChestSlotEntry(1, "minecraft:diamond", 3)), 5000L);
        StoredContainer second = m.getContainer(CTX, id).orElseThrow();
        assertEquals("Materiallager", second.label());
        assertTrue(second.favorite());
        assertEquals(List.of(new ChestSlotEntry(0, "minecraft:iron_ingot", 10)), second.previousSnapshot().slots());
        assertEquals(List.of(new ChestSnapshotDiff.ItemDelta("minecraft:iron_ingot", 64), new ChestSnapshotDiff.ItemDelta("minecraft:diamond", 3)),
                ChestSnapshotDiff.sincePrevious(second));

        open(m, CTX, pos, List.of(new ChestSlotEntry(0, "minecraft:iron_ingot", 1)), 9000L);
        StoredContainer third = reload().getContainer(CTX, id).orElseThrow();
        assertEquals(second.slots(), third.previousSnapshot().slots(), "Only the immediately previous snapshot is retained");
        assertEquals(second.lastOpenedAtMs(), third.previousSnapshot().openedAtMs());
    }

    @Test
    @DisplayName("endCapture emits NEW once for a new storage, UPDATED (with contentsChanged) on reopen, and nothing when it fails closed")
    void captureEvents() {
        ChestManager m = newManager();
        StoragePosition pos = new StoragePosition(7, 64, 7);
        List<ChestSlotEntry> slots = List.of(new ChestSlotEntry(0, "minecraft:oak_log", 32));

        ChestCaptureEvent first = open(m, CTX, pos, slots, 1000L).orElseThrow();
        assertEquals(ChestCaptureEvent.Kind.NEW, first.kind());
        assertTrue(first.contentsChanged());
        assertEquals(1, first.distinctItemCount());

        ChestCaptureEvent same = open(m, CTX, pos, slots, 2000L).orElseThrow();
        assertEquals(ChestCaptureEvent.Kind.UPDATED, same.kind());
        assertFalse(same.contentsChanged());

        ChestCaptureEvent changed = open(m, CTX, pos, List.of(new ChestSlotEntry(0, "minecraft:oak_log", 16)), 3000L).orElseThrow();
        assertTrue(changed.contentsChanged());

        // Fails closed: a capture that never received a snapshot emits nothing and writes nothing.
        m.recordPendingInteraction(CTX, OW, StorageKind.CHEST, new StoragePosition(99, 64, 99), null, StorageShape.SINGLE, 4000L);
        assertTrue(m.tryBeginCapture(CTX, OW, CHEST_MENU, 4010L));
        assertTrue(m.endCapture(4020L).isEmpty());
        assertEquals(1, m.getIndexedCount(CTX));
    }

    @Test
    @DisplayName("Every mutation bumps the revision (deterministic cache invalidation); queries never do")
    void revisionBumpsOnMutationOnly() {
        ChestManager m = newManager();
        StoragePosition pos = new StoragePosition(8, 64, 8);
        long r0 = m.revision();
        open(m, CTX, pos, List.of(), 1000L);
        long r1 = m.revision();
        assertTrue(r1 > r0);

        m.getContainers(CTX);
        m.search(CTX, "x");
        m.getGroups(CTX);
        assertEquals(r1, m.revision(), "Read-only queries must not invalidate caches");

        m.setFavorite(CTX, id(CTX, pos), true);
        long r2 = m.revision();
        assertTrue(r2 > r1);
        m.forgetContainer(CTX, id(CTX, pos));
        assertTrue(m.revision() > r2);
        long r3 = m.revision();
        m.clearContext(CTX);
        assertTrue(m.revision() > r3);
    }

    @Test
    @DisplayName("Clearing the Kistor index for a context removes all its metadata and history; other contexts are untouched")
    void clearContextRemovesMetadata() {
        ChestManager m = newManager();
        StoragePosition pos = new StoragePosition(9, 64, 9);
        open(m, CTX, pos, List.of(new ChestSlotEntry(0, "minecraft:stone", 1)), 1000L);
        open(m, CTX, pos, List.of(new ChestSlotEntry(0, "minecraft:stone", 2)), 2000L);
        open(m, OTHER_CTX, pos, List.of(), 3000L);
        m.setGroup(CTX, id(CTX, pos), "Min bas");

        assertTrue(m.clearContext(CTX));
        ChestManager reloaded = reload();
        assertEquals(0, reloaded.getIndexedCount(CTX));
        assertTrue(reloaded.getGroups(CTX).isEmpty());
        assertEquals(1, reloaded.getIndexedCount(OTHER_CTX));
    }
}
