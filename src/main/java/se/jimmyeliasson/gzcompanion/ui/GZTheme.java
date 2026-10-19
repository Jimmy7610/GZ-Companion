package se.jimmyeliasson.gzcompanion.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import se.jimmyeliasson.gzcompanion.ui.layout.TextUtil;
import se.jimmyeliasson.gzcompanion.ui.layout.UiRect;
import se.jimmyeliasson.gzcompanion.ui.theme.ThemeTokens;
import se.jimmyeliasson.gzcompanion.ui.theme.UiTheme;

/**
 * Design system: the ACTIVE theme's tokens plus the shared component primitives (cards, buttons,
 * badges, icons) every tab draws with.
 *
 * <p><b>Themes.</b> The {@code COLOR_*} fields are the active theme's colors. They are written
 * ONLY by {@link #applyTheme} (from {@link ThemeTokens}) and start out as the Minecraft theme, so
 * every tab keeps reading {@code GZTheme.COLOR_*} and follows a theme switch on the next frame
 * without knowing which theme is active. Fonts and text shadows are applied centrally by
 * {@link TextUtil} via {@link #textStyle()} / {@link #textShadows()}. All UI drawing happens on the
 * render thread, which is also where {@link #applyTheme} is called.
 */
public final class GZTheme {
    public static boolean DEBUG_LAYOUT = false;

    private static UiTheme activeTheme = UiTheme.DEFAULT;
    private static ThemeTokens tokens = UiTheme.DEFAULT.tokens();
    /** Built lazily on first text draw (needs Minecraft's text classes); null = default font. */
    private static Style textStyle;
    private static boolean textStyleResolved;

    // Surfaces & Glass (ARGB) - active theme
    public static int COLOR_BACKDROP;
    public static int COLOR_PANEL_BG;
    public static int COLOR_CARD_BG;
    public static int COLOR_CARD_HOVER;
    public static int COLOR_CARD_INNER;
    public static int COLOR_NAV_ACTIVE;
    public static int COLOR_NAV_HOVER;

    // Accents & Borders (ARGB) - active theme
    public static int COLOR_EMERALD;
    public static int COLOR_MINT;
    public static int COLOR_EMERALD_DARK;
    public static int COLOR_BORDER_SUBTLE;
    public static int COLOR_BORDER_MODAL;
    public static int COLOR_BORDER_EMERALD;

    // Typography colors (ARGB, opaque) - active theme
    public static int COLOR_TEXT_PRIMARY;
    public static int COLOR_TEXT_SECONDARY;
    public static int COLOR_TEXT_MUTED;
    public static int COLOR_TEXT_ACCENT;
    public static int COLOR_TEXT_ON_EMERALD;

    // Status dots & semantic colors (ARGB, opaque) - active theme
    public static int COLOR_STATUS_GREEN;
    public static int COLOR_STATUS_YELLOW;
    public static int COLOR_STATUS_RED;
    public static int COLOR_STATUS_GREY;

    static {
        copyTokens(tokens);
    }

    private GZTheme() {}

    /**
     * Switches the whole Companion UI to {@code theme}, effective from the next drawn frame - no
     * restart, no screen rebuild. Null falls back to the default theme.
     */
    public static void applyTheme(UiTheme theme) {
        UiTheme next = theme != null ? theme : UiTheme.DEFAULT;
        activeTheme = next;
        tokens = next.tokens();
        copyTokens(tokens);
        textStyle = null;
        textStyleResolved = false;
    }

    public static UiTheme activeTheme() {
        return activeTheme;
    }

    public static ThemeTokens tokens() {
        return tokens;
    }

    /** Whether the active theme allows Minecraft's text drop shadow. */
    public static boolean textShadows() {
        return tokens.textShadows();
    }

    /** The text style selecting the active theme's font, or null for Minecraft's default font. */
    public static Style textStyle() {
        if (!textStyleResolved) {
            String fontId = tokens.fontId();
            textStyle = fontId == null ? null : Style.EMPTY.withFont(new FontDescription.Resource(Identifier.parse(fontId)));
            textStyleResolved = true;
        }
        return textStyle;
    }

    private static void copyTokens(ThemeTokens t) {
        COLOR_BACKDROP = t.backdrop();
        COLOR_PANEL_BG = t.panelBg();
        COLOR_CARD_BG = t.cardBg();
        COLOR_CARD_HOVER = t.cardHover();
        COLOR_CARD_INNER = t.cardInner();
        COLOR_NAV_ACTIVE = t.navActive();
        COLOR_NAV_HOVER = t.navHover();
        COLOR_EMERALD = t.emerald();
        COLOR_MINT = t.mint();
        COLOR_EMERALD_DARK = t.emeraldDark();
        COLOR_BORDER_SUBTLE = t.borderSubtle();
        COLOR_BORDER_MODAL = t.borderModal();
        COLOR_BORDER_EMERALD = t.borderEmerald();
        COLOR_TEXT_PRIMARY = t.textPrimary();
        COLOR_TEXT_SECONDARY = t.textSecondary();
        COLOR_TEXT_MUTED = t.textMuted();
        COLOR_TEXT_ACCENT = t.textAccent();
        COLOR_TEXT_ON_EMERALD = t.textOnEmerald();
        COLOR_STATUS_GREEN = t.statusGreen();
        COLOR_STATUS_YELLOW = t.statusYellow();
        COLOR_STATUS_RED = t.statusRed();
        COLOR_STATUS_GREY = t.statusGrey();
    }

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
        // Pixel-chamfered corners (Minecraft theme): clip 1px from 4 outer corner points for a refined look
        if (tokens.chamferedCorners() && width > 6 && height > 6) {
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
        float scale = TypographyScale.META.getScale();
        int textW = TextUtil.scaledWidth(font, label, scale);
        int badgeW = textW + 14;
        int badgeH = 11;

        int border = tokens.badgeBorder();
        extractor.fill(x, y, x + badgeW, y + badgeH, tokens.badgeBg());
        extractor.fill(x, y, x + badgeW, y + 1, border);
        extractor.fill(x, y + badgeH - 1, x + badgeW, y + badgeH, border);
        extractor.fill(x, y, x + 1, y + badgeH, border);
        extractor.fill(x + badgeW - 1, y, x + badgeW, y + badgeH, border);

        drawStatusDot(extractor, x + 3, y + 3, dotArgb);
        TextUtil.drawScaledText(extractor, font, label, x + 10, y + 2, scale, opaque(textArgb), false);
    }

    public static void drawStatusDot(GuiGraphicsExtractor extractor, int x, int y, int colorArgb) {
        int c = opaque(colorArgb);
        extractor.fill(x, y, x + 4, y + 4, c);
    }

    public static void drawButton(GuiGraphicsExtractor extractor, Font font, UiRect rect,
                                  String text, boolean isPrimary, boolean isHovered) {
        float scale = isPrimary ? TypographyScale.BODY.getScale() : TypographyScale.SMALL.getScale();
        drawButton(extractor, font, rect.x(), rect.y(), rect.width(), rect.height(), text, isPrimary, isHovered, scale);
    }

    public static void drawButton(GuiGraphicsExtractor extractor, Font font, UiRect rect,
                                  String text, boolean isPrimary, boolean isHovered, float scale) {
        drawButton(extractor, font, rect.x(), rect.y(), rect.width(), rect.height(), text, isPrimary, isHovered, scale);
    }

    public static void drawButton(GuiGraphicsExtractor extractor, Font font, int x, int y, int width, int height,
                                  String text, boolean isPrimary, boolean isHovered) {
        float scale = isPrimary ? TypographyScale.BODY.getScale() : TypographyScale.SMALL.getScale();
        drawButton(extractor, font, x, y, width, height, text, isPrimary, isHovered, scale);
    }

    public static void drawButton(GuiGraphicsExtractor extractor, Font font, int x, int y, int width, int height,
                                  String text, boolean isPrimary, boolean isHovered, float scale) {
        if (width <= 0 || height <= 0) return;
        int bg = isPrimary
                ? (isHovered ? COLOR_EMERALD_DARK : COLOR_EMERALD)
                : (isHovered ? tokens.buttonHoverBg() : tokens.buttonBg());
        int border = isPrimary
                ? COLOR_BORDER_EMERALD
                : (isHovered ? tokens.buttonHoverBorder() : COLOR_BORDER_SUBTLE);
        int textColor = isPrimary
                ? COLOR_TEXT_ON_EMERALD
                : (isHovered ? COLOR_TEXT_PRIMARY : COLOR_TEXT_SECONDARY);

        drawCard(extractor, x, y, width, height, bg, border);

        int textH = (int) Math.ceil(8 * scale);
        int textY = y + (height - textH) / 2;
        // CRITICAL: Drop shadow must be FALSE for primary dark text on emerald background
        boolean dropShadow = !isPrimary;
        TextUtil.drawScaledCenteredText(extractor, font, text, x + (width / 2), textY, width - 4, scale, textColor, dropShadow);
    }

    public static void drawDebugBounds(GuiGraphicsExtractor extractor, int x, int y, int w, int h, int color) {
        extractor.fill(x, y, x + w, y + 1, color);
        extractor.fill(x, y + h - 1, x + w, y + h, color);
        extractor.fill(x, y, x + 1, y + h, color);
        extractor.fill(x + w - 1, y, x + w, y + h, color);
    }
}