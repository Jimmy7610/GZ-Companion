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

    // ------------------------------------------------------------------
    // Regression: independent code review found ItemHoverTooltips.resolvePreferredGameZoneName
    // inferring a GameZone custom item's identity from baseMinecraftItemId alone - unsafe, since
    // ItemKnowledgeBase.byBaseItemId() is intentionally shallow (several relics may legitimately
    // share one base item; matching the base id never proves a given ItemStack IS that relic).
    // That inference has been removed entirely - ItemTooltipContent never looks anything up itself,
    // it only ever uses whatever preferredName its caller explicitly supplies. These tests pin that
    // contract down using the exact minecraft:iron_pickaxe / "Miner's Companion" example from the
    // report, at the one layer that's actually unit-testable without a live Minecraft/knowledge-base
    // environment (ItemHoverTooltips itself needs a real ItemStack and CompanionSession, so the
    // "no implicit lookup" guarantee is structural there - it no longer has a knowledge-base
    // dependency at all - and is exercised concretely here for the name-assembly logic it delegates to).
    // ------------------------------------------------------------------

    @Test
    @DisplayName("A generic item sharing its base id with SEVERAL verified relics still shows the plain vanilla name when no identity is supplied")
    void genericItemWithMultipleVerifiedRelicsSharingBaseId_staysVanilla() {
        // Simulates minecraft:iron_pickaxe, which several different VERIFIED relics might use as
        // their carrier item elsewhere in the knowledge base - irrelevant here, since this call
        // site never looked any of them up and passes no preferred name.
        List<String> lines = ItemTooltipContent.buildLines("Iron Pickaxe", null, 0, "minecraft:iron_pickaxe", false, 1);
        assertEquals(List.of("Iron Pickaxe"), lines);
        assertNotEquals("Miner's Companion", lines.get(0));
    }

    @Test
    @DisplayName("A generic item sharing its base id with EXACTLY ONE verified relic still shows the plain vanilla name when no identity is supplied")
    void genericItemWithExactlyOneVerifiedRelicSharingBaseId_staysVanilla() {
        // Even a single VERIFIED relic sharing this base item does not prove THIS particular
        // ItemStack is that relic - cardinality of matches must never substitute for real identity.
        List<String> lines = ItemTooltipContent.buildLines("Iron Pickaxe", null, 0, "minecraft:iron_pickaxe", false, 1);
        assertEquals("Iron Pickaxe", lines.get(0));
    }

    @Test
    @DisplayName("Real Swedish example: a plain iron pickaxe reads 'Järnhacka', never 'Miner's Companion', unless identity is explicitly supplied")
    void plainIronPickaxe_neverBecomesMinersCompanionWithoutExplicitIdentity() {
        assertEquals(List.of("Järnhacka"),
                ItemTooltipContent.buildLines("Järnhacka", null, 0, "minecraft:iron_pickaxe", false, 1));

        // The exact same base item, but THIS call site genuinely knows (from trusted context, e.g.
        // rendering one specific CustomItemKnowledge entry it already holds) that this icon really
        // is that relic - only then may the preferred name be supplied.
        assertEquals(List.of("Miner's Companion"),
                ItemTooltipContent.buildLines("Järnhacka", "Miner's Companion", 0, "minecraft:iron_pickaxe", false, 1));
    }
}
