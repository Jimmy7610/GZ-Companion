package se.jimmyeliasson.gzcompanion.ui.tabs;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import se.jimmyeliasson.gzcompanion.ui.GZCompanionMainScreen;
import se.jimmyeliasson.gzcompanion.ui.GZTheme;
import se.jimmyeliasson.gzcompanion.ui.TabType;

/**
 * Polished, consistent placeholder view for tabs under development.
 */
public class PlaceholderTabComponent {

    public void render(GuiGraphicsExtractor extractor, Font font, int x, int y, int width, int height, int mouseX, int mouseY, TabType tab, GZCompanionMainScreen mainScreen) {
        GZTheme.drawCard(extractor, x, y, width, height, GZTheme.COLOR_CARD_BG, GZTheme.COLOR_BORDER_SUBTLE);

        extractor.fill(x + 2, y + 2, x + 4, y + 28, GZTheme.COLOR_EMERALD);
        extractor.text(font, tab.getDisplayName(), x + 10, y + 8, GZTheme.COLOR_TEXT_PRIMARY, true);
        extractor.text(font, tab.getDescription(), x + 10, y + 19, GZTheme.COLOR_TEXT_SECONDARY, false);

        int boxY = y + 34;
        int boxH = height - 42;
        GZTheme.drawCard(extractor, x + 8, boxY, width - 16, boxH, 0x800A1017, 0x33475569);

        int centerX = x + width / 2;
        int msgY = boxY + 24;

        String icon = "[" + tab.getIconSymbol() + "]";
        int iconW = font.width(icon);
        extractor.text(font, icon, centerX - iconW / 2, msgY, GZTheme.COLOR_MINT, true);

        String title = tab.getDisplayName() + " - Modul under utveckling";
        int titleW = font.width(title);
        extractor.text(font, title, centerX - titleW / 2, msgY + 14, GZTheme.COLOR_TEXT_PRIMARY, true);

        String desc1 = getTabExplanation(tab);
        int desc1W = font.width(desc1);
        extractor.text(font, desc1, centerX - desc1W / 2, msgY + 28, GZTheme.COLOR_TEXT_SECONDARY, false);

        String desc2 = "Denna funktion kommer att aktiveras i en framtida uppdatering av GZ Companion.";
        int desc2W = font.width(desc2);
        extractor.text(font, desc2, centerX - desc2W / 2, msgY + 39, GZTheme.COLOR_TEXT_MUTED, false);

        int btnW = 120;
        int btnH = 18;
        int btnX = centerX - btnW / 2;
        int btnY = boxY + boxH - 26;

        boolean hov = mouseX >= btnX && mouseX < btnX + btnW && mouseY >= btnY && mouseY < btnY + btnH;
        GZTheme.drawButton(extractor, font, btnX, btnY, btnW, btnH, "< Tillbaka till Hem", true, hov);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button, int x, int y, int width, int height, GZCompanionMainScreen mainScreen) {
        if (button != 0) return false;
        int centerX = x + width / 2;
        int boxY = y + 34;
        int boxH = height - 42;
        int btnW = 120;
        int btnH = 18;
        int btnX = centerX - btnW / 2;
        int btnY = boxY + boxH - 26;

        if (mouseX >= btnX && mouseX < btnX + btnW && mouseY >= btnY && mouseY < btnY + btnH) {
            mainScreen.setActiveTab(TabType.HEM);
            return true;
        }
        return false;
    }

    private String getTabExplanation(TabType tab) {
        return switch (tab) {
            case GUIDE -> "Har hittar du interaktiva steg-for-steg-guider och framstegsmal for GameZoneMC.";
            case CRAFTING -> "Snabb och smidig receptoversikt anpassad efter serverns unika foremal.";
            case KISTOR -> "Hall koll pa dina kistor och forvaringar du legitimt har oppnat.";
            case SETTLEMENT -> "Hjalpmedel for att hantera claims, stader och medlemmar pa GameZoneMC.";
            case BYGGPLANER -> "Planera dina byggen och berakna materialkostnader enkelt.";
            case MARKETWATCH -> "Overvaka priser och trender pa serverns handelsmarknad.";
            case KOMMANDON -> "Snabbguide och genvagar till alla vanliga och anvandbara serverkommandon.";
            case INSTALLNINGAR -> "Anpassa utseende, tangentbindningar och installningar for GZ Companion.";
            default -> "Information om denna funktion kommer snart.";
        };
    }
}