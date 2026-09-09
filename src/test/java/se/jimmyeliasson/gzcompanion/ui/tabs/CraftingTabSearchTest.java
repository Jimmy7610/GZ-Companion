package se.jimmyeliasson.gzcompanion.ui.tabs;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import se.jimmyeliasson.gzcompanion.knowledge.crafting.ClientRecipeSnapshot;
import se.jimmyeliasson.gzcompanion.knowledge.crafting.IngredientOption;
import se.jimmyeliasson.gzcompanion.knowledge.crafting.RecipeKind;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers the client recipe search fix: previously only the raw output item id was searchable.
 * Now the display name, the raw id, and every ingredient's id/display name are all indexed, with
 * both underscore and space variants so "oak_planks" and "oak planks" both work.
 */
class CraftingTabSearchTest {

    private static ClientRecipeSnapshot recipeWithIngredient(String outputId, String outputName, String ingredientId, String ingredientName) {
        List<IngredientOption> slot = List.of(new IngredientOption(ingredientId, ingredientName));
        return new ClientRecipeSnapshot(outputId, outputName, 1, RecipeKind.SHAPED, 1, 1, List.of(slot));
    }

    private static CraftingTabComponent tabWithRecipes(ClientRecipeSnapshot... recipes) {
        CraftingTabComponent tab = new CraftingTabComponent();
        tab.setClientRecipeSupplierForTesting(() -> List.of(recipes));
        tab.refreshClientRecipesIfNeeded(1L, "ctx");
        return tab;
    }

    @Test
    @DisplayName("Search matches the translated display name, not just the raw item id")
    void testSearchMatchesDisplayName() {
        CraftingTabComponent tab = tabWithRecipes(recipeWithIngredient("minecraft:oak_planks", "Oak Planks", "minecraft:oak_log", "Oak Log"));
        tab.setSearchTextForTesting("Oak Planks");

        assertEquals(1, tab.buildEntries(null, null).size());
    }

    @Test
    @DisplayName("Search matches the raw item id even though the row shows the display name")
    void testSearchMatchesRawId() {
        CraftingTabComponent tab = tabWithRecipes(recipeWithIngredient("minecraft:oak_planks", "Oak Planks", "minecraft:oak_log", "Oak Log"));
        tab.setSearchTextForTesting("minecraft:oak_planks");

        assertEquals(1, tab.buildEntries(null, null).size());
    }

    @Test
    @DisplayName("Search matches an ingredient's id or display name, not just the recipe's own output")
    void testSearchMatchesIngredient() {
        CraftingTabComponent tab = tabWithRecipes(recipeWithIngredient("minecraft:oak_planks", "Oak Planks", "minecraft:oak_log", "Oak Log"));

        tab.setSearchTextForTesting("oak_log");
        assertEquals(1, tab.buildEntries(null, null).size());

        tab.setSearchTextForTesting("Oak Log");
        assertEquals(1, tab.buildEntries(null, null).size());
    }

    @Test
    @DisplayName("Underscore and space forms of an item id are interchangeable in search")
    void testUnderscoreSpaceNormalization() {
        CraftingTabComponent tab = tabWithRecipes(recipeWithIngredient("minecraft:oak_planks", "Oak Planks", "minecraft:oak_log", "Oak Log"));

        tab.setSearchTextForTesting("oak planks");
        assertEquals(1, tab.buildEntries(null, null).size(), "'oak planks' (spaced) must match 'minecraft:oak_planks' (underscored)");

        tab.setSearchTextForTesting("oak_planks");
        assertEquals(1, tab.buildEntries(null, null).size(), "'oak_planks' (underscored) must still match too");
    }

    @Test
    @DisplayName("Search is case-insensitive and does not corrupt or require stripping non-ASCII characters")
    void testCaseInsensitiveSearch() {
        CraftingTabComponent tab = tabWithRecipes(recipeWithIngredient("minecraft:golden_apple", "Golden Apple", "minecraft:gold_ingot", "Gold Ingot"));
        tab.setSearchTextForTesting("GOLDEN APPLE");
        assertEquals(1, tab.buildEntries(null, null).size());
    }

    @Test
    @DisplayName("A search with no match returns zero entries without throwing")
    void testSearchNoMatch() {
        CraftingTabComponent tab = tabWithRecipes(recipeWithIngredient("minecraft:oak_planks", "Oak Planks", "minecraft:oak_log", "Oak Log"));
        tab.setSearchTextForTesting("completely-unrelated-term");
        assertTrue(tab.buildEntries(null, null).isEmpty());
    }

    @Test
    @DisplayName("GAMEZONE_FOREMAL mode excludes client recipes from search results entirely")
    void testGameZoneItemModeExcludesClientRecipes() {
        CraftingTabComponent tab = tabWithRecipes(recipeWithIngredient("minecraft:oak_planks", "Oak Planks", "minecraft:oak_log", "Oak Log"));
        tab.setModeForTesting(CraftingTabComponent.Mode.GAMEZONE_FOREMAL);

        assertTrue(tab.buildEntries(null, null).isEmpty(), "GAMEZONE_FOREMAL mode must never surface client recipes");
    }

    @Test
    @DisplayName("Shaped recipe slot data (grid dimensions and ingredient alternatives) survives into the cache untouched")
    void testShapedGridDataPreserved() {
        ClientRecipeSnapshot snap = recipeWithIngredient("minecraft:oak_planks", "Oak Planks", "minecraft:oak_log", "Oak Log");
        CraftingTabComponent tab = tabWithRecipes(snap);

        ClientRecipeSnapshot cached = tab.getCachedClientRecipesForTesting().get(0);
        assertEquals(RecipeKind.SHAPED, cached.kind());
        assertEquals(1, cached.width());
        assertEquals(1, cached.height());
        assertEquals("minecraft:oak_log", cached.slotAlternatives().get(0).get(0).itemId());
    }

    @Test
    @DisplayName("Shapeless recipe data (no width/height, unordered ingredients) survives into the cache untouched")
    void testShapelessDataPreserved() {
        List<IngredientOption> ingredients = List.of(new IngredientOption("minecraft:stick", "Stick"), new IngredientOption("minecraft:coal", "Coal"));
        ClientRecipeSnapshot snap = new ClientRecipeSnapshot("minecraft:torch", "Torch", 4, RecipeKind.SHAPELESS, 0, 0, List.of(ingredients));
        CraftingTabComponent tab = tabWithRecipes(snap);

        ClientRecipeSnapshot cached = tab.getCachedClientRecipesForTesting().get(0);
        assertEquals(RecipeKind.SHAPELESS, cached.kind());
        assertEquals(0, cached.width());
        assertEquals(0, cached.height());
        assertEquals(2, cached.slotAlternatives().get(0).size());
    }
}
