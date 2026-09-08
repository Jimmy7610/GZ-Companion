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
 * Renders the Home ("Hem") tab following docs/design/GZ-COMPANION-UI-REFERENCE.png.
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

        int gap = 8;
        int leftColW = (int) (width * 0.58f);
        int rightColW = width - leftColW - gap;

        // 1. TOP ROW: Welcome Card & Server Status Card
        int topRowH = 68;

        // Left: Welcome Card
        GZTheme.drawCard(extractor, x, y, leftColW, topRowH, GZTheme.COLOR_CARD_BG, GZTheme.COLOR_BORDER_SUBTLE);
        extractor.fill(x + 2, y + 2, x + 5, y + topRowH - 2, 0xFF10B981);
        
        extractor.fill(x + 12, y + 10, x + 48, y + 46, 0x661E293B);
        extractor.fill(x + 12, y + 10, x + 48, y + 11, 0x66475569);
        extractor.fill(x + 12, y + 45, x + 48, y + 46, 0x66475569);
        extractor.fill(x + 12, y + 10, x + 13, y + 46, 0x66475569);
        extractor.fill(x + 47, y + 10, x + 48, y + 46, 0x66475569);
        extractor.text(font, "👤", x + 24, y + 22, GZTheme.COLOR_MINT, false);

        extractor.text(font, "V\u00E4lkommen,", x + 56, y + 10, GZTheme.COLOR_TEXT_SECONDARY, false);
        extractor.text(font, playerName, x + 56, y + 22, GZTheme.COLOR_MINT, true);
        extractor.text(font, "Kul att du \u00E4r h\u00E4r! GZ Companion hj\u00E4lper dig f\u00E5 ut mer.", x + 56, y + 36, GZTheme.COLOR_TEXT_MUTED, false);
        extractor.text(font, "All data h\u00E5lls lokalt & s\u00E4kert f\u00F6r schysst spel.", x + 56, y + 48, GZTheme.COLOR_TEXT_MUTED, false);

        // Right: Server Status Card
        int rightX = x + leftColW + gap;
        GZTheme.drawCard(extractor, rightX, y, rightColW, topRowH, GZTheme.COLOR_CARD_BG, GZTheme.COLOR_BORDER_SUBTLE);
        
        extractor.text(font, "📶 Serverstatus", rightX + 10, y + 10, GZTheme.COLOR_TEXT_PRIMARY, false);
        int badgeX = rightX + rightColW - 65;
        GZTheme.drawBadge(extractor, font, badgeX, y + 8, isGameZone ? "Online" : "Lokal",
                isGameZone ? GZTheme.COLOR_STATUS_GREEN : GZTheme.COLOR_STATUS_YELLOW,
                isGameZone ? GZTheme.COLOR_STATUS_GREEN : GZTheme.COLOR_STATUS_YELLOW);

        extractor.text(font, "Serverprofil:", rightX + 10, y + 28, GZTheme.COLOR_TEXT_SECONDARY, false);
        extractor.text(font, isGameZone ? "GameZoneMC" : "Frist\u00E5ende", rightX + 80, y + 28, GZTheme.COLOR_TEXT_PRIMARY, false);

        extractor.text(font, "Anslutning:", rightX + 10, y + 40, GZTheme.COLOR_TEXT_SECONDARY, false);
        if (isGameZone) {
            extractor.text(font, "\u25CF GameZone ansluten", rightX + 80, y + 40, GZTheme.COLOR_STATUS_GREEN, false);
        } else {
            extractor.text(font, "\u25CB Inte ansluten", rightX + 80, y + 40, GZTheme.COLOR_TEXT_MUTED, false);
        }

        extractor.text(font, "Server:", rightX + 10, y + 52, GZTheme.COLOR_TEXT_SECONDARY, false);
        extractor.text(font, serverAddr, rightX + 80, y + 52, GZTheme.COLOR_TEXT_MUTED, false);

        // 2. MIDDLE ROW: Versions & Compatibility Bar
        int midY = y + topRowH + gap;
        int midH = 48;
        GZTheme.drawCard(extractor, x, midY, width, midH, GZTheme.COLOR_CARD_BG, GZTheme.COLOR_BORDER_SUBTLE);
        extractor.text(font, "\u2726 Versioner och kompatibilitet", x + 10, midY + 6, GZTheme.COLOR_TEXT_PRIMARY, false);

        int badgeAreaY = midY + 20;
        int badgeW = (width - 20 - (gap * 3)) / 4;

        drawMiniBadge(extractor, font, x + 10, badgeAreaY, badgeW, 20, "Minecraft:", CompanionConstants.TARGET_MINECRAFT_VERSION, 0xFFFFFFFF);
        drawMiniBadge(extractor, font, x + 10 + (badgeW + gap), badgeAreaY, badgeW, 20, "GZ Companion:", CompanionConstants.getModVersion(), GZTheme.COLOR_MINT);
        drawMiniBadge(extractor, font, x + 10 + (badgeW + gap) * 2, badgeAreaY, badgeW, 20, "Rule Pack:", packVersion, 0xFFFFFFFF);
        drawMiniBadge(extractor, font, x + 10 + (badgeW + gap) * 3, badgeAreaY, badgeW, 20, "Kompatibilitet:", compat.overallStatus().getDisplayName(), compat.overallStatus().getRgbColor());

        // 3. BOTTOM ROW: Next Objective Card & Module Status Card
        int botY = midY + midH + gap;
        int botH = height - (botY - y);
        int botLeftW = (int) (width * 0.63f);
        int botRightW = width - botLeftW - gap;

        // Bottom Left: Next Objective
        GZTheme.drawCard(extractor, x, botY, botLeftW, botH, GZTheme.COLOR_CARD_BG, GZTheme.COLOR_BORDER_SUBTLE);
        extractor.text(font, "\uD83C\uDFAF N\u00E4sta uppgift", x + 12, botY + 8, GZTheme.COLOR_MINT, true);
        extractor.text(font, "\u00D6ppna guiden f\u00F6r att b\u00F6rja", x + 12, botY + 22, GZTheme.COLOR_TEXT_PRIMARY, true);
        extractor.text(font, "F\u00E5 hj\u00E4lp, l\u00E4r dig grunderna och kom ig\u00E5ng med din resa.", x + 12, botY + 34, GZTheme.COLOR_TEXT_SECONDARY, false);

        int chkY = botY + 48;
        drawCheckItem(extractor, font, x + 14, chkY, "\u25CB \u00D6ppna guiden f\u00F6r nyb\u00F6rjare");
        drawCheckItem(extractor, font, x + 14, chkY + 13, "\u25CB L\u00E4r dig grundl\u00E4ggande kommandon");
        drawCheckItem(extractor, font, x + 14, chkY + 26, "\u25CB Utforska s\u00E4kra zoner och skydd");
        drawCheckItem(extractor, font, x + 14, chkY + 39, "\u25CB Bli en del av gemenskapen");

        // Action Buttons Row
        int btnY = botY + botH - 28;
        int btnW = 95;
        boolean hov1 = isHovered(mouseX, mouseY, x + 12, btnY, btnW, 20);
        extractor.fill(x + 12, btnY, x + 12 + btnW, btnY + 20, hov1 ? 0xFF059669 : 0xFF10B981);
        extractor.text(font, "\uD83D\uDCD6 \u00D6ppna Guide \u2192", x + 16, btnY + 6, 0xFF051B11, true);

        boolean hov2 = isHovered(mouseX, mouseY, x + 12 + btnW + 6, btnY, btnW + 15, 20);
        extractor.fill(x + 12 + btnW + 6, btnY, x + 12 + btnW + 6 + btnW + 15, btnY + 20, hov2 ? 0xCC1E3142 : 0x991E293B);
        extractor.fill(x + 12 + btnW + 6, btnY, x + 12 + btnW + 6 + btnW + 15, btnY + 1, 0x4064748B);
        extractor.text(font, "\uD83D\uDCA1 Vad g\u00F6ra?", x + 12 + btnW + 14, btnY + 6, GZTheme.COLOR_TEXT_PRIMARY, false);

        boolean hov3 = isHovered(mouseX, mouseY, x + 12 + (btnW * 2) + 26, btnY, btnW + 5, 20);
        extractor.fill(x + 12 + (btnW * 2) + 26, btnY, x + 12 + (btnW * 2) + 26 + btnW + 5, btnY + 20, hov3 ? 0xCC1E3142 : 0x991E293B);
        extractor.fill(x + 12 + (btnW * 2) + 26, btnY, x + 12 + (btnW * 2) + 26 + btnW + 5, btnY + 1, 0x4064748B);
        extractor.text(font, "\uD83D\uDEE1 Kompatibilitet", x + 12 + (btnW * 2) + 32, btnY + 6, GZTheme.COLOR_TEXT_PRIMARY, false);

        // Bottom Right: Module Status (Dynamically queried from FeatureManager)
        int botRightX = x + botLeftW + gap;
        GZTheme.drawCard(extractor, botRightX, botY, botRightW, botH, GZTheme.COLOR_CARD_BG, GZTheme.COLOR_BORDER_SUBTLE);
        extractor.text(font, "\uD83E\uDDE9 Modulstatus", botRightX + 10, botY + 8, GZTheme.COLOR_TEXT_PRIMARY, true);

        int modY = botY + 24;
        int rowH = 12;
        TabType[] trackedTabs = {
            TabType.HEM, TabType.GUIDE, TabType.CRAFTING, TabType.KISTOR,
            TabType.SETTLEMENT, TabType.BYGGPLANER, TabType.MARKETWATCH,
            TabType.KOMMANDON, TabType.INSTALLNINGAR
        };

        for (int i = 0; i < trackedTabs.length; i++) {
            TabType tab = trackedTabs[i];
            ModuleStatus status = featureManager.getModuleStatus(tab);
            drawModuleRow(extractor, font, botRightX + 10, modY + (rowH * i), tab.getDisplayName(), status.getDisplayName(), status.getRgbColor());
        }

        if (feedbackMessage != null && System.currentTimeMillis() < feedbackExpiry) {
            int msgW = font.width(feedbackMessage) + 16;
            int toastX = x + (width - msgW) / 2;
            int toastY = y + height - 34;
            extractor.fill(toastX, toastY, toastX + msgW, toastY + 18, 0xEE0B1318);
            extractor.fill(toastX, toastY, toastX + msgW, toastY + 1, 0xFF10B981);
            extractor.fill(toastX, toastY + 17, toastX + msgW, toastY + 18, 0xFF10B981);
            extractor.text(font, feedbackMessage, toastX + 8, toastY + 5, GZTheme.COLOR_MINT, false);
        }
    }

    private void drawMiniBadge(GuiGraphicsExtractor extractor, Font font, int bx, int by, int bw, int bh, String title, String val, int valColor) {
        extractor.fill(bx, by, bx + bw, by + bh, 0x4D0F172A);
        extractor.fill(bx, by, bx + bw, by + 1, 0x33475569);
        extractor.fill(bx, by + bh - 1, bx + bw, by + bh, 0x33475569);
        extractor.fill(bx, by, bx + 1, by + bh, 0x33475569);
        extractor.fill(bx + bw - 1, by, bx + bw, by + bh, 0x33475569);

        extractor.text(font, title, bx + 4, by + 3, GZTheme.COLOR_TEXT_SECONDARY, false);
        extractor.text(font, val, bx + 4, by + 11, valColor, false);
    }

    private void drawCheckItem(GuiGraphicsExtractor extractor, Font font, int cx, int cy, String text) {
        extractor.text(font, text, cx, cy, GZTheme.COLOR_TEXT_SECONDARY, false);
    }

    private void drawModuleRow(GuiGraphicsExtractor extractor, Font font, int mx, int my, String modName, String statusName, int dotColor) {
        extractor.fill(mx, my + 3, mx + 4, my + 7, 0xFF000000 | dotColor);
        extractor.text(font, modName, mx + 8, my, GZTheme.COLOR_TEXT_PRIMARY, false);
        int statusW = font.width(statusName);
        extractor.text(font, statusName, mx + 120 - statusW, my, dotColor == 0x22C55E ? GZTheme.COLOR_TEXT_SECONDARY : GZTheme.COLOR_TEXT_MUTED, false);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button, int x, int y, int width, int height, GZCompanionMainScreen mainScreen) {
        if (button != 0) return false;

        int botY = y + 68 + 8 + 48 + 8;
        int botH = height - (botY - y);
        int btnY = botY + botH - 28;
        int btnW = 95;

        if (isHovered((int) mouseX, (int) mouseY, x + 12, btnY, btnW, 20)) {
            mainScreen.setActiveTab(TabType.GUIDE);
            return true;
        }

        if (isHovered((int) mouseX, (int) mouseY, x + 12 + btnW + 6, btnY, btnW + 15, 20)) {
            showToast("R\u00E5dgivaren \u00E4r aktiv! Kolla 'Guide' f\u00F6r n\u00E4sta steg.", 3000);
            return true;
        }

        if (isHovered((int) mouseX, (int) mouseY, x + 12 + (btnW * 2) + 26, btnY, btnW + 5, 20)) {
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