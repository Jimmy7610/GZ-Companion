package se.jimmyeliasson.gzcompanion.ui.tabs;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers the previously-wrong empty-state semantics: whether "there is truly no data" depends on
 * which mode is active, not on all three data sources at once.
 */
class CraftingTabEmptyStateTest {

    @Test
    @DisplayName("RECEPT mode is empty only when the crafting side has no data - the item side is irrelevant")
    void testReceptModeIgnoresItemSide() {
        assertFalse(CraftingTabComponent.hasDataForMode(CraftingTabComponent.Mode.RECEPT, false, true),
                "50 GameZone items existing must not make RECEPT mode look non-empty");
        assertTrue(CraftingTabComponent.hasDataForMode(CraftingTabComponent.Mode.RECEPT, true, false));
        assertTrue(CraftingTabComponent.hasDataForMode(CraftingTabComponent.Mode.RECEPT, true, true));
        assertFalse(CraftingTabComponent.hasDataForMode(CraftingTabComponent.Mode.RECEPT, false, false));
    }

    @Test
    @DisplayName("GAMEZONE_FOREMAL mode is empty only when the item side has no data - the crafting side is irrelevant")
    void testGameZoneItemModeIgnoresCraftingSide() {
        assertFalse(CraftingTabComponent.hasDataForMode(CraftingTabComponent.Mode.GAMEZONE_FOREMAL, true, false),
                "Client/GameZone recipes existing must not make the item mode look non-empty");
        assertTrue(CraftingTabComponent.hasDataForMode(CraftingTabComponent.Mode.GAMEZONE_FOREMAL, false, true));
        assertTrue(CraftingTabComponent.hasDataForMode(CraftingTabComponent.Mode.GAMEZONE_FOREMAL, true, true));
        assertFalse(CraftingTabComponent.hasDataForMode(CraftingTabComponent.Mode.GAMEZONE_FOREMAL, false, false));
    }

    @Test
    @DisplayName("ALLA mode is empty only when BOTH sides have no data")
    void testAllaModeRequiresBothSidesEmpty() {
        assertTrue(CraftingTabComponent.hasDataForMode(CraftingTabComponent.Mode.ALLA, true, false));
        assertTrue(CraftingTabComponent.hasDataForMode(CraftingTabComponent.Mode.ALLA, false, true));
        assertTrue(CraftingTabComponent.hasDataForMode(CraftingTabComponent.Mode.ALLA, true, true));
        assertFalse(CraftingTabComponent.hasDataForMode(CraftingTabComponent.Mode.ALLA, false, false));
    }

    @Test
    @DisplayName("Each mode has a distinct, honest empty-state message that names its real source(s)")
    void testEmptyStateMessagesAreDistinctPerMode() {
        String recept = CraftingTabComponent.emptyStateMessage(CraftingTabComponent.Mode.RECEPT);
        String items = CraftingTabComponent.emptyStateMessage(CraftingTabComponent.Mode.GAMEZONE_FOREMAL);
        String all = CraftingTabComponent.emptyStateMessage(CraftingTabComponent.Mode.ALLA);

        // RECEPT mode is genuinely two independent sources (the player's own receptbok AND any
        // verified GameZone Rule Pack recipes) - the wording must mention both, not imply the
        // Rule Pack is the only possible source.
        assertTrue(recept.toLowerCase().contains("receptbok"), "Must mention the player's own recipe book");
        assertTrue(recept.toLowerCase().contains("gamezone"), "Must also mention the GameZone Rule Pack side");
        assertTrue(items.toLowerCase().contains("föremål"));
        assertTrue(items.toLowerCase().contains("rule pack"), "Items truly are Rule Pack-only, so it's fine (and correct) to say so");
        assertNotEquals(recept, items);
        assertNotEquals(recept, all);
        assertNotEquals(items, all);
    }
}
