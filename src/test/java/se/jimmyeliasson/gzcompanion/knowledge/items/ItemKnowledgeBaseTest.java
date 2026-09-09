package se.jimmyeliasson.gzcompanion.knowledge.items;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import se.jimmyeliasson.gzcompanion.knowledge.common.VerificationMetadata;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ItemKnowledgeBaseTest {

    private static CustomItemKnowledge item(String id, String displayName, String baseItemId, String tier) {
        return new CustomItemKnowledge(id, displayName, baseItemId, "A test description", List.of(), "relic",
                tier, "Andvari", "GZR-TEST", List.of("Efficiency III"), null, null, null, null,
                VerificationMetadata.UNVERIFIED_DEFAULT);
    }

    @Test
    @DisplayName("empty() base has no items and no warnings")
    void testEmpty() {
        ItemKnowledgeBase base = ItemKnowledgeBase.empty();
        assertEquals(0, base.size());
        assertTrue(base.items().isEmpty());
    }

    @Test
    @DisplayName("byBaseItemId returns every matching item, never assuming a single owner")
    void testByBaseItemIdReturnsAllMatches() {
        ItemKnowledgeBase base = new ItemKnowledgeBase(List.of(
                item("r1", "Relic One", "minecraft:iron_pickaxe", "COMMON"),
                item("r2", "Relic Two", "minecraft:iron_pickaxe", "RARE"),
                item("r3", "Relic Three", "minecraft:diamond_sword", "EPIC")
        ), List.of());

        assertEquals(2, base.byBaseItemId("minecraft:iron_pickaxe").size());
        assertEquals(1, base.byBaseItemId("minecraft:diamond_sword").size());
        assertEquals(0, base.byBaseItemId("minecraft:nonexistent").size());
        assertEquals(0, base.byBaseItemId(null).size());
    }

    @Test
    @DisplayName("search matches display name, base item id, tier, and culture")
    void testSearchMatchesMultipleFields() {
        ItemKnowledgeBase base = new ItemKnowledgeBase(List.of(
                item("r1", "The Hammer of Creation", "minecraft:netherite_pickaxe", "MYTHIC"),
                item("r2", "Miner's Companion", "minecraft:iron_pickaxe", "COMMON")
        ), List.of());

        assertEquals(1, base.search("hammer").size());
        assertEquals(1, base.search("MYTHIC").size());
        assertEquals(2, base.search("andvari").size(), "Both fixture items share the Andvari culture");
        assertEquals(2, base.search(null).size());
        assertEquals(0, base.search("nonexistent-term").size());
    }
}
