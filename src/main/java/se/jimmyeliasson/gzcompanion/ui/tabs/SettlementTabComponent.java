package se.jimmyeliasson.gzcompanion.ui.tabs;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;
import se.jimmyeliasson.gzcompanion.chest.material.PlannerMaterialRequests;
import se.jimmyeliasson.gzcompanion.chest.model.StoredContainer;
import se.jimmyeliasson.gzcompanion.core.CompanionSession;
import se.jimmyeliasson.gzcompanion.gamezone.GameZoneLiveContext;
import se.jimmyeliasson.gzcompanion.gamezone.GameZoneLiveContextBuilder;
import se.jimmyeliasson.gzcompanion.gamezone.status.GameZoneStatusFormatter;
import se.jimmyeliasson.gzcompanion.knowledge.common.KnowledgeModuleStatus;
import se.jimmyeliasson.gzcompanion.knowledge.common.VerificationMetadata;
import se.jimmyeliasson.gzcompanion.knowledge.common.VerificationStatus;
import se.jimmyeliasson.gzcompanion.knowledge.crafting.bridge.MinecraftRecipeDisplayAdapter;
import se.jimmyeliasson.gzcompanion.knowledge.settlement.ItemRequirement;
import se.jimmyeliasson.gzcompanion.knowledge.settlement.LevelRangeSummary;
import se.jimmyeliasson.gzcompanion.knowledge.settlement.SettlementCatalog;
import se.jimmyeliasson.gzcompanion.knowledge.settlement.SettlementFoundation;
import se.jimmyeliasson.gzcompanion.knowledge.settlement.SettlementLevel;
import se.jimmyeliasson.gzcompanion.minecraft.OnlinePlayerSnapshot;
import se.jimmyeliasson.gzcompanion.settlement.EffectiveCurrentLevel;
import se.jimmyeliasson.gzcompanion.settlement.LiveLevelAlignment;
import se.jimmyeliasson.gzcompanion.settlement.SettlementLiveView;
import se.jimmyeliasson.gzcompanion.settlement.SettlementPlannerManager;
import se.jimmyeliasson.gzcompanion.settlement.storage.MemberNote;
import se.jimmyeliasson.gzcompanion.settlement.storage.SettlementPlannerProfile;
import se.jimmyeliasson.gzcompanion.ui.GZCompanionMainScreen;
import se.jimmyeliasson.gzcompanion.ui.GZTheme;
import se.jimmyeliasson.gzcompanion.ui.ItemHoverTooltips;
import se.jimmyeliasson.gzcompanion.ui.ReferenceModeBanner;
import se.jimmyeliasson.gzcompanion.ui.IconId;
import se.jimmyeliasson.gzcompanion.ui.TextInputHandler;
import se.jimmyeliasson.gzcompanion.ui.TypographyScale;
import se.jimmyeliasson.gzcompanion.ui.layout.SettlementLayout;
import se.jimmyeliasson.gzcompanion.ui.layout.TextUtil;
import se.jimmyeliasson.gzcompanion.ui.layout.UiRect;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Renders the Settlement tab: a LIVE GameZone settlement dashboard when connected and a
 * settlement is safely recognized, backed by a local reference/planner/calculator/organizer built
 * on verified GameZone Rule Pack knowledge (the current "Settlement Levels 1.0" progression) for
 * everything live data can't (or doesn't yet) cover.
 *
 * <p><b>Live data</b> (name/role/bonus/treasury/level, and same-settlement online players) comes
 * ONLY from {@link GameZoneLiveContextBuilder#refresh} - the exact same shared trackers/parsers
 * Home and Online already use - never a second parser, never a new network/entity/world scan. See
 * {@link SettlementLiveView} for how a raw live level is checked against the bundled {@link
 * SettlementCatalog} before it is trusted to drive automatic planning (a mismatch is shown
 * honestly but never trusted), and {@link EffectiveCurrentLevel} for how a trusted live level
 * takes over Progression/Material planning WITHOUT ever mutating the player's own manually saved
 * "Planerad nuvarande nivå".
 *
 * <p>When disconnected, or when no settlement can be safely recognized from live data, every
 * existing local/offline planning feature continues to work exactly as before - this tab never
 * claims to know the player's actual server-observed roster or inventory beyond what live TAB
 * data and local planning state legitimately provide.
 */
public class SettlementTabComponent implements TextInputHandler {
    private static final int ROW_H = 16;
    private static final int MATERIAL_ROW_H = 22;
    private static final int MEMBER_ROW_H = 24;
    private static final int GRID_ICON_SIZE = 14;

    enum Mode {
        OVERSIKT("Översikt"), PROGRESSION("Progression"), MATERIAL("Material"), MEDLEMMAR("Medlemmar");

        final String label;

        Mode(String label) {
            this.label = label;
        }

        Mode next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    private enum EditField { NAME, NOTE }

    private record ListRowHit(UiRect rect, Runnable action) {}

    private Mode mode = Mode.OVERSIKT;
    private SettlementLayout layout;

    // --- Översikt state ---
    /** Independent from every other mode's scroll offset - the LIVE settlement work added enough
     * content (live card, NÄSTA NIVÅ card, foundation info, verification trail) that Översikt can
     * now genuinely overflow its panel on a normal-sized window, which it never did before. */
    private int overviewScroll = 0;

    // --- Progression state ---
    private Integer progressionSelectedLevel = null;
    private int progressionListScroll = 0;
    private int progressionDetailScroll = 0;
    private boolean compactShowingDetail = false;

    // --- Material state ---
    private int materialScroll = 0;
    private boolean showingContainerPicker = false;
    private final java.util.Set<String> selectedContainerKeys = new java.util.HashSet<>();

    // --- Medlemmar state ---
    private int membersScroll = 0;
    private boolean editingMember = false;
    private String editingMemberId = null;
    private String editName = "";
    private String editNote = "";
    private EditField focusedField = null;
    private String pendingDeleteMemberId = null;
    private long pendingDeleteAtMs = 0L;

    private final List<ListRowHit> hitTargets = new ArrayList<>();
    private final ItemHoverTooltips itemHoverTooltips = new ItemHoverTooltips();
    /** The screen this tab last rendered into - used only to hand a material request to Kistor. */
    private GZCompanionMainScreen currentScreen;

    public ItemHoverTooltips getItemHoverTooltips() {
        return itemHoverTooltips;
    }

    @Override
    public boolean isTextInputFocused() {
        return mode == Mode.MEDLEMMAR && editingMember && focusedField != null;
    }

    public void render(GuiGraphicsExtractor extractor, Font font, UiRect bounds, int mouseX, int mouseY, GZCompanionMainScreen mainScreen) {
        // Settlement is GameZone-specific reference/planning data - show a small, unobtrusive note
        // when the current server/world isn't GameZoneMC, so its verified facts and local plans
        // are never mistaken for the current server's actual state. The layout must RESERVE this
        // strip rather than let the banner overlay live content.
        boolean showReferenceBanner = !CompanionSession.getInstance().isConnectedToGameZone();
        renderContent(extractor, font, ReferenceModeBanner.reserveBottomSpace(bounds, showReferenceBanner), mouseX, mouseY, mainScreen);
        if (showReferenceBanner) {
            ReferenceModeBanner.renderAtBottom(extractor, font, bounds);
        }
    }

    private void renderContent(GuiGraphicsExtractor extractor, Font font, UiRect bounds, int mouseX, int mouseY, GZCompanionMainScreen mainScreen) {
        this.currentScreen = mainScreen;
        this.layout = SettlementLayout.calculate(bounds);
        hitTargets.clear();
        itemHoverTooltips.clear();

        CompanionSession session = CompanionSession.getInstance();
        KnowledgeModuleStatus status = session.getSettlementCatalogStatus();

        // Refreshed via the SAME shared builder Home/Online use - see GameZoneLiveContextBuilder's
        // own doc comment for why this guarantees correct live data even if Settlement is the
        // very first tab opened this session (requirement: "same data from any tab").
        boolean connected = session.isConnectedToGameZone();
        String tabHeaderText = connected ? session.getBridge().getTabHeaderText().orElse(null) : null;
        List<OnlinePlayerSnapshot> onlinePlayers = connected ? session.getBridge().getOnlinePlayers() : List.of();
        GameZoneLiveContext liveContext = GameZoneLiveContextBuilder.refresh(
                session.getLiveStatusTracker(), session.getSettlementTracker(), connected, tabHeaderText, onlinePlayers);

        renderHeader(extractor, font, layout.headerRect(), status, liveContext);
        renderModeButton(extractor, font, layout.modeBtnRect(), mouseX, mouseY);

        if (!status.isAvailable()) {
            drawUnavailableState(extractor, font, layout.panelRect(), status);
            return;
        }

        SettlementCatalog catalog = session.getSettlementCatalog();
        if (catalog.size() == 0) {
            renderMessage(extractor, font, layout.panelRect(), "Ingen settlement-progression är tillgänglig just nu.");
            return;
        }

        SettlementLiveView live = SettlementLiveView.from(liveContext, catalog);
        String contextKey = session.getCurrentStorageContext();
        SettlementPlannerManager planner = session.getSettlementPlannerManager();
        SettlementPlannerProfile profile = planner.getProfile(contextKey);
        EffectiveCurrentLevel effective = EffectiveCurrentLevel.resolve(live.liveLevel(), profile.currentLevel());

        switch (mode) {
            case OVERSIKT -> renderOverview(extractor, font, layout.panelRect(), catalog, profile, live, effective, session);
            case PROGRESSION -> renderProgression(extractor, font, catalog, planner, profile, contextKey, mouseX, mouseY, effective);
            case MATERIAL -> renderMaterial(extractor, font, session, catalog, planner, profile, contextKey, mouseX, mouseY, effective, live);
            case MEDLEMMAR -> renderMembers(extractor, font, planner, profile, contextKey, mouseX, mouseY, live, session);
        }
    }

    private void renderHeader(GuiGraphicsExtractor extractor, Font font, UiRect headerRect, KnowledgeModuleStatus status, GameZoneLiveContext liveContext) {
        GZTheme.drawIcon(extractor, IconId.SETTLEMENT, headerRect.x(), headerRect.y() + 1, 10, GZTheme.COLOR_MINT);
        TextUtil.drawScaledText(extractor, font, "Settlement", headerRect.x() + 13, headerRect.y() + 1,
                TypographyScale.HEADING.getScale(), GZTheme.COLOR_TEXT_PRIMARY, true);

        // A catalog load problem is rarer and more actionable than the normal LIVE/PLANERING
        // state, so it keeps priority in this one badge slot; the "Laddad" catalog-loaded status
        // itself (the common case) is shown unobtrusively inside Översikt instead - see
        // renderOverview - rather than crowding this already-tight single-line header further.
        String badgeLabel;
        int dot;
        if (!status.isAvailable()) {
            badgeLabel = status.getDisplayName();
            dot = GZTheme.COLOR_STATUS_RED;
        } else if (liveContext.settlementRecognized()) {
            badgeLabel = "LIVE";
            dot = GZTheme.COLOR_STATUS_GREEN;
        } else {
            badgeLabel = "PLANERING";
            dot = GZTheme.COLOR_STATUS_GREY;
        }
        int badgeW = TextUtil.scaledWidth(font, badgeLabel, TypographyScale.META.getScale()) + 14;
        GZTheme.drawBadge(extractor, font, headerRect.right() - badgeW, headerRect.y(), badgeLabel, GZTheme.COLOR_TEXT_SECONDARY, dot);
    }

    private void renderModeButton(GuiGraphicsExtractor extractor, Font font, UiRect rect, int mouseX, int mouseY) {
        boolean hov = rect.contains(mouseX, mouseY);
        GZTheme.drawCard(extractor, rect, GZTheme.COLOR_NAV_ACTIVE, GZTheme.COLOR_BORDER_EMERALD);
        TextUtil.drawScaledEllipsizedText(extractor, font, "Läge: " + mode.label + "  (klicka för att byta)", rect.x() + 3, rect.y() + 2,
                rect.width() - 6, TypographyScale.META.getScale(), hov ? GZTheme.COLOR_TEXT_PRIMARY : GZTheme.COLOR_TEXT_SECONDARY, false);
    }

    private void drawUnavailableState(GuiGraphicsExtractor extractor, Font font, UiRect area, KnowledgeModuleStatus status) {
        GZTheme.drawCard(extractor, area, GZTheme.COLOR_CARD_BG, GZTheme.COLOR_BORDER_SUBTLE);
        String msg = switch (status) {
            case ERROR -> "Fel inträffade vid inläsning av settlement-datan.";
            case UNAVAILABLE -> "Settlement-data är inte tillgänglig just nu.";
            case INCOMPATIBLE -> "Settlement-datan är sparad med ett schema som inte stöds av denna version.";
            case LOADED -> "Laddar...";
        };
        TextUtil.drawCenteredText(extractor, font, msg, area.x() + (area.width() / 2),
                area.y() + (area.height() / 2) - 4, area.width(), GZTheme.COLOR_TEXT_MUTED, false);
    }

    private void renderMessage(GuiGraphicsExtractor extractor, Font font, UiRect area, String message) {
        GZTheme.drawCard(extractor, area, GZTheme.COLOR_CARD_BG, GZTheme.COLOR_BORDER_SUBTLE);
        int maxW = area.width() - 20;
        TextUtil.drawScaledWrappedText(extractor, font, message, area.x() + 10, area.y() + 10, maxW,
                TypographyScale.SMALL.getScale(), 4, 1, GZTheme.COLOR_TEXT_SECONDARY, false);
    }

    // ------------------------------------------------------------------
    // ÖVERSIKT
    // ------------------------------------------------------------------

    private static final String NO_SETTLEMENT_MESSAGE = "Inget settlement kunde identifieras från GameZones live-data.";
    private static final String FOUNDATION_UNAVAILABLE_MESSAGE = "Grundläggande settlement-fakta kunde inte laddas i denna omgång.";
    /** Height of the final "Rule Pack: ..." status line (a single, never-wrapped META-scale line)
     * plus a small trailing margin - see {@link #estimateOverviewContentHeight}. */
    private static final int OVERVIEW_TRAILING_LINE_H = 9 + 4;

    /**
     * Översikt's content (LIVE card / no-settlement message, progression summary, effective
     * current/target level, NÄSTA NIVÅ card, foundation info + verification trail, Rule Pack
     * status) can genuinely exceed the panel's height, so this is scissored and scrolled exactly
     * like Progression/Material/Medlemmar already are - see {@link #overviewScroll} and {@link
     * #estimateOverviewContentHeight}, which MUST mirror {@link #renderOverviewContent}'s own
     * increments so the computed max scroll always matches what is actually drawn.
     */
    private void renderOverview(GuiGraphicsExtractor extractor, Font font, UiRect area, SettlementCatalog catalog,
                                 SettlementPlannerProfile profile, SettlementLiveView live, EffectiveCurrentLevel effective,
                                 CompanionSession session) {
        GZTheme.drawCard(extractor, area, GZTheme.COLOR_CARD_BG, GZTheme.COLOR_BORDER_SUBTLE);
        int maxW = area.width() - 12;
        int contentTop = area.y() + 5;
        int viewportBottom = area.bottom() - 2;
        int visibleHeight = Math.max(1, viewportBottom - contentTop);

        int contentHeight = estimateOverviewContentHeight(font, maxW, catalog, profile, live, effective, session);
        overviewScroll = clampScroll(overviewScroll, contentHeight, visibleHeight);

        extractor.enableScissor(area.x() + 1, contentTop, area.right() - 1, viewportBottom);
        renderOverviewContent(extractor, font, area.x() + 6, contentTop - overviewScroll, maxW, catalog, profile, live, effective, session);
        extractor.disableScissor();
    }

    private void renderOverviewContent(GuiGraphicsExtractor extractor, Font font, int x, int y, int maxW, SettlementCatalog catalog,
                                        SettlementPlannerProfile profile, SettlementLiveView live, EffectiveCurrentLevel effective,
                                        CompanionSession session) {
        if (live.settlementRecognized()) {
            y = renderLiveOverviewCard(extractor, font, x, y, maxW, live);
        } else if (live.connected()) {
            y += TextUtil.drawScaledWrappedText(extractor, font, NO_SETTLEMENT_MESSAGE, x, y, maxW,
                    TypographyScale.SMALL.getScale(), 2, 1, GZTheme.COLOR_TEXT_MUTED, false);
        }

        TextUtil.drawScaledText(extractor, font, "Progression: " + catalog.size() + " nivåer, " + (catalog.size() - 1) + " uppgraderingar",
                x, y, TypographyScale.SMALL.getScale(), GZTheme.COLOR_TEXT_PRIMARY, false);
        y += 11;

        String currentLine;
        if (effective.known()) {
            currentLine = effective.live()
                    ? "Nuvarande nivå (LIVE): " + effective.level()
                    : "Planerad nuvarande nivå: " + effective.level();
        } else {
            currentLine = "Välj din nuvarande nivå i Progression-läget.";
        }
        TextUtil.drawScaledEllipsizedText(extractor, font, currentLine, x, y, maxW, TypographyScale.SMALL.getScale(), GZTheme.COLOR_TEXT_SECONDARY, false);
        y += 10;

        String targetLine = profile.targetLevel() != null
                ? "Mål-nivå: " + profile.targetLevel()
                : "Inget mål valt än.";
        TextUtil.drawScaledEllipsizedText(extractor, font, targetLine, x, y, maxW, TypographyScale.SMALL.getScale(), GZTheme.COLOR_TEXT_SECONDARY, false);
        y += 12;

        y = renderNextLevelCard(extractor, font, x, y, maxW, catalog, effective, session);

        SettlementFoundation foundation = catalog.foundation();
        if (foundation != null) {
            if (foundation.creationCommand() != null) {
                TextUtil.drawScaledEllipsizedText(extractor, font, "Skapa: " + foundation.creationCommand() + " (start: nivå " + foundation.startLevel() + ")",
                        x, y, maxW, TypographyScale.SMALL.getScale(), GZTheme.COLOR_TEXT_SECONDARY, false);
                y += 10;
            }
            y += 2;
            y += renderVerificationTrail(extractor, font, x, y, maxW, foundation.verification());
        } else {
            y += TextUtil.drawScaledWrappedText(extractor, font, FOUNDATION_UNAVAILABLE_MESSAGE, x, y, maxW,
                    TypographyScale.META.getScale(), 2, 1, GZTheme.COLOR_TEXT_MUTED, false);
        }

        String catalogBadge = session.getSettlementCatalogStatus().isAvailable() ? "Rule Pack: Laddad" : "Rule Pack: " + session.getSettlementCatalogStatus().getDisplayName();
        TextUtil.drawScaledText(extractor, font, catalogBadge, x, y, TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_MUTED, false);
    }

    /**
     * Pure content-height estimate for Översikt - MUST mirror {@link #renderOverviewContent}'s own
     * increments exactly (increment for increment), so the computed max scroll always matches
     * what is actually drawn. Only the two genuinely-wrapped messages need {@code font} at all
     * (via {@link TextUtil#measureWrappedHeightCapped}, the same capped-measurement/actual-drawn
     * pairing this project already uses for Bounty's/Progression's own scrollable detail panes);
     * every other line is a fixed-height single ellipsized/plain line.
     */
    private int estimateOverviewContentHeight(Font font, int maxW, SettlementCatalog catalog, SettlementPlannerProfile profile,
                                                SettlementLiveView live, EffectiveCurrentLevel effective, CompanionSession session) {
        int h = 0;
        if (live.settlementRecognized()) {
            h += liveOverviewCardHeight(live);
        } else if (live.connected()) {
            h += TextUtil.measureWrappedHeightCapped(font, NO_SETTLEMENT_MESSAGE, maxW, TypographyScale.SMALL.getScale(), 2, 1);
        }

        h += 11; // progression summary line
        h += 10; // current-level line
        h += 12; // target-level line

        h += nextLevelCardHeight(catalog, effective);

        SettlementFoundation foundation = catalog.foundation();
        if (foundation != null) {
            if (foundation.creationCommand() != null) h += 10;
            h += 2;
            h += estimateVerificationTrailHeight(foundation.verification());
        } else {
            h += TextUtil.measureWrappedHeightCapped(font, FOUNDATION_UNAVAILABLE_MESSAGE, maxW, TypographyScale.META.getScale(), 2, 1);
        }

        return h + OVERVIEW_TRAILING_LINE_H;
    }

    /**
     * "MITT SETTLEMENT · LIVE" - name, level·level-name, role, bonus, treasury, and how many
     * currently-online players share the local player's settlement (never a full roster - see
     * {@link SettlementLiveView#sameSettlementOnlineUsernames()}'s own doc comment). A raw
     * live/catalog mismatch is shown as an honest, restrained warning rather than hidden or
     * silently "corrected" - GameZone's own reported level/name always wins visually.
     */
    // Named row heights for the LIVE overview card, shared verbatim between renderLiveOverviewCard
    // (which draws each row using these exact constants) and liveOverviewCardHeight (which sums
    // the exact same constants). A prior version hardcoded these as bare literals in TWO places
    // that were supposed to agree but didn't - liveOverviewCardHeight silently omitted the online-
    // members row entirely, underestimating the card by 10px and clipping the bottom of Översikt.
    // Using ONE named constant per row for both the draw call's own increment AND the height sum
    // makes that specific class of drift a compile-time-visible single source of truth instead of
    // two coincidentally-matching numbers.
    private static final int LIVE_CARD_HEADER_ROW_H = 11;
    private static final int LIVE_CARD_NAME_ROW_H = 11;
    private static final int LIVE_CARD_LEVEL_ROW_H = 10;
    private static final int LIVE_CARD_ROLE_ROW_H = 10;
    private static final int LIVE_CARD_BONUS_ROW_H = 10;
    private static final int LIVE_CARD_TREASURY_ROW_H = 10;
    private static final int LIVE_CARD_ONLINE_ROW_H = 10;
    private static final int LIVE_CARD_MISMATCH_ROW_H = 10;
    private static final int LIVE_CARD_BOTTOM_MARGIN = 4;

    /** Net vertical space {@link #renderLiveOverviewCard} occupies (its background card height
     * plus the bottom margin it returns) - sums the SAME named row constants {@link
     * #renderLiveOverviewCard} draws with, so the two can no longer silently diverge (see the
     * constants' own doc comment above for exactly how they drifted before). */
    static int liveOverviewCardHeight(SettlementLiveView live) {
        int h = LIVE_CARD_HEADER_ROW_H + LIVE_CARD_NAME_ROW_H + LIVE_CARD_LEVEL_ROW_H + LIVE_CARD_ROLE_ROW_H
                + LIVE_CARD_BONUS_ROW_H + LIVE_CARD_TREASURY_ROW_H + LIVE_CARD_ONLINE_ROW_H;
        if (live.liveLevel().alignment() == LiveLevelAlignment.MISMATCH) h += LIVE_CARD_MISMATCH_ROW_H;
        return h + LIVE_CARD_BOTTOM_MARGIN;
    }

    private int renderLiveOverviewCard(GuiGraphicsExtractor extractor, Font font, int x, int y, int maxW, SettlementLiveView live) {
        int cardH = liveOverviewCardHeight(live) - LIVE_CARD_BOTTOM_MARGIN; // the background card itself excludes the trailing bottom margin
        UiRect card = new UiRect(x - 2, y - 2, maxW + 4, cardH);
        GZTheme.drawCard(extractor, card, GZTheme.COLOR_CARD_INNER, GZTheme.COLOR_BORDER_EMERALD);

        TextUtil.drawScaledText(extractor, font, "MITT SETTLEMENT", x, y, TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_MUTED, false);
        String liveBadge = "LIVE";
        int liveBadgeW = TextUtil.scaledWidth(font, liveBadge, TypographyScale.META.getScale());
        GZTheme.drawStatusDot(extractor, x + maxW - liveBadgeW - 8, y + 3, GZTheme.COLOR_STATUS_GREEN);
        TextUtil.drawScaledText(extractor, font, liveBadge, x + maxW - liveBadgeW - 2, y, TypographyScale.META.getScale(), GZTheme.COLOR_STATUS_GREEN, false);
        y += LIVE_CARD_HEADER_ROW_H;

        TextUtil.drawScaledEllipsizedText(extractor, font, live.settlementName(), x, y, maxW,
                TypographyScale.HEADING.getScale(), GZTheme.COLOR_MINT, true);
        y += LIVE_CARD_NAME_ROW_H;

        String levelLine = live.liveLevel().level() != null
                ? "Nivå " + live.liveLevel().level() + (live.liveLevel().levelName() != null ? " · " + live.liveLevel().levelName() : "")
                : "Nivå okänd";
        TextUtil.drawScaledEllipsizedText(extractor, font, levelLine, x, y, maxW, TypographyScale.SMALL.getScale(), GZTheme.COLOR_TEXT_SECONDARY, false);
        y += LIVE_CARD_LEVEL_ROW_H;

        String roleLine = "Roll: " + (live.role() != null ? live.role() : "Okänd");
        TextUtil.drawScaledEllipsizedText(extractor, font, roleLine, x, y, maxW, TypographyScale.SMALL.getScale(), GZTheme.COLOR_TEXT_SECONDARY, false);
        y += LIVE_CARD_ROLE_ROW_H;

        String bonusLine = "Settlementbonus: " + GameZoneStatusFormatter.formatBonusPercent(live.bonusPercent());
        TextUtil.drawScaledEllipsizedText(extractor, font, bonusLine, x, y, maxW, TypographyScale.SMALL.getScale(), GZTheme.COLOR_TEXT_SECONDARY, false);
        y += LIVE_CARD_BONUS_ROW_H;

        String treasuryLine = "Stadskassa: " + GameZoneStatusFormatter.formatMoney(live.treasury()) + " Coins";
        TextUtil.drawScaledEllipsizedText(extractor, font, treasuryLine, x, y, maxW, TypographyScale.SMALL.getScale(), GZTheme.COLOR_TEXT_SECONDARY, false);
        y += LIVE_CARD_TREASURY_ROW_H;

        int online = live.sameSettlementOnlineUsernames().size();
        String onlineLine = online + (online == 1 ? " från ditt settlement online" : " settlementmedlemmar online");
        TextUtil.drawScaledEllipsizedText(extractor, font, onlineLine, x, y, maxW, TypographyScale.SMALL.getScale(), GZTheme.COLOR_TEXT_MUTED, false);
        y += LIVE_CARD_ONLINE_ROW_H;

        if (live.liveLevel().alignment() == LiveLevelAlignment.MISMATCH) {
            TextUtil.drawScaledWrappedText(extractor, font, "Live-data och Companion-datan skiljer sig.", x, y, maxW,
                    TypographyScale.META.getScale(), 2, 1, GZTheme.COLOR_STATUS_YELLOW, false);
            y += LIVE_CARD_MISMATCH_ROW_H;
        }

        return y + LIVE_CARD_BOTTOM_MARGIN;
    }

    /**
     * NÄSTA NIVÅ - number, name, coin cost, building requirement/unlock summary, and a material
     * requirement COUNT (never an invented completion percentage - see class doc comment). Uses
     * {@code effective}'s level (live-trusted or manually planned) exactly like Progression/
     * Material do, so this card always agrees with the rest of the tab about which level is
     * "current."
     */
    /** Net vertical space {@link #renderNextLevelCard} occupies, given the same catalog/effective
     * inputs - extracted so {@link #estimateOverviewContentHeight} can never drift out of sync
     * with what is actually drawn. Font-independent: every line here is a fixed-height single
     * ellipsized/plain line, never wrapped. */
    static int nextLevelCardHeight(SettlementCatalog catalog, EffectiveCurrentLevel effective) {
        if (!effective.known()) return 0;
        SettlementLevel next = catalog.byLevel(effective.level() + 1).orElse(null);
        if (next == null) return 0;
        int h = 9 + 10 + 10; // "NÄSTA NIVÅ" label + name line + cost line
        if (next.requiredBuildingName() != null) h += 9;
        return h + 4;
    }

    private int renderNextLevelCard(GuiGraphicsExtractor extractor, Font font, int x, int y, int maxW,
                                     SettlementCatalog catalog, EffectiveCurrentLevel effective, CompanionSession session) {
        if (!effective.known()) return y;
        SettlementLevel next = catalog.byLevel(effective.level() + 1).orElse(null);
        if (next == null) return y;

        TextUtil.drawScaledText(extractor, font, "NÄSTA NIVÅ", x, y, TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_MUTED, false);
        y += 9;
        TextUtil.drawScaledEllipsizedText(extractor, font, next.level() + " · " + next.name(), x, y, maxW,
                TypographyScale.SMALL.getScale(), GZTheme.COLOR_MINT, true);
        y += 10;
        TextUtil.drawScaledEllipsizedText(extractor, font, GameZoneStatusFormatter.formatMoney(next.coinCost()) + " Coins"
                        + (next.items().isEmpty() ? "" : " · " + next.items().size() + " materialkrav"),
                x, y, maxW, TypographyScale.SMALL.getScale(), GZTheme.COLOR_TEXT_SECONDARY, false);
        y += 10;
        if (next.requiredBuildingName() != null) {
            TextUtil.drawScaledEllipsizedText(extractor, font, "Kräver: " + next.requiredBuildingName(), x, y, maxW,
                    TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_MUTED, false);
            y += 9;
        }
        return y + 4;
    }

    // ------------------------------------------------------------------
    // PROGRESSION
    // ------------------------------------------------------------------

    private void renderProgression(GuiGraphicsExtractor extractor, Font font, SettlementCatalog catalog,
                                    SettlementPlannerManager planner, SettlementPlannerProfile profile, String contextKey,
                                    int mouseX, int mouseY, EffectiveCurrentLevel effective) {
        boolean showUnverified = CompanionSession.getInstance().getSettingsManager().getSettings().showUnverifiedKnowledge();
        List<SettlementLevel> levels = showUnverified ? catalog.levels()
                : catalog.levels().stream().filter(l -> l.verification().status() == VerificationStatus.VERIFIED).toList();
        if (levels.isEmpty()) levels = catalog.levels();

        if (progressionSelectedLevel == null || catalog.byLevel(progressionSelectedLevel).isEmpty()) {
            Integer preferred = effective.known() && catalog.byLevel(effective.level()).isPresent() ? effective.level() : null;
            progressionSelectedLevel = preferred != null ? preferred : levels.get(0).level();
        }

        if (layout.isCompact()) {
            if (compactShowingDetail) {
                renderProgressionDetail(extractor, font, layout.detailRect(), catalog, planner, profile, contextKey, mouseX, mouseY, true, effective);
            } else {
                renderProgressionList(extractor, font, layout.listRect(), levels, mouseX, mouseY, effective, profile);
            }
        } else {
            renderProgressionList(extractor, font, layout.listRect(), levels, mouseX, mouseY, effective, profile);
            renderProgressionDetail(extractor, font, layout.detailRect(), catalog, planner, profile, contextKey, mouseX, mouseY, false, effective);
        }
    }

    /** Past/current/next/target derivation for one progression row - purely a display decision,
     * never persisted (see docs/SETTLEMENT-COMPANION.md's Progression section). A level can
     * legitimately be BOTH the effective current/next level AND the chosen target - all flags are
     * independent so the UI can show both badges together rather than picking only one. */
    record ProgressionRowState(boolean past, boolean current, boolean next, boolean target) {
        boolean any() {
            return past || current || next || target;
        }
    }

    static ProgressionRowState progressionRowState(int level, EffectiveCurrentLevel effective, Integer targetLevel) {
        boolean past = effective.known() && level < effective.level();
        boolean current = effective.known() && level == effective.level();
        boolean next = effective.known() && level == effective.level() + 1;
        boolean target = targetLevel != null && level == targetLevel;
        return new ProgressionRowState(past, current, next, target);
    }

    private static int calculateMaxScroll(int rowH, int rowCount, int visibleH, int headerH) {
        int totalH = headerH + (rowCount * rowH);
        return Math.max(0, totalH - Math.max(1, visibleH));
    }

    /**
     * Pure scroll-offset clamp for free-flowing (non-row-based) content, used by Översikt: never
     * negative, never past the point where the actual content ends. If {@code contentHeight} is
     * shorter than (or equal to) {@code viewportHeight}, the max is 0 - everything fits, so no
     * scroll is ever needed or permitted.
     */
    static int clampScroll(int offset, int contentHeight, int viewportHeight) {
        int maxScroll = Math.max(0, contentHeight - Math.max(1, viewportHeight));
        return Math.max(0, Math.min(offset, maxScroll));
    }

    /** Short row-corner label for a progression row's derived state - never more than one word so
     * it always fits the narrow row height; PAST is deliberately silent (no badge) since "already
     * done" is the default/expected state for most rows once a current level is known. */
    static String progressionRowBadge(ProgressionRowState state) {
        if (state.current()) return "HÄR";
        if (state.next()) return "NÄSTA";
        if (state.target()) return "MÅL";
        return "";
    }

    private void renderProgressionList(GuiGraphicsExtractor extractor, Font font, UiRect listRect, List<SettlementLevel> levels,
                                        int mouseX, int mouseY, EffectiveCurrentLevel effective, SettlementPlannerProfile profile) {
        GZTheme.drawCard(extractor, listRect, GZTheme.COLOR_CARD_BG, GZTheme.COLOR_BORDER_SUBTLE);

        int maxScroll = calculateMaxScroll(ROW_H, levels.size(), listRect.height() - 4, 12);
        progressionListScroll = Math.max(0, Math.min(progressionListScroll, maxScroll));

        extractor.enableScissor(listRect.x() + 1, listRect.y() + 1, listRect.right() - 1, listRect.bottom() - 1);
        int currentY = listRect.y() + 3 - progressionListScroll;
        TextUtil.drawScaledText(extractor, font, "NIVÅER", listRect.x() + 4, currentY, TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_MUTED, false);
        currentY += 12;

        for (SettlementLevel level : levels) {
            UiRect rowRect = new UiRect(listRect.x() + 2, currentY, listRect.width() - 4, ROW_H);
            if (currentY + ROW_H >= listRect.y() && currentY <= listRect.bottom()) {
                boolean isSelected = level.level() == progressionSelectedLevel;
                boolean isHovered = rowRect.contains(mouseX, mouseY);
                ProgressionRowState rowState = progressionRowState(level.level(), effective, profile.targetLevel());
                int bg = isSelected ? GZTheme.COLOR_NAV_ACTIVE : (rowState.current() ? GZTheme.COLOR_NAV_HOVER : (isHovered ? GZTheme.COLOR_NAV_HOVER : 0));
                if (bg != 0) GZTheme.drawCard(extractor, rowRect, bg, isSelected || rowState.current() ? GZTheme.COLOR_BORDER_EMERALD : 0);

                GZTheme.drawStatusDot(extractor, rowRect.x() + 4, rowRect.y() + 6, level.verification().status().getArgbColor());
                String badge = progressionRowBadge(rowState);
                int badgeW = badge.isEmpty() ? 0 : TextUtil.scaledWidth(font, badge, TypographyScale.META.getScale()) + 4;
                TextUtil.drawScaledEllipsizedText(extractor, font, level.level() + ". " + level.name(), rowRect.x() + 11, rowRect.y() + 3,
                        rowRect.width() - 15 - badgeW, TypographyScale.SMALL.getScale(), rowState.past() ? GZTheme.COLOR_TEXT_MUTED : GZTheme.COLOR_TEXT_PRIMARY, isSelected);
                if (!badge.isEmpty()) {
                    TextUtil.drawScaledRightAlignedText(extractor, font, badge, rowRect.right() - 2, rowRect.y() + 3, badgeW,
                            TypographyScale.META.getScale(), rowState.current() ? GZTheme.COLOR_STATUS_GREEN : GZTheme.COLOR_MINT, true);
                }

                final int lvl = level.level();
                hitTargets.add(new ListRowHit(rowRect, () -> {
                    progressionSelectedLevel = lvl;
                    progressionDetailScroll = 0;
                    compactShowingDetail = true;
                }));
            }
            currentY += ROW_H;
        }
        extractor.disableScissor();
    }

    private static final int UNLOCK_TEXT_MAX_LINES = 3;

    private int estimateLevelDetailHeight(Font font, int maxW, SettlementLevel level, boolean hasStateBadge) {
        int h = 11 + 10; // heading + coin cost
        if (hasStateBadge) h += 10;
        if (level.requiredBuildingName() != null) h += 10;
        if (level.unlockedBuildingName() != null) {
            h += TextUtil.measureWrappedHeightCapped(font, unlockedBuildingLine(level), maxW,
                    TypographyScale.SMALL.getScale(), UNLOCK_TEXT_MAX_LINES, 1) + 2;
        }
        h += 10; // "MATERIAL" label
        h += level.items().size() * 12;
        h += 6;
        h += estimateVerificationTrailHeight(level.verification());
        return h;
    }

    /**
     * "Låser upp: ..." is important gameplay information (which building/bonus a level unlocks)
     * and must remain fully readable rather than ellipsized - human QA found it cut off in
     * compact mode. Extracted so the render call and the height estimate always agree on content.
     */
    static String unlockedBuildingLine(SettlementLevel level) {
        return "Låser upp: " + level.unlockedBuildingName()
                + (level.unlockedBuildingBonus() != null ? " (" + level.unlockedBuildingBonus() + ")" : "");
    }

    /**
     * Whether this level is the profile's currently-planned settlement level - drives the
     * "Sätt som nuvarande" button's immediate visible feedback (human QA found clicking it gave
     * no indication anything happened until returning to Overview).
     */
    static boolean isCurrentLevel(SettlementPlannerProfile profile, int level) {
        return profile.currentLevel() != null && profile.currentLevel() == level;
    }

    static boolean isTargetLevel(SettlementPlannerProfile profile, int level) {
        return profile.targetLevel() != null && profile.targetLevel() == level;
    }

    static String currentButtonLabel(boolean isCurrent) {
        return isCurrent ? "✓ Nuvarande" : "Sätt som nuvarande";
    }

    static String targetButtonLabel(boolean isTarget) {
        return isTarget ? "✓ Mål" : "Sätt som mål";
    }

    /**
     * While a trusted LIVE level is active, "Sätt som nuvarande" must never look like it changes
     * the player's REAL GameZone level - it still writes to the exact same manual/offline
     * fallback field ({@code SettlementPlannerProfile.currentLevel()}), just clearly relabeled so
     * it reads as an offline override rather than a live-state change. See {@link
     * #currentButtonLabel} for the normal (non-live) labels, which stay exactly as before -
     * existing tests assert those exact strings.
     */
    static String offlineCurrentButtonLabel(boolean isManualCurrent) {
        return isManualCurrent ? "✓ Manuell nuvarande (offline)" : "Sätt som OFFLINE-nuvarande";
    }

    private static final int PROGRESSION_DETAIL_TOP_PAD_COMPACT = 16;
    private static final int PROGRESSION_DETAIL_TOP_PAD_NORMAL = 4;
    /** Height of the fixed bottom action-button strip ("Sätt som OFFLINE-nuvarande" / "Sätt som
     * mål") - matches the button rects' own {@code btnY = detailRect.bottom() - PROGRESSION_DETAIL_BTN_STRIP_H}
     * in {@link #renderProgressionDetail} exactly, so the content viewport this excludes and the
     * buttons' own drawn position can never silently disagree again (see this class' Översikt
     * fix's own "named constants, not two coincidentally-matching literals" reasoning). */
    private static final int PROGRESSION_DETAIL_BTN_STRIP_H = 14;
    /** Small breathing-room gap between the last scrollable content pixel and the button strip. */
    private static final int PROGRESSION_DETAIL_BTN_GAP = 2;

    /**
     * REGRESSION (QA bug #3): the scrollable/scissored content viewport for Progression's detail
     * pane. Previously spanned all the way to {@code detailRect.bottom() - 1}, which INCLUDED the
     * space the fixed bottom action-button strip actually occupies - the max-scroll calculation
     * believed those pixels were visible content, when the buttons were drawn on top of them the
     * whole time. This is the ONE place that decides where the viewport ends; {@link
     * #renderProgressionDetail} uses this SAME {@link UiRect} for the scissor AND the max-scroll
     * calculation, and the fixed button strip's own {@code btnY} is derived from the identical
     * {@link #PROGRESSION_DETAIL_BTN_STRIP_H} constant this excludes - there is no second,
     * independently-computed viewport that could silently disagree with this one.
     */
    static UiRect progressionDetailContentArea(UiRect detailRect, boolean isCompact) {
        int contentTop = detailRect.y() + (isCompact ? PROGRESSION_DETAIL_TOP_PAD_COMPACT : PROGRESSION_DETAIL_TOP_PAD_NORMAL);
        int btnY = detailRect.bottom() - PROGRESSION_DETAIL_BTN_STRIP_H;
        int contentBottom = btnY - PROGRESSION_DETAIL_BTN_GAP;
        int height = Math.max(1, contentBottom - contentTop);
        return new UiRect(detailRect.x() + 1, contentTop, detailRect.width() - 2, height);
    }

    private void renderProgressionDetail(GuiGraphicsExtractor extractor, Font font, UiRect detailRect, SettlementCatalog catalog,
                                          SettlementPlannerManager planner, SettlementPlannerProfile profile, String contextKey,
                                          int mouseX, int mouseY, boolean isCompact, EffectiveCurrentLevel effective) {
        GZTheme.drawCard(extractor, detailRect, GZTheme.COLOR_CARD_BG, GZTheme.COLOR_BORDER_SUBTLE);
        SettlementLevel level = catalog.byLevel(progressionSelectedLevel).orElse(null);
        if (level == null) return;

        UiRect contentArea = progressionDetailContentArea(detailRect, isCompact);
        int pad = 5;
        int maxW = contentArea.width() - (pad * 2);

        ProgressionRowState rowState = progressionRowState(level.level(), effective, profile.targetLevel());
        int maxScroll = Math.max(0, estimateLevelDetailHeight(font, maxW, level, rowState.any()) - contentArea.height());
        progressionDetailScroll = Math.max(0, Math.min(progressionDetailScroll, maxScroll));

        if (isCompact) {
            UiRect backBtn = layout.backBtnRect();
            GZTheme.drawButton(extractor, font, backBtn, "< Lista", false, backBtn.contains(mouseX, mouseY), TypographyScale.SMALL.getScale());
            final boolean fCompact = true;
            hitTargets.add(new ListRowHit(backBtn, () -> compactShowingDetail = false));
        }

        extractor.enableScissor(contentArea.x(), contentArea.y(), contentArea.right(), contentArea.bottom());
        int x = contentArea.x() + pad;
        int y = contentArea.y() + 2 - progressionDetailScroll;

        TextUtil.drawScaledEllipsizedText(extractor, font, level.level() + ". " + level.name(), x, y, maxW,
                TypographyScale.HEADING.getScale(), GZTheme.COLOR_MINT, true);
        y += 11;

        if (rowState.any()) {
            String stateLabel = rowState.current()
                    ? ("DU ÄR HÄR" + (effective.live() ? " · LIVE" : ""))
                    : (rowState.next() ? "NÄSTA" : "");
            if (!stateLabel.isEmpty() && rowState.target()) stateLabel += " · MÅL";
            else if (stateLabel.isEmpty() && rowState.target()) stateLabel = "MÅL";
            TextUtil.drawScaledEllipsizedText(extractor, font, stateLabel, x, y, maxW,
                    TypographyScale.META.getScale(), rowState.current() ? GZTheme.COLOR_STATUS_GREEN : GZTheme.COLOR_MINT, true);
            y += 10;
        }

        TextUtil.drawScaledEllipsizedText(extractor, font, "Kostnad: " + level.coinCost() + " Coins", x, y, maxW,
                TypographyScale.SMALL.getScale(), GZTheme.COLOR_TEXT_SECONDARY, false);
        y += 10;

        if (level.requiredBuildingName() != null) {
            TextUtil.drawScaledEllipsizedText(extractor, font, "Kräver byggnad: " + level.requiredBuildingName(), x, y, maxW,
                    TypographyScale.SMALL.getScale(), GZTheme.COLOR_TEXT_SECONDARY, false);
            y += 10;
        }
        if (level.unlockedBuildingName() != null) {
            y += TextUtil.drawScaledWrappedText(extractor, font, unlockedBuildingLine(level), x, y, maxW,
                    TypographyScale.SMALL.getScale(), UNLOCK_TEXT_MAX_LINES, 1, GZTheme.COLOR_STATUS_YELLOW, false) + 2;
        }

        TextUtil.drawScaledText(extractor, font, "MATERIAL", x, y, TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_MUTED, false);
        y += 10;
        for (ItemRequirement item : level.items()) {
            String variantSuffix = item.distinctVariantsRequired() != null ? " (" + item.distinctVariantsRequired() + " olika)" : "";
            String line = "- " + item.displayName() + " x" + item.count() + variantSuffix;
            TextUtil.drawScaledEllipsizedText(extractor, font, line, x, y, maxW, TypographyScale.SMALL.getScale(), GZTheme.COLOR_TEXT_PRIMARY, false);
            y += 12;
        }

        y += 6;
        renderVerificationTrail(extractor, font, x, y, maxW, level.verification());
        extractor.disableScissor();

        // Planner action buttons live below the scissored content, in the fixed bottom strip.
        // Human QA found "Sätt som nuvarande"/"Sätt som mål" gave no visible feedback once
        // clicked - the label/state below now reflects the CURRENT profile every frame, so the
        // moment a click actually updates the profile it is immediately visible, no chat/toast
        // needed.
        boolean isCurrent = isCurrentLevel(profile, level.level());
        boolean isTarget = isTargetLevel(profile, level.level());

        // While a trusted LIVE level drives planning, this button is clearly relabeled as an
        // OFFLINE/manual fallback action (see offlineCurrentButtonLabel's own doc comment) -
        // it must never look like it changes the player's real GameZone level.
        String currentBtnLabel = effective.live() ? offlineCurrentButtonLabel(isCurrent) : currentButtonLabel(isCurrent);

        int btnY = detailRect.bottom() - PROGRESSION_DETAIL_BTN_STRIP_H;
        int btnW = Math.max(40, (detailRect.width() - 12) / 2);
        UiRect setCurrentBtn = new UiRect(detailRect.x() + 4, btnY, btnW, 11);
        UiRect setTargetBtn = new UiRect(detailRect.x() + 8 + btnW, btnY, btnW, 11);
        GZTheme.drawButton(extractor, font, setCurrentBtn, currentBtnLabel,
                true, setCurrentBtn.contains(mouseX, mouseY), TypographyScale.META.getScale());
        GZTheme.drawButton(extractor, font, setTargetBtn, targetButtonLabel(isTarget),
                isTarget, setTargetBtn.contains(mouseX, mouseY), TypographyScale.META.getScale());

        int selLevel = progressionSelectedLevel;
        hitTargets.add(new ListRowHit(setCurrentBtn, () -> planner.setCurrentLevel(contextKey, selLevel)));
        hitTargets.add(new ListRowHit(setTargetBtn, () -> planner.setTargetLevel(contextKey, selLevel)));
    }

    // ------------------------------------------------------------------
    // MATERIAL
    // ------------------------------------------------------------------

    private void renderMaterial(GuiGraphicsExtractor extractor, Font font, CompanionSession session, SettlementCatalog catalog,
                                 SettlementPlannerManager planner, SettlementPlannerProfile profile, String contextKey,
                                 int mouseX, int mouseY, EffectiveCurrentLevel effective, SettlementLiveView live) {
        UiRect area = layout.panelRect();
        GZTheme.drawCard(extractor, area, GZTheme.COLOR_CARD_BG, GZTheme.COLOR_BORDER_SUBTLE);

        if (!effective.known()) {
            renderMessage(extractor, font, area, "Välj en nuvarande nivå i Progression-läget, eller anslut till GameZone, för att se materiallistan.");
            return;
        }
        if (profile.targetLevel() == null) {
            renderMessage(extractor, font, area, "Välj en mål-nivå i Progression-läget för att se materiallistan.");
            return;
        }
        if (!effective.isValidTarget(profile.targetLevel())) {
            renderMessage(extractor, font, area, "Målnivån måste vara högre än nuvarande nivå.");
            return;
        }

        LevelRangeSummary summary = catalog.levelRange(effective.level(), profile.targetLevel());
        int x = area.x() + 4;
        int maxW = area.width() - 8;
        int y = area.y() + 4;

        String startLine = effective.live()
                ? "Startnivå: " + effective.level() + (live.liveLevel().levelName() != null ? " · " + live.liveLevel().levelName() : "") + " (LIVE)"
                : "Startnivå: " + effective.level() + " (lokal planering)";
        TextUtil.drawScaledEllipsizedText(extractor, font, startLine, x, y, maxW, TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_MUTED, false);
        y += 9;

        TextUtil.drawScaledEllipsizedText(extractor, font,
                "Nivå " + effective.level() + " -> " + profile.targetLevel() + ": " + summary.upgradeCount() + " uppgraderingar, " + summary.totalCoinCost() + " Coins",
                x, y, maxW, TypographyScale.SMALL.getScale(), GZTheme.COLOR_TEXT_PRIMARY, false);
        y += 12;

        boolean chestEstimateEnabled = PlannerMaterialRequests.isChestLookupEnabled(session.getSettingsManager().getSettings());
        if (chestEstimateEnabled) {
            int btnW = Math.min(150, (area.width() - 8 - 3) / 2);
            UiRect calcBtn = new UiRect(x, y, btnW, 11);
            boolean calcHov = calcBtn.contains(mouseX, mouseY);
            GZTheme.drawButton(extractor, font, calcBtn, "Beräkna från sparade kistor", false, calcHov, TypographyScale.META.getScale());
            hitTargets.add(new ListRowHit(calcBtn, () -> showingContainerPicker = !showingContainerPicker));

            // Kistor 2.0: show where this level range's materials were last known to be - local
            // last-known chest snapshots only, never GameZone's live server inventory.
            UiRect findBtn = new UiRect(calcBtn.right() + 3, y, btnW, 11);
            GZTheme.drawButton(extractor, font, findBtn, "Hitta material i kistor", true, findBtn.contains(mouseX, mouseY), TypographyScale.META.getScale());
            GZCompanionMainScreen screen = currentScreen;
            int fromLevel = effective.level();
            int toLevel = profile.targetLevel();
            List<ItemRequirement> requirements = summary.mergedItems();
            hitTargets.add(new ListRowHit(findBtn, () -> {
                if (screen != null) screen.openKistorMaterialRequest(PlannerMaterialRequests.fromSettlement(fromLevel, toLevel, requirements));
            }));
            y += 13;

            y += TextUtil.drawScaledWrappedText(extractor, font, "Lokalt estimat från senast känt innehåll. Detta är inte serverns registrerade settlement inventory.",
                    x, y, maxW, TypographyScale.META.getScale(), 2, 1, GZTheme.COLOR_TEXT_MUTED, false) + 4;
        } else {
            showingContainerPicker = false;
        }

        if (showingContainerPicker) {
            y = renderContainerPicker(extractor, font, session, contextKey, x, y, maxW, mouseX, mouseY, summary);
        }

        UiRect listArea = new UiRect(area.x(), y, area.width(), area.bottom() - y);
        renderMaterialList(extractor, font, listArea, planner, profile, contextKey, summary, mouseX, mouseY);
    }

    private int renderContainerPicker(GuiGraphicsExtractor extractor, Font font, CompanionSession session, String contextKey,
                                       int x, int y, int maxW, int mouseX, int mouseY, LevelRangeSummary summary) {
        List<StoredContainer> containers = session.getChestManager().getContainers(contextKey);
        if (containers.isEmpty()) {
            TextUtil.drawScaledText(extractor, font, "Inga sparade kistor hittades för detta sammanhang.", x, y, TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_MUTED, false);
            return y + 12;
        }

        // Capped rather than scrolled: this picker is not inside a scissored/scrollable region, so
        // an unbounded container list would visually overflow the tab (and remain clickable) past
        // its actual visible bounds. A player with more indexed containers than this can still pick
        // among the most recently opened ones; the rest can be added to the estimate in a later pass.
        final int MAX_PICKER_ROWS = 8;
        int shown = Math.min(containers.size(), MAX_PICKER_ROWS);
        for (int i = 0; i < shown; i++) {
            StoredContainer container = containers.get(i);
            String key = container.id().asStableKey();
            boolean checked = selectedContainerKeys.contains(key);
            UiRect rowRect = new UiRect(x, y, maxW, 10);
            String label = (checked ? "[x] " : "[ ] ") + (container.label() != null ? container.label() : container.kind().getDisplayName());
            TextUtil.drawScaledEllipsizedText(extractor, font, label, x, y, maxW, TypographyScale.META.getScale(),
                    rowRect.contains(mouseX, mouseY) ? GZTheme.COLOR_TEXT_PRIMARY : GZTheme.COLOR_TEXT_SECONDARY, false);
            hitTargets.add(new ListRowHit(rowRect, () -> {
                if (!selectedContainerKeys.remove(key)) selectedContainerKeys.add(key);
            }));
            y += 10;
        }
        if (containers.size() > shown) {
            TextUtil.drawScaledText(extractor, font, "+" + (containers.size() - shown) + " fler (visas inte här)", x, y,
                    TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_MUTED, false);
            y += 10;
        }

        UiRect applyBtn = new UiRect(x, y + 2, Math.min(90, maxW), 11);
        GZTheme.drawButton(extractor, font, applyBtn, "Beräkna", true, applyBtn.contains(mouseX, mouseY), TypographyScale.META.getScale());
        hitTargets.add(new ListRowHit(applyBtn, () -> applyContainerEstimate(session, contextKey, summary)));
        return y + 15;
    }

    private void applyContainerEstimate(CompanionSession session, String contextKey, LevelRangeSummary summary) {
        List<StoredContainer> containers = session.getChestManager().getContainers(contextKey);
        java.util.Map<String, Integer> totals = new java.util.HashMap<>();
        for (StoredContainer container : containers) {
            if (!selectedContainerKeys.contains(container.id().asStableKey())) continue;
            for (StoredContainer.AggregatedItem item : container.aggregatedItems()) {
                totals.merge(item.itemId(), item.count(), Integer::sum);
            }
        }
        SettlementPlannerManager planner = session.getSettlementPlannerManager();
        for (ItemRequirement req : summary.mergedItems()) {
            if (!req.hasConcreteItem()) continue;
            int sum = totals.getOrDefault(req.itemId(), 0);
            planner.setOwnedAmount(contextKey, req.mergeKey(), sum);
        }
        showingContainerPicker = false;
    }

    private void renderMaterialList(GuiGraphicsExtractor extractor, Font font, UiRect listArea, SettlementPlannerManager planner,
                                     SettlementPlannerProfile profile, String contextKey, LevelRangeSummary summary, int mouseX, int mouseY) {
        List<ItemRequirement> items = summary.mergedItems();
        int maxScroll = calculateMaxScroll(MATERIAL_ROW_H, items.size(), listArea.height() - 2, 0);
        materialScroll = Math.max(0, Math.min(materialScroll, maxScroll));

        extractor.enableScissor(listArea.x(), listArea.y(), listArea.right(), listArea.bottom());
        int currentY = listArea.y() + 2 - materialScroll;
        int x = listArea.x() + 2;
        int maxW = listArea.width() - 4;

        for (ItemRequirement item : items) {
            if (currentY + MATERIAL_ROW_H >= listArea.y() && currentY <= listArea.bottom()) {
                int owned = profile.ownedAmount(item.mergeKey());
                int missing = Math.max(0, item.count() - owned);

                if (item.hasConcreteItem()) {
                    ItemStack stack = MinecraftRecipeDisplayAdapter.resolveDisplayStack(item.itemId());
                    if (!stack.isEmpty()) {
                        try {
                            extractor.fakeItem(stack, x, currentY + 3);
                            // No preferred name: a material requirement only ever carries a plain
                            // Minecraft item id, never a specific known GameZone custom item's
                            // identity - see ItemHoverTooltips' class doc for why that must never
                            // be inferred from the id alone.
                            itemHoverTooltips.register(new UiRect(x, currentY + 3, 16, 16), stack, item.itemId(), null, item.count(), 1);
                        } catch (Exception ignored) {
                            // A single unbakeable item icon must never take down the whole tab.
                        }
                    }
                }

                int textX = x + GRID_ICON_SIZE + 3;
                String variantSuffix = item.distinctVariantsRequired() != null ? " (" + item.distinctVariantsRequired() + " olika)" : "";
                TextUtil.drawScaledEllipsizedText(extractor, font, item.displayName() + variantSuffix, textX, currentY, maxW - GRID_ICON_SIZE - 3,
                        TypographyScale.SMALL.getScale(), GZTheme.COLOR_TEXT_PRIMARY, false);

                String statusLine = "Behövs: " + item.count() + "   Har: " + owned + "   Saknas: " + missing;
                TextUtil.drawScaledEllipsizedText(extractor, font, statusLine, textX, currentY + 10, maxW - GRID_ICON_SIZE - 3,
                        TypographyScale.META.getScale(), missing == 0 ? GZTheme.COLOR_STATUS_GREEN : GZTheme.COLOR_TEXT_SECONDARY, false);

                UiRect minusBtn = new UiRect(listArea.right() - 34, currentY + 3, 14, 12);
                UiRect plusBtn = new UiRect(listArea.right() - 17, currentY + 3, 14, 12);
                GZTheme.drawButton(extractor, font, minusBtn, "-", false, minusBtn.contains(mouseX, mouseY), TypographyScale.META.getScale());
                GZTheme.drawButton(extractor, font, plusBtn, "+", false, plusBtn.contains(mouseX, mouseY), TypographyScale.META.getScale());

                String mergeKey = item.mergeKey();
                hitTargets.add(new ListRowHit(minusBtn, () -> planner.setOwnedAmount(contextKey, mergeKey, Math.max(0, owned - 1))));
                hitTargets.add(new ListRowHit(plusBtn, () -> planner.setOwnedAmount(contextKey, mergeKey, owned + 1)));
            }
            currentY += MATERIAL_ROW_H;
        }
        extractor.disableScissor();
    }

    // ------------------------------------------------------------------
    // MEDLEMMAR
    // ------------------------------------------------------------------

    private void renderMembers(GuiGraphicsExtractor extractor, Font font, SettlementPlannerManager planner,
                                SettlementPlannerProfile profile, String contextKey, int mouseX, int mouseY,
                                SettlementLiveView live, CompanionSession session) {
        UiRect area = layout.panelRect();
        GZTheme.drawCard(extractor, area, GZTheme.COLOR_CARD_BG, GZTheme.COLOR_BORDER_SUBTLE);

        int x = area.x() + 4;
        int maxW = area.width() - 8;
        int y = area.y() + 4;

        if (live.settlementRecognized()) {
            y = renderLiveOnlineSection(extractor, font, x, y, maxW, live, profile, session.getBridge().getPlayerName(), mouseX, mouseY);
        }

        TextUtil.drawScaledEllipsizedText(extractor, font, "Lokala anteckningar - inte serverns riktiga medlemslista.", x, y, maxW,
                TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_MUTED, false);
        y += 11;

        UiRect addBtn = new UiRect(x, y, Math.min(120, maxW), 11);
        GZTheme.drawButton(extractor, font, addBtn, "+ Lägg till medlem", true, addBtn.contains(mouseX, mouseY), TypographyScale.META.getScale());
        hitTargets.add(new ListRowHit(addBtn, this::beginAddMember));
        y += 14;

        if (editingMember) {
            y = renderMemberEditForm(extractor, font, x, y, maxW, mouseX, mouseY, planner, contextKey);
        }

        UiRect listArea = new UiRect(area.x(), y, area.width(), area.bottom() - y);
        renderMembersList(extractor, font, listArea, planner, profile, contextKey, mouseX, mouseY);
    }

    private static final int MAX_LIVE_ONLINE_ROWS_SHOWN = 6;

    /**
     * "ONLINE FRÅN DITT SETTLEMENT · N" - ONLY same-settlement players currently visible in the
     * live TAB/player-list data (never a full roster, never an offline inference - see {@link
     * SettlementLiveView#sameSettlementOnlineUsernames()}'s own doc comment). Capped rather than
     * scrolled (mirrors the existing container-picker's own convention) so a large live settlement
     * can never visually overflow this fixed, non-scrolling block. Clicking a row opens the
     * existing local-note edit form pre-filled with that player's name - a pure UX convenience
     * that creates no persisted record until the player explicitly clicks "Spara".
     */
    private int renderLiveOnlineSection(GuiGraphicsExtractor extractor, Font font, int x, int y, int maxW,
                                         SettlementLiveView live, SettlementPlannerProfile profile, String localPlayerName,
                                         int mouseX, int mouseY) {
        List<String> online = sortedOnlineSettlementMembers(live.sameSettlementOnlineUsernames());
        TextUtil.drawScaledText(extractor, font, "ONLINE FRÅN DITT SETTLEMENT · " + online.size(), x, y,
                TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_MUTED, false);
        y += 10;

        int shown = Math.min(online.size(), MAX_LIVE_ONLINE_ROWS_SHOWN);
        for (int i = 0; i < shown; i++) {
            String username = online.get(i);
            boolean isSelf = isLocalPlayer(username, localPlayerName);
            MemberNote matchingNote = findMatchingLocalNote(profile, username);

            UiRect rowRect = new UiRect(x, y, maxW, matchingNote != null ? 18 : 9);
            if (rowRect.contains(mouseX, mouseY)) GZTheme.drawCard(extractor, rowRect, GZTheme.COLOR_NAV_HOVER, 0);

            String line = username + (isSelf ? "  DU" : "");
            TextUtil.drawScaledEllipsizedText(extractor, font, line, x + 2, y, maxW - 4,
                    TypographyScale.SMALL.getScale(), isSelf ? GZTheme.COLOR_MINT : GZTheme.COLOR_TEXT_PRIMARY, isSelf);
            y += 9;
            if (matchingNote != null) {
                TextUtil.drawScaledEllipsizedText(extractor, font, matchingNote.note(), x + 6, y, maxW - 8,
                        TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_MUTED, false);
                y += 9;
            }

            final String uname = username;
            final MemberNote noteForClick = matchingNote;
            hitTargets.add(new ListRowHit(rowRect, () -> {
                if (!editingMember) {
                    editingMember = true;
                    editingMemberId = noteForClick != null ? noteForClick.id() : null;
                    editName = uname;
                    editNote = noteForClick != null ? noteForClick.note() : "";
                    focusedField = EditField.NOTE;
                }
            }));
        }
        if (online.size() > shown) {
            TextUtil.drawScaledText(extractor, font, "+" + (online.size() - shown) + " fler online", x, y,
                    TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_MUTED, false);
            y += 9;
        }
        return y + 5;
    }

    /** Pure, deterministic sort for the live-online list - alphabetical, case-insensitive, so the
     * same set of names always renders in the same order regardless of TAB-list iteration order. */
    static List<String> sortedOnlineSettlementMembers(List<String> usernames) {
        return usernames.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    static boolean isLocalPlayer(String candidateUsername, String localPlayerName) {
        return candidateUsername != null && localPlayerName != null && candidateUsername.equalsIgnoreCase(localPlayerName);
    }

    /** Case-insensitive name match only - never a fuzzy guess. Returns the first match; local
     * notes are player-authored free text, so an exact duplicate name is the player's own concern. */
    private static MemberNote findMatchingLocalNote(SettlementPlannerProfile profile, String username) {
        if (username == null) return null;
        for (MemberNote note : profile.members()) {
            if (note.playerName().equalsIgnoreCase(username)) return note;
        }
        return null;
    }

    private void beginAddMember() {
        editingMember = true;
        editingMemberId = null;
        editName = "";
        editNote = "";
        focusedField = EditField.NAME;
    }

    private int renderMemberEditForm(GuiGraphicsExtractor extractor, Font font, int x, int y, int maxW, int mouseX, int mouseY,
                                      SettlementPlannerManager planner, String contextKey) {
        UiRect nameRect = new UiRect(x, y, maxW, 11);
        drawTextField(extractor, font, nameRect, "Namn...", editName, focusedField == EditField.NAME);
        hitTargets.add(new ListRowHit(nameRect, () -> focusedField = EditField.NAME));
        y += 13;

        UiRect noteRect = new UiRect(x, y, maxW, 11);
        drawTextField(extractor, font, noteRect, "Anteckning (roll, ansvar, fritext)...", editNote, focusedField == EditField.NOTE);
        hitTargets.add(new ListRowHit(noteRect, () -> focusedField = EditField.NOTE));
        y += 14;

        int btnW = Math.max(40, (maxW - 4) / 2);
        UiRect saveBtn = new UiRect(x, y, btnW, 11);
        UiRect cancelBtn = new UiRect(x + btnW + 4, y, btnW, 11);
        GZTheme.drawButton(extractor, font, saveBtn, "Spara", true, saveBtn.contains(mouseX, mouseY), TypographyScale.META.getScale());
        GZTheme.drawButton(extractor, font, cancelBtn, "Avbryt", false, cancelBtn.contains(mouseX, mouseY), TypographyScale.META.getScale());
        hitTargets.add(new ListRowHit(saveBtn, () -> saveMember(planner, contextKey)));
        hitTargets.add(new ListRowHit(cancelBtn, this::cancelEditMember));
        y += 14;
        return y;
    }

    private void drawTextField(GuiGraphicsExtractor extractor, Font font, UiRect rect, String placeholder, String value, boolean focused) {
        int bg = focused ? GZTheme.COLOR_CARD_HOVER : GZTheme.COLOR_CARD_INNER;
        int border = focused ? GZTheme.COLOR_BORDER_EMERALD : GZTheme.COLOR_BORDER_SUBTLE;
        GZTheme.drawCard(extractor, rect, bg, border);
        int maxW = rect.width() - 6;
        if (value.isEmpty() && !focused) {
            TextUtil.drawScaledEllipsizedText(extractor, font, placeholder, rect.x() + 3, rect.y() + 2, maxW, TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_MUTED, false);
        } else {
            String shown = value + (focused && ((System.currentTimeMillis() / 500) % 2 == 0) ? "_" : "");
            TextUtil.drawScaledEllipsizedText(extractor, font, shown, rect.x() + 3, rect.y() + 2, maxW, TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_PRIMARY, false);
        }
    }

    private void saveMember(SettlementPlannerManager planner, String contextKey) {
        if (editName.isBlank() && editNote.isBlank()) {
            cancelEditMember();
            return;
        }
        String id = editingMemberId != null ? editingMemberId : UUID.randomUUID().toString();
        planner.addOrUpdateMember(contextKey, new MemberNote(id, editName, editNote));
        cancelEditMember();
    }

    private void cancelEditMember() {
        editingMember = false;
        editingMemberId = null;
        editName = "";
        editNote = "";
        focusedField = null;
    }

    private void renderMembersList(GuiGraphicsExtractor extractor, Font font, UiRect listArea, SettlementPlannerManager planner,
                                    SettlementPlannerProfile profile, String contextKey, int mouseX, int mouseY) {
        List<MemberNote> members = profile.members();
        int maxScroll = calculateMaxScroll(MEMBER_ROW_H, members.size(), listArea.height() - 2, 0);
        membersScroll = Math.max(0, Math.min(membersScroll, maxScroll));

        extractor.enableScissor(listArea.x(), listArea.y(), listArea.right(), listArea.bottom());
        int currentY = listArea.y() + 2 - membersScroll;
        int x = listArea.x() + 2;
        int maxW = listArea.width() - 4;

        long now = System.currentTimeMillis();
        for (MemberNote member : members) {
            if (currentY + MEMBER_ROW_H >= listArea.y() && currentY <= listArea.bottom()) {
                UiRect rowRect = new UiRect(x, currentY, maxW, MEMBER_ROW_H - 2);
                boolean isHovered = rowRect.contains(mouseX, mouseY);
                if (isHovered) GZTheme.drawCard(extractor, rowRect, GZTheme.COLOR_NAV_HOVER, 0);

                TextUtil.drawScaledEllipsizedText(extractor, font, member.playerName(), x + 2, currentY + 1, maxW - 46,
                        TypographyScale.SMALL.getScale(), GZTheme.COLOR_TEXT_PRIMARY, false);
                TextUtil.drawScaledEllipsizedText(extractor, font, member.note(), x + 2, currentY + 11, maxW - 46,
                        TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_SECONDARY, false);

                boolean pendingConfirm = member.id().equals(pendingDeleteMemberId) && (now - pendingDeleteAtMs) < 3000L;
                UiRect deleteBtn = new UiRect(x + maxW - 42, currentY + 5, 40, 11);
                GZTheme.drawButton(extractor, font, deleteBtn, pendingConfirm ? "Säker?" : "Ta bort", false,
                        deleteBtn.contains(mouseX, mouseY), TypographyScale.META.getScale());

                String memberId = member.id();
                hitTargets.add(new ListRowHit(deleteBtn, () -> {
                    if (memberId.equals(pendingDeleteMemberId) && (System.currentTimeMillis() - pendingDeleteAtMs) < 3000L) {
                        planner.removeMember(contextKey, memberId);
                        pendingDeleteMemberId = null;
                    } else {
                        pendingDeleteMemberId = memberId;
                        pendingDeleteAtMs = System.currentTimeMillis();
                    }
                }));
                hitTargets.add(new ListRowHit(new UiRect(x, currentY, maxW - 46, MEMBER_ROW_H - 2), () -> {
                    editingMember = true;
                    editingMemberId = memberId;
                    editName = member.playerName();
                    editNote = member.note();
                    focusedField = null;
                }));
            }
            currentY += MEMBER_ROW_H;
        }

        if (members.isEmpty()) {
            TextUtil.drawScaledText(extractor, font, "Inga lokala medlemsanteckningar ännu.", x, currentY, TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_MUTED, false);
        }
        extractor.disableScissor();
    }

    // ------------------------------------------------------------------
    // Verification trail (shared rendering helper, mirrors CraftingTabComponent)
    // ------------------------------------------------------------------

    private int renderVerificationTrail(GuiGraphicsExtractor extractor, Font font, int x, int y, int maxW, VerificationMetadata verification) {
        int startY = y;
        VerificationStatus status = verification.status();
        GZTheme.drawBadge(extractor, font, x, y, status.getDisplayName(), GZTheme.COLOR_TEXT_SECONDARY, status.getArgbColor());
        y += 12;

        if (verification.hasSource()) {
            TextUtil.drawScaledEllipsizedText(extractor, font, "Källa: " + verification.sourceName(), x, y, maxW,
                    TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_SECONDARY, false);
            y += 9;
            if (verification.lastVerified() != null) {
                TextUtil.drawScaledEllipsizedText(extractor, font, "Senast kontrollerad: " + verification.lastVerified(), x, y, maxW,
                        TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_MUTED, false);
                y += 9;
            }
        } else {
            y += TextUtil.drawScaledWrappedText(extractor, font, "Den här informationen har ännu inte bekräftats.", x, y, maxW,
                    TypographyScale.META.getScale(), 2, 1, GZTheme.COLOR_TEXT_MUTED, false) + 2;
        }
        return y - startY;
    }

    private static int estimateVerificationTrailHeight(VerificationMetadata verification) {
        int h = 12;
        if (verification.hasSource()) {
            h += 9;
            if (verification.lastVerified() != null) h += 9;
        } else {
            h += 20;
        }
        return h;
    }

    // ------------------------------------------------------------------
    // Input handling
    // ------------------------------------------------------------------

    public boolean mouseClicked(double mouseX, double mouseY, int button, UiRect bounds, GZCompanionMainScreen mainScreen) {
        if (button != 0) return false;
        if (layout == null) layout = SettlementLayout.calculate(bounds);

        if (layout.modeBtnRect().contains(mouseX, mouseY)) {
            mode = mode.next();
            compactShowingDetail = false;
            showingContainerPicker = false;
            cancelEditMember();
            return true;
        }

        for (ListRowHit hit : hitTargets) {
            if (hit.rect().contains(mouseX, mouseY)) {
                hit.action().run();
                return true;
            }
        }

        // While the member edit form is open, swallow every other click inside the tab bounds
        // so the user can't accidentally interact with a stale list row behind the form.
        return mode == Mode.MEDLEMMAR && editingMember;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (layout == null) return false;

        if (mode == Mode.OVERSIKT && layout.panelRect().contains(mouseX, mouseY)) {
            overviewScroll = Math.max(0, overviewScroll - (int) (scrollY * 14));
            return true;
        } else if (mode == Mode.PROGRESSION) {
            if (layout.isCompact()) {
                if (!layout.panelRect().contains(mouseX, mouseY)) {
                    return false;
                }
                if (compactShowingDetail) {
                    progressionDetailScroll = Math.max(0, progressionDetailScroll - (int) (scrollY * 14));
                } else {
                    progressionListScroll = Math.max(0, progressionListScroll - (int) (scrollY * 14));
                }
                return true;
            }
            if (layout.listRect().contains(mouseX, mouseY)) {
                progressionListScroll = Math.max(0, progressionListScroll - (int) (scrollY * 14));
                return true;
            }
            if (layout.detailRect().contains(mouseX, mouseY)) {
                progressionDetailScroll = Math.max(0, progressionDetailScroll - (int) (scrollY * 14));
                return true;
            }
        } else if (mode == Mode.MATERIAL && layout.panelRect().contains(mouseX, mouseY)) {
            materialScroll = Math.max(0, materialScroll - (int) (scrollY * 14));
            return true;
        } else if (mode == Mode.MEDLEMMAR && layout.panelRect().contains(mouseX, mouseY)) {
            membersScroll = Math.max(0, membersScroll - (int) (scrollY * 14));
            return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (event == null || focusedField == null || !editingMember) return false;
        String s = event.codepointAsString();
        if (s == null || s.isEmpty()) return false;

        if (focusedField == EditField.NAME && editName.length() < 32) {
            editName += s;
            return true;
        }
        if (focusedField == EditField.NOTE && editNote.length() < 96) {
            editNote += s;
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event == null || focusedField == null || !editingMember) return false;
        int key = event.key();

        if (key == GLFW.GLFW_KEY_BACKSPACE) {
            if (focusedField == EditField.NAME && !editName.isEmpty()) {
                editName = editName.substring(0, editName.length() - 1);
            } else if (focusedField == EditField.NOTE && !editNote.isEmpty()) {
                editNote = editNote.substring(0, editNote.length() - 1);
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_TAB) {
            focusedField = (focusedField == EditField.NAME) ? EditField.NOTE : EditField.NAME;
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            cancelEditMember();
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Test-only accessors - package-private, read by SettlementTabComponentTest to verify scroll
    // state transitions/independence without needing a live CompanionSession/Font.
    // ------------------------------------------------------------------

    void setModeForTesting(Mode m) {
        this.mode = m;
    }

    Mode getModeForTesting() {
        return mode;
    }

    int getOverviewScrollForTesting() {
        return overviewScroll;
    }

    void setOverviewScrollForTesting(int value) {
        this.overviewScroll = value;
    }

    int getMaterialScrollForTesting() {
        return materialScroll;
    }

    void setMaterialScrollForTesting(int value) {
        this.materialScroll = value;
    }

    int getProgressionListScrollForTesting() {
        return progressionListScroll;
    }

    int getProgressionDetailScrollForTesting() {
        return progressionDetailScroll;
    }

    void setProgressionDetailScrollForTesting(int value) {
        this.progressionDetailScroll = value;
    }

    void setCompactShowingDetailForTesting(boolean value) {
        this.compactShowingDetail = value;
    }

    boolean getCompactShowingDetailForTesting() {
        return compactShowingDetail;
    }
}
