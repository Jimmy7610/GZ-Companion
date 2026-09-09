package se.jimmyeliasson.gzcompanion.knowledge.crafting;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import se.jimmyeliasson.gzcompanion.knowledge.common.KnowledgeLoadResult;

import static org.junit.jupiter.api.Assertions.*;

class CraftingKnowledgeLoaderTest {

    @Test
    @DisplayName("Bundled crafting-overrides.json loads successfully with no GameZone recipes, since none are documented on the wiki")
    void testLoadBundled() {
        KnowledgeLoadResult<CraftingKnowledgeBase> result = new CraftingKnowledgeLoader().load();
        assertTrue(result.isUsable());
        assertEquals(0, result.data().size());
        assertTrue(result.data().loadWarnings().isEmpty());
    }

    @Test
    @DisplayName("Missing resource fails closed with ERROR outcome")
    void testMissingResourceFailsClosed() {
        KnowledgeLoadResult<CraftingKnowledgeBase> result = new CraftingKnowledgeLoader("/does/not/exist.json").load();
        assertFalse(result.isUsable());
        assertEquals(KnowledgeLoadResult.Outcome.ERROR, result.outcome());
    }

    @Test
    @DisplayName("A future schemaVersion is rejected as INCOMPATIBLE_SCHEMA")
    void testFutureSchemaRejected() {
        KnowledgeLoadResult<CraftingKnowledgeBase> result = new CraftingKnowledgeLoader("/knowledge-fixtures/crafting-future-schema.json").load();
        assertEquals(KnowledgeLoadResult.Outcome.INCOMPATIBLE_SCHEMA, result.outcome());
    }

    @Test
    @DisplayName("A valid 3x3 shaped recipe with a full grid loads correctly")
    void testValidShaped3x3() {
        KnowledgeLoadResult<CraftingKnowledgeBase> result = new CraftingKnowledgeLoader("/knowledge-fixtures/crafting-shaped-3x3.json").load();
        assertTrue(result.isUsable());
        assertEquals(1, result.data().size());
        GameZoneCraftingEntry entry = result.data().entries().get(0);
        assertEquals(RecipeKind.SHAPED, entry.kind());
        assertEquals(3, entry.width());
        assertEquals(3, entry.height());
        assertEquals(9, entry.grid().size());
    }

    @Test
    @DisplayName("A shaped recipe with a grid array bigger than declared 3x3 is rejected, not truncated")
    void testShapedGridSizeMustMatchDeclaredDimensions() {
        KnowledgeLoadResult<CraftingKnowledgeBase> result = new CraftingKnowledgeLoader("/knowledge-fixtures/crafting-shaped-mismatched-grid.json").load();
        assertTrue(result.isUsable());
        assertEquals(0, result.data().size(), "A recipe whose grid length doesn't match width*height must be skipped entirely");
        assertFalse(result.data().loadWarnings().isEmpty());
    }

    @Test
    @DisplayName("A shaped recipe declaring a grid dimension above the 3x3 crafting-table maximum is rejected")
    void testShapedGridExceedingMaxDimensionRejected() {
        KnowledgeLoadResult<CraftingKnowledgeBase> result = new CraftingKnowledgeLoader("/knowledge-fixtures/crafting-shaped-too-large.json").load();
        assertTrue(result.isUsable());
        assertEquals(0, result.data().size());
        assertFalse(result.data().loadWarnings().isEmpty());
    }

    @Test
    @DisplayName("A shapeless recipe with a non-empty ingredient list loads correctly")
    void testValidShapeless() {
        KnowledgeLoadResult<CraftingKnowledgeBase> result = new CraftingKnowledgeLoader("/knowledge-fixtures/crafting-shapeless.json").load();
        assertTrue(result.isUsable());
        assertEquals(1, result.data().size());
        GameZoneCraftingEntry entry = result.data().entries().get(0);
        assertEquals(RecipeKind.SHAPELESS, entry.kind());
        assertEquals(0, entry.width());
        assertEquals(0, entry.height());
        assertFalse(entry.ingredients().isEmpty());
    }

    @Test
    @DisplayName("A shapeless recipe with zero ingredients is rejected")
    void testShapelessWithNoIngredientsRejected() {
        KnowledgeLoadResult<CraftingKnowledgeBase> result = new CraftingKnowledgeLoader("/knowledge-fixtures/crafting-shapeless-empty.json").load();
        assertTrue(result.isUsable());
        assertEquals(0, result.data().size());
        assertFalse(result.data().loadWarnings().isEmpty());
    }

    @Test
    @DisplayName("An unknown recipe kind or source string is rejected, never defaulted to a guess")
    void testUnknownKindAndSourceRejected() {
        KnowledgeLoadResult<CraftingKnowledgeBase> result = new CraftingKnowledgeLoader("/knowledge-fixtures/crafting-unknown-kind-source.json").load();
        assertTrue(result.isUsable());
        assertEquals(0, result.data().size());
        assertFalse(result.data().loadWarnings().isEmpty());
    }

    @Test
    @DisplayName("An ingredient tag reference (#minecraft:planks) is parsed correctly alongside item-id ingredients")
    void testTagIngredientParsed() {
        KnowledgeLoadResult<CraftingKnowledgeBase> result = new CraftingKnowledgeLoader("/knowledge-fixtures/crafting-shapeless.json").load();
        GameZoneCraftingEntry entry = result.data().entries().get(0);
        boolean hasTag = entry.ingredients().stream().anyMatch(i -> i.tag() != null);
        assertTrue(hasTag, "Fixture must include at least one tag-based ingredient");
    }
}
