package se.jimmyeliasson.gzcompanion.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure tooltip-line assembly logic - no live Minecraft Font/ItemStack needed. Production always
 * supplies {@code vanillaDisplayName} from one real Minecraft API call
 * ({@code ItemStack.getHoverName().getString()}, see {@link ItemHoverTooltips}); this class itself
 * contains no item-id-keyed branching whatsoever, so whatever real name is supplied simply flows
 * through unchanged - there is no hardcoded item-name table to test around.
 */
class ItemTooltipContentTest {

    @Test
    @DisplayName("A vanilla item with no preferred name shows exactly its supplied display name, whatever it is")
    void vanillaItem_resolvesSuppliedDisplayName() {
        assertEquals(List.of("Iron Ingot"),
                ItemTooltipContent.buildLines("Iron Ingot", null, 0, "minecraft:iron_ingot", false, 1));
        assertEquals(List.of("Diamond"),
                ItemTooltipContent.buildLines("Diamond", null, 0, "minecraft:diamond", false, 1),
                "A different supplied name must produce a different result - proves there is no hardcoded per-item table baked into this logic.");
    }

    @Test
    @DisplayName("A verified GameZone preferred name wins over the vanilla carrier item's own name")
    void preferredGameZoneName_winsOverVanillaName() {
        List<String> lines = ItemTooltipContent.buildLines("Diamond Pickaxe", "Miner's Companion", 0, "minecraft:diamond_pickaxe", false, 1);
        assertEquals("Miner's Companion", lines.get(0));
        assertFalse(lines.get(0).contains("Diamond Pickaxe"), "The vanilla carrier item's name must not leak through once a preferred name is supplied.");
    }

    @Test
    @DisplayName("No preferred custom name (null or blank) falls back to the vanilla item name")
    void noPreferredName_fallsBackToVanillaName() {
        assertEquals("Iron Ingot", ItemTooltipContent.buildLines("Iron Ingot", null, 0, "minecraft:iron_ingot", false, 1).get(0));
        assertEquals("Iron Ingot", ItemTooltipContent.buildLines("Iron Ingot", "", 0, "minecraft:iron_ingot", false, 1).get(0));
        assertEquals("Iron Ingot", ItemTooltipContent.buildLines("Iron Ingot", "   ", 0, "minecraft:iron_ingot", false, 1).get(0));
    }

    @Test
    @DisplayName("Technical IDs disabled: the raw Minecraft id never appears in the tooltip")
    void technicalIdsDisabled_idNeverShown() {
        List<String> lines = ItemTooltipContent.buildLines("Iron Ingot", null, 0, "minecraft:iron_ingot", false, 1);
        assertFalse(lines.contains("minecraft:iron_ingot"));
        assertEquals(1, lines.size());
    }

    @Test
    @DisplayName("Technical IDs enabled: the raw Minecraft id is shown as a secondary line")
    void technicalIdsEnabled_idShownAsSecondaryLine() {
        List<String> lines = ItemTooltipContent.buildLines("Iron Ingot", null, 0, "minecraft:iron_ingot", true, 1);
        assertEquals(List.of("Iron Ingot", "minecraft:iron_ingot"), lines);
    }

    @Test
    @DisplayName("A cell with several valid alternatives states that clearly, rather than implying the representative item is the sole option")
    void alternativeSlot_neverFalselyImpliesSoleOption() {
        List<String> withAlternatives = ItemTooltipContent.buildLines("Oak Planks", null, 0, "minecraft:oak_planks", false, 6);
        assertEquals(List.of("Oak Planks", "1 av 6 giltiga alternativ"), withAlternatives);

        List<String> withoutAlternatives = ItemTooltipContent.buildLines("Oak Planks", null, 0, "minecraft:oak_planks", false, 1);
        assertEquals(List.of("Oak Planks"), withoutAlternatives,
                "The only-one-valid-item case must never add a spurious 'alternatives' line.");
    }

    @Test
    @DisplayName("A quantity greater than one is shown as a suffix on the name line; one or fewer is omitted entirely")
    void quantitySuffix_shownOnlyWhenGreaterThanOne() {
        assertEquals("Iron Ingot ×3", ItemTooltipContent.buildLines("Iron Ingot", null, 3, "minecraft:iron_ingot", false, 1).get(0));
        assertEquals("Iron Ingot", ItemTooltipContent.buildLines("Iron Ingot", null, 1, "minecraft:iron_ingot", false, 1).get(0));
        assertEquals("Iron Ingot", ItemTooltipContent.buildLines("Iron Ingot", null, 0, "minecraft:iron_ingot", false, 1).get(0));
    }

    @Test
    @DisplayName("Every optional line composes together without interfering with one another")
    void allOptionalLinesCompose() {
        List<String> lines = ItemTooltipContent.buildLines("Oak Planks", "Ancient Plank", 4, "minecraft:oak_planks", true, 6);
        assertEquals(List.of("Ancient Plank ×4", "minecraft:oak_planks", "1 av 6 giltiga alternativ"), lines);
    }
}
