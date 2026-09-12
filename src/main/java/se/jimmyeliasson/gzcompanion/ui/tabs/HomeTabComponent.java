package se.jimmyeliasson.gzcompanion.ui.tabs;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import se.jimmyeliasson.gzcompanion.advisor.AdvisorContextBuilder;
import se.jimmyeliasson.gzcompanion.advisor.AdvisorEngine;
import se.jimmyeliasson.gzcompanion.advisor.AdvisorSuggestion;
import se.jimmyeliasson.gzcompanion.core.CompanionConstants;
import se.jimmyeliasson.gzcompanion.core.CompanionSession;
import se.jimmyeliasson.gzcompanion.core.feature.FeatureManager;
import se.jimmyeliasson.gzcompanion.core.feature.ModuleStatus;
import se.jimmyeliasson.gzcompanion.diagnostics.CompatibilityResult;
import se.jimmyeliasson.gzcompanion.gamezone.status.GameZoneLiveStatus;
import se.jimmyeliasson.gzcompanion.gamezone.status.GameZoneStatusFormatter;
import se.jimmyeliasson.gzcompanion.guide.GuideEngine;
import se.jimmyeliasson.gzcompanion.guide.model.GuideStep;
import se.jimmyeliasson.gzcompanion.guide.progress.GuideContext;
import se.jimmyeliasson.gzcompanion.ui.GZCompanionMainScreen;
import se.jimmyeliasson.gzcompanion.ui.GZTheme;
import se.jimmyeliasson.gzcompanion.ui.HomeCopy;
import se.jimmyeliasson.gzcompanion.ui.IconId;
import se.jimmyeliasson.gzcompanion.ui.TabType;
import se.jimmyeliasson.gzcompanion.ui.TypographyScale;
import se.jimmyeliasson.gzcompanion.ui.layout.HomeTabLayout;
import se.jimmyeliasson.gzcompanion.ui.layout.LiveGameZoneCardLayout;
import se.jimmyeliasson.gzcompanion.ui.layout.TextUtil;
import se.jimmyeliasson.gzcompanion.ui.layout.UiRect;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders the compact, polished Home ("Hem") tab following docs/design/GZ-COMPANION-UI-REFERENCE.png.
 */
public class HomeTabComponent {
    private static final int ROW_GAP = 4;

    private String feedbackMessage = null;
    private long feedbackExpiry = 0;

    private boolean showAdvisor = false;
    private List<AdvisorSuggestion> advisorSuggestions = List.of();
    private UiRect advisorCloseBtnRect = new UiRect(0, 0, 0, 0);
    private final List<AdvisorHit> advisorHitTargets = new ArrayList<>();

    private record AdvisorHit(UiRect rect, Runnable action) {}

    private HomeTabLayout layout;
    private UiRect onlinePlayerCountRect = null;

    /**
     * Whole-page scroll for the Home tab - the existing dense 5-card grid always renders at its
     * exact, unchanged pixel layout (see {@link HomeTabLayout}); this offset only ever reveals the
     * LIVE GAMEZONE card appended below it (see docs/LIVE-GAMEZONE-STATUS.md), never rescales or
     * repositions the existing cards themselves.
     */
    private int homeScrollOffset = 0;
    private int homeMaxScroll = 0;

    public HomeTabLayout getLayout() {
        return layout;
    }

    public void calculateLayout(UiRect bounds) {
        this.layout = HomeTabLayout.calculate(bounds);
    }

    public void render(GuiGraphicsExtractor extractor, Font font, UiRect bounds, int mouseX, int mouseY, GZCompanionMainScreen mainScreen) {
        CompanionSession session = CompanionSession.getInstance();
        String playerName = session.getBridge().getPlayerName();
        boolean isGameZone = session.getBridge().isConnectedToGameZone();
        String serverAddr = session.getBridge().getCurrentServerAddress().orElse(HomeCopy.SERVER_LOCAL_WORLD);
        CompatibilityResult compat = session.getCompatibilityResult();
        String packVersion = session.getActiveRulePack() != null ? session.getActiveRulePack().manifest().packVersion() : HomeCopy.RULE_PACK_FALLBACK;
        FeatureManager featureManager = session.getFeatureManager();

        String tabHeaderText = isGameZone ? session.getBridge().getTabHeaderText().orElse(null) : null;
        GameZoneLiveStatus liveStatus = session.getLiveStatusTracker().update(isGameZone, tabHeaderText);

        // Measure the existing grid's UNSCROLLED bottom edge and the live card's own natural
        // height up front - the single source of truth both for how far scrolling is allowed to go
        // and for where the live card actually gets drawn, so the two can never drift apart.
        HomeTabLayout naturalLayout = HomeTabLayout.calculate(new UiRect(bounds.x(), bounds.y(), bounds.width(), bounds.height()));
        int liveCardH = computeLiveCardHeight(font, bounds.width(), isGameZone, liveStatus);
        int naturalContentH = (naturalLayout.moduleRect().bottom() + ROW_GAP + liveCardH) - bounds.y();
        homeMaxScroll = Math.max(0, naturalContentH - bounds.height());
        homeScrollOffset = Math.max(0, Math.min(homeScrollOffset, homeMaxScroll));

        UiRect scrolledBounds = new UiRect(bounds.x(), bounds.y() - homeScrollOffset, bounds.width(), bounds.height());
        calculateLayout(scrolledBounds);

        extractor.enableScissor(bounds.x(), bounds.y(), bounds.right(), bounds.bottom());

        UiRect welcomeRect = layout.welcomeRect();
        UiRect serverRect = layout.serverRect();
        UiRect versionRect = layout.versionRect();
        UiRect objectiveRect = layout.objectiveRect();
        UiRect moduleRect = layout.moduleRect();
        UiRect primaryBtnRect = layout.primaryButtonRect();
        UiRect secondaryBtn1Rect = layout.secondaryButton1Rect();
        UiRect secondaryBtn2Rect = layout.secondaryButton2Rect();

        // 1. WELCOME CARD (60% width)
        GZTheme.drawCard(extractor, welcomeRect, GZTheme.COLOR_CARD_BG, GZTheme.COLOR_BORDER_SUBTLE);
        extractor.fill(welcomeRect.x() + 2, welcomeRect.y() + 2, welcomeRect.x() + 4, welcomeRect.bottom() - 2, GZTheme.COLOR_EMERALD);

        int avatarSize = Math.max(16, welcomeRect.height() - 12);
        int avatarX = welcomeRect.x() + 7;
        int avatarY = welcomeRect.y() + 6;
        GZTheme.drawCard(extractor, avatarX, avatarY, avatarSize, avatarSize, GZTheme.COLOR_CARD_INNER, GZTheme.COLOR_BORDER_SUBTLE);
        GZTheme.drawIcon(extractor, IconId.PLAYER, avatarX + ((avatarSize - 12) / 2), avatarY + ((avatarSize - 12) / 2), 12, GZTheme.COLOR_MINT);

        int textX = avatarX + avatarSize + 6;
        int maxWelcomeTextW = welcomeRect.right() - textX - 5;
        TextUtil.drawScaledText(extractor, font, HomeCopy.WELCOME_PREFIX, textX, welcomeRect.y() + 5, TypographyScale.SMALL.getScale(), GZTheme.COLOR_TEXT_SECONDARY, false);
        TextUtil.drawScaledEllipsizedText(extractor, font, playerName, textX, welcomeRect.y() + 14, maxWelcomeTextW, TypographyScale.HEADING.getScale(), GZTheme.COLOR_MINT, true);
        TextUtil.drawScaledEllipsizedText(extractor, font, HomeCopy.WELCOME_SUBTITLE, textX, welcomeRect.y() + 24, maxWelcomeTextW, TypographyScale.SMALL.getScale(), GZTheme.COLOR_TEXT_SECONDARY, false);
        TextUtil.drawScaledEllipsizedText(extractor, font, HomeCopy.WELCOME_NOTE, textX, welcomeRect.y() + 33, maxWelcomeTextW, TypographyScale.SMALL.getScale(), GZTheme.COLOR_TEXT_MUTED, false);

        // 2. SERVER STATUS CARD (40% width)
        GZTheme.drawCard(extractor, serverRect, GZTheme.COLOR_CARD_BG, GZTheme.COLOR_BORDER_SUBTLE);
        int sPad = 5;
        TextUtil.drawScaledEllipsizedText(extractor, font, HomeCopy.SERVER_TITLE, serverRect.x() + sPad, serverRect.y() + 4, serverRect.width() - 44, TypographyScale.HEADING.getScale(), GZTheme.COLOR_TEXT_PRIMARY, true);
        GZTheme.drawBadge(extractor, font, serverRect.right() - 44, serverRect.y() + 3,
                isGameZone ? HomeCopy.SERVER_ONLINE_BADGE : HomeCopy.SERVER_LOCAL_BADGE,
                isGameZone ? GZTheme.COLOR_STATUS_GREEN : GZTheme.COLOR_STATUS_YELLOW,
                isGameZone ? GZTheme.COLOR_STATUS_GREEN : GZTheme.COLOR_STATUS_YELLOW);

        int row1Y = serverRect.y() + 15;
        int row2Y = serverRect.y() + 24;
        int row3Y = serverRect.y() + 33;
        int sValX = serverRect.x() + 36;
        int sValW = serverRect.right() - sValX - sPad;

        TextUtil.drawScaledText(extractor, font, HomeCopy.SERVER_LABEL_PROFILE, serverRect.x() + sPad, row1Y, TypographyScale.SMALL.getScale(), GZTheme.COLOR_TEXT_SECONDARY, false);
        TextUtil.drawScaledEllipsizedText(extractor, font, isGameZone ? HomeCopy.SERVER_PROFILE_GAMEZONE : HomeCopy.SERVER_PROFILE_STANDALONE, sValX, row1Y, sValW, TypographyScale.BODY.getScale(), GZTheme.COLOR_TEXT_PRIMARY, false);

        TextUtil.drawScaledText(extractor, font, HomeCopy.SERVER_LABEL_STATUS, serverRect.x() + sPad, row2Y, TypographyScale.SMALL.getScale(), GZTheme.COLOR_TEXT_SECONDARY, false);
        GZTheme.drawStatusDot(extractor, sValX, row2Y + 2, isGameZone ? GZTheme.COLOR_STATUS_GREEN : GZTheme.COLOR_STATUS_GREY);
        // When connected, the status line also shows the current online player count and becomes
        // clickable, jumping straight to the Online tab - kept to this one existing row (no new
        // row/card added) so the dashboard never gets more crowded.
        String statusValue = HomeCopy.SERVER_STATUS_CONNECTED;
        if (isGameZone) {
            int onlineCount = session.getBridge().getOnlinePlayers().size();
            statusValue = HomeCopy.SERVER_STATUS_CONNECTED + " · " + onlineCount + " spelare";
            onlinePlayerCountRect = new UiRect(sValX + 6, row2Y, sValW - 6, 9);
        } else {
            onlinePlayerCountRect = null;
        }
        TextUtil.drawScaledEllipsizedText(extractor, font, isGameZone ? statusValue : HomeCopy.SERVER_STATUS_DISCONNECTED, sValX + 6, row2Y, sValW - 6, TypographyScale.BODY.getScale(),
                isGameZone ? GZTheme.COLOR_STATUS_GREEN : GZTheme.COLOR_TEXT_MUTED, false);

        TextUtil.drawScaledText(extractor, font, HomeCopy.SERVER_LABEL_SERVER, serverRect.x() + sPad, row3Y, TypographyScale.SMALL.getScale(), GZTheme.COLOR_TEXT_SECONDARY, false);
        TextUtil.drawScaledEllipsizedText(extractor, font, serverAddr, sValX, row3Y, sValW, TypographyScale.BODY.getScale(), GZTheme.COLOR_TEXT_MUTED, false);

        // 3. VERSION STRIP (Weighted allocations)
        GZTheme.drawCard(extractor, versionRect, GZTheme.COLOR_CARD_BG, GZTheme.COLOR_BORDER_SUBTLE);
        UiRect[] badges = layout.versionBadgeRects();
        drawMiniBadge(extractor, font, badges[0], HomeCopy.STRIP_HEADER_MINECRAFT, CompanionConstants.TARGET_MINECRAFT_VERSION, GZTheme.COLOR_TEXT_PRIMARY);
        drawMiniBadge(extractor, font, badges[1], HomeCopy.STRIP_HEADER_COMPANION, CompanionConstants.getModVersion(), GZTheme.COLOR_MINT);
        drawMiniBadge(extractor, font, badges[2], HomeCopy.STRIP_HEADER_RULE_PACK, packVersion, GZTheme.COLOR_TEXT_PRIMARY);
        drawMiniBadge(extractor, font, badges[3], HomeCopy.STRIP_HEADER_STATUS, compat.overallStatus().getDisplayName(), compat.overallStatus().getArgbColor());

        // 4. NEXT OBJECTIVE CARD (61% width)
        GZTheme.drawCard(extractor, objectiveRect, GZTheme.COLOR_CARD_BG, GZTheme.COLOR_BORDER_SUBTLE);
        int oPad = 5;
        int maxObjW = objectiveRect.width() - (oPad * 2);

        GuideEngine engine = session.getGuideEngine();
        GuideContext context = session.getCurrentGuideContext();
        boolean isGuideComplete = engine != null && engine.isRequiredGuideComplete(context);
        GuideStep nextStep = engine != null ? engine.getActiveOrNextStep(context) : null;

        String objHeader;
        String objTitle;
        String objDesc;
        String chk1;
        String chk2;
        String chk3;

        if (isGuideComplete) {
            int optLeft = engine.getIncompleteOptionalStepsCount(context);
            objHeader = "NYBÖRJARGUIDEN KLAR";
            objTitle = "Grunderna i Minecraft avklarade";
            objDesc = "Du behärskar nu de viktigaste grunderna:";
            chk1 = "✓ Verktyg, vapen, mat & skydd";
            chk2 = "✓ Gruvstart, sten- och järnåldern";
            chk3 = optLeft > 0 ? "Valfria bonussteg kvar: " + optLeft + " st (t.ex. säng)" : "✓ Alla steg i nybörjarguiden slutförda";
        } else if (nextStep != null) {
            objHeader = HomeCopy.OBJECTIVE_HEADER;
            objTitle = nextStep.title();
            objDesc = engine.resolveTokens(nextStep.summary());
            chk1 = nextStep.why() != null ? engine.resolveTokens(nextStep.why()) : HomeCopy.CHECK_ITEM_1;
            chk2 = selectObjectiveSecondaryText(nextStep, engine);
            chk3 = !nextStep.conditions().isEmpty() && nextStep.conditions().get(0).description() != null
                    ? nextStep.conditions().get(0).description() : HomeCopy.CHECK_ITEM_3;
        } else {
            objHeader = HomeCopy.OBJECTIVE_HEADER;
            objTitle = "Öppna Nybörjarguiden";
            objDesc = "Lär dig grunderna i Minecraft steg för steg.";
            chk1 = HomeCopy.CHECK_ITEM_1;
            chk2 = HomeCopy.CHECK_ITEM_2;
            chk3 = HomeCopy.CHECK_ITEM_3;
        }

        GZTheme.drawIcon(extractor, IconId.OBJECTIVE, objectiveRect.x() + oPad, objectiveRect.y() + 5, 9,
                isGuideComplete ? GZTheme.COLOR_STATUS_GREEN : GZTheme.COLOR_MINT);
        TextUtil.drawScaledText(extractor, font, objHeader, objectiveRect.x() + oPad + 13, objectiveRect.y() + 5,
                TypographyScale.HEADING.getScale(), isGuideComplete ? GZTheme.COLOR_STATUS_GREEN : GZTheme.COLOR_MINT, true);
        TextUtil.drawScaledEllipsizedText(extractor, font, objTitle, objectiveRect.x() + oPad, objectiveRect.y() + 15, maxObjW, TypographyScale.HEADING.getScale(), GZTheme.COLOR_TEXT_PRIMARY, true);
        TextUtil.drawScaledEllipsizedText(extractor, font, objDesc, objectiveRect.x() + oPad, objectiveRect.y() + 24, maxObjW, TypographyScale.SMALL.getScale(), GZTheme.COLOR_TEXT_SECONDARY, false);

        int chkY = objectiveRect.y() + 34;
        int chkSpacing = 8;
        drawCheckItem(extractor, font, objectiveRect.x() + oPad, chkY, chk1, maxObjW);
        drawCheckItem(extractor, font, objectiveRect.x() + oPad, chkY + chkSpacing, chk2, maxObjW);
        drawCheckItem(extractor, font, objectiveRect.x() + oPad, chkY + (chkSpacing * 2), chk3, maxObjW);

        // Render Action Buttons
        GZTheme.drawButton(extractor, font, primaryBtnRect, HomeCopy.ACTION_OPEN_GUIDE, true, primaryBtnRect.contains(mouseX, mouseY), TypographyScale.BODY.getScale());
        GZTheme.drawButton(extractor, font, secondaryBtn1Rect, HomeCopy.ACTION_WHAT_TO_DO, false, secondaryBtn1Rect.contains(mouseX, mouseY), TypographyScale.SMALL.getScale());
        GZTheme.drawButton(extractor, font, secondaryBtn2Rect, HomeCopy.ACTION_COMPATIBILITY, false, secondaryBtn2Rect.contains(mouseX, mouseY), TypographyScale.SMALL.getScale());

        // 5. MODULE STATUS CARD (39% width)
        GZTheme.drawCard(extractor, moduleRect, GZTheme.COLOR_CARD_BG, GZTheme.COLOR_BORDER_SUBTLE);
        int mPad = 5;
        TextUtil.drawScaledEllipsizedText(extractor, font, HomeCopy.MODULES_TITLE, moduleRect.x() + mPad, moduleRect.y() + 4, moduleRect.width() - 10, TypographyScale.HEADING.getScale(), GZTheme.COLOR_TEXT_PRIMARY, true);

        TabType[] trackedTabs = {
            TabType.HEM, TabType.ONLINE, TabType.GUIDE, TabType.CRAFTING, TabType.KISTOR,
            TabType.SETTLEMENT, TabType.BYGGPLANER, TabType.MARKETWATCH,
            TabType.KOMMANDON, TabType.INSTALLNINGAR
        };

        int availableRowsH = moduleRect.height() - 18;
        int rowH = Math.max(8, Math.min(10, availableRowsH / trackedTabs.length));
        int modStartY = moduleRect.y() + 15;

        for (int i = 0; i < trackedTabs.length; i++) {
            TabType tab = trackedTabs[i];
            ModuleStatus status = featureManager.getModuleStatus(tab);
            String compactStatus = (status == ModuleStatus.AVAILABLE) ? HomeCopy.MODULE_STATUS_ACTIVE : HomeCopy.MODULE_STATUS_SOON;
            drawModuleRow(extractor, font, moduleRect.x() + mPad, modStartY + (i * rowH), moduleRect.width() - (mPad * 2), tab.getDisplayName(), compactStatus, status.getRgbColor());
        }

        // 6. LIVE GAMEZONE CARD - see docs/LIVE-GAMEZONE-STATUS.md
        UiRect liveCardRect = new UiRect(bounds.x(), moduleRect.bottom() + ROW_GAP, bounds.width(), liveCardH);
        renderLiveGameZoneCard(extractor, font, liveCardRect, isGameZone, liveStatus);

        extractor.disableScissor();

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

        if (showAdvisor) {
            renderAdvisorOverlay(extractor, font, bounds, mouseX, mouseY);
        }
    }

    // ------------------------------------------------------------------
    // Live GameZone card - see docs/LIVE-GAMEZONE-STATUS.md
    //
    // Fair play: every value shown here comes ONLY from the vanilla TAB header Component GameZone
    // already sends this client and Minecraft already renders on screen - the exact same seam
    // (MinecraftBridge.getTabHeaderText()) already used and human-QA-verified for automatic
    // settlement detection on the Online tab. No commands, no menus, no external APIs.
    // ------------------------------------------------------------------

    private static final int LIVE_HEADER_H = 10;
    private static final int LIVE_LINE_H = 9;
    private static final int LIVE_CARD_PAD = 4;

    /**
     * The single source of truth for the live card's total height - called identically here (for
     * scroll-range measurement) and from {@link #renderLiveGameZoneCard} (for actual drawing), so
     * the two can never drift apart, mirroring the Online tab's own established
     * "measure-before-render" anti-drift convention.
     */
    private int computeLiveCardHeight(Font font, int width, boolean connected, GameZoneLiveStatus status) {
        int innerW = Math.max(10, width - (LIVE_CARD_PAD * 2));
        if (!connected) {
            return LIVE_HEADER_H + 2 + LIVE_LINE_H + 6;
        }
        if (!status.hasAnyData()) {
            int msgH = TextUtil.measureWrappedHeightCapped(font, HomeCopy.LIVE_NO_DATA_DETAIL, innerW, TypographyScale.SMALL.getScale(), 2, 1);
            return LIVE_HEADER_H + 2 + LIVE_LINE_H + 2 + msgH + 6;
        }
        boolean showSubCards = status.hasCity() || status.hasEconomy() || status.hasServerInfo();
        return LiveGameZoneCardLayout.calculate(new UiRect(0, 0, width, 0), status.hasSettlement(), showSubCards).contentHeight();
    }

    private void renderLiveGameZoneCard(GuiGraphicsExtractor extractor, Font font, UiRect cardRect, boolean connected, GameZoneLiveStatus status) {
        GZTheme.drawCard(extractor, cardRect, GZTheme.COLOR_CARD_BG, GZTheme.COLOR_BORDER_SUBTLE);

        int innerX = cardRect.x() + LIVE_CARD_PAD;
        int innerW = Math.max(10, cardRect.width() - (LIVE_CARD_PAD * 2));
        int headerY = cardRect.y() + LIVE_CARD_PAD;

        TextUtil.drawScaledText(extractor, font, HomeCopy.LIVE_TITLE, innerX, headerY, TypographyScale.HEADING.getScale(), GZTheme.COLOR_TEXT_PRIMARY, true);

        if (connected) {
            String badge = HomeCopy.LIVE_BADGE_CONNECTED;
            int badgeTextW = TextUtil.scaledWidth(font, badge, TypographyScale.META.getScale());
            int badgeX = cardRect.right() - LIVE_CARD_PAD - badgeTextW - 6;
            GZTheme.drawStatusDot(extractor, badgeX, headerY + 2, GZTheme.COLOR_STATUS_GREEN);
            TextUtil.drawScaledText(extractor, font, badge, badgeX + 6, headerY, TypographyScale.META.getScale(), GZTheme.COLOR_STATUS_GREEN, false);
        }

        int contentY = headerY + LIVE_HEADER_H;

        if (!connected) {
            TextUtil.drawScaledText(extractor, font, HomeCopy.LIVE_DISCONNECTED, innerX, contentY, TypographyScale.SMALL.getScale(), GZTheme.COLOR_TEXT_MUTED, false);
            return;
        }

        if (!status.hasAnyData()) {
            TextUtil.drawScaledText(extractor, font, HomeCopy.LIVE_CONNECTED_NO_DATA, innerX, contentY, TypographyScale.SMALL.getScale(), GZTheme.COLOR_STATUS_GREEN, false);
            contentY += LIVE_LINE_H + 2;
            TextUtil.drawScaledWrappedText(extractor, font, HomeCopy.LIVE_NO_DATA_DETAIL, innerX, contentY, innerW,
                    TypographyScale.SMALL.getScale(), 2, 1, GZTheme.COLOR_TEXT_MUTED, false);
            return;
        }

        boolean showSubCards = status.hasCity() || status.hasEconomy() || status.hasServerInfo();
        LiveGameZoneCardLayout cardLayout = LiveGameZoneCardLayout.calculate(cardRect, status.hasSettlement(), showSubCards);

        UiRect settlementRect = cardLayout.settlementRect();
        if (status.hasSettlement()) {
            TextUtil.drawScaledEllipsizedText(extractor, font, status.settlementName(), settlementRect.x(), settlementRect.y(),
                    settlementRect.width(), TypographyScale.BODY.getScale(), GZTheme.COLOR_MINT, true);
            String roleLine = (status.settlementRole() != null ? status.settlementRole() : HomeCopy.LIVE_UNKNOWN)
                    + " · " + GameZoneStatusFormatter.formatBonusPercent(status.settlementBonusPercent());
            TextUtil.drawScaledEllipsizedText(extractor, font, roleLine, settlementRect.x(), settlementRect.y() + 10,
                    settlementRect.width(), TypographyScale.SMALL.getScale(), GZTheme.COLOR_TEXT_SECONDARY, false);
        } else {
            TextUtil.drawScaledEllipsizedText(extractor, font, HomeCopy.LIVE_NO_SETTLEMENT, settlementRect.x(), settlementRect.y(),
                    settlementRect.width(), TypographyScale.SMALL.getScale(), GZTheme.COLOR_TEXT_MUTED, false);
        }

        if (!showSubCards) return;

        String cityLine1 = status.cityName() != null ? status.cityName() : HomeCopy.LIVE_UNKNOWN;
        String cityLine2 = HomeCopy.LIVE_LABEL_LEVEL + " " + (status.cityLevel() != null ? status.cityLevel() : HomeCopy.LIVE_UNKNOWN);
        renderLiveSubCard(extractor, font, cardLayout.stadRect(), HomeCopy.LIVE_SECTION_STAD, cityLine1, cityLine2);

        String coinsLine = GameZoneStatusFormatter.formatMoney(status.coins()) + " " + HomeCopy.LIVE_LABEL_COINS;
        String treasuryLine = GameZoneStatusFormatter.formatMoney(status.treasury()) + " " + HomeCopy.LIVE_LABEL_TREASURY;
        renderLiveSubCard(extractor, font, cardLayout.ekonomiRect(), HomeCopy.LIVE_SECTION_EKONOMI, coinsLine, treasuryLine);

        String playersLine = (status.onlinePlayers() != null && status.maxPlayers() != null)
                ? status.onlinePlayers() + " / " + status.maxPlayers()
                : HomeCopy.LIVE_UNKNOWN;
        String tpsLine = HomeCopy.LIVE_LABEL_TPS + " " + GameZoneStatusFormatter.formatTps(status.tps());
        renderLiveSubCard(extractor, font, cardLayout.serverRect(), HomeCopy.LIVE_SECTION_SERVER, playersLine, tpsLine);
    }

    private void renderLiveSubCard(GuiGraphicsExtractor extractor, Font font, UiRect rect, String title, String line1, String line2) {
        GZTheme.drawCard(extractor, rect, GZTheme.COLOR_CARD_INNER, GZTheme.COLOR_BORDER_SUBTLE);
        int x = rect.x() + 3;
        int maxW = Math.max(10, rect.width() - 6);
        TextUtil.drawScaledText(extractor, font, title, x, rect.y() + 2, TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_MUTED, false);
        TextUtil.drawScaledEllipsizedText(extractor, font, line1, x, rect.y() + 11, maxW, TypographyScale.SMALL.getScale(), GZTheme.COLOR_TEXT_PRIMARY, false);
        TextUtil.drawScaledEllipsizedText(extractor, font, line2, x, rect.y() + 20, maxW, TypographyScale.SMALL.getScale(), GZTheme.COLOR_TEXT_SECONDARY, false);
    }

    // ------------------------------------------------------------------
    // Advisor overlay ("Vad ska jag göra?") - M8
    // ------------------------------------------------------------------

    private void renderAdvisorOverlay(GuiGraphicsExtractor extractor, Font font, UiRect bounds, int mouseX, int mouseY) {
        advisorHitTargets.clear();
        extractor.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), GZTheme.COLOR_BACKDROP);

        UiRect card = bounds.inset(6, 4);
        GZTheme.drawCard(extractor, card, GZTheme.COLOR_PANEL_BG, GZTheme.COLOR_BORDER_MODAL);

        int pad = 6;
        int x = card.x() + pad;
        int maxW = card.width() - (pad * 2);
        int y = card.y() + 4;

        GZTheme.drawIcon(extractor, IconId.OBJECTIVE, x, y, 10, GZTheme.COLOR_MINT);
        TextUtil.drawScaledText(extractor, font, "Vad ska jag göra?", x + 13, y + 1, TypographyScale.HEADING.getScale(), GZTheme.COLOR_TEXT_PRIMARY, true);

        advisorCloseBtnRect = new UiRect(card.right() - 16, card.y() + 3, 12, 10);
        boolean closeHov = advisorCloseBtnRect.contains(mouseX, mouseY);
        extractor.fill(advisorCloseBtnRect.x(), advisorCloseBtnRect.y(), advisorCloseBtnRect.right(), advisorCloseBtnRect.bottom(), closeHov ? 0x99EF4444 : 0x221E293B);
        TextUtil.drawCenteredText(extractor, font, "x", advisorCloseBtnRect.x() + (advisorCloseBtnRect.width() / 2), advisorCloseBtnRect.y() + 1,
                advisorCloseBtnRect.width(), closeHov ? GZTheme.COLOR_TEXT_PRIMARY : GZTheme.COLOR_TEXT_MUTED, false);
        advisorHitTargets.add(new AdvisorHit(advisorCloseBtnRect, () -> showAdvisor = false));

        y += 14;
        extractor.enableScissor(card.x(), y, card.right(), card.bottom() - 4);
        for (AdvisorSuggestion suggestion : advisorSuggestions) {
            // There is no scroll here (at most MAX_SUGGESTIONS short entries) - but a suggestion
            // that would render past the card's visible bottom must still never leave a clickable
            // "Kopiera" button outside the scissored area, so stop drawing once we run out of room.
            if (y >= card.bottom() - 14) break;

            TextUtil.drawScaledEllipsizedText(extractor, font, suggestion.title(), x, y, maxW, TypographyScale.SMALL.getScale(), GZTheme.COLOR_MINT, true);
            y += 10;
            y += TextUtil.drawScaledWrappedText(extractor, font, "Varför: " + suggestion.reason(), x, y, maxW,
                    TypographyScale.META.getScale(), 2, 1, GZTheme.COLOR_TEXT_SECONDARY, false) + 1;
            y += TextUtil.drawScaledWrappedText(extractor, font, "Nästa steg: " + suggestion.nextStep(), x, y, maxW,
                    TypographyScale.META.getScale(), 2, 1, GZTheme.COLOR_TEXT_MUTED, false) + 1;

            if (suggestion.optionalCommand() != null) {
                UiRect copyBtn = new UiRect(x, y, Math.min(120, maxW), 10);
                boolean hov = copyBtn.contains(mouseX, mouseY);
                GZTheme.drawButton(extractor, font, copyBtn, "Kopiera " + suggestion.optionalCommand(), false, hov, TypographyScale.META.getScale());
                String command = suggestion.optionalCommand();
                advisorHitTargets.add(new AdvisorHit(copyBtn, () -> copyToClipboard(command)));
                y += 12;
            }
            y += 6;
        }
        extractor.disableScissor();
    }

    private void copyToClipboard(String text) {
        try {
            Minecraft client = Minecraft.getInstance();
            if (client != null && client.keyboardHandler != null) {
                client.keyboardHandler.setClipboard(text);
                showToast("Kopierat: " + text, 2000);
            }
        } catch (Exception ignored) {
            // Clipboard access is a pure local OS convenience - never let a failure here affect anything else.
        }
    }

    /**
     * Chooses the Home dashboard's secondary objective line for a real, specific active guide
     * step. Never falls back to generic onboarding placeholder copy for a real step - if the
     * step has no tip, its real description is used instead; if that is absent too, an empty
     * string is returned rather than inventing or reusing unrelated placeholder text.
     */
    static String selectObjectiveSecondaryText(GuideStep step, GuideEngine engine) {
        if (step.tip() != null && !step.tip().isBlank()) {
            return engine.resolveTokens("Tips: " + step.tip());
        }
        if (step.description() != null && !step.description().isBlank()) {
            return engine.resolveTokens(step.description());
        }
        return "";
    }

    private void drawMiniBadge(GuiGraphicsExtractor extractor, Font font, UiRect rect, String title, String val, int valArgb) {
        GZTheme.drawCard(extractor, rect, GZTheme.COLOR_CARD_INNER, GZTheme.COLOR_BORDER_SUBTLE);
        TextUtil.drawScaledEllipsizedText(extractor, font, title, rect.x() + 3, rect.y() + 2, rect.width() - 6, TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_SECONDARY, false);
        TextUtil.drawScaledEllipsizedText(extractor, font, val, rect.x() + 3, rect.y() + 9, rect.width() - 6, TypographyScale.BODY.getScale(), GZTheme.opaque(valArgb), false);
    }

    private void drawCheckItem(GuiGraphicsExtractor extractor, Font font, int cx, int cy, String text, int maxW) {
        TextUtil.drawScaledEllipsizedText(extractor, font, text, cx, cy, maxW, TypographyScale.SMALL.getScale(), GZTheme.COLOR_TEXT_SECONDARY, false);
    }

    private void drawModuleRow(GuiGraphicsExtractor extractor, Font font, int mx, int my, int rowW, String modName, String statusName, int dotColor) {
        GZTheme.drawStatusDot(extractor, mx, my + 2, dotColor);
        float scale = TypographyScale.SMALL.getScale();
        int statusW = TextUtil.scaledWidth(font, statusName, scale);
        int rightStatusX = mx + rowW;
        int maxModNameW = Math.max(10, rowW - statusW - 8);

        TextUtil.drawScaledEllipsizedText(extractor, font, modName, mx + 6, my, maxModNameW, scale, GZTheme.COLOR_TEXT_PRIMARY, false);
        TextUtil.drawScaledRightAlignedText(extractor, font, statusName, rightStatusX, my, statusW + 2, scale,
                (dotColor == 0x22C55E || dotColor == 0xFF22C55E) ? GZTheme.COLOR_STATUS_GREEN : GZTheme.COLOR_TEXT_MUTED, false);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button, UiRect bounds, GZCompanionMainScreen mainScreen) {
        if (button != 0) return false;
        // Must match the same scroll offset render() drew with, so hit-rects line up with what's on screen.
        calculateLayout(new UiRect(bounds.x(), bounds.y() - homeScrollOffset, bounds.width(), bounds.height()));

        if (showAdvisor) {
            for (AdvisorHit hit : advisorHitTargets) {
                if (hit.rect().contains(mouseX, mouseY)) {
                    hit.action().run();
                    return true;
                }
            }
            // Swallow every other click while the overlay is open, so it can't be clicked "through".
            return true;
        }

        CompanionSession session = CompanionSession.getInstance();
        GuideEngine engine = session.getGuideEngine();
        GuideContext context = session.getCurrentGuideContext();
        GuideStep nextStep = engine != null ? engine.getActiveOrNextStep(context) : null;

        if (onlinePlayerCountRect != null && onlinePlayerCountRect.contains(mouseX, mouseY)) {
            mainScreen.setActiveTab(TabType.ONLINE);
            return true;
        }

        if (layout.primaryButtonRect().contains(mouseX, mouseY)) {
            mainScreen.openGuideStep(nextStep != null ? nextStep.id() : null);
            return true;
        }

        if (layout.secondaryButton1Rect().contains(mouseX, mouseY)) {
            advisorSuggestions = AdvisorEngine.generate(AdvisorContextBuilder.build(session));
            showAdvisor = true;
            return true;
        }

        if (layout.secondaryButton2Rect().contains(mouseX, mouseY)) {
            CompatibilityResult res = CompanionSession.getInstance().getCompatibilityResult();
            showToast("Kompatibilitet: " + res.overallStatus().getDisplayName(), 3500);
            return true;
        }

        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (showAdvisor) return false;
        homeScrollOffset = Math.max(0, Math.min(homeMaxScroll, homeScrollOffset - (int) (scrollY * 14)));
        return true;
    }

    private void showToast(String message, long durationMs) {
        this.feedbackMessage = message;
        this.feedbackExpiry = System.currentTimeMillis() + durationMs;
    }
}