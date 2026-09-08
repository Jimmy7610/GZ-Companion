package se.jimmyeliasson.gzcompanion.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import se.jimmyeliasson.gzcompanion.ui.layout.TextUtil;
import se.jimmyeliasson.gzcompanion.ui.layout.UiRect;

/**
 * Design system tokens, pixel-chamfered component rendering primitives, and layout styling.
 */
public final class GZTheme {
    public static boolean DEBUG_LAYOUT = false;

    // Surfaces & Glass (ARGB)
    public static final int COLOR_BACKDROP = 0xB3070A0E;      // 70% dark background overlay
    public static final int COLOR_PANEL_BG = 0xF20D141C;      // 95% dark navy dialog container
    public static final int COLOR_CARD_BG = 0xCC131F2B;       // 80% slate card surface
    public static final int COLOR_CARD_HOVER = 0xE61E2E3D;    // 90% slate hover surface
    public static final int COLOR_CARD_INNER = 0x800A1017;    // 50% recessed inner container
    public static final int COLOR_NAV_ACTIVE = 0x4D10B981;    // 30% emerald active tab background
    public static final int COLOR_NAV_HOVER = 0x331E293B;     // 20% slate tab hover background

    // Accents & Borders (ARGB)
    public static final int COLOR_EMERALD = 0xFF10B981;         // Primary action & logo (Opaque)
    public static final int COLOR_MINT = 0xFF34D399;            // Highlights, player name, positive text (Opaque)
    public static final int COLOR_EMERALD_DARK = 0xFF059669;    // Primary button hover/press (Opaque)
    public static final int COLOR_BORDER_SUBTLE = 0x33475569;   // Card border (20% slate)
    public static final int COLOR_BORDER_MODAL = 0x66475569;    // Outer modal frame (40% slate)
    public static final int COLOR_BORDER_EMERALD = 0x9910B981;  // Active item border (60% emerald)

    // Typography (ARGB - All Opaque 0xFF Alpha)
    public static final int COLOR_TEXT_PRIMARY = 0xFFF8FAFC;    // Headings, titles, high contrast
    public static final int COLOR_TEXT_SECONDARY = 0xFF94A3B8;  // Body text, subtitles, labels
    public static final int COLOR_TEXT_MUTED = 0xFF64748B;      // Footers, placeholders, inactive tabs
    public static final int COLOR_TEXT_ACCENT = 0xFF34D399;     // Mint highlight
    public static final int COLOR_TEXT_ON_EMERALD = 0xFF04170E; // Dark contrast text on emerald buttons

    // Status Dots & Semantic Colors (ARGB - All Opaque 0xFF Alpha)
    public static final int COLOR_STATUS_GREEN = 0xFF22C55E;
    public static final int COLOR_STATUS_YELLOW = 0xFFF59E0B;
    public static final int COLOR_STATUS_RED = 0xFFEF4444;
    public static final int COLOR_STATUS_GREY = 0xFF64748B;

    private GZTheme() {}

    public static int opaque(int rgb) {
        return (rgb & 0xFF000000) == 0 ? (0xFF000000 | rgb) : rgb;
    }

    /**
     * Draws a subtle pixel-chamfered card container.
     */
    public static void drawCard(GuiGraphicsExtractor extractor, UiRect rect, int bgArgb, int borderArgb) {
        drawCard(extractor, rect.x(), rect.y(), rect.width(), rect.height(), bgArgb, borderArgb);
    }

    public static void drawCard(GuiGraphicsExtractor extractor, int x, int y, int width, int height, int bgArgb, int borderArgb) {
        if (width <= 0 || height <= 0) return;
        // Pixel-chamfered corners: clip 1px from 4 outer corner points for a refined look
        if (width > 6 && height > 6) {
            // Main body
            extractor.fill(x + 1, y, x + width - 1, y + height, bgArgb);
            extractor.fill(x, y + 1, x + 1, y + height - 1, bgArgb);
            extractor.fill(x + width - 1, y + 1, x + width, y + height - 1, bgArgb);

            // Borders (horizontal top/bottom, vertical sides)
            extractor.fill(x + 1, y, x + width - 1, y + 1, borderArgb);
            extractor.fill(x + 1, y + height - 1, x + width - 1, y + height, borderArgb);
            extractor.fill(x, y + 1, x + 1, y + height - 1, borderArgb);
            extractor.fill(x + width - 1, y + 1, x + width, y + height - 1, borderArgb);
        } else {
            extractor.fill(x, y, x + width, y + height, bgArgb);
            extractor.fill(x, y, x + width, y + 1, borderArgb);
            extractor.fill(x, y + height - 1, x + width, y + height, borderArgb);
            extractor.fill(x, y, x + 1, y + height, borderArgb);
            extractor.fill(x + width - 1, y, x + width, y + height, borderArgb);
        }

        if (DEBUG_LAYOUT) {
            drawDebugBounds(extractor, x, y, width, height, 0x44FF00FF);
        }
    }

    public static void drawIcon(GuiGraphicsExtractor extractor, IconId icon, int x, int y, int size, int tintColor) {
        if (icon == null) return;
        Identifier id = icon.getIdentifier();
        extractor.blit(RenderPipelines.GUI_TEXTURED, id, x, y, 0.0F, 0.0F, size, size, 16, 16, 16, 16, tintColor);
    }

    public static void drawBadge(GuiGraphicsExtractor extractor, Font font, int x, int y, String label, int textArgb, int dotArgb) {
        int textW = font.width(label);
        int badgeW = textW + 14;
        int badgeH = 11;

        extractor.fill(x, y, x + badgeW, y + badgeH, 0x800A1017);
        extractor.fill(x, y, x + badgeW, y + 1, 0x40475569);
        extractor.fill(x, y + badgeH - 1, x + badgeW, y + badgeH, 0x40475569);
        extractor.fill(x, y, x + 1, y + badgeH, 0x40475569);
        extractor.fill(x + badgeW - 1, y, x + badgeW, y + badgeH, 0x40475569);

        drawStatusDot(extractor, x + 3, y + 3, dotArgb);
        extractor.text(font, label, x + 10, y + 2, opaque(textArgb), false);
    }

    public static void drawStatusDot(GuiGraphicsExtractor extractor, int x, int y, int colorArgb) {
        int c = opaque(colorArgb);
        extractor.fill(x, y, x + 4, y + 4, c);
    }

    public static void drawButton(GuiGraphicsExtractor extractor, Font font, UiRect rect,
                                  String text, boolean isPrimary, boolean isHovered) {
        drawButton(extractor, font, rect.x(), rect.y(), rect.width(), rect.height(), text, isPrimary, isHovered);
    }

    public static void drawButton(GuiGraphicsExtractor extractor, Font font, int x, int y, int width, int height,
                                  String text, boolean isPrimary, boolean isHovered) {
        if (width <= 0 || height <= 0) return;
        int bg = isPrimary
                ? (isHovered ? COLOR_EMERALD_DARK : COLOR_EMERALD)
                : (isHovered ? 0xE61E2E3D : 0x991E293B);
        int border = isPrimary
                ? COLOR_BORDER_EMERALD
                : (isHovered ? 0x8064748B : COLOR_BORDER_SUBTLE);
        int textColor = isPrimary
                ? COLOR_TEXT_ON_EMERALD
                : (isHovered ? COLOR_TEXT_PRIMARY : COLOR_TEXT_SECONDARY);

        drawCard(extractor, x, y, width, height, bg, border);

        int textY = y + (height - 8) / 2;
        // CRITICAL: Drop shadow must be FALSE for primary dark text on emerald background
        boolean dropShadow = !isPrimary;
        TextUtil.drawCenteredText(extractor, font, text, x + (width / 2), textY, width - 4, textColor, dropShadow);
    }

    public static void drawDebugBounds(GuiGraphicsExtractor extractor, int x, int y, int w, int h, int color) {
        extractor.fill(x, y, x + w, y + 1, color);
        extractor.fill(x, y + h - 1, x + w, y + h, color);
        extractor.fill(x, y, x + 1, y + h, color);
        extractor.fill(x + w - 1, y, x + w, y + h, color);
    }
}