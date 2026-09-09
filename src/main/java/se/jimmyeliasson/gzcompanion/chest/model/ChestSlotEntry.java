package se.jimmyeliasson.gzcompanion.chest.model;

import java.util.Objects;

/**
 * One top-level visible storage slot observed in a legitimately opened storage menu.
 * Deliberately minimal: no NBT/data components, no nested container inspection.
 */
public record ChestSlotEntry(int slotIndex, String itemId, int count) {
    public ChestSlotEntry {
        itemId = Objects.requireNonNullElse(itemId, "minecraft:air");
        count = Math.max(0, count);
    }
}
