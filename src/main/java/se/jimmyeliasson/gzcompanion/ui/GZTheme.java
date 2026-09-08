package se.jimmyeliasson.gzcompanion.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Design system tokens, color definitions, and drawing helpers following docs/design/DESIGN-SYSTEM.md.
 *
 * CRITICAL RENDERING NOTE FOR MINECRAFT 26.1.2:
 * All color integers passed to Minecraft GUI methods (extractor.fill, extractor.text, etc.)
 * MUST include an explicit Alpha byte (ARGB, 32-bit). An RGB value like 0xF8FAFC will be
 * interpreted as Alpha=0x00 and render completely invisible.
 */
public final class GZTheme {
    // Surfaces & Glass (ARGB)
    public static final int COLOR_BACKDROP = 0xB3070A0E;      // 70% dark background overlay
    public static final int COLOR_PANEL_BG = 0xF00D141C;      // 94% dark navy dialog container
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

    /**
     * Ensures an RGB or ARGB color has full alpha (0xFF) if no alpha was provided.
     */
    public static int opaque(int rgb) {
        return (rgb & 0xFF000000) == 0 ? (0xFF000000 | rgb) : rgb;
    }

    /**
     * Draws a card container with background and 1px border.
     */
    public static void drawCard(GuiGraphicsExtractor extractor, int x, int y, int width, int height, int bgArgb, int borderArgb) {
        extractor.fill(x, y, x + width, y + height, bgArgb);
        extractor.fill(x, y, x + width, y + 1, borderArgb);
        extractor.fill(x, y + height - 1, x + width, y + height, borderArgb);
        extractor.fill(x, y, x + 1, y + height, borderArgb);
        extractor.fill(x + width - 1, y, x + width, y + height, borderArgb);
    }

    /**
     * Draws a status badge pill with a colored status dot.
     */
    public static void drawBadge(GuiGraphicsExtractor extractor, Font font, int x, int y, String label, int textArgb, int dotArgb) {
        int textW = font.width(label);
        int badgeW = textW + 16;
        int badgeH = 13;

        extractor.fill(x, y, x + badgeW, y + badgeH, 0x800A1017);
        extractor.fill(x, y, x + badgeW, y + 1, 0x40475569);
        extractor.fill(x, y + badgeH - 1, x + badgeW, y + badgeH, 0x40475569);
        extractor.fill(x, y, x + 1, y + badgeH, 0x40475569);
        extractor.fill(x + badgeW - 1, y, x + badgeW, y + badgeH, 0x40475569);

        // Status Dot
        drawStatusDot(extractor, x + 4, y + 4, dotArgb);
        // Label Text
        extractor.text(font, label, x + 12, y + 3, opaque(textArgb), false);
    }

    /**
     * Draws a crisp 4x4 pixel status dot.
     */
    public static void drawStatusDot(GuiGraphicsExtractor extractor, int x, int y, int colorArgb) {
        int c = opaque(colorArgb);
        extractor.fill(x, y, x + 4, y + 4, c);
    }

    /**
     * Draws a consistent action button with state styling.
     */
    public static void drawButton(GuiGraphicsExtractor extractor, Font font, int x, int y, int width, int height,
                                  String text, boolean isPrimary, boolean isHovered) {
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

        int textW = font.width(text);
        int textX = x + Math.max(4, (width - textW) / 2);
        int textY = y + (height - 8) / 2;
        extractor.text(font, text, textX, textY, textColor, isPrimary);
    }
}