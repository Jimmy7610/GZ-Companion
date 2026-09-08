package se.jimmyeliasson.gzcompanion.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Design system constants and drawing helpers following docs/design/DESIGN-SYSTEM.md.
 */
public final class GZTheme {
    // Surfaces & Glass
    public static final int COLOR_BACKDROP = 0xB3070A0E;      // 70% dark background overlay
    public static final int COLOR_PANEL_BG = 0xE60F171E;      // Main dialog container
    public static final int COLOR_CARD_BG = 0x9915222E;       // Card surface
    public static final int COLOR_CARD_HOVER = 0xCC1E3142;    // Hovered card
    public static final int COLOR_NAV_ACTIVE = 0x4D10B981;    // Active tab background
    public static final int COLOR_NAV_HOVER = 0x261E293B;     // Inactive tab hover

    // Accents & Borders
    public static final int COLOR_EMERALD = 0x10B981;         // Primary action & logo
    public static final int COLOR_MINT = 0x34D399;            // Highlights, player name, positive text
    public static final int COLOR_BORDER_SUBTLE = 0x33475569; // Card border
    public static final int COLOR_BORDER_MODAL = 0x6664748B;  // Outer modal frame
    public static final int COLOR_BORDER_EMERALD = 0x8010B981;// Active item border

    // Typography
    public static final int COLOR_TEXT_PRIMARY = 0xF8FAFC;    // Headings, titles
    public static final int COLOR_TEXT_SECONDARY = 0x94A3B8;  // Body text, subtitles
    public static final int COLOR_TEXT_MUTED = 0x64748B;      // Footers, placeholders
    public static final int COLOR_TEXT_ACCENT = 0x34D399;     // Mint highlight

    // Status Dots & Badges
    public static final int COLOR_STATUS_GREEN = 0x22C55E;
    public static final int COLOR_STATUS_YELLOW = 0xF59E0B;
    public static final int COLOR_STATUS_RED = 0xEF4444;
    public static final int COLOR_STATUS_GREY = 0x64748B;

    private GZTheme() {}

    /**
     * Draws a card container with background and border.
     */
    public static void drawCard(GuiGraphicsExtractor extractor, int x, int y, int width, int height, int bgArgb, int borderArgb) {
        extractor.fill(x, y, x + width, y + height, bgArgb);
        extractor.fill(x, y, x + width, y + 1, borderArgb);
        extractor.fill(x, y + height - 1, x + width, y + height, borderArgb);
        extractor.fill(x, y, x + 1, y + height, borderArgb);
        extractor.fill(x + width - 1, y, x + width, y + height, borderArgb);
    }

    /**
     * Draws a status badge pill.
     */
    public static void drawBadge(GuiGraphicsExtractor extractor, Font font, int x, int y, String label, int textRgb, int dotColor) {
        int textW = font.width(label);
        int badgeW = textW + 16;
        int badgeH = 14;
        
        extractor.fill(x, y, x + badgeW, y + badgeH, 0x660F172A);
        extractor.fill(x, y, x + badgeW, y + 1, 0x4064748B);
        extractor.fill(x, y + badgeH - 1, x + badgeW, y + badgeH, 0x4064748B);
        extractor.fill(x, y, x + 1, y + badgeH, 0x4064748B);
        extractor.fill(x + badgeW - 1, y, x + badgeW, y + badgeH, 0x4064748B);

        // Dot
        extractor.fill(x + 4, y + 5, x + 8, y + 9, 0xFF000000 | dotColor);
        // Text
        extractor.text(font, label, x + 11, y + 3, textRgb, false);
    }
}