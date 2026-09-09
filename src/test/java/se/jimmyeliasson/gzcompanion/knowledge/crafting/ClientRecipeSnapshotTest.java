package se.jimmyeliasson.gzcompanion.knowledge.crafting;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ClientRecipeSnapshotTest {

    private static ClientRecipeSnapshot shaped(String outputId, String outputName, String... firstSlotIds) {
        List<IngredientOption> slot = List.of();
        if (firstSlotIds.length > 0) {
            slot = List.of(new IngredientOption(firstSlotIds[0], firstSlotIds[0]));
        }
        return new ClientRecipeSnapshot(outputId, outputName, 1, RecipeKind.SHAPED, 1, 1, List.of(slot));
    }

    @Test
    @DisplayName("outputDisplayName defaults to outputItemId when blank or null")
    void testDisplayNameDefaultsToId() {
        ClientRecipeSnapshot withNull = new ClientRecipeSnapshot("minecraft:stick", null, 1, RecipeKind.SHAPELESS, 0, 0, List.of());
        assertEquals("minecraft:stick", withNull.outputDisplayName());

        ClientRecipeSnapshot withBlank = new ClientRecipeSnapshot("minecraft:stick", "   ", 1, RecipeKind.SHAPELESS, 0, 0, List.of());
        assertEquals("minecraft:stick", withBlank.outputDisplayName());
    }

    @Test
    @DisplayName("Two snapshots with identical content produce the same stable key regardless of object identity")
    void testStableKeyIsContentDerived() {
        ClientRecipeSnapshot a = shaped("minecraft:stick", "Stick", "minecraft:oak_planks");
        ClientRecipeSnapshot b = shaped("minecraft:stick", "Stick", "minecraft:oak_planks");
        assertEquals(a.stableKey(), b.stableKey());
    }

    @Test
    @DisplayName("Recipes with different outputs produce different stable keys")
    void testDifferentOutputsProduceDifferentKeys() {
        ClientRecipeSnapshot a = shaped("minecraft:stick", "Stick", "minecraft:oak_planks");
        ClientRecipeSnapshot b = shaped("minecraft:torch", "Torch", "minecraft:oak_planks");
        assertNotEquals(a.stableKey(), b.stableKey());
    }

    @Test
    @DisplayName("Recipes with the same output but different ingredients produce different stable keys")
    void testSameOutputDifferentIngredientsProduceDifferentKeys() {
        ClientRecipeSnapshot a = shaped("minecraft:planks", "Planks", "minecraft:oak_log");
        ClientRecipeSnapshot b = shaped("minecraft:planks", "Planks", "minecraft:birch_log");
        assertNotEquals(a.stableKey(), b.stableKey());
    }

    @Test
    @DisplayName("A stable key survives being looked up after the owning list is rebuilt/reordered")
    void testStableKeySurvivesListReordering() {
        ClientRecipeSnapshot recipeA = shaped("minecraft:stick", "Stick", "minecraft:oak_planks");
        ClientRecipeSnapshot recipeB = shaped("minecraft:torch", "Torch", "minecraft:coal");

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
