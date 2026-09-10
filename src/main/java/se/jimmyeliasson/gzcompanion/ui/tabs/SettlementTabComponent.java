package se.jimmyeliasson.gzcompanion.ui.tabs;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;
import se.jimmyeliasson.gzcompanion.chest.model.StoredContainer;
import se.jimmyeliasson.gzcompanion.core.CompanionSession;
import se.jimmyeliasson.gzcompanion.knowledge.common.KnowledgeModuleStatus;
import se.jimmyeliasson.gzcompanion.knowledge.common.VerificationMetadata;
import se.jimmyeliasson.gzcompanion.knowledge.common.VerificationStatus;
import se.jimmyeliasson.gzcompanion.knowledge.crafting.bridge.MinecraftRecipeDisplayAdapter;
import se.jimmyeliasson.gzcompanion.knowledge.settlement.ItemRequirement;
import se.jimmyeliasson.gzcompanion.knowledge.settlement.LevelRangeSummary;
import se.jimmyeliasson.gzcompanion.knowledge.settlement.SettlementCatalog;
import se.jimmyeliasson.gzcompanion.knowledge.settlement.SettlementFoundation;
import se.jimmyeliasson.gzcompanion.knowledge.settlement.SettlementLevel;
import se.jimmyeliasson.gzcompanion.settlement.SettlementPlannerManager;
import se.jimmyeliasson.gzcompanion.settlement.storage.MemberNote;
import se.jimmyeliasson.gzcompanion.settlement.storage.SettlementPlannerProfile;
import se.jimmyeliasson.gzcompanion.ui.GZCompanionMainScreen;
import se.jimmyeliasson.gzcompanion.ui.GZTheme;
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
 * Renders the Settlement tab: a local reference/planner/calculator/organizer built entirely on
 * verified GameZone Rule Pack knowledge (the current "Settlement Levels 1.0" progression) plus
 * purely local planning state. Never claims to know the player's actual, server-observed
 * settlement level, roster, or inventory - only what the player locally chose to plan around, or
 * what a "last-known" Chest Manager estimate explicitly labeled as such provides.
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

    @Override
    public boolean isTextInputFocused() {
        return mode == Mode.MEDLEMMAR && editingMember && focusedField != null;
    }

    public void render(GuiGraphicsExtractor extractor, Font font, UiRect bounds, int mouseX, int mouseY, GZCompanionMainScreen mainScreen) {
        this.layout = SettlementLayout.calculate(bounds);
        hitTargets.clear();

        CompanionSession session = CompanionSession.getInstance();
        KnowledgeModuleStatus status = session.getSettlementCatalogStatus();

        renderHeader(extractor, font, layout.headerRect(), status);
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

        String contextKey = session.getCurrentStorageContext();
        SettlementPlannerManager planner = session.getSettlementPlannerManager();
        SettlementPlannerProfile profile = planner.getProfile(contextKey);

        switch (mode) {
            case OVERSIKT -> renderOverview(extractor, font, layout.panelRect(), catalog, profile);
            case PROGRESSION -> renderProgression(extractor, font, catalog, planner, profile, contextKey, mouseX, mouseY);
            case MATERIAL -> renderMaterial(extractor, font, session, catalog, planner, profile, contextKey, mouseX, mouseY);
            case MEDLEMMAR -> renderMembers(extractor, font, planner, profile, contextKey, mouseX, mouseY);
        }
    }

    private void renderHeader(GuiGraphicsExtractor extractor, Font font, UiRect headerRect, KnowledgeModuleStatus status) {
        GZTheme.drawIcon(extractor, IconId.SETTLEMENT, headerRect.x(), headerRect.y() + 1, 10, GZTheme.COLOR_MINT);
        TextUtil.drawScaledText(extractor, font, "Settlement", headerRect.x() + 13, headerRect.y() + 1,
                TypographyScale.HEADING.getScale(), GZTheme.COLOR_TEXT_PRIMARY, true);

        String badgeLabel = status.isAvailable() ? "Laddad" : status.getDisplayName();
        int badgeW = TextUtil.scaledWidth(font, badgeLabel, TypographyScale.META.getScale()) + 14;
        int dot = status.isAvailable() ? GZTheme.COLOR_STATUS_GREEN : GZTheme.COLOR_STATUS_RED;
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

    private void renderOverview(GuiGraphicsExtractor extractor, Font font, UiRect area, SettlementCatalog catalog, SettlementPlannerProfile profile) {
        GZTheme.drawCard(extractor, area, GZTheme.COLOR_CARD_BG, GZTheme.COLOR_BORDER_SUBTLE);
        int x = area.x() + 6;
        int maxW = area.width() - 12;
        int y = area.y() + 5;

        TextUtil.drawScaledText(extractor, font, "Progression: " + catalog.size() + " nivåer, " + (catalog.size() - 1) + " uppgraderingar",
                x, y, TypographyScale.SMALL.getScale(), GZTheme.COLOR_TEXT_PRIMARY, false);
        y += 11;

        String currentLine = profile.currentLevel() != null
                ? "Planerad nuvarande nivå: " + profile.currentLevel()
                : "Välj din nuvarande nivå i Progression-läget.";
        TextUtil.drawScaledEllipsizedText(extractor, font, currentLine, x, y, maxW, TypographyScale.SMALL.getScale(), GZTheme.COLOR_TEXT_SECONDARY, false);
        y += 10;

        String targetLine = profile.targetLevel() != null
                ? "Mål-nivå: " + profile.targetLevel()
                : "Inget mål valt än.";
        TextUtil.drawScaledEllipsizedText(extractor, font, targetLine, x, y, maxW, TypographyScale.SMALL.getScale(), GZTheme.COLOR_TEXT_SECONDARY, false);
        y += 12;

        if (profile.currentLevel() != null) {
            SettlementLevel next = catalog.byLevel(profile.currentLevel() + 1).orElse(null);
            if (next != null) {
                TextUtil.drawScaledEllipsizedText(extractor, font, "Nästa nivå: " + next.name() + " (" + next.coinCost() + " Coins)",
                        x, y, maxW, TypographyScale.SMALL.getScale(), GZTheme.COLOR_MINT, false);
            }
            y += 12;
        }

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
            TextUtil.drawScaledWrappedText(extractor, font, "Grundläggande settlement-fakta kunde inte laddas i denna omgång.", x, y, maxW,
                    TypographyScale.META.getScale(), 2, 1, GZTheme.COLOR_TEXT_MUTED, false);
        }
    }

    // ------------------------------------------------------------------
    // PROGRESSION
    // ------------------------------------------------------------------

    private void renderProgression(GuiGraphicsExtractor extractor, Font font, SettlementCatalog catalog,
                                    SettlementPlannerManager planner, SettlementPlannerProfile profile, String contextKey,
                                    int mouseX, int mouseY) {
        boolean showUnverified = CompanionSession.getInstance().getSettingsManager().getSettings().showUnverifiedKnowledge();
        List<SettlementLevel> levels = showUnverified ? catalog.levels()
                : catalog.levels().stream().filter(l -> l.verification().status() == VerificationStatus.VERIFIED).toList();
        if (levels.isEmpty()) levels = catalog.levels();

        if (progressionSelectedLevel == null || catalog.byLevel(progressionSelectedLevel).isEmpty()) {
            progressionSelectedLevel = profile.currentLevel() != null && catalog.byLevel(profile.currentLevel()).isPresent()
                    ? profile.currentLevel() : levels.get(0).level();
        }

        if (layout.isCompact()) {
            if (compactShowingDetail) {
                renderProgressionDetail(extractor, font, layout.detailRect(), catalog, planner, contextKey, mouseX, mouseY, true);
            } else {
                renderProgressionList(extractor, font, layout.listRect(), levels, mouseX, mouseY);
            }
        } else {
            renderProgressionList(extractor, font, layout.listRect(), levels, mouseX, mouseY);
            renderProgressionDetail(extractor, font, layout.detailRect(), catalog, planner, contextKey, mouseX, mouseY, false);
        }
    }

    private static int calculateMaxScroll(int rowH, int rowCount, int visibleH, int headerH) {
        int totalH = headerH + (rowCount * rowH);
        return Math.max(0, totalH - Math.max(1, visibleH));
    }

    private void renderProgressionList(GuiGraphicsExtractor extractor, Font font, UiRect listRect, List<SettlementLevel> levels, int mouseX, int mouseY) {
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
                int bg = isSelected ? GZTheme.COLOR_NAV_ACTIVE : (isHovered ? GZTheme.COLOR_NAV_HOVER : 0);
                if (bg != 0) GZTheme.drawCard(extractor, rowRect, bg, isSelected ? GZTheme.COLOR_BORDER_EMERALD : 0);

                GZTheme.drawStatusDot(extractor, rowRect.x() + 4, rowRect.y() + 6, level.verification().status().getArgbColor());
                TextUtil.drawScaledEllipsizedText(extractor, font, level.level() + ". " + level.name(), rowRect.x() + 11, rowRect.y() + 3,
                        rowRect.width() - 15, TypographyScale.SMALL.getScale(), GZTheme.COLOR_TEXT_PRIMARY, isSelected);

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

    private int estimateLevelDetailHeight(SettlementLevel level) {
        int h = 11 + 10; // heading + coin cost
        if (level.requiredBuildingName() != null) h += 10;
        if (level.unlockedBuildingName() != null) h += 10;
        h += 10; // "MATERIAL" label
        h += level.items().size() * 12;
        h += 6;
        h += estimateVerificationTrailHeight(level.verification());
        return h;
    }

    private void renderProgressionDetail(GuiGraphicsExtractor extractor, Font font, UiRect detailRect, SettlementCatalog catalog,
                                          SettlementPlannerManager planner, String contextKey, int mouseX, int mouseY, boolean isCompact) {
        GZTheme.drawCard(extractor, detailRect, GZTheme.COLOR_CARD_BG, GZTheme.COLOR_BORDER_SUBTLE);
        SettlementLevel level = catalog.byLevel(progressionSelectedLevel).orElse(null);
        if (level == null) return;

        int contentTop = detailRect.y() + (isCompact ? 16 : 4);
        UiRect contentArea = new UiRect(detailRect.x() + 1, contentTop, detailRect.width() - 2, detailRect.bottom() - contentTop - 1);
        int pad = 5;
        int maxW = contentArea.width() - (pad * 2);

        int maxScroll = Math.max(0, estimateLevelDetailHeight(level) - contentArea.height());
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

        TextUtil.drawScaledEllipsizedText(extractor, font, "Kostnad: " + level.coinCost() + " Coins", x, y, maxW,
                TypographyScale.SMALL.getScale(), GZTheme.COLOR_TEXT_SECONDARY, false);
        y += 10;

        if (level.requiredBuildingName() != null) {
            TextUtil.drawScaledEllipsizedText(extractor, font, "Kräver byggnad: " + level.requiredBuildingName(), x, y, maxW,
                    TypographyScale.SMALL.getScale(), GZTheme.COLOR_TEXT_SECONDARY, false);
            y += 10;
        }
        if (level.unlockedBuildingName() != null) {
            String line = "Låser upp: " + level.unlockedBuildingName()
                    + (level.unlockedBuildingBonus() != null ? " (" + level.unlockedBuildingBonus() + ")" : "");
            TextUtil.drawScaledEllipsizedText(extractor, font, line, x, y, maxW,
                    TypographyScale.SMALL.getScale(), GZTheme.COLOR_STATUS_YELLOW, false);
            y += 10;
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
        int btnY = detailRect.bottom() - 14;
        int btnW = Math.max(40, (detailRect.width() - 12) / 2);
        UiRect setCurrentBtn = new UiRect(detailRect.x() + 4, btnY, btnW, 11);
        UiRect setTargetBtn = new UiRect(detailRect.x() + 8 + btnW, btnY, btnW, 11);
        GZTheme.drawButton(extractor, font, setCurrentBtn, "Sätt som nuvarande", true, setCurrentBtn.contains(mouseX, mouseY), TypographyScale.META.getScale());
        GZTheme.drawButton(extractor, font, setTargetBtn, "Sätt som mål", false, setTargetBtn.contains(mouseX, mouseY), TypographyScale.META.getScale());

        int selLevel = progressionSelectedLevel;
        hitTargets.add(new ListRowHit(setCurrentBtn, () -> planner.setCurrentLevel(contextKey, selLevel)));
        hitTargets.add(new ListRowHit(setTargetBtn, () -> planner.setTargetLevel(contextKey, selLevel)));
    }

    // ------------------------------------------------------------------
    // MATERIAL
    // ------------------------------------------------------------------

    private void renderMaterial(GuiGraphicsExtractor extractor, Font font, CompanionSession session, SettlementCatalog catalog,
                                 SettlementPlannerManager planner, SettlementPlannerProfile profile, String contextKey,
                                 int mouseX, int mouseY) {
        UiRect area = layout.panelRect();
        GZTheme.drawCard(extractor, area, GZTheme.COLOR_CARD_BG, GZTheme.COLOR_BORDER_SUBTLE);

        if (profile.currentLevel() == null || profile.targetLevel() == null || profile.targetLevel() <= profile.currentLevel()) {
            renderMessage(extractor, font, area, "Välj en nuvarande nivå och en högre mål-nivå i Progression-läget för att se materiallistan.");
            return;
        }

        LevelRangeSummary summary = catalog.levelRange(profile.currentLevel(), profile.targetLevel());
        int x = area.x() + 4;
        int maxW = area.width() - 8;
        int y = area.y() + 4;

        TextUtil.drawScaledEllipsizedText(extractor, font,
                "Nivå " + profile.currentLevel() + " -> " + profile.targetLevel() + ": " + summary.upgradeCount() + " uppgraderingar, " + summary.totalCoinCost() + " Coins",
                x, y, maxW, TypographyScale.SMALL.getScale(), GZTheme.COLOR_TEXT_PRIMARY, false);
        y += 12;

        boolean chestEstimateEnabled = session.getSettingsManager().getSettings().useLastKnownChestDataInPlanners();
        if (chestEstimateEnabled) {
            UiRect calcBtn = new UiRect(x, y, Math.min(150, area.width() - 8), 11);
            boolean calcHov = calcBtn.contains(mouseX, mouseY);
            GZTheme.drawButton(extractor, font, calcBtn, "Beräkna från sparade kistor", false, calcHov, TypographyScale.META.getScale());
            hitTargets.add(new ListRowHit(calcBtn, () -> showingContainerPicker = !showingContainerPicker));
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
                                SettlementPlannerProfile profile, String contextKey, int mouseX, int mouseY) {
        UiRect area = layout.panelRect();
        GZTheme.drawCard(extractor, area, GZTheme.COLOR_CARD_BG, GZTheme.COLOR_BORDER_SUBTLE);

        int x = area.x() + 4;
        int maxW = area.width() - 8;
        int y = area.y() + 4;

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

        if (mode == Mode.PROGRESSION) {
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
}
