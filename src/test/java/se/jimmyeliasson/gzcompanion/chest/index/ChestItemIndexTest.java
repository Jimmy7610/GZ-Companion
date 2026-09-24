package se.jimmyeliasson.gzcompanion.chest.index;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import se.jimmyeliasson.gzcompanion.chest.model.ChestItemSortMode;
import se.jimmyeliasson.gzcompanion.chest.model.ChestSlotEntry;
import se.jimmyeliasson.gzcompanion.chest.model.StorageKind;
import se.jimmyeliasson.gzcompanion.chest.model.StorageMetadata;
import se.jimmyeliasson.gzcompanion.chest.model.StoragePosition;
import se.jimmyeliasson.gzcompanion.chest.model.StorageShape;
import se.jimmyeliasson.gzcompanion.chest.model.StoredContainer;
import se.jimmyeliasson.gzcompanion.chest.model.StoredContainerId;
import se.jimmyeliasson.gzcompanion.chest.nav.PlayerPose;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChestItemIndexTest {
    static final String CTX = "p@@server:gz";
    static final String OW = "minecraft:overworld";

    static StoredContainer container(String ctx, String dim, int x, String label, long openedAt, ChestSlotEntry... slots) {
        StoredContainerId id = new StoredContainerId(ctx, dim, new StoragePosition(x, 64, 0), StorageKind.CHEST);
        return new StoredContainer(id, label, null, StorageShape.SINGLE, openedAt, List.of(slots), StorageMetadata.EMPTY, null);
    }

    static StoredContainer container(int x, String label, long openedAt, ChestSlotEntry... slots) {
        return container(CTX, OW, x, label, openedAt, slots);
    }

    static ChestSlotEntry slot(int i, String id, int count) {
        return new ChestSlotEntry(i, id, count);
    }

    @Test
    @DisplayName("One item in one container")
    void oneItemOneContainer() {
        ChestItemIndex index = ChestItemIndex.build(CTX, List.of(container(1, "A", 100, slot(0, "minecraft:iron_ingot", 12))), id -> id);
        assertEquals(1, index.size());
        ChestItemEntry e = index.get("minecraft:iron_ingot").orElseThrow();
        assertEquals(12, e.totalCount());
        assertEquals(1, e.containerCount());
        assertEquals("A", e.locations().get(0).title());
        assertEquals(12, e.locations().get(0).count());
    }

    @Test
    @DisplayName("The same item across multiple containers sums totals and lists each contributor, largest first")
    void sameItemAcrossContainers() {
        ChestItemIndex index = ChestItemIndex.build(CTX, List.of(
                container(1, "Materiallager", 100, slot(0, "minecraft:iron_ingot", 256)),
                container(2, "Gruvbas", 200, slot(0, "minecraft:iron_ingot", 128)),
                container(3, "Gamla huset", 300, slot(0, "minecraft:iron_ingot", 54))), id -> id);
        ChestItemEntry e = index.get("minecraft:iron_ingot").orElseThrow();
        assertEquals(438, e.totalCount());
        assertEquals(3, e.containerCount());
        assertEquals(List.of("Materiallager", "Gruvbas", "Gamla huset"), e.locations().stream().map(ItemLocation::title).toList());
    }

    @Test
    @DisplayName("Duplicate slots within one container are summed into a single contribution")
    void duplicateSlotsWithinContainer() {
        ChestItemIndex index = ChestItemIndex.build(CTX, List.of(container(1, "A", 100,
                slot(0, "minecraft:stone", 64), slot(1, "minecraft:stone", 64), slot(5, "minecraft:stone", 10))), id -> id);
        ChestItemEntry e = index.get("minecraft:stone").orElseThrow();
        assertEquals(138, e.totalCount());
        assertEquals(1, e.containerCount());
    }

    @Test
    @DisplayName("Zero counts, blank ids and air are ignored")
    void invalidCountsIgnored() {
        ChestItemIndex index = ChestItemIndex.build(CTX, List.of(container(1, "A", 100,
                slot(0, "minecraft:stone", 0), slot(1, "minecraft:air", 5), slot(2, "", 3), slot(3, "minecraft:dirt", -4))), id -> id);
        assertTrue(index.isEmpty());
        assertEquals(0, index.totalOf("minecraft:stone"));
    }

    @Test
    @DisplayName("Context isolation: containers from another world/server never count toward this context's totals")
    void contextIsolation() {
        ChestItemIndex index = ChestItemIndex.build(CTX, List.of(
                container(CTX, OW, 1, "Mine", 100, slot(0, "minecraft:diamond", 5)),
                container("p@@singleplayer:other", OW, 1, "Other world", 100, slot(0, "minecraft:diamond", 99))), id -> id);
        assertEquals(5, index.totalOf("minecraft:diamond"));
        assertEquals(1, index.get("minecraft:diamond").orElseThrow().containerCount());
    }

    @Test
    @DisplayName("Default order is largest total first, ties by display name then id; other sort modes are deterministic too")
    void deterministicOrder() {
        ChestItemIndex index = ChestItemIndex.build(CTX, List.of(
                container(1, "A", 100, slot(0, "minecraft:b_item", 10), slot(1, "minecraft:a_item", 10), slot(2, "minecraft:c_item", 50)),
                container(2, "B", 100, slot(0, "minecraft:a_item", 1))), id -> id.replace("minecraft:", ""));
        assertEquals(List.of("minecraft:c_item", "minecraft:a_item", "minecraft:b_item"),
                index.entries().stream().map(ChestItemEntry::itemId).toList());
        assertEquals(List.of("minecraft:a_item", "minecraft:b_item", "minecraft:c_item"),
                index.entries(ChestItemSortMode.NAME).stream().map(ChestItemEntry::itemId).toList());
        assertEquals("minecraft:a_item", index.entries(ChestItemSortMode.SPREAD).get(0).itemId(), "Spread across the most storage first");
    }

    @Test
    @DisplayName("Hitta närmaste only considers same-dimension locations and picks the smallest horizontal distance")
    void nearestSameDimension() {
        ChestItemIndex index = ChestItemIndex.build(CTX, List.of(
                container(CTX, OW, 100, "Far", 100, slot(0, "minecraft:diamond", 17)),
                container(CTX, OW, 10, "Near", 100, slot(0, "minecraft:diamond", 4)),
                container(CTX, "minecraft:the_nether", 1, "Nether", 100, slot(0, "minecraft:diamond", 1))), id -> id);
        List<ItemLocation> locations = index.get("minecraft:diamond").orElseThrow().locations();

        PlayerPose overworld = new PlayerPose(OW, 0, 64, 0, 0f);
        assertEquals("Near", ChestItemIndex.nearestSameDimension(locations, overworld).orElseThrow().title());
        PlayerPose nether = new PlayerPose("minecraft:the_nether", 500, 64, 500, 0f);
        assertEquals("Nether", ChestItemIndex.nearestSameDimension(locations, nether).orElseThrow().title(),
                "Distance across dimensions is never compared");
        PlayerPose end = new PlayerPose("minecraft:the_end", 0, 64, 0, 0f);
        assertTrue(ChestItemIndex.nearestSameDimension(locations, end).isEmpty());
    }
}
