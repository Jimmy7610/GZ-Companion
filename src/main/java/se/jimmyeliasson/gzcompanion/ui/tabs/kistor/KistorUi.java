package se.jimmyeliasson.gzcompanion.ui.tabs.kistor;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import se.jimmyeliasson.gzcompanion.chest.bridge.ChestItemIcons;
import se.jimmyeliasson.gzcompanion.chest.model.ChestFreshness;
import se.jimmyeliasson.gzcompanion.chest.model.DimensionNames;
import se.jimmyeliasson.gzcompanion.chest.model.StoragePosition;
import se.jimmyeliasson.gzcompanion.chest.nav.ChestNavigationMath;
import se.jimmyeliasson.gzcompanion.chest.nav.PlayerPose;
import se.jimmyeliasson.gzcompanion.ui.GZTheme;
import se.jimmyeliasson.gzcompanion.ui.TypographyScale;
import se.jimmyeliasson.gzcompanion.ui.layout.TextUtil;
import se.jimmyeliasson.gzcompanion.ui.layout.UiRect;

import java.util.Objects;

/**
 * Shared Kistor 2.0 presentation helpers (badges, buttons, item icons, distance text). Keeps the
 * existing GZ Companion visual language - every primitive here is built on {@link GZTheme}.
 */
public final class KistorUi {
    /** Shown wherever cached contents are presented. Never "live". */
    public static final String LAST_KNOWN_WARNING = "Kan ha ändrats sedan du öppnade förvaringen.";

    private KistorUi() {}

    public static int freshnessColor(ChestFreshness freshness) {
        return switch (freshness) {
            case FRESH -> GZTheme.COLOR_STATUS_GREEN;
            case RECENT -> GZTheme.COLOR_MINT;
            case EARLIER -> GZTheme.COLOR_TEXT_SECONDARY;
            case OLDER, UNKNOWN -> GZTheme.COLOR_TEXT_MUTED;
        };
    }

    /** Small right-aligned freshness badge; returns its drawn width. */
    public static int drawFreshnessBadgeRightAligned(GuiGraphicsExtractor extractor, Font font, int right, int y, ChestFreshness freshness) {
        String label = freshness.badge();
        float scale = TypographyScale.META.getScale() * 0.9f;
        int textW = TextUtil.scaledWidth(font, label, scale);
        int w = textW + 6;
        int x = right - w;
        int color = freshnessColor(freshness);
        extractor.fill(x, y, x + w, y + 8, 0x660A1017);
        extractor.fill(x, y + 7, x + w, y + 8, (color & 0x00FFFFFF) | 0x80000000);
        TextUtil.drawScaledText(extractor, font, label, x + 3, y + 1, scale, color, false);
        return w;
    }

    /** A compact secondary button (or a primary emerald one), with an optional disabled look. */
    public static void drawButton(GuiGraphicsExtractor extractor, Font font, UiRect rect, String label, boolean primary,
                                  boolean hovered, boolean enabled) {
        if (rect.width() <= 0) return;
        if (!enabled) {
            GZTheme.drawCard(extractor, rect, 0x400A1017, GZTheme.COLOR_BORDER_SUBTLE);
            TextUtil.drawScaledCenteredText(extractor, font, label, rect.x() + rect.width() / 2, rect.y() + (rect.height() - 7) / 2,
                    rect.width() - 4, TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_MUTED, false);
            return;
        }
        GZTheme.drawButton(extractor, font, rect, label, primary, hovered, TypographyScale.META.getScale());
    }

    /** A quiet, low-emphasis button used for destructive-but-secondary actions ("Glöm"). */
    public static void drawQuietButton(GuiGraphicsExtractor extractor, Font font, UiRect rect, String label, boolean hovered, boolean armed) {
        if (rect.width() <= 0) return;
        int bg = armed ? 0x99EF4444 : (hovered ? GZTheme.COLOR_NAV_HOVER : 0x300A1017);
        int border = armed ? GZTheme.COLOR_STATUS_RED : 0x22475569;
        int text = armed ? GZTheme.COLOR_TEXT_PRIMARY : GZTheme.COLOR_TEXT_MUTED;
        GZTheme.drawCard(extractor, rect, bg, border);
        TextUtil.drawScaledCenteredText(extractor, font, label, rect.x() + rect.width() / 2, rect.y() + (rect.height() - 7) / 2,
                rect.width() - 4, TypographyScale.META.getScale(), text, false);
    }

    /**
     * Draws a real item icon and registers its hover tooltip. The tooltip deliberately shows no
     * quantity (the shared tooltip's quantity means "needed"); counts are shown as text instead,
     * always labeled as last known.
     */
    public static void drawItemIcon(KistorRenderContext ctx, String itemId, int x, int y, int size) {
        boolean real = ChestItemIcons.draw(ctx.extractor(), itemId, x, y, size);
        // Only icons actually visible inside the current scissored pane get a hover target, so a
        // row scrolled out of view can never show a tooltip.
        if (real && ctx.extractor().containsPointInScissor(x + size / 2, y + size / 2)) {
            ctx.tooltips().register(new UiRect(x, y, size, size), ChestItemIcons.resolve(itemId), itemId, null, 0, 1);
        }
    }

    /** Swedish-style thousands grouping with a space: 1200 -> "1 200". */
    public static String formatCount(long value) {
        String raw = Long.toString(Math.abs(value));
        StringBuilder sb = new StringBuilder();
        int lead = raw.length() % 3;
        for (int i = 0; i < raw.length(); i++) {
            if (i > 0 && (i - lead) % 3 == 0) sb.append(' ');
            sb.append(raw.charAt(i));
        }
        return (value < 0 ? "-" : "") + sb;
    }

    /**
     * "483 block" when the player's own position is known and in the same dimension; the
     * storage's dimension name otherwise (distance across dimensions is never meaningful).
     */
    public static String distanceOrDimension(PlayerPose pose, String dimensionKey, StoragePosition anchor) {
        if (pose != null && Objects.equals(pose.dimensionKey(), dimensionKey)) {
            long d = Math.round(ChestNavigationMath.horizontalDistance(pose.x(), pose.z(), anchor.x() + 0.5, anchor.z() + 0.5));
            return formatCount(d) + " block";
        }
        return DimensionNames.shortName(dimensionKey);
    }

    /** Horizontal distance to a same-dimension storage, or -1 if not comparable. */
    public static double sameDimensionDistance(PlayerPose pose, String dimensionKey, StoragePosition anchor) {
        if (pose == null || !Objects.equals(pose.dimensionKey(), dimensionKey)) return -1;
        return ChestNavigationMath.horizontalDistance(pose.x(), pose.z(), anchor.x() + 0.5, anchor.z() + 0.5);
    }

    public static void drawSectionLabel(GuiGraphicsExtractor extractor, Font font, String text, int x, int y) {
        TextUtil.drawScaledText(extractor, font, text, x, y, TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_MUTED, false);
    }

    /** The shared inline text field used by search, label, group and note editors. */
    public static void drawTextField(GuiGraphicsExtractor extractor, Font font, UiRect rect, String text, String placeholder,
                                     boolean focused, long nowMs) {
        GZTheme.drawCard(extractor, rect, focused ? GZTheme.COLOR_CARD_HOVER : GZTheme.COLOR_CARD_INNER,
                focused ? GZTheme.COLOR_BORDER_EMERALD : GZTheme.COLOR_BORDER_SUBTLE);
        int textY = rect.y() + Math.max(1, (rect.height() - 7) / 2);
        if ((text == null || text.isEmpty()) && !focused) {
            TextUtil.drawScaledEllipsizedText(extractor, font, placeholder, rect.x() + 4, textY, rect.width() - 8,
                    TypographyScale.SMALL.getScale(), GZTheme.COLOR_TEXT_MUTED, false);
        } else {
            String shown = (text != null ? text : "") + (focused && ((nowMs / 500) % 2 == 0) ? "_" : "");
            TextUtil.drawScaledEllipsizedText(extractor, font, shown, rect.x() + 4, textY, rect.width() - 8,
                    TypographyScale.SMALL.getScale(), GZTheme.COLOR_TEXT_PRIMARY, false);
        }
    }
}
