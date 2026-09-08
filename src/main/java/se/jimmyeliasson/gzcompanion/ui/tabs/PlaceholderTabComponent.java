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

        extractor.fill(x + 2, y + 2, x + 5, y + 36, 0xFF10B981);
        extractor.text(font, tab.getIconSymbol() + "  " + tab.getDisplayName(), x + 14, y + 10, GZTheme.COLOR_TEXT_PRIMARY, true);
        extractor.text(font, tab.getDescription(), x + 14, y + 23, GZTheme.COLOR_TEXT_SECONDARY, false);

        int boxY = y + 42;
        int boxH = height - 52;
        GZTheme.drawCard(extractor, x + 10, boxY, width - 20, boxH, 0x4D0F172A, 0x33475569);

        int centerX = x + width / 2;
        int msgY = boxY + 30;
        
        String icon = tab.getIconSymbol();
        int iconW = font.width(icon);
        extractor.text(font, icon, centerX - iconW / 2, msgY, GZTheme.COLOR_MINT, true);

        String title = tab.getDisplayName() + " \u2014 Modul under utveckling";
        int titleW = font.width(title);
        extractor.text(font, title, centerX - titleW / 2, msgY + 16, GZTheme.COLOR_TEXT_PRIMARY, true);

        String desc1 = getTabExplanation(tab);
        int desc1W = font.width(desc1);
        extractor.text(font, desc1, centerX - desc1W / 2, msgY + 32, GZTheme.COLOR_TEXT_SECONDARY, false);

        String desc2 = "Denna funktion kommer att aktiveras i en framtida uppdatering av GZ Companion.";
        int desc2W = font.width(desc2);
        extractor.text(font, desc2, centerX - desc2W / 2, msgY + 44, GZTheme.COLOR_TEXT_MUTED, false);

        int btnW = 120;
        int btnH = 20;
        int btnX = centerX - btnW / 2;
        int btnY = boxY + boxH - 34;

        boolean hov = mouseX >= btnX && mouseX < btnX + btnW && mouseY >= btnY && mouseY < btnY + btnH;
        extractor.fill(btnX, btnY, btnX + btnW, btnY + btnH, hov ? 0xFF059669 : 0xFF10B981);
        String btnText = "\u2302 Tillbaka till Hem";
        int btnTextW = font.width(btnText);
        extractor.text(font, btnText, btnX + (btnW - btnTextW) / 2, btnY + 6, 0xFF051B11, true);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button, int x, int y, int width, int height, GZCompanionMainScreen mainScreen) {
        if (button != 0) return false;
        int centerX = x + width / 2;
        int boxY = y + 42;
        int boxH = height - 52;
        int btnW = 120;
        int btnH = 20;
        int btnX = centerX - btnW / 2;
        int btnY = boxY + boxH - 34;

        if (mouseX >= btnX && mouseX < btnX + btnW && mouseY >= btnY && mouseY < btnY + btnH) {
            mainScreen.setActiveTab(TabType.HEM);
            return true;
        }
        return false;
    }

    private String getTabExplanation(TabType tab) {
        return switch (tab) {
            case GUIDE -> "H\u00E4r hittar du interaktiva steg-f\u00F6r-steg-guider och framstegsm\u00E5l f\u00F6r GameZoneMC.";
            case CRAFTING -> "Snabb och smidig recept\u00F6versikt anpassad efter serverns unika f\u00F6rem\u00E5l.";
            case KISTOR -> "H\u00E5ll koll p\u00E5 dina kistor och f\u00F6rvaringar du legitimt har \u00F6ppnat.";
            case SETTLEMENT -> "Hj\u00E4lpmedel f\u00F6r att hantera claims, st\u00E4der och medlemmar p\u00E5 GameZoneMC.";
            case BYGGPLANER -> "Planera dina byggen och ber\u00E4kna materialkostnader enkelt.";
            case MARKETWATCH -> "\u00D6vervaka priser och trender p\u00E5 serverns handelsmarknad.";
            case KOMMANDON -> "Snabbguide och genv\u00E4gar till alla vanliga och anv\u00E4ndbara serverkommandon.";
            case INSTALLNINGAR -> "Anpassa utseende, tangentbindningar och inst\u00E4llningar f\u00F6r GZ Companion.";
            default -> "Information om denna funktion kommer snart.";
        };
    }
}