package se.jimmyeliasson.gzcompanion.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import se.jimmyeliasson.gzcompanion.core.CompanionSession;
import se.jimmyeliasson.gzcompanion.ui.layout.UiRect;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared item-icon hover tooltip infrastructure for every GZ Companion tab that renders a real
 * item icon via {@code GuiGraphicsExtractor.fakeItem} (Crafting, Byggplaner, Settlement,
 * MarketWatch). Each tab owns one instance, clears it at the start of its own render() (mirroring
 * the existing per-tab {@code hitTargets} click-target convention), and registers one target
 * alongside every icon it draws. {@link GZCompanionMainScreen} then resolves and renders exactly
 * one tooltip for the active tab - AFTER that tab's own scissor region has closed - via
 * Minecraft's own deferred tooltip mechanism ({@code setComponentTooltipForNextFrame}), so it is
 * never clipped by a scissored detail/list pane and never drawn behind the rest of the UI.
 *
 * <p><b>Identity contract:</b> the default name is always the real ItemStack's own vanilla
 * localized name ({@code ItemStack.getHoverName()}) - never a hardcoded item-name table. This
 * class deliberately does NOT look up a GameZone custom item name from a base Minecraft item id -
 * {@code ItemKnowledgeBase.byBaseItemId(...)} is intentionally shallow (it matches only
 * {@code baseMinecraftItemId}, never inspects the actual ItemStack's components/NBT), and several
 * GameZone relics may legitimately share one base item. A plain {@code minecraft:iron_pickaxe}
 * icon must never become "Miner's Companion" (or any other relic's name) just because some
 * VERIFIED relic happens to use that same carrier item - that would be a false identity claim,
 * violating this project's "better vanilla/unknown than wrong GameZone data" principle. A caller
 * may pass an explicit {@code preferredVerifiedName} to {@link #register} ONLY when it already
 * knows the exact custom-item identity from trusted context (e.g. it is rendering one specific
 * {@code CustomItemKnowledge} entry it already has in hand) - never derived from
 * {@code baseMinecraftItemId}, item type, or "the only/first VERIFIED match".
 *
 * <p>A malformed/unbakeable item never crashes the tab it came from - see {@link #register} (no
 * real stack means no tooltip target, the tab's existing textual fallback stands alone) and
 * {@link #renderHoveredTooltip} (any failure while building/setting the tooltip is swallowed).
 */
public final class ItemHoverTooltips {
    private record Target(ItemStack stack, String itemId, String preferredVerifiedName, int quantity, int alternativeCount) {}

    private final ItemHoverCollector<Target> collector = new ItemHoverCollector<>();

    public void clear() {
        collector.clear();
    }

    /** Registers a hover target with no known custom identity, no alternatives/quantity concept - most call sites. */
    public void register(UiRect rect, ItemStack stack, String itemId) {
        register(rect, stack, itemId, null, 0, 1);
    }

    /**
     * @param preferredVerifiedName the exact GameZone custom display name for this precise icon,
     *                              if and ONLY if the caller already knows that identity from
     *                              trusted context (e.g. it is rendering a specific
     *                              {@code CustomItemKnowledge} entry it already holds) - null
     *                              whenever the caller only has a plain Minecraft item id. Never
     *                              derive this from {@code itemId} inside this class.
     */
    public void register(UiRect rect, ItemStack stack, String itemId, String preferredVerifiedName, int quantity, int alternativeCount) {
        if (stack == null || stack.isEmpty()) return; // no real ItemStack resolved - the existing textual fallback stands alone, no tooltip target needed
        collector.register(rect, new Target(stack, itemId, preferredVerifiedName, quantity, alternativeCount));
    }

    /**
     * Drops any target that scrolled outside a scissored area after the fact - needed only where a
     * tab draws a whole scrolled section unconditionally and relies on GL scissor alone to clip it
     * visually (see BuildingsTabComponent's identical {@code hitTargets} filtering for why).
     */
    public void removeOutside(UiRect visibleArea) {
        collector.removeIf(t -> ItemHoverCollector.isOutsideVisibleArea(t.rect(), visibleArea));
    }

    public void renderHoveredTooltip(GuiGraphicsExtractor extractor, Font font, int mouseX, int mouseY) {
        collector.hovered(mouseX, mouseY).ifPresent(target -> {
            try {
                List<String> lines = buildLines(target);
                if (lines.isEmpty()) return;
                List<Component> components = new ArrayList<>(lines.size());
                for (String line : lines) {
                    components.add(Component.literal(line));
                }
                extractor.setComponentTooltipForNextFrame(font, components, mouseX, mouseY);
            } catch (Exception ignored) {
                // Tooltip generation/rendering must fail safely for one bad item - never break the tab.
            }
        });
    }

    private static List<String> buildLines(Target target) {
        String vanillaName = target.stack().getHoverName().getString();
        boolean showTechnicalIds = CompanionSession.getInstance().getSettingsManager().getSettings().showTechnicalIds();
        return ItemTooltipContent.buildLines(vanillaName, target.preferredVerifiedName(), target.quantity(), target.itemId(), showTechnicalIds, target.alternativeCount());
    }
}
