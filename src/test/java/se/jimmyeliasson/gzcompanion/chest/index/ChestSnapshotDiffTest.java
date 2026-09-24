package se.jimmyeliasson.gzcompanion.chest.index;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import se.jimmyeliasson.gzcompanion.chest.index.ChestSnapshotDiff.ItemDelta;
import se.jimmyeliasson.gzcompanion.chest.model.ChestSlotEntry;
import se.jimmyeliasson.gzcompanion.chest.model.StorageKind;
import se.jimmyeliasson.gzcompanion.chest.model.StoragePosition;
import se.jimmyeliasson.gzcompanion.chest.model.StorageShape;
import se.jimmyeliasson.gzcompanion.chest.model.StoredContainer;
import se.jimmyeliasson.gzcompanion.chest.model.StoredContainerId;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChestSnapshotDiffTest {
    static ChestSlotEntry s(int i, String id, int c) {
        return new ChestSlotEntry(i, id, c);
    }

    @Test
    @DisplayName("An item that appeared is a gain")
    void addedItem() {
        assertEquals(List.of(new ItemDelta("minecraft:oak_log", 32)),
                ChestSnapshotDiff.compute(List.of(), List.of(s(0, "minecraft:oak_log", 32))));
    }

    @Test
    @DisplayName("An item that disappeared is a loss of its whole previous count")
    void removedItem() {
        assertEquals(List.of(new ItemDelta("minecraft:diamond", -12)),
                ChestSnapshotDiff.compute(List.of(s(0, "minecraft:diamond", 12)), List.of()));
    }

    @Test
    @DisplayName("Increased and decreased counts, summed across slots, ordered by delta descending")
    void increasedAndDecreased() {
        List<ChestSlotEntry> before = List.of(s(0, "minecraft:iron_ingot", 64), s(1, "minecraft:cobblestone", 64), s(2, "minecraft:cobblestone", 64),
                s(3, "minecraft:diamond", 20));
        List<ChestSlotEntry> after = List.of(s(0, "minecraft:iron_ingot", 64), s(5, "minecraft:iron_ingot", 64), s(3, "minecraft:diamond", 8),
                s(9, "minecraft:oak_log", 32));
        assertEquals(List.of(
                new ItemDelta("minecraft:iron_ingot", 64),
                new ItemDelta("minecraft:oak_log", 32),
                new ItemDelta("minecraft:diamond", -12),
                new ItemDelta("minecraft:cobblestone", -128)), ChestSnapshotDiff.compute(before, after));
    }

    @Test
    @DisplayName("No differences (even if items moved between slots) yields an empty diff")
    void noDifferences() {
        assertTrue(ChestSnapshotDiff.compute(List.of(s(0, "minecraft:stone", 10), s(1, "minecraft:stone", 5)),
                List.of(s(7, "minecraft:stone", 15))).isEmpty());
    }

    @Test
    @DisplayName("A container without a previous snapshot has no 'since previous' diff")
    void noPreviousSnapshot() {
        StoredContainer c = new StoredContainer(new StoredContainerId("c", "minecraft:overworld", new StoragePosition(0, 0, 0), StorageKind.CHEST),
                null, null, StorageShape.SINGLE, 1L, List.of(s(0, "minecraft:stone", 1)));
        assertTrue(ChestSnapshotDiff.sincePrevious(c).isEmpty());
    }
}
