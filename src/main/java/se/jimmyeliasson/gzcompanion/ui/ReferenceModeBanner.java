package se.jimmyeliasson.gzcompanion.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import se.jimmyeliasson.gzcompanion.ui.layout.TextUtil;
import se.jimmyeliasson.gzcompanion.ui.layout.UiRect;

/**
 * A small, unobtrusive context line shown on GameZone-specific tabs (Kommandon, the GameZone
 * item/relic area in Crafting, Settlement, Byggplaner, MarketWatch) when the client is NOT
 * currently connected to GameZoneMC. These tabs still work fully offline as verified GameZone
 * reference/planning tools - this banner only clarifies that the current server/world does not
 * itself confirm those facts, never a giant intrusive warning. Never shown for plain Minecraft
 * content (e.g. Crafting's own client-recipe-book entries).
 */
public final class ReferenceModeBanner {
    public static final String TEXT = "Referensläge — du är inte ansluten till GameZoneMC.";
    private static final int HEIGHT = 10;

    private ReferenceModeBanner() {}

    /** Draws a thin floating strip pinned to the bottom of {@code bounds}. Purely informational - no click target. */
    public static void renderAtBottom(GuiGraphicsExtractor extractor, Font font, UiRect bounds) {
        int y = bounds.bottom() - HEIGHT;
        extractor.fill(bounds.x(), y, bounds.right(), bounds.bottom(), 0xCC1E1B0A);
        TextUtil.drawScaledEllipsizedText(extractor, font, TEXT, bounds.x() + 4, y + 1, bounds.width() - 8,
                TypographyScale.META.getScale(), GZTheme.COLOR_STATUS_YELLOW, false);
    }

    /** Draws the same note inline within an already-scrolled content flow (e.g. Crafting's item detail). Returns the height consumed. */
    public static int renderInline(GuiGraphicsExtractor extractor, Font font, int x, int y, int maxW) {
        TextUtil.drawScaledEllipsizedText(extractor, font, TEXT, x, y, maxW, TypographyScale.META.getScale(), GZTheme.COLOR_STATUS_YELLOW, false);
        return HEIGHT;
    }

    public static int inlineHeight() {
        return HEIGHT;
    }
}
