package se.jimmyeliasson.gzcompanion.ui.tabs;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import se.jimmyeliasson.gzcompanion.core.CompanionConstants;
import se.jimmyeliasson.gzcompanion.core.CompanionSession;
import se.jimmyeliasson.gzcompanion.core.feature.FeatureManager;
import se.jimmyeliasson.gzcompanion.core.feature.ModuleStatus;
import se.jimmyeliasson.gzcompanion.diagnostics.CompatibilityResult;
import se.jimmyeliasson.gzcompanion.ui.GZCompanionMainScreen;
import se.jimmyeliasson.gzcompanion.ui.GZTheme;
import se.jimmyeliasson.gzcompanion.ui.TabType;

/**
 * Renders the compact, high-contrast Home ("Hem") tab following docs/design/GZ-COMPANION-UI-REFERENCE.png.
 */
public class HomeTabComponent {
    private String feedbackMessage = null;
    private long feedbackExpiry = 0;

    public void render(GuiGraphicsExtractor extractor, Font font, int x, int y, int width, int height, int mouseX, int mouseY, GZCompanionMainScreen mainScreen) {
        CompanionSession session = CompanionSession.getInstance();
        String playerName = session.getBridge().getPlayerName();
        boolean isGameZone = session.getBridge().isConnectedToGameZone();
        String serverAddr = session.getBridge().getCurrentServerAddress().orElse("Inte ansluten");
        CompatibilityResult compat = session.getCompatibilityResult();
        String packVersion = session.getActiveRulePack() != null ? session.getActiveRulePack().manifest().packVersion() : "1.0.0";
        FeatureManager featureManager = session.getFeatureManager();

        int gap = 6;
        int leftColW = (int) (width * 0.56f);
        int rightColW = width - leftColW - gap;

        // 1. TOP ROW: Welcome Card & Server Status Card (Height 58px)
        int topRowH = 58;

        // Left: Welcome Card
        GZTheme.drawCard(extractor, x, y, leftColW, topRowH, GZTheme.COLOR_CARD_BG, GZTheme.COLOR_BORDER_SUBTLE);
        // Emerald left accent strip
        extractor.fill(x + 2, y + 2, x + 4, y + topRowH - 2, GZTheme.COLOR_EMERALD);

        // Player Head Avatar Placeholder (Recessed Square)
        int headX = x + 10;
        int headY = y + 8;
        int headSize = 36;
        extractor.fill(headX, headY, headX + headSize, headY + headSize, 0x990A1017);
        extractor.fill(headX, headY, headX + headSize, headY + 1, 0x40475569);
        extractor.fill(headX, headY + headSize - 1, headX + headSize, headY + headSize, 0x40475569);
        extractor.fill(headX, headY, headX + 1, headY + headSize, 0x40475569);
        extractor.fill(headX + headSize - 1, headY, headX + headSize, headY + headSize, 0x40475569);
        extractor.text(font, "@", headX + 13, headY + 14, GZTheme.COLOR_MINT, true);

        // Welcome Text
        int textLeft = headX + headSize + 8;
        extractor.text(font, "V\u00E4lkommen,", textLeft, y + 8, GZTheme.COLOR_TEXT_SECONDARY, false);
        extractor.text(font, playerName, textLeft, y + 18, GZTheme.COLOR_MINT, true);
        extractor.text(font, "Kul att du \u00E4r h\u00E4r! GZ Companion hj\u00E4lper dig.", textLeft, y + 30, GZTheme.COLOR_TEXT_SECONDARY, false);
        extractor.text(font, "All data h\u00E5lls lokalt f\u00F6r schysst spel.", textLeft, y + 41, GZTheme.COLOR_TEXT_MUTED, false);

        // Right: Server Status Card
        int rightX = x + leftColW + gap;
        GZTheme.drawCard(extractor, rightX, y, rightColW, topRowH, GZTheme.COLOR_CARD_BG, GZTheme.COLOR_BORDER_SUBTLE);

        extractor.text(font, "Serverstatus", rightX + 8, y + 7, GZTheme.COLOR_TEXT_PRIMARY, true);
        int badgeX = rightX + rightColW - 56;
        GZTheme.drawBadge(extractor, font, badgeX, y + 5, isGameZone ? "Online" : "Lokal",
                isGameZone ? GZTheme.COLOR_STATUS_GREEN : GZTheme.COLOR_STATUS_YELLOW,
                isGameZone ? GZTheme.COLOR_STATUS_GREEN : GZTheme.COLOR_STATUS_YELLOW);

        extractor.text(font, "Profil:", rightX + 8, y + 21, GZTheme.COLOR_TEXT_SECONDARY, false);
        extractor.text(font, isGameZone ? "GameZoneMC" : "Frist\u00E5ende", rightX + 50, y + 21, GZTheme.COLOR_TEXT_PRIMARY, false);

        extractor.text(font, "Status:", rightX + 8, y + 32, GZTheme.COLOR_TEXT_SECONDARY, false);
        if (isGameZone) {
            GZTheme.drawStatusDot(extractor, rightX + 50, y + 35, GZTheme.COLOR_STATUS_GREEN);
            extractor.text(font, "Ansluten", rightX + 57, y + 32, GZTheme.COLOR_STATUS_GREEN, false);
        } else {
            GZTheme.drawStatusDot(extractor, rightX + 50, y + 35, GZTheme.COLOR_STATUS_GREY);
            extractor.text(font, "Ej ansluten", rightX + 57, y + 32, GZTheme.COLOR_TEXT_MUTED, false);
        }

        extractor.text(font, "Host:", rightX + 8, y + 43, GZTheme.COLOR_TEXT_SECONDARY, false);
        extractor.text(font, serverAddr, rightX + 50, y + 43, GZTheme.COLOR_TEXT_MUTED, false);

        // 2. MIDDLE ROW: Versions & Compatibility Strip (Height 36px)
        int midY = y + topRowH + gap;
        int midH = 36;
        GZTheme.drawCard(extractor, x, midY, width, midH, GZTheme.COLOR_CARD_BG, GZTheme.COLOR_BORDER_SUBTLE);

        int badgeW = (width - 16 - (gap * 3)) / 4;
        int badgeH = 24;
        int bY = midY + 6;

        drawMiniBadge(extractor, font, x + 8, bY, badgeW, badgeH, "Minecraft", CompanionConstants.TARGET_MINECRAFT_VERSION, GZTheme.COLOR_TEXT_PRIMARY);
        drawMiniBadge(extractor, font, x + 8 + (badgeW + gap), bY, badgeW, badgeH, "GZ Companion", CompanionConstants.getModVersion(), GZTheme.COLOR_MINT);
        drawMiniBadge(extractor, font, x + 8 + (badgeW + gap) * 2, bY, badgeW, badgeH, "Rule Pack", packVersion, GZTheme.COLOR_TEXT_PRIMARY);
        drawMiniBadge(extractor, font, x + 8 + (badgeW + gap) * 3, bY, badgeW, badgeH, "Kompatibilitet", compat.overallStatus().getDisplayName(), compat.overallStatus().getArgbColor());

        // 3. BOTTOM ROW: Next Objective Card & Module Status Card
        int botY = midY + midH + gap;
        int botH = height - (botY - y);
        int botLeftW = (int) (width * 0.60f);
        int botRightW = width - botLeftW - gap;

        // Bottom Left: Next Objective
        GZTheme.drawCard(extractor, x, botY, botLeftW, botH, GZTheme.COLOR_CARD_BG, GZTheme.COLOR_BORDER_SUBTLE);
        extractor.text(font, "N\u00E4sta uppgift", x + 10, botY + 7, GZTheme.COLOR_MINT, true);
        extractor.text(font, "\u00D6ppna guiden f\u00F6r att b\u00F6rja", x + 10, botY + 18, GZTheme.COLOR_TEXT_PRIMARY, true);
        extractor.text(font, "F\u00E5 hj\u00E4lp och l\u00E4r dig grunderna p\u00E5 servern.", x + 10, botY + 29, GZTheme.COLOR_TEXT_SECONDARY, false);

        int chkY = botY + 41;
        drawCheckItem(extractor, font, x + 10, chkY, "[ ] \u00D6ppna guiden f\u00F6r nyb\u00F6rjare");
        drawCheckItem(extractor, font, x + 10, chkY + 10, "[ ] L\u00E4r dig grundl\u00E4ggande funktioner");
        drawCheckItem(extractor, font, x + 10, chkY + 20, "[ ] Utforska s\u00E4kra zoner och skydd");

        // Action Buttons Row (Primary + Secondary Actions)
        int btnY = botY + botH - 24;
        int btn1W = 88;
        int btn2W = 80;
        int btn3W = 84;
        boolean hov1 = isHovered(mouseX, mouseY, x + 10, btnY, btn1W, 18);
        GZTheme.drawButton(extractor, font, x + 10, btnY, btn1W, 18, "> \u00D6ppna Guide", true, hov1);

        boolean hov2 = isHovered(mouseX, mouseY, x + 10 + btn1W + 4, btnY, btn2W, 18);
        GZTheme.drawButton(extractor, font, x + 10 + btn1W + 4, btnY, btn2W, 18, "Vad g\u00F6ra?", false, hov2);

        boolean hov3 = isHovered(mouseX, mouseY, x + 10 + btn1W + 4 + btn2W + 4, btnY, btn3W, 18);
        GZTheme.drawButton(extractor, font, x + 10 + btn1W + 4 + btn2W + 4, btnY, btn3W, 18, "Kompatibilitet", false, hov3);

        // Bottom Right: Module Status
        int botRightX = x + botLeftW + gap;
        GZTheme.drawCard(extractor, botRightX, botY, botRightW, botH, GZTheme.COLOR_CARD_BG, GZTheme.COLOR_BORDER_SUBTLE);
        extractor.text(font, "Modulstatus", botRightX + 8, botY + 7, GZTheme.COLOR_TEXT_PRIMARY, true);

        int modY = botY + 20;
        int rowH = 10;
        TabType[] trackedTabs = {
            TabType.HEM, TabType.GUIDE, TabType.CRAFTING, TabType.KISTOR,
            TabType.SETTLEMENT, TabType.BYGGPLANER, TabType.MARKETWATCH,
            TabType.KOMMANDON, TabType.INSTALLNINGAR
        };

        for (int i = 0; i < trackedTabs.length; i++) {
            TabType tab = trackedTabs[i];
            ModuleStatus status = featureManager.getModuleStatus(tab);
            drawModuleRow(extractor, font, botRightX + 8, modY + (rowH * i), tab.getDisplayName(), status.getDisplayName(), status.getRgbColor());
        }

        // Optional Toast Notification
        if (feedbackMessage != null && System.currentTimeMillis() < feedbackExpiry) {
            int msgW = font.width(feedbackMessage) + 16;
            int toastX = x + (width - msgW) / 2;
            int toastY = y + height - 28;
            extractor.fill(toastX, toastY, toastX + msgW, toastY + 18, 0xF00B1318);
            extractor.fill(toastX, toastY, toastX + msgW, toastY + 1, GZTheme.COLOR_EMERALD);
            extractor.fill(toastX, toastY + 17, toastX + msgW, toastY + 18, GZTheme.COLOR_EMERALD);
            extractor.text(font, feedbackMessage, toastX + 8, toastY + 5, GZTheme.COLOR_MINT, false);
        }
    }

    private void drawMiniBadge(GuiGraphicsExtractor extractor, Font font, int bx, int by, int bw, int bh, String title, String val, int valArgb) {
        extractor.fill(bx, by, bx + bw, by + bh, 0x800A1017);
        extractor.fill(bx, by, bx + bw, by + 1, 0x33475569);
        extractor.fill(bx, by + bh - 1, bx + bw, by + bh, 0x33475569);
        extractor.fill(bx, by, bx + 1, by + bh, 0x33475569);
        extractor.fill(bx + bw - 1, by, bx + bw, by + bh, 0x33475569);

        extractor.text(font, title, bx + 4, by + 3, GZTheme.COLOR_TEXT_SECONDARY, false);
        extractor.text(font, val, bx + 4, by + 12, GZTheme.opaque(valArgb), false);
    }

    private void drawCheckItem(GuiGraphicsExtractor extractor, Font font, int cx, int cy, String text) {
        extractor.text(font, text, cx, cy, GZTheme.COLOR_TEXT_SECONDARY, false);
    }

    private void drawModuleRow(GuiGraphicsExtractor extractor, Font font, int mx, int my, String modName, String statusName, int dotColor) {
        GZTheme.drawStatusDot(extractor, mx, my + 2, dotColor);
        extractor.text(font, modName, mx + 8, my, GZTheme.COLOR_TEXT_PRIMARY, false);
        int statusW = font.width(statusName);
        int rightAlignX = mx + 104 - statusW;
        extractor.text(font, statusName, rightAlignX, my, (dotColor == 0x22C55E || dotColor == 0xFF22C55E) ? GZTheme.COLOR_STATUS_GREEN : GZTheme.COLOR_TEXT_MUTED, false);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button, int x, int y, int width, int height, GZCompanionMainScreen mainScreen) {
        if (button != 0) return false;

        int topRowH = 58;
        int midH = 36;
        int gap = 6;
        int botY = y + topRowH + gap + midH + gap;
        int botH = height - (botY - y);
        int btnY = botY + botH - 24;
        int btn1W = 88;
        int btn2W = 80;
        int btn3W = 84;

        if (isHovered((int) mouseX, (int) mouseY, x + 10, btnY, btn1W, 18)) {
            mainScreen.setActiveTab(TabType.GUIDE);
            return true;
        }

        if (isHovered((int) mouseX, (int) mouseY, x + 10 + btn1W + 4, btnY, btn2W, 18)) {
            showToast("R\u00E5dgivaren \u00E4r aktiv! Kolla 'Guide' f\u00F6r n\u00E4sta steg.", 3000);
            return true;
        }

        if (isHovered((int) mouseX, (int) mouseY, x + 10 + btn1W + 4 + btn2W + 4, btnY, btn3W, 18)) {
            CompatibilityResult res = CompanionSession.getInstance().getCompatibilityResult();
            showToast("Kompatibilitet: " + res.overallStatus().getDisplayName() + " (" + res.summaryMessage() + ")", 4000);
            return true;
        }

        return false;
    }

    private void showToast(String message, long durationMs) {
        this.feedbackMessage = message;
        this.feedbackExpiry = System.currentTimeMillis() + durationMs;
    }

    private boolean isHovered(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
}