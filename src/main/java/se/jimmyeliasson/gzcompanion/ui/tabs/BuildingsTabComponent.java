package se.jimmyeliasson.gzcompanion.ui.tabs;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;
import se.jimmyeliasson.gzcompanion.building.BuildingPlanManager;
import se.jimmyeliasson.gzcompanion.building.storage.BuildingPlan;
import se.jimmyeliasson.gzcompanion.building.storage.BuildingRequirementKey;
import se.jimmyeliasson.gzcompanion.core.CompanionSession;
import se.jimmyeliasson.gzcompanion.knowledge.building.BuildingKnowledgeBase;
import se.jimmyeliasson.gzcompanion.knowledge.building.BuildingRequirement;
import se.jimmyeliasson.gzcompanion.knowledge.building.GlobalBuildingRules;
import se.jimmyeliasson.gzcompanion.knowledge.building.SettlementBuilding;
import se.jimmyeliasson.gzcompanion.knowledge.common.KnowledgeModuleStatus;
import se.jimmyeliasson.gzcompanion.knowledge.common.VerificationMetadata;
import se.jimmyeliasson.gzcompanion.knowledge.common.VerificationStatus;
import se.jimmyeliasson.gzcompanion.knowledge.crafting.bridge.MinecraftRecipeDisplayAdapter;
import se.jimmyeliasson.gzcompanion.ui.GZCompanionMainScreen;
import se.jimmyeliasson.gzcompanion.ui.GZTheme;
import se.jimmyeliasson.gzcompanion.ui.ReferenceModeBanner;
import se.jimmyeliasson.gzcompanion.ui.IconId;
import se.jimmyeliasson.gzcompanion.ui.TextInputHandler;
import se.jimmyeliasson.gzcompanion.ui.TypographyScale;
import se.jimmyeliasson.gzcompanion.ui.layout.BuildingLayout;
import se.jimmyeliasson.gzcompanion.ui.layout.TextUtil;
import se.jimmyeliasson.gzcompanion.ui.layout.UiRect;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Renders the Byggplaner tab: a searchable reference to the current GameZone Building System 1.0
 * catalog, a local "Planeringsestimat" structure/material calculator, and local per-building
 * plans with a fixed local checklist. Never claims a calculated estimate guarantees GameZone's
 * actual server-side building approval - see docs/BUILDING-PLANNER.md.
 */
public class BuildingsTabComponent implements TextInputHandler {
    private static final int MAX_SEARCH_LENGTH = 48;
    private static final int ROW_H = 20;

    private record ListRowHit(UiRect rect, Runnable action) {}

    private String searchText = "";
    private boolean searchFocused = false;
    private String selectedBuildingId = null;
    private int listScrollOffset = 0;
    private int detailScrollOffset = 0;
    private boolean compactShowingDetail = false;

    // --- Structure Calculator (session-local, not persisted until saved into a plan) ---
    private int calcWidth = 7;
    private int calcDepth = 7;
    private int calcHeight = 5;

    // --- Local plan editing ---
    private String renamingPlanId = null;
    private String renameText = "";
    private String pendingDeletePlanId = null;
    private long pendingDeleteAtMs = 0L;

    private BuildingLayout layout;
    private final List<ListRowHit> hitTargets = new ArrayList<>();

    @Override
    public boolean isTextInputFocused() {
        return searchFocused || renamingPlanId != null;
    }

    public void render(GuiGraphicsExtractor extractor, Font font, UiRect bounds, int mouseX, int mouseY, GZCompanionMainScreen mainScreen) {
        renderContent(extractor, font, bounds, mouseX, mouseY, mainScreen);
        // Byggplaner is GameZone-specific reference/planning data - show a small, unobtrusive note
        // when the current server/world isn't GameZoneMC, so verified requirements and local plans
        // are never mistaken for the current server's actual state.
        if (!CompanionSession.getInstance().isConnectedToGameZone()) {
            ReferenceModeBanner.renderAtBottom(extractor, font, bounds);
        }
    }

    private void renderContent(GuiGraphicsExtractor extractor, Font font, UiRect bounds, int mouseX, int mouseY, GZCompanionMainScreen mainScreen) {
        this.layout = BuildingLayout.calculate(bounds);
        hitTargets.clear();

        CompanionSession session = CompanionSession.getInstance();
        KnowledgeModuleStatus status = session.getBuildingKnowledgeStatus();

        renderHeader(extractor, font, layout.headerRect(), status);
        if (!status.isAvailable()) {
            renderSearch(extractor, font, mouseX, mouseY);
            drawUnavailableState(extractor, font, layout.listRect(), status);
            return;
        }

        BuildingKnowledgeBase base = session.getBuildingKnowledgeBase();
        renderSearch(extractor, font, mouseX, mouseY);

        List<SettlementBuilding> entries = base.search(searchText);
        boolean showUnverified = session.getSettingsManager().getSettings().showUnverifiedKnowledge();
        if (!showUnverified) {
            List<SettlementBuilding> verifiedOnly = entries.stream()
                    .filter(b -> b.verification().status() == VerificationStatus.VERIFIED).toList();
            if (!verifiedOnly.isEmpty()) entries = verifiedOnly;
        }
        if (selectedBuildingId == null || entries.stream().noneMatch(b -> b.id().equals(selectedBuildingId))) {
            selectedBuildingId = entries.isEmpty() ? null : entries.get(0).id();
            compactShowingDetail = false;
        }

        if (entries.isEmpty()) {
            renderEmptyState(extractor, font, layout.listRect());
            return;
        }

        String contextKey = session.getCurrentStorageContext();
        BuildingPlanManager plans = session.getBuildingPlanManager();

        if (layout.isCompact()) {
            if (compactShowingDetail && selectedBuildingId != null) {
                renderDetail(extractor, font, base, plans, contextKey, mouseX, mouseY, true);
            } else {
                renderList(extractor, font, entries, mouseX, mouseY);
            }
        } else {
            renderList(extractor, font, entries, mouseX, mouseY);
            renderDetail(extractor, font, base, plans, contextKey, mouseX, mouseY, false);
        }
    }

    private void renderHeader(GuiGraphicsExtractor extractor, Font font, UiRect headerRect, KnowledgeModuleStatus status) {
        GZTheme.drawIcon(extractor, IconId.BUILDING, headerRect.x(), headerRect.y() + 1, 10, GZTheme.COLOR_MINT);
        TextUtil.drawScaledText(extractor, font, "Byggplaner", headerRect.x() + 13, headerRect.y() + 1,
                TypographyScale.HEADING.getScale(), GZTheme.COLOR_TEXT_PRIMARY, true);

        String badgeLabel = status.isAvailable() ? "Laddad" : status.getDisplayName();
        int badgeW = TextUtil.scaledWidth(font, badgeLabel, TypographyScale.META.getScale()) + 14;
        int dot = status.isAvailable() ? GZTheme.COLOR_STATUS_GREEN : GZTheme.COLOR_STATUS_RED;
        GZTheme.drawBadge(extractor, font, headerRect.right() - badgeW, headerRect.y(), badgeLabel, GZTheme.COLOR_TEXT_SECONDARY, dot);
    }

    private void renderSearch(GuiGraphicsExtractor extractor, Font font, int mouseX, int mouseY) {
        UiRect searchRect = layout.searchRect();
        UiRect clearBtnRect = layout.clearBtnRect();
        int bg = searchFocused ? GZTheme.COLOR_CARD_HOVER : GZTheme.COLOR_CARD_INNER;
        int border = searchFocused ? GZTheme.COLOR_BORDER_EMERALD : GZTheme.COLOR_BORDER_SUBTLE;
        GZTheme.drawCard(extractor, searchRect, bg, border);

        int textX = searchRect.x() + 4;
        int textY = searchRect.y() + 3;
        int maxW = searchRect.width() - 8;

        if (searchText.isEmpty() && !searchFocused) {
            TextUtil.drawScaledEllipsizedText(extractor, font, "Sök byggnad...", textX, textY, maxW, TypographyScale.SMALL.getScale(), GZTheme.COLOR_TEXT_MUTED, false);
        } else {
            String shown = searchText + (searchFocused && ((System.currentTimeMillis() / 500) % 2 == 0) ? "_" : "");
            TextUtil.drawScaledEllipsizedText(extractor, font, shown, textX, textY, maxW, TypographyScale.SMALL.getScale(), GZTheme.COLOR_TEXT_PRIMARY, false);
        }

        if (!searchText.isEmpty()) {
            boolean hov = clearBtnRect.contains(mouseX, mouseY);
            GZTheme.drawCard(extractor, clearBtnRect, hov ? GZTheme.COLOR_CARD_HOVER : GZTheme.COLOR_CARD_INNER, GZTheme.COLOR_BORDER_SUBTLE);
            TextUtil.drawCenteredText(extractor, font, "x", clearBtnRect.x() + (clearBtnRect.width() / 2), clearBtnRect.y() + 2,
                    clearBtnRect.width(), hov ? GZTheme.COLOR_TEXT_PRIMARY : GZTheme.COLOR_TEXT_MUTED, false);
        }
    }

    private void drawUnavailableState(GuiGraphicsExtractor extractor, Font font, UiRect area, KnowledgeModuleStatus status) {
        GZTheme.drawCard(extractor, area, GZTheme.COLOR_CARD_BG, GZTheme.COLOR_BORDER_SUBTLE);
        String msg = switch (status) {
            case ERROR -> "Fel inträffade vid inläsning av byggnadsdatan.";
            case UNAVAILABLE -> "Byggnadsdata är inte tillgänglig just nu.";
            case INCOMPATIBLE -> "Byggnadsdatan är sparad med ett schema som inte stöds av denna version.";
            case LOADED -> "Laddar...";
        };
        TextUtil.drawCenteredText(extractor, font, msg, area.x() + (area.width() / 2),
                area.y() + (area.height() / 2) - 4, area.width(), GZTheme.COLOR_TEXT_MUTED, false);
    }

    private void renderEmptyState(GuiGraphicsExtractor extractor, Font font, UiRect area) {
        GZTheme.drawCard(extractor, area, GZTheme.COLOR_CARD_BG, GZTheme.COLOR_BORDER_SUBTLE);
        TextUtil.drawCenteredText(extractor, font, "Inga byggnader matchar sökningen.", area.x() + (area.width() / 2),
                area.y() + (area.height() / 2) - 4, area.width(), GZTheme.COLOR_TEXT_MUTED, false);
    }

    private static int calculateMaxScroll(int rowH, int rowCount, int visibleH, int headerH) {
        int totalH = headerH + (rowCount * rowH);
        return Math.max(0, totalH - Math.max(1, visibleH));
    }

    private void renderList(GuiGraphicsExtractor extractor, Font font, List<SettlementBuilding> entries, int mouseX, int mouseY) {
        UiRect listRect = layout.listRect();
        GZTheme.drawCard(extractor, listRect, GZTheme.COLOR_CARD_BG, GZTheme.COLOR_BORDER_SUBTLE);

        int maxScroll = calculateMaxScroll(ROW_H, entries.size(), listRect.height() - 4, 12);
        listScrollOffset = Math.max(0, Math.min(listScrollOffset, maxScroll));

        extractor.enableScissor(listRect.x() + 1, listRect.y() + 1, listRect.right() - 1, listRect.bottom() - 1);
        int currentY = listRect.y() + 3 - listScrollOffset;
        TextUtil.drawScaledText(extractor, font, entries.size() + " BYGGNADER", listRect.x() + 4, currentY, TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_MUTED, false);
        currentY += 12;

        for (SettlementBuilding building : entries) {
            UiRect rowRect = new UiRect(listRect.x() + 2, currentY, listRect.width() - 4, ROW_H);
            if (currentY + ROW_H >= listRect.y() && currentY <= listRect.bottom()) {
                boolean isSelected = building.id().equals(selectedBuildingId);
                boolean isHovered = rowRect.contains(mouseX, mouseY);
                int bg = isSelected ? GZTheme.COLOR_NAV_ACTIVE : (isHovered ? GZTheme.COLOR_NAV_HOVER : 0);
                if (bg != 0) GZTheme.drawCard(extractor, rowRect, bg, isSelected ? GZTheme.COLOR_BORDER_EMERALD : 0);

                int dotColor = building.hasLevelRequirementConflict()
                        ? building.levelRequirementVerification().status().getArgbColor()
                        : building.verification().status().getArgbColor();
                GZTheme.drawStatusDot(extractor, rowRect.x() + 4, rowRect.y() + 5, dotColor);
                TextUtil.drawScaledEllipsizedText(extractor, font, building.name(), rowRect.x() + 11, rowRect.y() + 2,
                        rowRect.width() - 15, TypographyScale.SMALL.getScale(), GZTheme.COLOR_TEXT_PRIMARY, isSelected);
                TextUtil.drawScaledEllipsizedText(extractor, font, "Nivå " + building.levelRequirement() + " · " + building.licenseCost() + " Coins",
                        rowRect.x() + 11, rowRect.y() + 11, rowRect.width() - 15, TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_MUTED, false);

                final String bId = building.id();
                final Integer bMinWidth = building.minWidth();
                final Integer bMinDepth = building.minDepth();
                final Integer bMinHeight = building.minHeight();
                hitTargets.add(new ListRowHit(rowRect, () -> {
                    selectedBuildingId = bId;
                    detailScrollOffset = 0;
                    compactShowingDetail = true;
                    // Start the calculator at exactly this building's published minimum, so it
                    // opens already showing a passing status - the player can then reduce a
                    // dimension to see the warning, or increase for a safety margin.
                    if (bMinWidth != null && bMinDepth != null) {
                        calcWidth = bMinWidth;
                        calcDepth = bMinDepth;
                        calcHeight = bMinHeight != null ? bMinHeight : calcHeight;
                    }
                }));
            }
            currentY += ROW_H;
        }
        extractor.disableScissor();
    }

    private void renderDetail(GuiGraphicsExtractor extractor, Font font, BuildingKnowledgeBase base, BuildingPlanManager plans,
                               String contextKey, int mouseX, int mouseY, boolean isCompact) {
        UiRect detailRect = layout.detailRect();
        GZTheme.drawCard(extractor, detailRect, GZTheme.COLOR_CARD_BG, GZTheme.COLOR_BORDER_SUBTLE);
        SettlementBuilding building = base.byId(selectedBuildingId).orElse(null);
        if (building == null) return;

        int contentTop = detailRect.y() + (isCompact ? 16 : 4);
        UiRect contentArea = new UiRect(detailRect.x() + 1, contentTop, detailRect.width() - 2, detailRect.bottom() - contentTop - 1);

        if (isCompact) {
            UiRect backBtn = layout.backBtnRect();
            GZTheme.drawButton(extractor, font, backBtn, "< Lista", false, backBtn.contains(mouseX, mouseY), TypographyScale.SMALL.getScale());
            hitTargets.add(new ListRowHit(backBtn, () -> compactShowingDetail = false));
        }

        // Everything added to hitTargets from here on belongs to the scrolled content flow below -
        // remember where it starts so the offscreen-row filter at the end never touches the back
        // button above (which is deliberately outside the scissored/scrolled area).
        int scrolledSectionStart = hitTargets.size();

        int maxScroll = Math.max(0, estimateDetailHeight(building, base.globalRules(), plans.getPlansForBuilding(contextKey, building.id())) - contentArea.height());
        detailScrollOffset = Math.max(0, Math.min(detailScrollOffset, maxScroll));

        extractor.enableScissor(contentArea.x(), contentArea.y(), contentArea.right(), contentArea.bottom());
        int pad = 5;
        int maxW = contentArea.width() - (pad * 2);
        int x = contentArea.x() + pad;
        int y = contentArea.y() + 2 - detailScrollOffset;

        TextUtil.drawScaledEllipsizedText(extractor, font, building.name(), x, y, maxW, TypographyScale.HEADING.getScale(), GZTheme.COLOR_MINT, true);
        y += 11;

        if (building.hasLevelRequirementConflict()) {
            // Two current canonical GameZone sources make directly contradictory claims about
            // when this building is actually available - never silently pick one number and
            // present it as settled VERIFIED truth. Show both raw values and let the player see
            // the disagreement themselves.
            y += TextUtil.drawScaledWrappedText(extractor, font, "GameZones Wiki innehåller motstridiga nivåuppgifter för denna byggnad.",
                    x, y, maxW, TypographyScale.META.getScale(), 2, 1, GZTheme.COLOR_STATUS_RED, false) + 2;
            TextUtil.drawScaledEllipsizedText(extractor, font, "Byggnadssidan anger nivåkrav: Settlementnivå " + building.levelRequirement(),
                    x, y, maxW, TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_SECONDARY, false);
            y += 9;
            TextUtil.drawScaledEllipsizedText(extractor, font, "Krävs före nivå: " + building.progressionRequiredForUpgradeToLevel() + " (enligt Settlement Upgrade)",
                    x, y, maxW, TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_SECONDARY, false);
            y += 10;
        } else {
            TextUtil.drawScaledEllipsizedText(extractor, font, "Nivåkrav: Settlementnivå " + building.levelRequirement(),
                    x, y, maxW, TypographyScale.SMALL.getScale(), GZTheme.COLOR_TEXT_SECONDARY, false);
            y += 10;
        }
        TextUtil.drawScaledEllipsizedText(extractor, font, "Licenskostnad: " + building.licenseCost() + " Coins",
                x, y, maxW, TypographyScale.SMALL.getScale(), GZTheme.COLOR_TEXT_SECONDARY, false);
        y += 10;

        // "Minsta storlek" is shown prominently here (not buried inside the calculator below) -
        // per-building minimum footprint is real, verified data now, not an unpublished gap.
        if (building.hasPublishedMinimumFootprint()) {
            String sizeLine = "Minsta storlek: " + building.minWidth() + " × " + building.minDepth();
            if (building.minHeight() != null) {
                sizeLine += " (höjd: " + building.minHeight() + " block)";
            }
            TextUtil.drawScaledEllipsizedText(extractor, font, sizeLine, x, y, maxW, TypographyScale.SMALL.getScale(), GZTheme.COLOR_TEXT_PRIMARY, true);
            y += 10;
        }
        TextUtil.drawScaledEllipsizedText(extractor, font, "Väggkrav: minst " + base.globalRules().minWallCoveragePercent() + "% täckning",
                x, y, maxW, TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_SECONDARY, false);
        y += 9;
        TextUtil.drawScaledEllipsizedText(extractor, font, "Takkrav: minst " + base.globalRules().minRoofCoveragePercent() + "% täckning",
                x, y, maxW, TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_SECONDARY, false);
        y += 11;

        TextUtil.drawScaledText(extractor, font, "SPECIALKRAV", x, y, TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_MUTED, false);
        y += 10;
        for (BuildingRequirement req : building.specialRequirements()) {
            if (req.hasConcreteItem()) {
                ItemStack stack = MinecraftRecipeDisplayAdapter.resolveDisplayStack(req.itemId());
                if (!stack.isEmpty()) {
                    try {
                        extractor.fakeItem(stack, x, y - 1);
                    } catch (Exception ignored) {
                        // A single unbakeable item icon must never take down the whole tab.
                    }
                }
            }
            String line = "x" + req.count() + "  " + req.displayName();
            TextUtil.drawScaledEllipsizedText(extractor, font, line, x + 16, y, maxW - 16, TypographyScale.SMALL.getScale(), GZTheme.COLOR_TEXT_PRIMARY, false);
            y += 12;
        }

        if (!building.mainBonus().isBlank()) {
            y += 4;
            TextUtil.drawScaledText(extractor, font, "BONUS", x, y, TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_MUTED, false);
            y += 9;
            y += TextUtil.drawScaledWrappedText(extractor, font, building.mainBonus(), x, y, maxW, TypographyScale.SMALL.getScale(), 2, 1, GZTheme.COLOR_STATUS_YELLOW, false) + 2;
        }

        y += 4;
        y += renderVerificationTrail(extractor, font, x, y, maxW, building.verification());

        y += 6;
        y = renderStructureCalculator(extractor, font, x, y, maxW, building, base.globalRules(), mouseX, mouseY);

        y += 6;
        renderPlansSection(extractor, font, x, y, maxW, building, plans, contextKey, mouseX, mouseY);

        extractor.disableScissor();

        // The calculator/plans rows above were added to hitTargets unconditionally as part of one
        // continuous scrolled flow (unlike the list pane, which only adds a hit target when a row's
        // own per-row visibility check passes). A row scrolled outside contentArea would otherwise
        // stay clickable even though scissoring makes it invisible - filter those out here, but only
        // within the scrolled section (never the back button added above it).
        hitTargets.subList(scrolledSectionStart, hitTargets.size())
                .removeIf(hit -> hit.rect().bottom() <= contentArea.y() || hit.rect().y() >= contentArea.bottom());
    }

    private int estimateDetailHeight(SettlementBuilding building, GlobalBuildingRules rules, List<BuildingPlan> buildingPlans) {
        int h = 11 + 10; // heading, license cost
        h += building.hasLevelRequirementConflict() ? (20 + 9 + 10) : 10; // conflict warning block, or the plain "Nivåkrav" line
        if (building.hasPublishedMinimumFootprint()) h += 10; // "Minsta storlek"
        h += 9 + 11; // Väggkrav, Takkrav
        h += 10; // "SPECIALKRAV"
        h += building.specialRequirements().size() * 12;
        if (!building.mainBonus().isBlank()) h += 4 + 9 + 11; // "BONUS" label + wrapped text estimate
        h += 4 + estimateVerificationTrailHeight(building.verification());
        h += 6 + 90; // structure calculator block (fixed-height estimate)
        h += 6 + 20 + (buildingPlans.size() * 34) + 16;
        return h;
    }

    // ------------------------------------------------------------------
    // Structure Calculator ("Planeringsestimat")
    // ------------------------------------------------------------------

    private int renderStructureCalculator(GuiGraphicsExtractor extractor, Font font, int x, int y, int maxW,
                                           SettlementBuilding building, GlobalBuildingRules rules, int mouseX, int mouseY) {
        TextUtil.drawScaledText(extractor, font, "PLANERINGSESTIMAT", x, y, TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_MUTED, false);
        y += 10;

        y = renderDimensionStepper(extractor, font, x, y, maxW, "Bredd", calcWidth, mouseX, mouseY,
                () -> calcWidth = Math.max(1, calcWidth - 1), () -> calcWidth = Math.min(64, calcWidth + 1));
        y = renderDimensionStepper(extractor, font, x, y, maxW, "Djup", calcDepth, mouseX, mouseY,
                () -> calcDepth = Math.max(1, calcDepth - 1), () -> calcDepth = Math.min(64, calcDepth + 1));
        y = renderDimensionStepper(extractor, font, x, y, maxW, "Höjd", calcHeight, mouseX, mouseY,
                () -> calcHeight = Math.max(1, calcHeight - 1), () -> calcHeight = Math.min(64, calcHeight + 1));

        long floorArea = (long) calcWidth * calcDepth;
        long wallArea = 2L * (calcWidth + calcDepth) * calcHeight;
        long minRoofBlocks = Math.ceil(floorArea * (rules.minRoofCoveragePercent() / 100.0)) > 0
                ? (long) Math.ceil(floorArea * (rules.minRoofCoveragePercent() / 100.0)) : 0;
        long minWallBlocks = (long) Math.ceil(wallArea * (rules.minWallCoveragePercent() / 100.0));

        TextUtil.drawScaledEllipsizedText(extractor, font, "Golv/tak-yta: " + floorArea + " block", x, y, maxW,
                TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_SECONDARY, false);
        y += 9;
        TextUtil.drawScaledEllipsizedText(extractor, font, "Väggyta: " + wallArea + " block", x, y, maxW,
                TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_SECONDARY, false);
        y += 9;
        TextUtil.drawScaledEllipsizedText(extractor, font, "Minsta tak-täckning (" + rules.minRoofCoveragePercent() + "%): ~" + minRoofBlocks + " block",
                x, y, maxW, TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_SECONDARY, false);
        y += 9;
        TextUtil.drawScaledEllipsizedText(extractor, font, "Minsta vägg-täckning (" + rules.minWallCoveragePercent() + "%): ~" + minWallBlocks + " block",
                x, y, maxW, TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_SECONDARY, false);
        y += 11;

        if (building.hasPublishedMinimumFootprint()) {
            boolean tooSmall = !building.fitsFootprint(calcWidth, calcDepth, calcHeight);
            if (tooSmall) {
                y += TextUtil.drawScaledWrappedText(extractor, font, "För litet för " + building.name() + " - minsta storlek är "
                        + building.minWidth() + " × " + building.minDepth()
                        + (building.minHeight() != null ? " (höjd " + building.minHeight() + ")" : "") + ".",
                        x, y, maxW, TypographyScale.META.getScale(), 2, 1, GZTheme.COLOR_STATUS_RED, false) + 2;
            } else {
                y += TextUtil.drawScaledWrappedText(extractor, font, "Måtten uppfyller " + building.name() + "s publicerade minimikrav.",
                        x, y, maxW, TypographyScale.META.getScale(), 2, 1, GZTheme.COLOR_STATUS_GREEN, false) + 2;
            }
        }

        y += TextUtil.drawScaledWrappedText(extractor, font,
                "Detta är en lokal planeringsberäkning, inte en garanti för att GameZone godkänner bygget.",
                x, y, maxW, TypographyScale.META.getScale(), 2, 1, GZTheme.COLOR_TEXT_MUTED, false) + 2;
        return y;
    }

    private int renderDimensionStepper(GuiGraphicsExtractor extractor, Font font, int x, int y, int maxW, String label, int value,
                                        int mouseX, int mouseY, Runnable onMinus, Runnable onPlus) {
        TextUtil.drawScaledEllipsizedText(extractor, font, label + ": " + value, x, y + 2, maxW - 36, TypographyScale.SMALL.getScale(), GZTheme.COLOR_TEXT_PRIMARY, false);
        UiRect minusBtn = new UiRect(x + maxW - 34, y, 14, 12);
        UiRect plusBtn = new UiRect(x + maxW - 17, y, 14, 12);
        GZTheme.drawButton(extractor, font, minusBtn, "-", false, minusBtn.contains(mouseX, mouseY), TypographyScale.META.getScale());
        GZTheme.drawButton(extractor, font, plusBtn, "+", false, plusBtn.contains(mouseX, mouseY), TypographyScale.META.getScale());
        hitTargets.add(new ListRowHit(minusBtn, onMinus));
        hitTargets.add(new ListRowHit(plusBtn, onPlus));
        return y + 14;
    }

    // ------------------------------------------------------------------
    // Local plans section
    // ------------------------------------------------------------------

    private void renderPlansSection(GuiGraphicsExtractor extractor, Font font, int x, int y, int maxW, SettlementBuilding building,
                                     BuildingPlanManager plans, String contextKey, int mouseX, int mouseY) {
        TextUtil.drawScaledText(extractor, font, "MINA LOKALA PLANER", x, y, TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_MUTED, false);
        y += 10;

        UiRect newPlanBtn = new UiRect(x, y, Math.min(140, maxW), 11);
        GZTheme.drawButton(extractor, font, newPlanBtn, "+ Ny plan (" + calcWidth + "x" + calcDepth + "x" + calcHeight + ")", true,
                newPlanBtn.contains(mouseX, mouseY), TypographyScale.META.getScale());
        String buildingId = building.id();
        hitTargets.add(new ListRowHit(newPlanBtn, () -> {
            int w = calcWidth, d = calcDepth, h = calcHeight;
            plans.createPlan(contextKey, buildingId, "Plan " + (plans.getPlansForBuilding(contextKey, buildingId).size() + 1), w, d, h, System.currentTimeMillis());
        }));
        y += 14;

        List<BuildingPlan> buildingPlans = plans.getPlansForBuilding(contextKey, buildingId);
        if (buildingPlans.isEmpty()) {
            TextUtil.drawScaledText(extractor, font, "Inga sparade planer för denna byggnad ännu.", x, y, TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_MUTED, false);
            return;
        }

        long now = System.currentTimeMillis();
        for (BuildingPlan plan : buildingPlans) {
            y = renderPlanRow(extractor, font, x, y, maxW, plan, plans, contextKey, mouseX, mouseY, now);
        }
    }

    private int renderPlanRow(GuiGraphicsExtractor extractor, Font font, int x, int y, int maxW, BuildingPlan plan,
                               BuildingPlanManager plans, String contextKey, int mouseX, int mouseY, long now) {
        GZTheme.drawCard(extractor, new UiRect(x, y, maxW, 32), GZTheme.COLOR_CARD_INNER, GZTheme.COLOR_BORDER_SUBTLE);
        int rowX = x + 3;
        int rowMaxW = maxW - 6;

        boolean isRenaming = plan.id().equals(renamingPlanId);
        if (isRenaming) {
            UiRect nameRect = new UiRect(rowX, y + 2, rowMaxW - 40, 11);
            GZTheme.drawCard(extractor, nameRect, GZTheme.COLOR_CARD_HOVER, GZTheme.COLOR_BORDER_EMERALD);
            String shown = renameText + (((System.currentTimeMillis() / 500) % 2 == 0) ? "_" : "");
            TextUtil.drawScaledEllipsizedText(extractor, font, shown, nameRect.x() + 2, nameRect.y() + 1, nameRect.width() - 4,
                    TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_PRIMARY, false);
            hitTargets.add(new ListRowHit(nameRect, () -> {}));

            UiRect saveBtn = new UiRect(rowX + rowMaxW - 38, y + 2, 38, 11);
            GZTheme.drawButton(extractor, font, saveBtn, "Spara", true, saveBtn.contains(mouseX, mouseY), TypographyScale.META.getScale());
            String planId = plan.id();
            hitTargets.add(new ListRowHit(saveBtn, () -> {
                plans.renamePlan(contextKey, planId, renameText);
                renamingPlanId = null;
            }));
        } else {
            TextUtil.drawScaledEllipsizedText(extractor, font, plan.planName() + " (" + plan.width() + "x" + plan.depth() + "x" + plan.height() + ")",
                    rowX, y + 2, rowMaxW - 40, TypographyScale.SMALL.getScale(), GZTheme.COLOR_TEXT_PRIMARY, false);

            UiRect renameBtn = new UiRect(rowX + rowMaxW - 38, y + 2, 38, 11);
            GZTheme.drawButton(extractor, font, renameBtn, "Byt namn", false, renameBtn.contains(mouseX, mouseY), TypographyScale.META.getScale());
            String planId = plan.id();
            hitTargets.add(new ListRowHit(renameBtn, () -> {
                renamingPlanId = planId;
                renameText = plan.planName();
            }));
        }

        boolean pendingConfirm = plan.id().equals(pendingDeletePlanId) && (now - pendingDeleteAtMs) < 3000L;
        UiRect deleteBtn = new UiRect(rowX + rowMaxW - 38, y + 15, 38, 11);
        GZTheme.drawButton(extractor, font, deleteBtn, pendingConfirm ? "Säker?" : "Ta bort", false, deleteBtn.contains(mouseX, mouseY), TypographyScale.META.getScale());
        String planIdForDelete = plan.id();
        hitTargets.add(new ListRowHit(deleteBtn, () -> {
            if (planIdForDelete.equals(pendingDeletePlanId) && (System.currentTimeMillis() - pendingDeleteAtMs) < 3000L) {
                plans.deletePlan(contextKey, planIdForDelete);
                pendingDeletePlanId = null;
            } else {
                pendingDeletePlanId = planIdForDelete;
                pendingDeleteAtMs = System.currentTimeMillis();
            }
        }));

        int checkX = rowX;
        for (BuildingRequirementKey key : BuildingRequirementKey.values()) {
            boolean done = plan.isCompleted(key);
            String label = (done ? "[x] " : "[ ] ") + key.getDisplayName();
            int labelW = TextUtil.scaledWidth(font, label, TypographyScale.META.getScale()) + 4;
            UiRect checkRect = new UiRect(checkX, y + 16, labelW, 9);
            TextUtil.drawScaledText(extractor, font, label, checkX, y + 16, TypographyScale.META.getScale(),
                    done ? GZTheme.COLOR_STATUS_GREEN : GZTheme.COLOR_TEXT_SECONDARY, false);
            String planIdForToggle = plan.id();
            hitTargets.add(new ListRowHit(checkRect, () -> plans.toggleRequirement(contextKey, planIdForToggle, key)));
            checkX += labelW + 4;
        }

        return y + 36;
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
        if (layout == null) layout = BuildingLayout.calculate(bounds);

        boolean insideSearch = layout.searchRect().contains(mouseX, mouseY);
        boolean insideClear = layout.clearBtnRect().contains(mouseX, mouseY);

        if (insideClear) {
            searchText = "";
            return true;
        }

        searchFocused = insideSearch;
        if (insideSearch) {
            return true;
        }

        if (layout.isCompact() && compactShowingDetail && layout.backBtnRect().contains(mouseX, mouseY)) {
            compactShowingDetail = false;
            return true;
        }

        for (ListRowHit hit : hitTargets) {
            if (hit.rect().contains(mouseX, mouseY)) {
                hit.action().run();
                return true;
            }
        }

        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (layout == null) return false;

        if (layout.listRect().contains(mouseX, mouseY)) {
            listScrollOffset = Math.max(0, listScrollOffset - (int) (scrollY * 14));
            return true;
        }
        if (layout.detailRect().contains(mouseX, mouseY)) {
            detailScrollOffset = Math.max(0, detailScrollOffset - (int) (scrollY * 14));
            return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (event == null) return false;
        String s = event.codepointAsString();
        if (s == null || s.isEmpty()) return false;

        if (renamingPlanId != null) {
            if (renameText.length() < 32) renameText += s;
            return true;
        }
        if (searchFocused) {
            if (searchText.length() < MAX_SEARCH_LENGTH) searchText += s;
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event == null) return false;
        int key = event.key();

        if (renamingPlanId != null) {
            if (key == GLFW.GLFW_KEY_BACKSPACE) {
                if (!renameText.isEmpty()) renameText = renameText.substring(0, renameText.length() - 1);
                return true;
            }
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                renamingPlanId = null;
                return true;
            }
            return false;
        }

        if (searchFocused) {
            if (key == GLFW.GLFW_KEY_BACKSPACE) {
                if (!searchText.isEmpty()) searchText = searchText.substring(0, searchText.length() - 1);
                return true;
            }
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                searchFocused = false;
                return true;
            }
            return false;
        }

        return false;
    }
}
