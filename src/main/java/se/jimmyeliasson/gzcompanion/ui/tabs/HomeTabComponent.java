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
import se.jimmyeliasson.gzcompanion.update.UpdateManager;
import se.jimmyeliasson.gzcompanion.update.UpdateState;

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

    /** Click targets for the update banner/panel (see docs/UPDATES.md) - separate from the advisor overlay's own list since both can be relevant across renders. */
    private record HomeHit(UiRect rect, Runnable action) {}
    private final List<HomeHit> homeHitTargets = new ArrayList<>();
    private boolean showUpdatePanel = false;

    public HomeTabLayout getLayout() {
        return layout;
    }

    public void calculateLayout(UiRect bounds) {
        this.layout = HomeTabLayout.calculate(bounds);
    }

    public void render(GuiGraphicsExtractor extractor, Font font, UiRect bounds, int mouseX, int mouseY, GZCompanionMainScreen mainScreen) {
        homeHitTargets.clear();
        CompanionSession session = CompanionSession.getInstance();
        String playerName = session.getBridge().getPlayerName();
        boolean isGameZone = session.getBridge().isConnectedToGameZone();
        String serverAddr = session.getBridge().getCurrentServerAddress().orElse(HomeCopy.SERVER_LOCAL_WORLD);
        CompatibilityResult compat = session.getCompatibilityResult();
        String packVersion = session.getActiveRulePack() != null ? session.getActiveRulePack().manifest().packVersion() : HomeCopy.RULE_PACK_FALLBACK;
        FeatureManager featureManager = session.getFeatureManager();

        String tabHeaderText = isGameZone ? session.getBridge().getTabHeaderText().orElse(null) : null;
        GameZoneLiveStatus liveStatus = session.getLiveStatusTracker().update(isGameZone, tabHeaderText);

        // Update banner - a fixed, non-scrolling strip pinned at the very top of Home, only ever
        // present when there is something update-related to show (see docs/UPDATES.md). When
        // absent, bannerH is 0 and the rest of Home is 100% pixel-identical to before this feature.
        UpdateManager.Snapshot updateSnapshot = session.getUpdateManager().getSnapshot();
        boolean showBanner = isUpdateBannerState(updateSnapshot.state());
        int bannerH = showBanner ? UPDATE_BANNER_H : 0;
        if (showBanner) {
            renderUpdateBanner(extractor, font, new UiRect(bounds.x(), bounds.y(), bounds.width(), UPDATE_BANNER_H), mouseX, mouseY, updateSnapshot);
        }
        UiRect contentViewport = new UiRect(bounds.x(), bounds.y() + bannerH, bounds.width(), bounds.height() - bannerH);

        // Measure the existing grid's UNSCROLLED bottom edge and the live card's own natural
        // height up front - the single source of truth both for how far scrolling is allowed to go
        // and for where the live card actually gets drawn, so the two can never drift apart.
        HomeTabLayout naturalLayout = HomeTabLayout.calculate(new UiRect(contentViewport.x(), contentViewport.y(), contentViewport.width(), contentViewport.height()));
        int liveCardH = computeLiveCardHeight(font, contentViewport.width(), isGameZone, liveStatus);
        int naturalContentH = (naturalLayout.moduleRect().bottom() + ROW_GAP + liveCardH) - contentViewport.y();
        homeMaxScroll = Math.max(0, naturalContentH - contentViewport.height());
        homeScrollOffset = Math.max(0, Math.min(homeScrollOffset, homeMaxScroll));

        UiRect scrolledBounds = new UiRect(contentViewport.x(), contentViewport.y() - homeScrollOffset, contentViewport.width(), contentViewport.height());
        calculateLayout(scrolledBounds);

        extractor.enableScissor(contentViewport.x(), contentViewport.y(), contentViewport.right(), contentViewport.bottom());

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
            TabType.SETTLEMENT, TabType.BYGGPLANER, TabType.MARKETWATCH, TabType.LEADERBOARDS,
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
        UiRect liveCardRect = new UiRect(contentViewport.x(), moduleRect.bottom() + ROW_GAP, contentViewport.width(), liveCardH);
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
        } else if (showUpdatePanel) {
            renderUpdatePanel(extractor, font, bounds, mouseX, mouseY, session, updateSnapshot);
        }
    }

    // ------------------------------------------------------------------
    // Update banner + detail panel - see docs/UPDATES.md. Every value shown here comes from
    // UpdateManager's own state (fetched from GitHub Releases in the background) - this method
    // never itself performs networking; it only ever reads the current Snapshot and dispatches
    // button clicks to UpdateManager.
    // ------------------------------------------------------------------

    private static final int UPDATE_BANNER_H = 22;

    private static boolean isUpdateBannerState(UpdateState state) {
        return state == UpdateState.UPDATE_AVAILABLE || state == UpdateState.DOWNLOADING
                || state == UpdateState.VERIFYING || state == UpdateState.READY_TO_INSTALL
                || state == UpdateState.STARTING_INSTALLER || state == UpdateState.ERROR;
    }

    private void renderUpdateBanner(GuiGraphicsExtractor extractor, Font font, UiRect rect, int mouseX, int mouseY, UpdateManager.Snapshot snapshot) {
        boolean hov = rect.contains(mouseX, mouseY);
        GZTheme.drawCard(extractor, rect, hov ? GZTheme.COLOR_CARD_HOVER : GZTheme.COLOR_CARD_INNER, GZTheme.COLOR_BORDER_EMERALD);

        String title = switch (snapshot.state()) {
            case UPDATE_AVAILABLE -> "UPPDATERING FINNS";
            case DOWNLOADING, VERIFYING -> "HÄMTAR UPPDATERING...";
            case READY_TO_INSTALL -> "UPPDATERING REDO";
            case STARTING_INSTALLER -> "STARTAR UPPDATERING...";
            case ERROR -> "UPPDATERINGSFEL";
            default -> "";
        };
        String subtitle = switch (snapshot.state()) {
            case UPDATE_AVAILABLE, DOWNLOADING, VERIFYING, STARTING_INSTALLER ->
                    snapshot.availableUpdate() != null ? "v" + snapshot.availableUpdate().version().toDisplayString() : "";
            case READY_TO_INSTALL -> "Minecraft behöver startas om";
            case ERROR -> snapshot.errorMessage() != null ? snapshot.errorMessage() : "";
            default -> "";
        };

        TextUtil.drawScaledText(extractor, font, title, rect.x() + 5, rect.y() + 2, TypographyScale.SMALL.getScale(), GZTheme.COLOR_MINT, true);
        int subtitleMaxW = rect.width() - 100;
        TextUtil.drawScaledEllipsizedText(extractor, font, subtitle, rect.x() + 5, rect.y() + 11, subtitleMaxW, TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_SECONDARY, false);

        UiRect showBtn = new UiRect(rect.right() - 90, rect.y() + 5, 84, 13);
        GZTheme.drawButton(extractor, font, showBtn, "Visa uppdatering", false, showBtn.contains(mouseX, mouseY), TypographyScale.META.getScale());
        homeHitTargets.add(new HomeHit(showBtn, () -> showUpdatePanel = true));
        // The whole banner (outside the button) also opens the panel, for a bigger, friendlier click target.
        homeHitTargets.add(new HomeHit(new UiRect(rect.x(), rect.y(), rect.width() - 90, rect.height()), () -> showUpdatePanel = true));
    }

    private void renderUpdatePanel(GuiGraphicsExtractor extractor, Font font, UiRect bounds, int mouseX, int mouseY,
                                    CompanionSession session, UpdateManager.Snapshot snapshot) {
        extractor.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), GZTheme.COLOR_BACKDROP);

        UiRect card = bounds.inset(6, 4);
        GZTheme.drawCard(extractor, card, GZTheme.COLOR_PANEL_BG, GZTheme.COLOR_BORDER_MODAL);

        int pad = 6;
        int x = card.x() + pad;
        int maxW = card.width() - (pad * 2);
        int y = card.y() + 4;

        TextUtil.drawScaledText(extractor, font, "UPPDATERA GZ COMPANION", x, y, TypographyScale.HEADING.getScale(), GZTheme.COLOR_TEXT_PRIMARY, true);

        UiRect closeBtn = new UiRect(card.right() - 16, card.y() + 3, 12, 10);
        boolean closeHov = closeBtn.contains(mouseX, mouseY);
        extractor.fill(closeBtn.x(), closeBtn.y(), closeBtn.right(), closeBtn.bottom(), closeHov ? 0x99EF4444 : 0x221E293B);
        TextUtil.drawCenteredText(extractor, font, "x", closeBtn.x() + (closeBtn.width() / 2), closeBtn.y() + 1,
                closeBtn.width(), closeHov ? GZTheme.COLOR_TEXT_PRIMARY : GZTheme.COLOR_TEXT_MUTED, false);
        homeHitTargets.add(new HomeHit(closeBtn, () -> showUpdatePanel = false));

        y += 14;

        UpdateManager manager = session.getUpdateManager();
        switch (snapshot.state()) {
            case UPDATE_AVAILABLE -> renderUpdateAvailablePanel(extractor, font, x, y, maxW, mouseX, mouseY, manager, snapshot);
            case DOWNLOADING, VERIFYING -> renderDownloadingPanel(extractor, font, x, y, maxW, snapshot);
            case READY_TO_INSTALL -> renderReadyToInstallPanel(extractor, font, x, y, maxW, mouseX, mouseY, session, manager, snapshot);
            case STARTING_INSTALLER -> TextUtil.drawScaledText(extractor, font, "Startar uppdateraren...", x, y, TypographyScale.SMALL.getScale(), GZTheme.COLOR_TEXT_SECONDARY, false);
            case ERROR -> renderErrorPanel(extractor, font, x, y, maxW, mouseX, mouseY, manager, snapshot);
            default -> showUpdatePanel = false; // state moved on (e.g. dismissed elsewhere) - nothing sensible to show
        }
    }

    private void renderUpdateAvailablePanel(GuiGraphicsExtractor extractor, Font font, int x, int y, int maxW, int mouseX, int mouseY,
                                             UpdateManager manager, UpdateManager.Snapshot snapshot) {
        String currentVersion = se.jimmyeliasson.gzcompanion.core.CompanionConstants.getModVersion();
        TextUtil.drawScaledText(extractor, font, "Nuvarande", x, y, TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_MUTED, false);
        TextUtil.drawScaledText(extractor, font, "v" + currentVersion, x, y + 9, TypographyScale.SMALL.getScale(), GZTheme.COLOR_TEXT_PRIMARY, false);
        y += 20;

        TextUtil.drawScaledText(extractor, font, "Ny version", x, y, TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_MUTED, false);
        TextUtil.drawScaledText(extractor, font, "v" + snapshot.availableUpdate().version().toDisplayString(), x, y + 9, TypographyScale.SMALL.getScale(), GZTheme.COLOR_MINT, true);
        y += 22;

        TextUtil.drawScaledText(extractor, font, "Nytt:", x, y, TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_MUTED, false);
        y += 10;
        for (String note : snapshot.availableUpdate().manifest().notes()) {
            y += TextUtil.drawScaledWrappedText(extractor, font, "• " + note, x, y, maxW, TypographyScale.SMALL.getScale(), 2, 1, GZTheme.COLOR_TEXT_SECONDARY, false) + 1;
        }
        y += 4;

        long sizeBytes = snapshot.availableUpdate().manifest().installerSizeBytes();
        TextUtil.drawScaledText(extractor, font, String.format(java.util.Locale.ROOT, "Storlek: %.1f MB", sizeBytes / (1024.0 * 1024.0)),
                x, y, TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_MUTED, false);
        y += 14;

        UiRect downloadBtn = new UiRect(x, y, Math.min(110, maxW), 13);
        GZTheme.drawButton(extractor, font, downloadBtn, "Ladda ner", true, downloadBtn.contains(mouseX, mouseY), TypographyScale.SMALL.getScale());
        homeHitTargets.add(new HomeHit(downloadBtn, manager::startDownload));

        UiRect laterBtn = new UiRect(downloadBtn.right() + 4, y, Math.min(80, maxW - downloadBtn.width() - 4), 13);
        GZTheme.drawButton(extractor, font, laterBtn, "Senare", false, laterBtn.contains(mouseX, mouseY), TypographyScale.SMALL.getScale());
        homeHitTargets.add(new HomeHit(laterBtn, () -> {
            manager.dismiss();
            showUpdatePanel = false;
        }));
    }

    private void renderDownloadingPanel(GuiGraphicsExtractor extractor, Font font, int x, int y, int maxW, UpdateManager.Snapshot snapshot) {
        TextUtil.drawScaledText(extractor, font, "Hämtar uppdatering...", x, y, TypographyScale.SMALL.getScale(), GZTheme.COLOR_TEXT_PRIMARY, false);
        y += 12;

        double downloadedMb = snapshot.downloadedBytes() / (1024.0 * 1024.0);
        double totalMb = Math.max(snapshot.totalBytes(), 1) / (1024.0 * 1024.0);
        int percent = snapshot.totalBytes() > 0 ? (int) Math.min(100, (snapshot.downloadedBytes() * 100L) / snapshot.totalBytes()) : 0;

        TextUtil.drawScaledText(extractor, font, String.format(java.util.Locale.ROOT, "%.1f MB / %.1f MB", downloadedMb, totalMb),
                x, y, TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_SECONDARY, false);
        y += 10;

        UiRect barRect = new UiRect(x, y, maxW, 8);
        GZTheme.drawCard(extractor, barRect, GZTheme.COLOR_CARD_INNER, GZTheme.COLOR_BORDER_SUBTLE);
        int filledW = Math.max(0, Math.min(barRect.width() - 2, (int) ((barRect.width() - 2) * (percent / 100.0))));
        if (filledW > 0) {
            extractor.fill(barRect.x() + 1, barRect.y() + 1, barRect.x() + 1 + filledW, barRect.bottom() - 1, GZTheme.COLOR_EMERALD);
        }
        y += 11;
        TextUtil.drawScaledText(extractor, font, percent + " %", x, y, TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_MUTED, false);
    }

    private void renderReadyToInstallPanel(GuiGraphicsExtractor extractor, Font font, int x, int y, int maxW, int mouseX, int mouseY,
                                            CompanionSession session, UpdateManager manager, UpdateManager.Snapshot snapshot) {
        TextUtil.drawScaledText(extractor, font, "✓ Uppdateringen är redo", x, y, TypographyScale.SMALL.getScale(), GZTheme.COLOR_STATUS_GREEN, true);
        y += 12;
        y += TextUtil.drawScaledWrappedText(extractor, font, "Minecraft behöver startas om för att installera den.", x, y, maxW,
                TypographyScale.SMALL.getScale(), 2, 1, GZTheme.COLOR_TEXT_SECONDARY, false) + 6;

        UiRect applyBtn = new UiRect(x, y, Math.min(140, maxW), 13);
        GZTheme.drawButton(extractor, font, applyBtn, "Stäng och uppdatera", true, applyBtn.contains(mouseX, mouseY), TypographyScale.SMALL.getScale());
        homeHitTargets.add(new HomeHit(applyBtn, () -> {
            long pid = ProcessHandle.current().pid();
            boolean started = manager.applyUpdate(pid);
            if (started) {
                session.getBridge().requestGracefulShutdown();
            }
        }));

        UiRect laterBtn = new UiRect(applyBtn.right() + 4, y, Math.min(80, maxW - applyBtn.width() - 4), 13);
        GZTheme.drawButton(extractor, font, laterBtn, "Senare", false, laterBtn.contains(mouseX, mouseY), TypographyScale.SMALL.getScale());
        homeHitTargets.add(new HomeHit(laterBtn, () -> showUpdatePanel = false));
    }

    private void renderErrorPanel(GuiGraphicsExtractor extractor, Font font, int x, int y, int maxW, int mouseX, int mouseY,
                                   UpdateManager manager, UpdateManager.Snapshot snapshot) {
        String message = snapshot.errorMessage() != null ? snapshot.errorMessage() : "Ett okänt fel uppstod.";
        y += TextUtil.drawScaledWrappedText(extractor, font, message, x, y, maxW, TypographyScale.SMALL.getScale(), 3, 1, GZTheme.COLOR_STATUS_RED, false) + 8;

        UiRect retryBtn = new UiRect(x, y, Math.min(110, maxW), 13);
        GZTheme.drawButton(extractor, font, retryBtn, "Försök igen", false, retryBtn.contains(mouseX, mouseY), TypographyScale.SMALL.getScale());
        homeHitTargets.add(new HomeHit(retryBtn, () -> {
            if (snapshot.readyInstallerPath() != null) {
                long pid = ProcessHandle.current().pid();
                boolean started = manager.applyUpdate(pid);
                if (started) {
                    CompanionSession.getInstance().getBridge().requestGracefulShutdown();
                }
            } else {
                manager.startDownload();
            }
        }));

        UiRect laterBtn = new UiRect(retryBtn.right() + 4, y, Math.min(80, maxW - retryBtn.width() - 4), 13);
        GZTheme.drawButton(extractor, font, laterBtn, "Senare", false, laterBtn.contains(mouseX, mouseY), TypographyScale.SMALL.getScale());
        homeHitTargets.add(new HomeHit(laterBtn, () -> {
            manager.dismiss();
            showUpdatePanel = false;
        }));
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

        if (showUpdatePanel) {
            for (HomeHit hit : homeHitTargets) {
                if (hit.rect().contains(mouseX, mouseY)) {
                    hit.action().run();
                    return true;
                }
            }
            return true; // swallow clicks elsewhere while the modal update panel is open
        }

        // Must match the exact same banner-height/scroll-offset shift render() drew with, so
        // hit-rects line up with what's actually on screen.
        boolean bannerShown = isUpdateBannerState(CompanionSession.getInstance().getUpdateManager().getSnapshot().state());
        int bannerH = bannerShown ? UPDATE_BANNER_H : 0;
        calculateLayout(new UiRect(bounds.x(), bounds.y() + bannerH - homeScrollOffset, bounds.width(), bounds.height() - bannerH));

        for (HomeHit hit : homeHitTargets) {
            if (hit.rect().contains(mouseX, mouseY)) {
                hit.action().run();
                return true;
            }
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
        if (showAdvisor || showUpdatePanel) return false;
        homeScrollOffset = Math.max(0, Math.min(homeMaxScroll, homeScrollOffset - (int) (scrollY * 14)));
        return true;
    }

    private void showToast(String message, long durationMs) {
        this.feedbackMessage = message;
        this.feedbackExpiry = System.currentTimeMillis() + durationMs;
    }
}