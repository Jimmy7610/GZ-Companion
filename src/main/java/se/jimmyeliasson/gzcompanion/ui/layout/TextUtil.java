package se.jimmyeliasson.gzcompanion.ui.layout;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * Robust typography measurement, wrapping, ellipsizing, and alignment utilities
 * with support for typography scaling tokens.
 */
public final class TextUtil {
    private TextUtil() {}

    /**
     * Computes the scaled pixel width of a string.
     */
    public static int scaledWidth(Font font, String text, float scale) {
        if (text == null || text.isEmpty()) return 0;
        return (int) Math.ceil(font.width(text) * scale);
    }

    /**
     * Truncates a string and appends "..." if its unscaled pixel width exceeds maxPixelWidth.
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
     * Draws text with ellipsis (...) if it exceeds maxPixelWidth at 1.0 scale.
     */
    public static void drawEllipsizedText(GuiGraphicsExtractor extractor, Font font, String text,
                                          int x, int y, int maxPixelWidth, int colorArgb, boolean dropShadow) {
        drawScaledEllipsizedText(extractor, font, text, x, y, maxPixelWidth, 1.0f, colorArgb, dropShadow);
    }

    /**
     * Draws scaled text with ellipsis (...) if it exceeds maxPixelWidth in scaled screen coordinates.
     */
    public static void drawScaledEllipsizedText(GuiGraphicsExtractor extractor, Font font, String text,
                                                int x, int y, int maxPixelWidth, float scale, int colorArgb, boolean dropShadow) {
        if (text == null || text.isEmpty() || maxPixelWidth <= 0) return;
        int unscaledMaxW = (scale >= 0.999f) ? maxPixelWidth : (int) (maxPixelWidth / scale);
        String fitted = ellipsize(font, text, unscaledMaxW);
        drawScaledText(extractor, font, fitted, x, y, scale, colorArgb, dropShadow);
    }

    /**
     * Draws scaled text at (x, y) using Matrix3x2fStack.
     */
    public static void drawScaledText(GuiGraphicsExtractor extractor, Font font, String text,
                                      int x, int y, float scale, int colorArgb, boolean dropShadow) {
        if (text == null || text.isEmpty()) return;
        if (Math.abs(scale - 1.0f) < 0.001f) {
            extractor.text(font, text, x, y, colorArgb, dropShadow);
            return;
        }
        extractor.pose().pushMatrix();
        extractor.pose().translate(x, y);
        extractor.pose().scale(scale, scale);
        extractor.text(font, text, 0, 0, colorArgb, dropShadow);
        extractor.pose().popMatrix();
    }

    /**
     * Splits text into lines fitting inside maxPixelWidth at the specified typography scale.
     */
    public static List<FormattedCharSequence> splitLines(Font font, String text, int maxPixelWidth, float scale) {
        if (text == null || text.isEmpty() || maxPixelWidth <= 0 || font == null) return List.of();
        int unscaledMaxW = (scale >= 0.999f) ? maxPixelWidth : (int) (maxPixelWidth / scale);
        return font.split(net.minecraft.network.chat.Component.literal(text), unscaledMaxW);
    }

    /**
     * Computes total pixel height required for multi-line wrapped text at the specified typography scale.
     */
    public static int measureWrappedHeight(Font font, String text, int maxPixelWidth, float scale, int lineSpacing) {
        if (text == null || text.isEmpty() || maxPixelWidth <= 0 || font == null) return 0;
        List<FormattedCharSequence> lines = splitLines(font, text, maxPixelWidth, scale);
        if (lines.isEmpty()) return 0;
        int scaledLineH = (int) Math.ceil(9 * scale);
        return (lines.size() * scaledLineH) + ((lines.size() - 1) * lineSpacing);
    }

    /**
     * Computes the pixel height {@link #drawScaledWrappedText} would actually occupy for this
     * text, honoring the same {@code maxLines} cap - unlike {@link #measureWrappedHeight}, which
     * always measures every wrapped line regardless of any rendering cap. Use this (not the
     * uncapped variant) when computing a detail pane's true scrollable content height, so a
     * maxLines-truncated block never causes the computed max-scroll to under- or over-shoot what
     * is actually drawn.
     */
    public static int measureWrappedHeightCapped(Font font, String text, int maxPixelWidth, float scale, int maxLines, int lineSpacing) {
        if (text == null || text.isEmpty() || maxPixelWidth <= 0 || font == null || maxLines <= 0) return 0;
        List<FormattedCharSequence> lines = splitLines(font, text, maxPixelWidth, scale);
        if (lines.isEmpty()) return 0;
        int scaledLineH = (int) Math.ceil(9 * scale);
        int renderedLines = Math.min(lines.size(), maxLines);
        return (renderedLines * scaledLineH) + ((renderedLines - 1) * lineSpacing);
    }

    /**
     * Draws multi-line wrapped text inside a maximum pixel width.
     */
    public static int drawWrappedText(GuiGraphicsExtractor extractor, Font font, String text,
                                       int x, int y, int maxPixelWidth, int maxLines, int colorArgb, boolean dropShadow) {
        return drawScaledWrappedText(extractor, font, text, x, y, maxPixelWidth, 1.0f, maxLines, 1, colorArgb, dropShadow);
    }

    /**
     * Draws multi-line wrapped text at a specified typography scale.
     */
    public static int drawScaledWrappedText(GuiGraphicsExtractor extractor, Font font, String text,
                                            int x, int y, int maxPixelWidth, float scale, int maxLines, int lineSpacing,
                                            int colorArgb, boolean dropShadow) {
        if (text == null || text.isEmpty() || maxPixelWidth <= 0 || font == null) return 0;
        List<FormattedCharSequence> lines = splitLines(font, text, maxPixelWidth, scale);
        if (lines.isEmpty()) return 0;
        int scaledLineH = (int) Math.ceil(9 * scale);
        int currentY = y;
        int renderedLines = 0;
        for (int i = 0; i < lines.size() && i < maxLines; i++) {
            if (Math.abs(scale - 1.0f) < 0.001f) {
                extractor.text(font, lines.get(i), x, currentY, colorArgb, dropShadow);
            } else {
                extractor.pose().pushMatrix();
                extractor.pose().translate(x, currentY);
                extractor.pose().scale(scale, scale);
                extractor.text(font, lines.get(i), 0, 0, colorArgb, dropShadow);
                extractor.pose().popMatrix();
            }
            currentY += scaledLineH + lineSpacing;
            renderedLines++;
        }
        return (renderedLines * scaledLineH) + ((renderedLines - 1) * lineSpacing);
    }

    /**
     * Draws right-aligned text within bounds at 1.0 scale.
     */
    public static void drawRightAlignedText(GuiGraphicsExtractor extractor, Font font, String text,
                                           int rightX, int y, int maxPixelWidth, int colorArgb, boolean dropShadow) {
        drawScaledRightAlignedText(extractor, font, text, rightX, y, maxPixelWidth, 1.0f, colorArgb, dropShadow);
    }

    /**
     * Draws right-aligned text within bounds at a specified scale.
     */
    public static void drawScaledRightAlignedText(GuiGraphicsExtractor extractor, Font font, String text,
                                                  int rightX, int y, int maxPixelWidth, float scale, int colorArgb, boolean dropShadow) {
        if (text == null || text.isEmpty()) return;
        int unscaledMaxW = (scale >= 0.999f) ? maxPixelWidth : (int) (maxPixelWidth / scale);
        String fitted = ellipsize(font, text, unscaledMaxW);
        int textW = scaledWidth(font, fitted, scale);
        drawScaledText(extractor, font, fitted, rightX - textW, y, scale, colorArgb, dropShadow);
    }

    /**
     * Draws horizontally centered text at 1.0 scale.
     */
    public static void drawCenteredText(GuiGraphicsExtractor extractor, Font font, String text,
                                        int centerX, int y, int maxPixelWidth, int colorArgb, boolean dropShadow) {
        drawScaledCenteredText(extractor, font, text, centerX, y, maxPixelWidth, 1.0f, colorArgb, dropShadow);
    }

    /**
     * Draws horizontally centered text at a specified scale.
     */
    public static void drawScaledCenteredText(GuiGraphicsExtractor extractor, Font font, String text,
                                              int centerX, int y, int maxPixelWidth, float scale, int colorArgb, boolean dropShadow) {
        if (text == null || text.isEmpty()) return;
        int unscaledMaxW = (scale >= 0.999f) ? maxPixelWidth : (int) (maxPixelWidth / scale);
        String fitted = ellipsize(font, text, unscaledMaxW);
        int textW = scaledWidth(font, fitted, scale);
        drawScaledText(extractor, font, fitted, centerX - (textW / 2), y, scale, colorArgb, dropShadow);
    }
}