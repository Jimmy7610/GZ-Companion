package se.jimmyeliasson.gzcompanion.ui.tabs;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import se.jimmyeliasson.gzcompanion.knowledge.crafting.CraftingKnowledgeBase;
import se.jimmyeliasson.gzcompanion.knowledge.crafting.GameZoneCraftingEntry;
import se.jimmyeliasson.gzcompanion.knowledge.crafting.IngredientRef;
import se.jimmyeliasson.gzcompanion.knowledge.crafting.RecipeKind;
import se.jimmyeliasson.gzcompanion.knowledge.crafting.RecipeKnowledgeSource;
import se.jimmyeliasson.gzcompanion.knowledge.common.VerificationMetadata;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers the fix for the client recipe book being wrongly treated as dependent on
 * crafting-overrides.json's load status. {@code craftingBase == null} is exactly how
 * {@code CraftingTabComponent.render()} represents "crafting-overrides.json failed to load" (see
 * {@code craftingRulePackAvailable ? session.getCraftingKnowledgeBase() : null}), so these tests
 * exercise that exact real-world shape without needing to mock CompanionSession.
 */
class CraftingTabAvailabilityTest {

    private static GameZoneCraftingEntry sampleEntry() {
        return new GameZoneCraftingEntry("r1", "minecraft:stick", 1, RecipeKind.SHAPELESS, 0, 0, List.of(),
                List.of(IngredientRef.ofItems("minecraft:bamboo")), RecipeKnowledgeSource.GAMEZONE_ADDITION, null,
                VerificationMetadata.UNVERIFIED_DEFAULT);
    }

    @Test
    @DisplayName("Both Rule Pack modules broken (craftingBase null) + client recipes available => crafting-side data still present")
    void testBothModulesBrokenButClientRecipesPresent() {
        boolean hasCraftingSideData = CraftingTabComponent.hasCraftingSideData(null, true);
        assertTrue(hasCraftingSideData, "A broken crafting-overrides.json must never suppress the player's own recipe book");
    }

    @Test
    @DisplayName("RECEPT mode remains usable (non-empty) when only client recipes are available")
    void testReceptModeUsableWithOnlyClientRecipes() {
        boolean hasCraftingSideData = CraftingTabComponent.hasCraftingSideData(null, true);
        assertTrue(CraftingTabComponent.hasDataForMode(CraftingTabComponent.Mode.RECEPT, hasCraftingSideData, false));
    }

    @Test
    @DisplayName("ALLA mode remains usable (non-empty) when only client recipes are available and items are also broken")
    void testAllaModeUsableWithOnlyClientRecipes() {
        boolean hasCraftingSideData = CraftingTabComponent.hasCraftingSideData(null, true);
        assertTrue(CraftingTabComponent.hasDataForMode(CraftingTabComponent.Mode.ALLA, hasCraftingSideData, false));
    }

    @Test
    @DisplayName("Crafting ERROR + items ERROR + no client recipes + RECEPT => a controlled empty state (not a hard crash)")
    void testBothModulesBrokenAndNoClientRecipesIsControlledEmpty() {
        boolean hasCraftingSideData = CraftingTabComponent.hasCraftingSideData(null, false);
        assertFalse(hasCraftingSideData);
        assertFalse(CraftingTabComponent.hasDataForMode(CraftingTabComponent.Mode.RECEPT, hasCraftingSideData, false));
        // Confirms this resolves to the honest empty-state message path, not an exception.
        assertNotNull(CraftingTabComponent.emptyStateMessage(CraftingTabComponent.Mode.RECEPT));
    }

    @Test
    @DisplayName("Item module ERROR + GAMEZONE_FOREMAL mode requires the hard unavailable screen - no alternative source exists")
    void testItemErrorInGameZoneModeRequiresHardUnavailable() {
        assertTrue(CraftingTabComponent.requiresHardUnavailable(CraftingTabComponent.Mode.GAMEZONE_FOREMAL, false));
    }

    @Test
    @DisplayName("RECEPT/ALLA never require the hard unavailable screen, regardless of item module status")
    void testReceptAndAllaNeverRequireHardUnavailable() {
        assertFalse(CraftingTabComponent.requiresHardUnavailable(CraftingTabComponent.Mode.RECEPT, false));
        assertFalse(CraftingTabComponent.requiresHardUnavailable(CraftingTabComponent.Mode.RECEPT, true));
        assertFalse(CraftingTabComponent.requiresHardUnavailable(CraftingTabComponent.Mode.ALLA, false));
        assertFalse(CraftingTabComponent.requiresHardUnavailable(CraftingTabComponent.Mode.ALLA, true));
    }

    @Test
    @DisplayName("GAMEZONE_FOREMAL does not require the hard unavailable screen when items are available")
    void testGameZoneModeFineWhenItemsAvailable() {
        assertFalse(CraftingTabComponent.requiresHardUnavailable(CraftingTabComponent.Mode.GAMEZONE_FOREMAL, true));
    }

    @Test
    @DisplayName("A loaded, non-empty crafting base also counts as crafting-side data, independent of client recipes")
    void testLoadedCraftingBaseCountsAsData() {
        CraftingKnowledgeBase base = new CraftingKnowledgeBase(List.of(sampleEntry()), List.of());
        assertTrue(CraftingTabComponent.hasCraftingSideData(base, false));
    }

    @Test
    @DisplayName("An empty (but successfully loaded) crafting base with no client recipes is genuinely empty")
    void testEmptyLoadedCraftingBaseWithNoClientRecipesIsEmpty() {
        CraftingKnowledgeBase base = CraftingKnowledgeBase.empty();
        assertFalse(CraftingTabComponent.hasCraftingSideData(base, false));
    }
}
