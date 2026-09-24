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

import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class ChestSearchTest {
    static final String CTX = "p@@server:gz";
    static final Function<String, String> NAMES = id -> switch (id) {
        case "minecraft:iron_ingot" -> "Järntacka";
        case "minecraft:diamond" -> "Diamant";
        default -> id;
    };

    static StoredContainer storage(String dim, StoragePosition pos, StorageKind kind, StorageShape shape, String label, StorageMetadata meta,
                                   ChestSlotEntry... slots) {
        return new StoredContainer(new StoredContainerId(CTX, dim, pos, kind), label, null, shape, 1000L, List.of(slots), meta, null);
    }

    private static final StoredContainer LAGER = storage("minecraft:overworld", new StoragePosition(120, 64, -32), StorageKind.CHEST,
            StorageShape.DOUBLE, "Materiallager", new StorageMetadata(true, "Min bas", "Källaren bakom smedjan"),
            new ChestSlotEntry(0, "minecraft:iron_ingot", 256));
    private static final StoredContainer GRUVA = storage("minecraft:the_nether", new StoragePosition(-5, 40, 7), StorageKind.BARREL,
            StorageShape.NOT_APPLICABLE, "Gruvbas", StorageMetadata.EMPTY, new ChestSlotEntry(0, "minecraft:diamond", 4));

    private static boolean m(StoredContainer c, String query) {
        return ChestSearchMatcher.containerMatches(c, ChestSearchMatcher.normalizeQuery(query), NAMES);
    }

    @Test
    @DisplayName("Storage matches translated item name and raw item id")
    void itemNameAndId() {
        assertTrue(m(LAGER, "järn"));
        assertTrue(m(LAGER, "iron_ingot"));
        assertTrue(m(LAGER, "minecraft:iron"));
        assertFalse(m(LAGER, "diamant"));
    }

    @Test
    @DisplayName("Storage matches label, group, location note, type and double-chest text")
    void labelGroupNoteType() {
        assertTrue(m(LAGER, "materiallager"));
        assertTrue(m(LAGER, "MIN BAS"));
        assertTrue(m(LAGER, "smedjan"));
        assertTrue(m(LAGER, "kista"));
        assertTrue(m(LAGER, "dubbel kista"));
        assertTrue(m(GRUVA, "tunna"));
        assertTrue(m(GRUVA, "barrel"));
    }

    @Test
    @DisplayName("Storage matches dimension key and short dimension name")
    void dimension() {
        assertTrue(m(GRUVA, "nether"));
        assertTrue(m(GRUVA, "minecraft:the_nether"));
        assertTrue(m(LAGER, "overworld"));
        assertFalse(m(LAGER, "nether"));
    }

    @Test
    @DisplayName("Storage matches coordinates typed with spaces or commas")
    void coordinates() {
        assertTrue(m(LAGER, "120 64 -32"));
        assertTrue(m(LAGER, "120, 64, -32"));
        assertTrue(m(LAGER, "120,64,-32"));
        assertTrue(m(LAGER, "-32"));
        assertFalse(m(LAGER, "121 64"));
    }

    @Test
    @DisplayName("SAKER search: an item name/id match returns those items")
    void itemSearchByName() {
        ChestItemIndex index = ChestItemIndex.build(CTX, List.of(LAGER, GRUVA), NAMES);
        ChestItemSearchResult r = ChestItemSearch.search(CTX, index, List.of(LAGER, GRUVA), "diam", ChestItemSortMode.COUNT, NAMES);
        assertEquals(ChestItemSearchResult.Scope.ITEM_MATCH, r.scope());
        assertEquals(List.of("minecraft:diamond"), r.entries().stream().map(ChestItemEntry::itemId).toList());

        ChestItemSearchResult byId = ChestItemSearch.search(CTX, index, List.of(LAGER, GRUVA), "iron_ingot", ChestItemSortMode.COUNT, NAMES);
        assertEquals(List.of("minecraft:iron_ingot"), byId.entries().stream().map(ChestItemEntry::itemId).toList());
    }

    @Test
    @DisplayName("SAKER search: a storage metadata match (group/label/note/dimension) lists the things stored there")
    void itemSearchScopedToStorage() {
        ChestItemIndex index = ChestItemIndex.build(CTX, List.of(LAGER, GRUVA), NAMES);
        ChestItemSearchResult byGroup = ChestItemSearch.search(CTX, index, List.of(LAGER, GRUVA), "min bas", ChestItemSortMode.COUNT, NAMES);
        assertEquals(ChestItemSearchResult.Scope.STORAGE_MATCH, byGroup.scope());
        assertEquals(1, byGroup.matchedStorageCount());
        assertEquals(List.of("minecraft:iron_ingot"), byGroup.entries().stream().map(ChestItemEntry::itemId).toList());

        ChestItemSearchResult byDim = ChestItemSearch.search(CTX, index, List.of(LAGER, GRUVA), "nether", ChestItemSortMode.COUNT, NAMES);
        assertEquals(List.of("minecraft:diamond"), byDim.entries().stream().map(ChestItemEntry::itemId).toList());

        ChestItemSearchResult nothing = ChestItemSearch.search(CTX, index, List.of(LAGER, GRUVA), "zzz-nope", ChestItemSortMode.COUNT, NAMES);
        assertTrue(nothing.entries().isEmpty());
    }

    @Test
    @DisplayName("SAKER search: a blank query returns every item")
    void blankQuery() {
        ChestItemIndex index = ChestItemIndex.build(CTX, List.of(LAGER, GRUVA), NAMES);
        ChestItemSearchResult r = ChestItemSearch.search(CTX, index, List.of(LAGER, GRUVA), "   ", ChestItemSortMode.COUNT, NAMES);
        assertEquals(ChestItemSearchResult.Scope.ALL, r.scope());
        assertEquals(2, r.entries().size());
    }
}
