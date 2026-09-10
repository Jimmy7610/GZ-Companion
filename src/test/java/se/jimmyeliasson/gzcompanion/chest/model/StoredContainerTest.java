package se.jimmyeliasson.gzcompanion.chest.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Human QA blocker: a legitimately opened chest containing more distinct item types than fit on
 * screen only showed the first five in the detail pane, with no way to reach the rest. The actual
 * root cause was the detail pane's scroll routing (fixed separately in KistorTabComponent), not
 * this model - but this test locks in the invariant the whole fix depends on: the last-known-
 * content model itself must never cap or truncate the aggregated item list, no matter how many
 * distinct item types a single opened container legitimately contained.
 */
class StoredContainerTest {

    private StoredContainer containerWithDistinctItemTypes(int distinctTypes) {
        List<ChestSlotEntry> slots = new ArrayList<>();
        for (int i = 0; i < distinctTypes; i++) {
            slots.add(new ChestSlotEntry(i, "minecraft:item_" + i, 1));
        }
        StoredContainerId id = new StoredContainerId("ctx", "minecraft:overworld", new StoragePosition(0, 64, 0), StorageKind.CHEST);
        return new StoredContainer(id, null, null, StorageShape.SINGLE, System.currentTimeMillis(), slots);
    }

    @Test
    @DisplayName("aggregatedItems() returns every distinct item type, however many there were - no 5-item cap")
    void aggregatedItemsNeverCapsDistinctTypes() {
        int distinctTypes = 12; // comfortably more than the 5 the human QA report saw on screen
        StoredContainer container = containerWithDistinctItemTypes(distinctTypes);

        List<StoredContainer.AggregatedItem> items = container.aggregatedItems();

        assertEquals(distinctTypes, items.size(),
                "The cached model must retain every legitimately observed item type - truncation, if any, may only ever happen in the scrollable UI, never in the data itself.");
    }

    @Test
    @DisplayName("Identical item IDs across multiple slots are merged into one aggregated row, not duplicated")
    void identicalItemsAreMergedNotDuplicated() {
        List<ChestSlotEntry> slots = List.of(
                new ChestSlotEntry(0, "minecraft:cobblestone", 64),
                new ChestSlotEntry(1, "minecraft:cobblestone", 32),
                new ChestSlotEntry(2, "minecraft:coal", 10)
        );
        StoredContainerId id = new StoredContainerId("ctx", "minecraft:overworld", new StoragePosition(0, 64, 0), StorageKind.CHEST);
        StoredContainer container = new StoredContainer(id, null, null, StorageShape.SINGLE, System.currentTimeMillis(), slots);

        List<StoredContainer.AggregatedItem> items = container.aggregatedItems();

        assertEquals(2, items.size());
        assertEquals(96, items.stream().filter(i -> i.itemId().equals("minecraft:cobblestone")).findFirst().orElseThrow().count());
    }
}
