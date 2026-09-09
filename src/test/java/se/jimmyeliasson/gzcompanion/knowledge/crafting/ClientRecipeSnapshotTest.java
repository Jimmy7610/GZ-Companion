package se.jimmyeliasson.gzcompanion.knowledge.crafting;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ClientRecipeSnapshotTest {

    private static ClientRecipeSnapshot shaped(int recipeDisplayId, String outputId, String outputName, int outputCount, String... firstSlotIds) {
        List<IngredientOption> slot = List.of();
        if (firstSlotIds.length > 0) {
            slot = List.of(new IngredientOption(firstSlotIds[0], firstSlotIds[0]));
        }
        return new ClientRecipeSnapshot(outputId, outputName, outputCount, RecipeKind.SHAPED, 1, 1, List.of(slot), recipeDisplayId);
    }

    private static ClientRecipeSnapshot shaped(int recipeDisplayId, String outputId, String outputName, String... firstSlotIds) {
        return shaped(recipeDisplayId, outputId, outputName, 1, firstSlotIds);
    }

    @Test
    @DisplayName("outputDisplayName defaults to outputItemId when blank or null")
    void testDisplayNameDefaultsToId() {
        ClientRecipeSnapshot withNull = new ClientRecipeSnapshot("minecraft:stick", null, 1, RecipeKind.SHAPELESS, 0, 0, List.of(), 1);
        assertEquals("minecraft:stick", withNull.outputDisplayName());

        ClientRecipeSnapshot withBlank = new ClientRecipeSnapshot("minecraft:stick", "   ", 1, RecipeKind.SHAPELESS, 0, 0, List.of(), 2);
        assertEquals("minecraft:stick", withBlank.outputDisplayName());
    }

    @Test
    @DisplayName("Two snapshots sharing the same recipeDisplayId and content produce the same stable key")
    void testStableKeyIsContentDerived() {
        ClientRecipeSnapshot a = shaped(101, "minecraft:stick", "Stick", "minecraft:oak_planks");
        ClientRecipeSnapshot b = shaped(101, "minecraft:stick", "Stick", "minecraft:oak_planks");
        assertEquals(a.stableKey(), b.stableKey());
    }

    @Test
    @DisplayName("Recipes with different recipeDisplayIds (different real recipes) produce different stable keys")
    void testDifferentRecipeIdsProduceDifferentKeys() {
        ClientRecipeSnapshot a = shaped(101, "minecraft:stick", "Stick", "minecraft:oak_planks");
        ClientRecipeSnapshot b = shaped(102, "minecraft:stick", "Stick", "minecraft:oak_planks");
        assertNotEquals(a.stableKey(), b.stableKey(), "Different server-assigned recipe ids must never collide, even with identical content");
    }

    @Test
    @DisplayName("Recipes with different outputs produce different stable keys")
    void testDifferentOutputsProduceDifferentKeys() {
        ClientRecipeSnapshot a = shaped(101, "minecraft:stick", "Stick", "minecraft:oak_planks");
        ClientRecipeSnapshot b = shaped(102, "minecraft:torch", "Torch", "minecraft:oak_planks");
        assertNotEquals(a.stableKey(), b.stableKey());
    }

    @Test
    @DisplayName("Recipes with the same output but different ingredients produce different stable keys")
    void testSameOutputDifferentIngredientsProduceDifferentKeys() {
        ClientRecipeSnapshot a = shaped(101, "minecraft:planks", "Planks", "minecraft:oak_log");
        ClientRecipeSnapshot b = shaped(102, "minecraft:planks", "Planks", "minecraft:birch_log");
        assertNotEquals(a.stableKey(), b.stableKey());
    }

    @Test
    @DisplayName("Two recipes with the same output/ingredients but a different output count MUST have different stable keys")
    void testDifferentOutputCountProducesDifferentKey() {
        // Same recipeDisplayId deliberately, to prove outputCount itself is part of the key
        // formula and not merely incidentally different because the ids differ.
        ClientRecipeSnapshot a = shaped(101, "minecraft:stick", "Stick", 1, "minecraft:oak_planks");
        ClientRecipeSnapshot b = shaped(101, "minecraft:stick", "Stick", 4, "minecraft:oak_planks");
        assertNotEquals(a.stableKey(), b.stableKey(), "outputCount must be included in the stable key");
    }

    @Test
    @DisplayName("Ingredient alternative order within a slot does not affect the stable key (canonicalized/sorted)")
    void testIngredientAlternativeOrderDoesNotAffectKey() {
        List<IngredientOption> forward = List.of(new IngredientOption("minecraft:oak_planks", "Oak Planks"), new IngredientOption("minecraft:birch_planks", "Birch Planks"));
        List<IngredientOption> reversed = List.of(new IngredientOption("minecraft:birch_planks", "Birch Planks"), new IngredientOption("minecraft:oak_planks", "Oak Planks"));

        ClientRecipeSnapshot a = new ClientRecipeSnapshot("minecraft:stick", "Stick", 1, RecipeKind.SHAPED, 1, 1, List.of(forward), 101);
        ClientRecipeSnapshot b = new ClientRecipeSnapshot("minecraft:stick", "Stick", 1, RecipeKind.SHAPED, 1, 1, List.of(reversed), 101);

        assertEquals(a.stableKey(), b.stableKey(), "Alternative ordering within one slot must not be semantically meaningful for identity");
    }

    @Test
    @DisplayName("A stable key survives being looked up after the owning list is rebuilt/reordered")
    void testStableKeySurvivesListReordering() {
        ClientRecipeSnapshot recipeA = shaped(101, "minecraft:stick", "Stick", "minecraft:oak_planks");
        ClientRecipeSnapshot recipeB = shaped(102, "minecraft:torch", "Torch", "minecraft:coal");

        List<ClientRecipeSnapshot> originalOrder = List.of(recipeA, recipeB);
        String targetKey = recipeA.stableKey();

        // Simulate a recipe-book refresh returning the same logical recipes in a different order.
        List<ClientRecipeSnapshot> reordered = List.of(recipeB, recipeA);

        ClientRecipeSnapshot foundInOriginal = originalOrder.stream().filter(s -> s.stableKey().equals(targetKey)).findFirst().orElseThrow();
        ClientRecipeSnapshot foundAfterReorder = reordered.stream().filter(s -> s.stableKey().equals(targetKey)).findFirst().orElseThrow();

        assertEquals(foundInOriginal.outputItemId(), foundAfterReorder.outputItemId());
        assertSame(recipeA, foundAfterReorder);
    }
}
