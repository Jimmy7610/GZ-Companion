package se.jimmyeliasson.gzcompanion.knowledge.crafting;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import se.jimmyeliasson.gzcompanion.knowledge.common.VerificationMetadata;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CraftingKnowledgeBaseTest {

    private static GameZoneCraftingEntry shapeless(String id, String outputItemId, String note, String... ingredientItemIds) {
        List<IngredientRef> ingredients = List.of(IngredientRef.ofItems(ingredientItemIds));
        return new GameZoneCraftingEntry(id, outputItemId, 1, RecipeKind.SHAPELESS, 0, 0, List.of(), ingredients,
                RecipeKnowledgeSource.GAMEZONE_ADDITION, note, VerificationMetadata.UNVERIFIED_DEFAULT);
    }

    @Test
    @DisplayName("empty() base has no entries and no warnings")
    void testEmpty() {
        CraftingKnowledgeBase base = CraftingKnowledgeBase.empty();
        assertEquals(0, base.size());
        assertTrue(base.entries().isEmpty());
        assertTrue(base.loadWarnings().isEmpty());
    }

    @Test
    @DisplayName("search matches the output item id, notes, and ingredient item ids")
    void testSearchMatchesOutputAndIngredients() {
        CraftingKnowledgeBase base = new CraftingKnowledgeBase(List.of(
                shapeless("r1", "minecraft:golden_apple", "note about apple", "minecraft:apple", "minecraft:gold_ingot"),
                shapeless("r2", "minecraft:diamond_block", "note about diamond", "minecraft:diamond")
        ), List.of());

        assertEquals(1, base.search("golden_apple").size());
        assertEquals(1, base.search("gold_ingot").size());
        assertEquals(1, base.search("note about apple").size());
        assertEquals(2, base.search(null).size());
        assertEquals(0, base.search("nonexistent").size());
    }

    @Test
    @DisplayName("GameZoneCraftingEntry never accepts a null source or missing verification silently")
    void testEntryDefaultsUnverifiedWhenVerificationNull() {
        GameZoneCraftingEntry entry = new GameZoneCraftingEntry("r1", "minecraft:stick", 1, RecipeKind.SHAPELESS,
                0, 0, List.of(), List.of(IngredientRef.ofItems("minecraft:bamboo")),
                RecipeKnowledgeSource.GAMEZONE_ADDITION, null, null);
        assertEquals(se.jimmyeliasson.gzcompanion.knowledge.common.VerificationStatus.UNVERIFIED, entry.verification().status());
    }
}
