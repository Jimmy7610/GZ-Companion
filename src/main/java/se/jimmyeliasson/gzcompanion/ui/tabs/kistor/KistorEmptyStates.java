package se.jimmyeliasson.gzcompanion.ui.tabs.kistor;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import se.jimmyeliasson.gzcompanion.ui.GZTheme;
import se.jimmyeliasson.gzcompanion.ui.IconId;
import se.jimmyeliasson.gzcompanion.ui.TypographyScale;
import se.jimmyeliasson.gzcompanion.ui.layout.TextUtil;
import se.jimmyeliasson.gzcompanion.ui.layout.UiRect;

/** Shared Kistor empty states. */
final class KistorEmptyStates {
    private KistorEmptyStates() {}

    static void render(KistorRenderContext ctx, UiRect area, boolean noIndexedStorageAtAll, String noMatchText) {
        GuiGraphicsExtractor g = ctx.extractor();
        Font font = ctx.font();
        GZTheme.drawCard(g, area, GZTheme.COLOR_CARD_BG, GZTheme.COLOR_BORDER_SUBTLE);
        int centerX = area.x() + area.width() / 2;
        int maxW = area.width() - 20;
        int y = area.y() + Math.max(6, area.height() / 2 - 24);

        GZTheme.drawIcon(g, IconId.CHEST, centerX - 8, y, 16, GZTheme.COLOR_MINT);
        y += 20;

        if (noIndexedStorageAtAll) {
            TextUtil.drawCenteredText(g, font, "Du har inga sparade förvaringar ännu.", centerX, y, maxW, GZTheme.COLOR_TEXT_PRIMARY, true);
            y += 12;
            y += TextUtil.drawScaledWrappedText(g, font,
                    "Öppna en kista, tunna eller annan stödd förvaring så sparar GZ Companion senast känt innehåll automatiskt.",
                    area.x() + 10, y, maxW, TypographyScale.SMALL.getScale(), 3, 1, GZTheme.COLOR_TEXT_SECONDARY, false);
            y += 6;
            TextUtil.drawScaledCenteredText(g, font, "Inga oöppnade förvaringar skannas.", centerX, y, maxW,
                    TypographyScale.META.getScale(), GZTheme.COLOR_STATUS_GREEN, false);
        } else {
            TextUtil.drawCenteredText(g, font, noMatchText, centerX, y, maxW, GZTheme.COLOR_TEXT_PRIMARY, true);
        }
    }
}
