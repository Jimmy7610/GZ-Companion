package se.jimmyeliasson.gzcompanion.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import se.jimmyeliasson.gzcompanion.core.CompanionSession;
import se.jimmyeliasson.gzcompanion.knowledge.common.VerificationStatus;
import se.jimmyeliasson.gzcompanion.knowledge.items.CustomItemKnowledge;
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
 * <p>Prefers a VERIFIED GameZone custom item name over the underlying vanilla item's own name
 * where the knowledge base actually has one (see {@link #resolvePreferredGameZoneName}) - never
 * invents one; an UNVERIFIED/STALE/UNKNOWN/CONFLICT entry never overrides the honest vanilla name.
 * A malformed/unbakeable item never crashes the tab it came from - see {@link #register} (no real
 * stack means no tooltip target, the tab's existing textual fallback stands alone) and
 * {@link #renderHoveredTooltip} (any failure while building/setting the tooltip is swallowed).
 */
public final class ItemHoverTooltips {
    private record Target(ItemStack stack, String itemId, int quantity, int alternativeCount) {}

    private final ItemHoverCollector<Target> collector = new ItemHoverCollector<>();

    public void clear() {
        collector.clear();
    }

    /** Registers a hover target for a single concrete item with no alternatives/quantity concept - most call sites. */
    public void register(UiRect rect, ItemStack stack, String itemId) {
        register(rect, stack, itemId, 0, 1);
    }

    public void register(UiRect rect, ItemStack stack, String itemId, int quantity, int alternativeCount) {
        if (stack == null || stack.isEmpty()) return; // no real ItemStack resolved - the existing textual fallback stands alone, no tooltip target needed
        collector.register(rect, new Target(stack, itemId, quantity, alternativeCount));
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
        String preferredName = resolvePreferredGameZoneName(target.itemId());
        boolean showTechnicalIds = CompanionSession.getInstance().getSettingsManager().getSettings().showTechnicalIds();
        return ItemTooltipContent.buildLines(vanillaName, preferredName, target.quantity(), target.itemId(), showTechnicalIds, target.alternativeCount());
    }

    /**
     * Only ever prefers a name GZ Companion's own knowledge base has actually VERIFIED for this
     * exact base item - never an UNVERIFIED guess, and never invented from the ItemStack itself.
     * Several GameZone items may legitimately share one base item (see
     * {@link se.jimmyeliasson.gzcompanion.knowledge.items.ItemKnowledgeBase#byBaseItemId}) - the
     * first VERIFIED match wins deterministically rather than guessing between them.
     */
    private static String resolvePreferredGameZoneName(String itemId) {
        if (itemId == null) return null;
        CompanionSession session = CompanionSession.getInstance();
        if (!session.getItemKnowledgeStatus().isAvailable()) return null;
        for (CustomItemKnowledge candidate : session.getItemKnowledgeBase().byBaseItemId(itemId)) {
            if (candidate.verification().status() == VerificationStatus.VERIFIED) {
                return candidate.displayName();
            }
        }
        return null;
    }
}
