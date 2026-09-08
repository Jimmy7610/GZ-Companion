package se.jimmyeliasson.gzcompanion.ui.tabs;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import se.jimmyeliasson.gzcompanion.ui.GZCompanionMainScreen;
import se.jimmyeliasson.gzcompanion.ui.GZTheme;
import se.jimmyeliasson.gzcompanion.ui.TabType;
import se.jimmyeliasson.gzcompanion.ui.layout.TextUtil;
import se.jimmyeliasson.gzcompanion.ui.layout.UiRect;

/**
 * Polished, consistent placeholder view for tabs under development.
 */
public class PlaceholderTabComponent {
    private UiRect backBtnRect = new UiRect(0, 0, 0, 0);

    public void calculateLayout(UiRect bounds) {
        int btnW = Math.min(130, bounds.width() - 32);
        int btnH = 16;
        int btnX = bounds.x() + (bounds.width() - btnW) / 2;
        int btnY = bounds.bottom() - btnH - 12;
        this.backBtnRect = new UiRect(btnX, btnY, btnW, btnH);
    }

    public void render(GuiGraphicsExtractor extractor, Font font, UiRect bounds, int mouseX, int mouseY, TabType tab, GZCompanionMainScreen mainScreen) {
        calculateLayout(bounds);

        GZTheme.drawCard(extractor, bounds, GZTheme.COLOR_CARD_BG, GZTheme.COLOR_BORDER_SUBTLE);
        extractor.fill(bounds.x() + 2, bounds.y() + 2, bounds.x() + 4, bounds.y() + 28, GZTheme.COLOR_EMERALD);

        int pad = 8;
        GZTheme.drawIcon(extractor, tab.getIcon(), bounds.x() + pad + 2, bounds.y() + 6, 12, GZTheme.COLOR_MINT);
        extractor.text(font, tab.getDisplayName(), bounds.x() + pad + 18, bounds.y() + 7, GZTheme.COLOR_TEXT_PRIMARY, true);
        TextUtil.drawEllipsizedText(extractor, font, tab.getDescription(), bounds.x() + pad + 18, bounds.y() + 18, bounds.width() - 36, GZTheme.COLOR_TEXT_SECONDARY, false);

        int boxY = bounds.y() + 32;
        int boxH = bounds.height() - 40;
        UiRect innerBox = new UiRect(bounds.x() + pad, boxY, bounds.width() - (pad * 2), boxH);
        GZTheme.drawCard(extractor, innerBox, GZTheme.COLOR_CARD_INNER, GZTheme.COLOR_BORDER_SUBTLE);

        int centerX = innerBox.x() + (innerBox.width() / 2);
        int msgY = innerBox.y() + 16;
        int maxTextW = innerBox.width() - 16;

        GZTheme.drawIcon(extractor, tab.getIcon(), centerX - 8, msgY, 16, GZTheme.COLOR_MINT);

        String title = tab.getDisplayName() + " - Under utveckling";
        TextUtil.drawCenteredText(extractor, font, title, centerX, msgY + 20, maxTextW, GZTheme.COLOR_TEXT_PRIMARY, true);

        String desc1 = getTabExplanation(tab);
        TextUtil.drawCenteredText(extractor, font, desc1, centerX, msgY + 34, maxTextW, GZTheme.COLOR_TEXT_SECONDARY, false);

        String desc2 = "Denna funktion aktiveras i en kommande uppdatering.";
        TextUtil.drawCenteredText(extractor, font, desc2, centerX, msgY + 46, maxTextW, GZTheme.COLOR_TEXT_MUTED, false);

        GZTheme.drawButton(extractor, font, backBtnRect, "Tillbaka till Hem", true, backBtnRect.contains(mouseX, mouseY));
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button, UiRect bounds, GZCompanionMainScreen mainScreen) {
        if (button != 0) return false;
        calculateLayout(bounds);

        if (backBtnRect.contains(mouseX, mouseY)) {
            mainScreen.setActiveTab(TabType.HEM);
            return true;
        }
        return false;
    }

    private String getTabExplanation(TabType tab) {
        return switch (tab) {
            case GUIDE -> "Här hittar du interaktiva steg-för-steg-guider och framstegsmål för GameZoneMC.";
            case CRAFTING -> "Snabb och smidig receptöversikt anpassad efter serverns unika föremål.";
            case KISTOR -> "Håll koll på dina kistor och förvaringar du legitimt har öppnat.";
            case SETTLEMENT -> "Hjälpmedel för att hantera claims, städer och medlemmar på GameZoneMC.";
            case BYGGPLANER -> "Planera dina byggen och beräkna materialkostnader enkelt.";
            case MARKETWATCH -> "Övervaka priser och trender på serverns handelsmarknad.";
            case KOMMANDON -> "Snabbguide och genvägar till alla vanliga och användbara serverkommandon.";
            case INSTALLNINGAR -> "Anpassa utseende, tangentbindningar och inställningar för GZ Companion.";
            default -> "Information om denna funktion kommer snart.";
        };
    }
}