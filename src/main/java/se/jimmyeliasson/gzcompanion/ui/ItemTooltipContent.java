package se.jimmyeliasson.gzcompanion.ui;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure assembly of an item hover tooltip's text lines - no live Minecraft {@code Font}/
 * {@code ItemStack} needed, so this is unit-testable in isolation. Production always derives
 * {@code vanillaDisplayName} from one real Minecraft API call ({@code ItemStack.getHoverName()},
 * see {@link ItemHoverTooltips}) - never a hardcoded item-name table, so localization always
 * follows Minecraft's own translations.
 */
public final class ItemTooltipContent {
    private ItemTooltipContent() {}

    /**
     * @param vanillaDisplayName the real Minecraft translated/display name for the underlying item.
     * @param preferredName      a VERIFIED GameZone custom name for this exact item, or null/blank if only the vanilla identity is known - callers must never invent one.
     * @param quantity           how many of this item the current context needs; {@code <= 1} omits any quantity suffix (a single crafting-grid cell always holds exactly one item, so most call sites pass 0).
     * @param technicalId        the raw Minecraft id ("minecraft:iron_ingot") - only ever shown when {@code showTechnicalIds} is true.
     * @param showTechnicalIds   the player's own CompanionSettings.showTechnicalIds() value - callers must never hardcode true.
     * @param alternativeCount   how many alternative items would also satisfy this exact slot; {@code <= 1} means this is the only valid item, so no "alternatives" line is added - never falsely implies a representative icon is the sole option when it isn't.
     */
    public static List<String> buildLines(String vanillaDisplayName, String preferredName, int quantity,
                                           String technicalId, boolean showTechnicalIds, int alternativeCount) {
        List<String> lines = new ArrayList<>();

        String name = (preferredName != null && !preferredName.isBlank()) ? preferredName : vanillaDisplayName;
        if (name == null || name.isBlank()) {
            name = "?";
        }
        lines.add(quantity > 1 ? name + " ×" + quantity : name);

        if (showTechnicalIds && technicalId != null && !technicalId.isBlank()) {
            lines.add(technicalId);
        }

        if (alternativeCount > 1) {
            lines.add("1 av " + alternativeCount + " giltiga alternativ");
        }

        return lines;
    }
}
