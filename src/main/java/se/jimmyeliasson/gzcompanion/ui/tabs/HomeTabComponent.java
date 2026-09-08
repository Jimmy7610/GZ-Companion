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
import se.jimmyeliasson.gzcompanion.ui.IconId;
import se.jimmyeliasson.gzcompanion.ui.TabType;
import se.jimmyeliasson.gzcompanion.ui.layout.HomeTabLayout;
import se.jimmyeliasson.gzcompanion.ui.layout.TextUtil;
import se.jimmyeliasson.gzcompanion.ui.layout.UiRect;

/**
 * Renders the compact, polished Home ("Hem") tab following docs/design/GZ-COMPANION-UI-REFERENCE.png.
 */
public class HomeTabComponent {
    private String feedbackMessage = null;
    private long feedbackExpiry = 0;

    private HomeTabLayout layout;

    public HomeTabLayout getLayout() {
        return layout;
    }

    public void calculateLayout(UiRect bounds) {
        this.layout = HomeTabLayout.calculate(bounds);
    }

    public void render(GuiGraphicsExtractor extractor, Font font, UiRect bounds, int mouseX, int mouseY, GZCompanionMainScreen mainScreen) {
        calculateLayout(bounds);

        CompanionSession session = CompanionSession.getInstance();
        String playerName = session.getBridge().getPlayerName();
        boolean isGameZone = session.getBridge().isConnectedToGameZone();
        String serverAddr = session.getBridge().getCurrentServerAddress().orElse("Lokal värld");
        CompatibilityResult compat = session.getCompatibilityResult();
        String packVersion = session.getActiveRulePack() != null ? session.getActiveRulePack().manifest().packVersion() : "1.0.0";
        FeatureManager featureManager = session.getFeatureManager();

        UiRect welcomeRect = layout.welcomeRect();
        UiRect serverRect = layout.serverRect();
        UiRect versionRect = layout.versionRect();
        UiRect objectiveRect = layout.objectiveRect();
        UiRect moduleRect = layout.moduleRect();
        UiRect primaryBtnRect = layout.primaryButtonRect();
        UiRect secondaryBtn1Rect = layout.secondaryButton1Rect();
        UiRect secondaryBtn2Rect = layout.secondaryButton2Rect();

        // 1. WELCOME CARD
        GZTheme.drawCard(extractor, welcomeRect, GZTheme.COLOR_CARD_BG, GZTheme.COLOR_BORDER_SUBTLE);
        extractor.fill(welcomeRect.x() + 2, welcomeRect.y() + 2, welcomeRect.x() + 4, welcomeRect.bottom() - 2, GZTheme.COLOR_EMERALD);

        int avatarSize = Math.max(16, welcomeRect.height() - 12);
        int avatarX = welcomeRect.x() + 7;
        int avatarY = welcomeRect.y() + 6;
        GZTheme.drawCard(extractor, avatarX, avatarY, avatarSize, avatarSize, GZTheme.COLOR_CARD_INNER, GZTheme.COLOR_BORDER_SUBTLE);
        GZTheme.drawIcon(extractor, IconId.PLAYER, avatarX + ((avatarSize - 12) / 2), avatarY + ((avatarSize - 12) / 2), 12, GZTheme.COLOR_MINT);

        int textX = avatarX + avatarSize + 6;
        int maxWelcomeTextW = welcomeRect.right() - textX - 5;
        extractor.text(font, "Välkommen,", textX, welcomeRect.y() + 5, GZTheme.COLOR_TEXT_SECONDARY, false);
        TextUtil.drawEllipsizedText(extractor, font, playerName, textX, welcomeRect.y() + 14, maxWelcomeTextW, GZTheme.COLOR_MINT, true);
        TextUtil.drawEllipsizedText(extractor, font, "GZ Companion hjälper dig.", textX, welcomeRect.y() + 24, maxWelcomeTextW, GZTheme.COLOR_TEXT_SECONDARY, false);
        TextUtil.drawEllipsizedText(extractor, font, "Allt sparas lokalt.", textX, welcomeRect.y() + 33, maxWelcomeTextW, GZTheme.COLOR_TEXT_MUTED, false);

        // 2. SERVER STATUS CARD
        GZTheme.drawCard(extractor, serverRect, GZTheme.COLOR_CARD_BG, GZTheme.COLOR_BORDER_SUBTLE);
        int sPad = 5;
        TextUtil.drawEllipsizedText(extractor, font, "Serverstatus", serverRect.x() + sPad, serverRect.y() + 4, serverRect.width() - 44, GZTheme.COLOR_TEXT_PRIMARY, true);
        GZTheme.drawBadge(extractor, font, serverRect.right() - 44, serverRect.y() + 3, isGameZone ? "Online" : "Lokal",
                isGameZone ? GZTheme.COLOR_STATUS_GREEN : GZTheme.COLOR_STATUS_YELLOW,
                isGameZone ? GZTheme.COLOR_STATUS_GREEN : GZTheme.COLOR_STATUS_YELLOW);

        int row1Y = serverRect.y() + 15;
        int row2Y = serverRect.y() + 24;
        int row3Y = serverRect.y() + 33;
        int sValX = serverRect.x() + 38;
        int sValW = serverRect.right() - sValX - sPad;

        extractor.text(font, "Profil:", serverRect.x() + sPad, row1Y, GZTheme.COLOR_TEXT_SECONDARY, false);
        TextUtil.drawEllipsizedText(extractor, font, isGameZone ? "GameZoneMC" : "Fristående", sValX, row1Y, sValW, GZTheme.COLOR_TEXT_PRIMARY, false);

        extractor.text(font, "Status:", serverRect.x() + sPad, row2Y, GZTheme.COLOR_TEXT_SECONDARY, false);
        GZTheme.drawStatusDot(extractor, sValX, row2Y + 2, isGameZone ? GZTheme.COLOR_STATUS_GREEN : GZTheme.COLOR_STATUS_GREY);
        TextUtil.drawEllipsizedText(extractor, font, isGameZone ? "Ansluten" : "Ej ansluten", sValX + 6, row2Y, sValW - 6,
                isGameZone ? GZTheme.COLOR_STATUS_GREEN : GZTheme.COLOR_TEXT_MUTED, false);

        extractor.text(font, "Server:", serverRect.x() + sPad, row3Y, GZTheme.COLOR_TEXT_SECONDARY, false);
        TextUtil.drawEllipsizedText(extractor, font, serverAddr, sValX, row3Y, sValW, GZTheme.COLOR_TEXT_MUTED, false);

        // 3. VERSION STRIP
        GZTheme.drawCard(extractor, versionRect, GZTheme.COLOR_CARD_BG, GZTheme.COLOR_BORDER_SUBTLE);
        int vPad = 3;
        int vCardW = (versionRect.width() - (vPad * 5)) / 4;
        int vCardH = versionRect.height() - (vPad * 2);

        drawMiniBadge(extractor, font, versionRect.x() + vPad, versionRect.y() + vPad, vCardW, vCardH, "Minecraft", CompanionConstants.TARGET_MINECRAFT_VERSION, GZTheme.COLOR_TEXT_PRIMARY);
        drawMiniBadge(extractor, font, versionRect.x() + vPad + (vCardW + vPad), versionRect.y() + vPad, vCardW, vCardH, "Companion", CompanionConstants.getModVersion(), GZTheme.COLOR_MINT);
        drawMiniBadge(extractor, font, versionRect.x() + vPad + (vCardW + vPad) * 2, versionRect.y() + vPad, vCardW, vCardH, "Rule Pack", packVersion, GZTheme.COLOR_TEXT_PRIMARY);
        drawMiniBadge(extractor, font, versionRect.x() + vPad + (vCardW + vPad) * 3, versionRect.y() + vPad, vCardW, vCardH, "Kompatibilitet", compat.overallStatus().getDisplayName(), compat.overallStatus().getArgbColor());

        // 4. NEXT OBJECTIVE CARD
        GZTheme.drawCard(extractor, objectiveRect, GZTheme.COLOR_CARD_BG, GZTheme.COLOR_BORDER_SUBTLE);
        int oPad = 5;
        int maxObjW = objectiveRect.width() - (oPad * 2);

        GZTheme.drawIcon(extractor, IconId.OBJECTIVE, objectiveRect.x() + oPad, objectiveRect.y() + 5, 9, GZTheme.COLOR_MINT);
        extractor.text(font, "Nästa uppgift", objectiveRect.x() + oPad + 13, objectiveRect.y() + 5, GZTheme.COLOR_MINT, true);
        TextUtil.drawEllipsizedText(extractor, font, "Öppna guiden för att börja", objectiveRect.x() + oPad, objectiveRect.y() + 15, maxObjW, GZTheme.COLOR_TEXT_PRIMARY, true);
        TextUtil.drawEllipsizedText(extractor, font, "Lär dig grunderna steg för steg.", objectiveRect.x() + oPad, objectiveRect.y() + 24, maxObjW, GZTheme.COLOR_TEXT_SECONDARY, false);

        int chkY = objectiveRect.y() + 34;
        int chkSpacing = 8;
        drawCheckItem(extractor, font, objectiveRect.x() + oPad, chkY, "[ ] Öppna guiden för nybörjare", maxObjW);
        drawCheckItem(extractor, font, objectiveRect.x() + oPad, chkY + chkSpacing, "[ ] Lär dig grundläggande funktioner", maxObjW);
        drawCheckItem(extractor, font, objectiveRect.x() + oPad, chkY + (chkSpacing * 2), "[ ] Utforska säkra zoner och skydd", maxObjW);

        // Render Action Buttons (Row 1 Primary, Row 2 Secondaries)
        GZTheme.drawButton(extractor, font, primaryBtnRect, "Öppna Guide", true, primaryBtnRect.contains(mouseX, mouseY));
        GZTheme.drawButton(extractor, font, secondaryBtn1Rect, "Vad göra?", false, secondaryBtn1Rect.contains(mouseX, mouseY));
        GZTheme.drawButton(extractor, font, secondaryBtn2Rect, "Kompatibilitet", false, secondaryBtn2Rect.contains(mouseX, mouseY));

        // 5. MODULE STATUS CARD
        GZTheme.drawCard(extractor, moduleRect, GZTheme.COLOR_CARD_BG, GZTheme.COLOR_BORDER_SUBTLE);
        int mPad = 5;
        TextUtil.drawEllipsizedText(extractor, font, "Modulstatus", moduleRect.x() + mPad, moduleRect.y() + 4, moduleRect.width() - 10, GZTheme.COLOR_TEXT_PRIMARY, true);

        TabType[] trackedTabs = {
            TabType.HEM, TabType.GUIDE, TabType.CRAFTING, TabType.KISTOR,
            TabType.SETTLEMENT, TabType.BYGGPLANER, TabType.MARKETWATCH,
            TabType.KOMMANDON, TabType.INSTALLNINGAR
        };

        int availableRowsH = moduleRect.height() - 18;
        int rowH = Math.max(8, Math.min(10, availableRowsH / trackedTabs.length));
        int modStartY = moduleRect.y() + 15;

        for (int i = 0; i < trackedTabs.length; i++) {
            TabType tab = trackedTabs[i];
            ModuleStatus status = featureManager.getModuleStatus(tab);
            String compactStatus = (status == ModuleStatus.AVAILABLE) ? "Aktiv" : "Snart";
            drawModuleRow(extractor, font, moduleRect.x() + mPad, modStartY + (i * rowH), moduleRect.width() - (mPad * 2), tab.getDisplayName(), compactStatus, status.getRgbColor());
        }

        // Toast Notification
        if (feedbackMessage != null && System.currentTimeMillis() < feedbackExpiry) {
            int msgW = font.width(feedbackMessage) + 14;
            int toastX = bounds.x() + (bounds.width() - msgW) / 2;
            int toastY = bounds.bottom() - 20;
            extractor.fill(toastX, toastY, toastX + msgW, toastY + 14, 0xF00B1318);
            extractor.fill(toastX, toastY, toastX + msgW, toastY + 1, GZTheme.COLOR_EMERALD);
            extractor.fill(toastX, toastY + 13, toastX + msgW, toastY + 14, GZTheme.COLOR_EMERALD);
            extractor.text(font, feedbackMessage, toastX + 7, toastY + 3, GZTheme.COLOR_MINT, false);
        }
    }

    private void drawMiniBadge(GuiGraphicsExtractor extractor, Font font, int bx, int by, int bw, int bh, String title, String val, int valArgb) {
        GZTheme.drawCard(extractor, bx, by, bw, bh, GZTheme.COLOR_CARD_INNER, GZTheme.COLOR_BORDER_SUBTLE);
        TextUtil.drawEllipsizedText(extractor, font, title, bx + 3, by + 2, bw - 6, GZTheme.COLOR_TEXT_SECONDARY, false);
        TextUtil.drawEllipsizedText(extractor, font, val, bx + 3, by + 9, bw - 6, GZTheme.opaque(valArgb), false);
    }

    private void drawCheckItem(GuiGraphicsExtractor extractor, Font font, int cx, int cy, String text, int maxW) {
        TextUtil.drawEllipsizedText(extractor, font, text, cx, cy, maxW, GZTheme.COLOR_TEXT_SECONDARY, false);
    }

    private void drawModuleRow(GuiGraphicsExtractor extractor, Font font, int mx, int my, int rowW, String modName, String statusName, int dotColor) {
        GZTheme.drawStatusDot(extractor, mx, my + 2, dotColor);
        int statusW = font.width(statusName);
        int rightStatusX = mx + rowW;
        int maxModNameW = Math.max(10, rowW - statusW - 10);

        TextUtil.drawEllipsizedText(extractor, font, modName, mx + 6, my, maxModNameW, GZTheme.COLOR_TEXT_PRIMARY, false);
        TextUtil.drawRightAlignedText(extractor, font, statusName, rightStatusX, my, statusW + 2,
                (dotColor == 0x22C55E || dotColor == 0xFF22C55E) ? GZTheme.COLOR_STATUS_GREEN : GZTheme.COLOR_TEXT_MUTED, false);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button, UiRect bounds, GZCompanionMainScreen mainScreen) {
        if (button != 0) return false;
        calculateLayout(bounds);

        if (layout.primaryButtonRect().contains(mouseX, mouseY)) {
            mainScreen.setActiveTab(TabType.GUIDE);
            return true;
        }

        if (layout.secondaryButton1Rect().contains(mouseX, mouseY)) {
            showToast("Rådgivaren är aktiv! Kolla 'Guide' för nästa steg.", 3000);
            return true;
        }

        if (layout.secondaryButton2Rect().contains(mouseX, mouseY)) {
            CompatibilityResult res = CompanionSession.getInstance().getCompatibilityResult();
            showToast("Kompatibilitet: " + res.overallStatus().getDisplayName(), 3500);
            return true;
        }

        return false;
    }

    private void showToast(String message, long durationMs) {
        this.feedbackMessage = message;
        this.feedbackExpiry = System.currentTimeMillis() + durationMs;
    }
}