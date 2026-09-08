package se.jimmyeliasson.gzcompanion.ui.layout;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * Robust typography measurement, wrapping, ellipsizing, and alignment utilities.
 */
public final class TextUtil {
    private TextUtil() {}

    /**
     * Draws text with ellipsis (...) if it exceeds maxPixelWidth.
     */
    public static void drawEllipsizedText(GuiGraphicsExtractor extractor, Font font, String text,
                                          int x, int y, int maxPixelWidth, int colorArgb, boolean dropShadow) {
        if (text == null || text.isEmpty() || maxPixelWidth <= 0) return;
        String fitted = ellipsize(font, text, maxPixelWidth);
        extractor.text(font, fitted, x, y, colorArgb, dropShadow);
    }

    /**
     * Truncates a string and appends "..." if it exceeds the pixel width.
     */
    public static String ellipsize(Font font, String text, int maxPixelWidth) {
        if (text == null) return "";
        if (font.width(text) <= maxPixelWidth) {
            return text;
        }
        int dotsW = font.width("...");
        if (maxPixelWidth <= dotsW) {
            return ".";
        }
        int avail = maxPixelWidth - dotsW;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (font.width(sb.toString() + c) > avail) {
                break;
            }
            sb.append(c);
        }
        return sb.toString().trim() + "...";
    }

    /**
     * Draws multi-line wrapped text inside a maximum pixel width.
     * Returns the total height rendered in pixels.
     */
    public static int drawWrappedText(GuiGraphicsExtractor extractor, Font font, String text,
                                       int x, int y, int maxPixelWidth, int maxLines, int colorArgb, boolean dropShadow) {
        if (text == null || text.isEmpty() || maxPixelWidth <= 0) return 0;
        List<FormattedCharSequence> lines = font.split(net.minecraft.network.chat.Component.literal(text), maxPixelWidth);
        int rendered = 0;
        int lineH = 9;
        for (int i = 0; i < lines.size() && i < maxLines; i++) {
            extractor.text(font, lines.get(i), x, y + (i * lineH), colorArgb, dropShadow);
            rendered++;
        }
        return rendered * lineH;
    }

    /**
     * Draws right-aligned text within bounds.
     */
    public static void drawRightAlignedText(GuiGraphicsExtractor extractor, Font font, String text,
                                           int rightX, int y, int maxPixelWidth, int colorArgb, boolean dropShadow) {
        if (text == null || text.isEmpty()) return;
        String fitted = ellipsize(font, text, maxPixelWidth);
        int textW = font.width(fitted);
        extractor.text(font, fitted, rightX - textW, y, colorArgb, dropShadow);
    }

    /**
     * Draws horizontally centered text.
     */
    public static void drawCenteredText(GuiGraphicsExtractor extractor, Font font, String text,
                                        int centerX, int y, int maxPixelWidth, int colorArgb, boolean dropShadow) {
        if (text == null || text.isEmpty()) return;
        String fitted = ellipsize(font, text, maxPixelWidth);
        int textW = font.width(fitted);
        extractor.text(font, fitted, centerX - (textW / 2), y, colorArgb, dropShadow);
    }
}