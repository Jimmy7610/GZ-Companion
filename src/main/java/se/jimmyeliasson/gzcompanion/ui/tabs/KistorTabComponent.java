package se.jimmyeliasson.gzcompanion.ui.tabs;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;
import se.jimmyeliasson.gzcompanion.chest.ChestManager;
import se.jimmyeliasson.gzcompanion.chest.KistorRuntime;
import se.jimmyeliasson.gzcompanion.chest.bridge.MinecraftPlayerPoseReader;
import se.jimmyeliasson.gzcompanion.chest.material.ChestMaterialRequest;
import se.jimmyeliasson.gzcompanion.chest.model.ChestManagerStatus;
import se.jimmyeliasson.gzcompanion.chest.model.StoredContainer;
import se.jimmyeliasson.gzcompanion.chest.model.StoredContainerId;
import se.jimmyeliasson.gzcompanion.chest.nav.ChestNavigationMath;
import se.jimmyeliasson.gzcompanion.chest.nav.ChestNavigationReading;
import se.jimmyeliasson.gzcompanion.chest.nav.KistorNavigationText;
import se.jimmyeliasson.gzcompanion.chest.nav.PlayerPose;
import se.jimmyeliasson.gzcompanion.core.CompanionSession;
import se.jimmyeliasson.gzcompanion.settings.CompanionSettings;
import se.jimmyeliasson.gzcompanion.ui.GZCompanionMainScreen;
import se.jimmyeliasson.gzcompanion.ui.GZTheme;
import se.jimmyeliasson.gzcompanion.ui.IconId;
import se.jimmyeliasson.gzcompanion.ui.ItemHoverTooltips;
import se.jimmyeliasson.gzcompanion.ui.TextInputHandler;
import se.jimmyeliasson.gzcompanion.ui.TypographyScale;
import se.jimmyeliasson.gzcompanion.ui.layout.KistorLayout;
import se.jimmyeliasson.gzcompanion.ui.layout.TextUtil;
import se.jimmyeliasson.gzcompanion.ui.layout.UiRect;
import se.jimmyeliasson.gzcompanion.ui.tabs.kistor.KistorActions;
import se.jimmyeliasson.gzcompanion.ui.tabs.kistor.KistorItemsView;
import se.jimmyeliasson.gzcompanion.ui.tabs.kistor.KistorMaterialView;
import se.jimmyeliasson.gzcompanion.ui.tabs.kistor.KistorRenderContext;
import se.jimmyeliasson.gzcompanion.ui.tabs.kistor.KistorStorageView;
import se.jimmyeliasson.gzcompanion.ui.tabs.kistor.KistorUi;
import se.jimmyeliasson.gzcompanion.ui.tabs.kistor.KistorUiState;
import se.jimmyeliasson.gzcompanion.ui.tabs.kistor.ScrollState;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Kistor 2.0 tab coordinator: header with the SAKER / FÖRVARING (/ MATERIAL) mode control, the
 * "NAVIGERAR" banner, the global local search, per-mode controls, and input routing. The views
 * themselves live in {@code ui.tabs.kistor} ({@link KistorItemsView}, {@link KistorStorageView},
 * {@link KistorMaterialView}); every domain rule lives in the pure {@code chest} packages.
 *
 * <p>Everything shown is "senast känt" (last known) from storage the player personally and
 * legitimately opened - never presented as live/current state.
 */
public class KistorTabComponent implements TextInputHandler {
    private final KistorUiState state = new KistorUiState();
    private final KistorItemsView itemsView = new KistorItemsView();
    private final KistorStorageView storageView = new KistorStorageView();
    private final KistorMaterialView materialView = new KistorMaterialView();
    private final ItemHoverTooltips itemHoverTooltips = new ItemHoverTooltips();
    private final List<KistorRenderContext.Hit> hits = new ArrayList<>();

    private KistorLayout layout;
    private boolean lastNavigating = false;
    private boolean lastMaterialAvailable = false;

    public KistorLayout getLayout() {
        return layout;
    }

    public ItemHoverTooltips getItemHoverTooltips() {
        return itemHoverTooltips;
    }

    /** True while ANY Companion text input owned by this tab (search or an inline editor) is focused. */
    @Override
    public boolean isTextInputFocused() {
        return state.isTextInputFocused();
    }

    public boolean isSearchFocused() {
        return state.searchFocused;
    }

    /**
     * Opens "Hitta material i kistor" for a planner's material requirement set (Settlement /
     * Byggplaner). The request itself is session-only state in {@link KistorRuntime}.
     */
    public void openMaterialRequest(ChestMaterialRequest request) {
        if (request == null) return;
        CompanionSession session = CompanionSession.getInstance();
        session.getKistorRuntime().openMaterialRequest(session.getCurrentStorageContext(), request);
        state.switchMode(KistorLayout.Mode.MATERIAL);
        state.mode = KistorLayout.Mode.MATERIAL;
        state.materialShowPickupList = false;
        state.materialScroll.reset();
    }

    private KistorLayout computeLayout(UiRect bounds, boolean navigating, boolean materialAvailable) {
        if (state.mode == KistorLayout.Mode.MATERIAL && !materialAvailable) state.mode = KistorLayout.Mode.SAKER;
        return KistorLayout.calculate(bounds, state.mode, navigating, materialAvailable, state.compactShowingDetail);
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    public void render(GuiGraphicsExtractor extractor, Font font, UiRect bounds, int mouseX, int mouseY, GZCompanionMainScreen mainScreen) {
        hits.clear();
        itemHoverTooltips.clear();
        long now = System.currentTimeMillis();

        CompanionSession session = CompanionSession.getInstance();
        ChestManager manager = session.getChestManager();
        KistorRuntime runtime = session.getKistorRuntime();
        String contextKey = session.getCurrentStorageContext();

        // Validate navigation against the CURRENT index/context (stops safely if forgotten/reset).
        runtime.navigation().onContextObserved(contextKey);
        Optional<StoredContainer> navTarget = runtime.navigation().resolveTarget(manager);
        boolean materialAvailable = runtime.materialRequest(contextKey).isPresent();

        this.layout = computeLayout(bounds, navTarget.isPresent(), materialAvailable);
        this.lastNavigating = navTarget.isPresent();
        this.lastMaterialAvailable = materialAvailable;

        boolean available = manager != null && manager.getStatus().isAvailable();
        renderHeader(extractor, font, manager, contextKey, mouseX, mouseY, available, materialAvailable);
        if (!available) {
            drawUnavailableState(extractor, font, new UiRect(bounds.x(), layout.searchRect().y(), bounds.width(), bounds.bottom() - layout.searchRect().y()),
                    manager != null ? manager.getStatus() : ChestManagerStatus.UNAVAILABLE);
            return;
        }

        Optional<PlayerPose> pose = MinecraftPlayerPoseReader.read(1.0f);
        CompanionSettings settings = session.getSettingsManager().getSettings();
        KistorRenderContext ctx = new KistorRenderContext(extractor, font, mouseX, mouseY, now, layout, state, manager, runtime, contextKey,
                pose, settings.showTechnicalIds(), settings.useLastKnownChestDataInPlanners(), hits, itemHoverTooltips);

        navTarget.ifPresent(target -> renderNavigationBanner(ctx, target));
        renderSearch(ctx);
        renderControls(ctx);

        switch (state.mode) {
            case SAKER -> itemsView.render(ctx);
            case FORVARING -> storageView.render(ctx);
            case MATERIAL -> runtime.materialRequest(contextKey).ifPresent(request -> materialView.render(ctx, request));
        }
    }

    private void renderHeader(GuiGraphicsExtractor g, Font font, ChestManager manager, String contextKey, int mouseX, int mouseY,
                              boolean available, boolean materialAvailable) {
        UiRect header = layout.headerRect();
        GZTheme.drawIcon(g, IconId.CHEST, header.x(), header.y() + 1, 10, GZTheme.COLOR_MINT);
        TextUtil.drawScaledText(g, font, "Kistor", header.x() + 13, header.y() + 1, TypographyScale.HEADING.getScale(), GZTheme.COLOR_TEXT_PRIMARY, true);
        if (available) {
            int count = manager.getIndexedCount(contextKey);
            int countX = header.x() + 13 + TextUtil.scaledWidth(font, "Kistor", TypographyScale.HEADING.getScale()) + 6;
            int maxW = layout.modeSakerRect().x() - countX - 4;
            if (maxW > 20) {
                TextUtil.drawScaledEllipsizedText(g, font, count + " sparade", countX, header.y() + 3, maxW,
                        TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_MUTED, false);
            }
        }
        drawSegment(g, font, layout.modeSakerRect(), "SAKER", state.mode == KistorLayout.Mode.SAKER, mouseX, mouseY);
        drawSegment(g, font, layout.modeForvaringRect(), "FÖRVARING", state.mode == KistorLayout.Mode.FORVARING, mouseX, mouseY);
        if (materialAvailable) {
            drawSegment(g, font, layout.modeMaterialRect(), "MATERIAL", state.mode == KistorLayout.Mode.MATERIAL, mouseX, mouseY);
        }
    }

    private static void drawSegment(GuiGraphicsExtractor g, Font font, UiRect rect, String label, boolean active, int mouseX, int mouseY) {
        boolean hovered = rect.contains(mouseX, mouseY);
        GZTheme.drawCard(g, rect, active ? GZTheme.COLOR_NAV_ACTIVE : (hovered ? GZTheme.COLOR_NAV_HOVER : GZTheme.COLOR_CARD_INNER),
                active ? GZTheme.COLOR_BORDER_EMERALD : GZTheme.COLOR_BORDER_SUBTLE);
        TextUtil.drawScaledCenteredText(g, font, label, rect.x() + rect.width() / 2, rect.y() + (rect.height() - 7) / 2, rect.width() - 4,
                TypographyScale.META.getScale(), active ? GZTheme.COLOR_TEXT_PRIMARY : GZTheme.COLOR_TEXT_SECONDARY, active);
    }

    private void renderNavigationBanner(KistorRenderContext ctx, StoredContainer target) {
        GuiGraphicsExtractor g = ctx.extractor();
        Font font = ctx.font();
        UiRect banner = layout.navBannerRect();
        GZTheme.drawCard(g, banner, 0x3310B981, GZTheme.COLOR_BORDER_EMERALD);

        String detail;
        if (state.bannerFlash != null && ctx.nowMs() < state.bannerFlashExpiry) {
            detail = state.bannerFlash;
        } else {
            ChestNavigationReading reading = ctx.playerPose().map(p -> ChestNavigationMath.evaluate(p, target)).orElse(null);
            detail = reading != null ? KistorNavigationText.bannerSummary(reading) : "";
        }
        String text = "NAVIGERAR: " + target.displayTitle() + (detail.isEmpty() ? "" : " · " + detail);
        TextUtil.drawScaledEllipsizedText(g, font, text, banner.x() + 4, banner.y() + 3, banner.width() - 8,
                TypographyScale.META.getScale(), GZTheme.COLOR_MINT, false);
        StoredContainerId id = target.id();
        UiRect openRect = new UiRect(banner.x(), banner.y(), banner.width(), banner.height());
        ctx.addHit(openRect, () -> state.openStorage(id));

        UiRect stop = layout.navStopBtnRect();
        KistorUi.drawButton(g, font, stop, "Stoppa", false, ctx.hovered(stop), true);
        ctx.addHit(stop, () -> KistorActions.stopNavigation(ctx));
    }

    private void renderSearch(KistorRenderContext ctx) {
        GuiGraphicsExtractor g = ctx.extractor();
        Font font = ctx.font();
        String placeholder = switch (state.mode) {
            case SAKER -> "Vad letar du efter? Sök sak, förvaring, grupp, plats...";
            case FORVARING -> "Vad letar du efter? Sök förvaring, grupp, notering, koordinat...";
            case MATERIAL -> "Vad letar du efter?";
        };
        KistorUi.drawTextField(g, font, layout.searchRect(), state.searchText, placeholder, state.searchFocused, ctx.nowMs());
        UiRect clear = layout.clearBtnRect();
        if (!state.searchText.isEmpty()) {
            boolean hov = ctx.hovered(clear);
            GZTheme.drawCard(g, clear, hov ? GZTheme.COLOR_CARD_HOVER : GZTheme.COLOR_CARD_INNER, GZTheme.COLOR_BORDER_SUBTLE);
            TextUtil.drawCenteredText(g, font, "x", clear.x() + clear.width() / 2, clear.y() + 2, clear.width(),
                    hov ? GZTheme.COLOR_TEXT_PRIMARY : GZTheme.COLOR_TEXT_MUTED, false);
        }
    }

    private void renderControls(KistorRenderContext ctx) {
        GuiGraphicsExtractor g = ctx.extractor();
        Font font = ctx.font();
        switch (state.mode) {
            case SAKER -> {
                drawControl(ctx, layout.filterBtnRect(), "Typ: " + state.typeFilter.getDisplayName(), state.typeFilter != se.jimmyeliasson.gzcompanion.chest.model.ChestTypeFilter.ALL);
                drawControl(ctx, layout.sortBtnRect(), "Sortering: " + state.itemSort.getDisplayName(), false);
                drawControl(ctx, layout.groupBtnRect(), "Grupp: " + state.groupFilter.displayName(), state.groupFilter.kind() != se.jimmyeliasson.gzcompanion.chest.model.ChestGroupFilter.Kind.ALL);
            }
            case FORVARING -> {
                drawControl(ctx, layout.filterBtnRect(), "Typ: " + state.typeFilter.getDisplayName(), state.typeFilter != se.jimmyeliasson.gzcompanion.chest.model.ChestTypeFilter.ALL);
                drawControl(ctx, layout.sortBtnRect(), "Sortering: " + state.storageSort.getDisplayName(), false);
                drawControl(ctx, layout.groupBtnRect(), "Grupp: " + state.groupFilter.displayName(), state.groupFilter.kind() != se.jimmyeliasson.gzcompanion.chest.model.ChestGroupFilter.Kind.ALL);
            }
            case MATERIAL -> {
                drawControl(ctx, layout.filterBtnRect(), state.materialShowPickupList ? "Visa: Hämtningslista" : "Visa: Tillgång", true);
                drawControl(ctx, layout.sortBtnRect(), state.materialShowPickupList ? "Visa tillgång" : "Hämtningslista", false);
                drawControl(ctx, layout.groupBtnRect(), "Stäng materiallista", false);
            }
        }
    }

    private void drawControl(KistorRenderContext ctx, UiRect rect, String label, boolean active) {
        GuiGraphicsExtractor g = ctx.extractor();
        boolean hov = ctx.hovered(rect);
        GZTheme.drawCard(g, rect, active ? GZTheme.COLOR_NAV_ACTIVE : (hov ? GZTheme.COLOR_NAV_HOVER : GZTheme.COLOR_CARD_INNER),
                active ? GZTheme.COLOR_BORDER_EMERALD : GZTheme.COLOR_BORDER_SUBTLE);
        TextUtil.drawScaledEllipsizedText(g, ctx.font(), label, rect.x() + 3, rect.y() + 2, rect.width() - 6,
                TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_SECONDARY, false);
    }

    private void drawUnavailableState(GuiGraphicsExtractor extractor, Font font, UiRect area, ChestManagerStatus status) {
        GZTheme.drawCard(extractor, area, GZTheme.COLOR_CARD_BG, GZTheme.COLOR_BORDER_SUBTLE);
        String msg = switch (status) {
            case ERROR -> "Fel inträffade vid inläsning av kistindexet.";
            case UNAVAILABLE -> "Kistor är inte tillgängligt just nu.";
            case INCOMPATIBLE -> "Kistindexet är sparat av en nyare version av GZ Companion och kan inte läsas här.";
            case LOADED -> "Laddar...";
        };
        TextUtil.drawScaledWrappedText(extractor, font, msg, area.x() + 10, area.y() + (area.height() / 2) - 8, area.width() - 20,
                TypographyScale.SMALL.getScale(), 3, 1, GZTheme.COLOR_TEXT_MUTED, false);
    }

    /**
     * Legacy pure list-scroll bound (kept for layout tests): the FÖRVARING list's section header
     * plus one row pitch per storage row, against the list viewport.
     */
    public static int calculateMaxListScroll(UiRect listRect, int rowCount) {
        return calculateMaxListScroll(listRect, rowCount, false);
    }

    public static int calculateMaxListScroll(UiRect listRect, int rowCount, boolean compact) {
        int rowH = compact ? KistorStorageView.COMPACT_ROW_H : KistorStorageView.ROW_H;
        int totalH = 2 + 11 + (rowCount * (rowH + 1)) + 2;
        int visibleH = Math.max(1, listRect.height() - 2);
        return Math.max(0, totalH - visibleH);
    }

    // ------------------------------------------------------------------
    // Input handling
    // ------------------------------------------------------------------

    private void ensureLayout(UiRect bounds) {
        if (layout == null && bounds != null) {
            layout = computeLayout(bounds, lastNavigating, lastMaterialAvailable);
        }
    }

    private KistorRenderContext.Hit findHit(double mouseX, double mouseY) {
        for (KistorRenderContext.Hit hit : hits) {
            if (hit.rect().contains(mouseX, mouseY)) return hit;
        }
        return null;
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button, UiRect bounds, GZCompanionMainScreen mainScreen) {
        if (button != 0) return false;
        ensureLayout(bounds);
        if (layout == null) return false;

        // 1. An open inline editor: its own controls (field, Spara, group chips) keep it open;
        //    any other click cancels it without saving ("click away cancels"), then is handled.
        if (state.editField != null) {
            KistorRenderContext.Hit hit = findHit(mouseX, mouseY);
            if (hit != null && hit.keepsEdit()) {
                hit.action().run();
                return true;
            }
            state.cancelEdit();
        }

        // 2. Search field + clear.
        if (layout.clearBtnRect().contains(mouseX, mouseY)) {
            state.searchText = "";
            return true;
        }
        state.searchFocused = layout.searchRect().contains(mouseX, mouseY);
        if (state.searchFocused) return true;

        // 3. Mode segments.
        if (layout.modeSakerRect().contains(mouseX, mouseY)) {
            state.switchMode(KistorLayout.Mode.SAKER);
            return true;
        }
        if (layout.modeForvaringRect().contains(mouseX, mouseY)) {
            state.switchMode(KistorLayout.Mode.FORVARING);
            return true;
        }
        if (layout.modeMaterialRect().contains(mouseX, mouseY)) {
            state.switchMode(KistorLayout.Mode.MATERIAL);
            return true;
        }

        // 4. Controls row.
        if (handleControlClick(mouseX, mouseY)) return true;

        // 5. Everything drawn last frame (content rows, banner STOPPA, pinned actions).
        KistorRenderContext.Hit hit = findHit(mouseX, mouseY);
        if (hit != null) {
            if (!(state.mode == KistorLayout.Mode.FORVARING && hit.rect().equals(layout.forgetBtnRect()))) {
                state.confirmingForget = false;
            }
            hit.action().run();
            return true;
        }
        return false;
    }

    private boolean handleControlClick(double mouseX, double mouseY) {
        boolean filter = layout.filterBtnRect().contains(mouseX, mouseY);
        boolean sort = layout.sortBtnRect().contains(mouseX, mouseY);
        boolean group = layout.groupBtnRect().contains(mouseX, mouseY);
        if (!filter && !sort && !group) return false;

        switch (state.mode) {
            case SAKER, FORVARING -> {
                if (filter) state.typeFilter = state.typeFilter.next();
                if (sort) {
                    if (state.mode == KistorLayout.Mode.SAKER) state.itemSort = state.itemSort.next();
                    else state.storageSort = state.storageSort.next();
                }
                if (group) {
                    CompanionSession session = CompanionSession.getInstance();
                    state.groupFilter = state.groupFilter.next(session.getChestManager().getGroups(session.getCurrentStorageContext()));
                }
                state.itemListScroll.reset();
                state.storageListScroll.reset();
            }
            case MATERIAL -> {
                if (filter || sort) {
                    state.materialShowPickupList = !state.materialShowPickupList;
                    state.materialScroll.reset();
                }
                if (group) {
                    CompanionSession.getInstance().getKistorRuntime().clearMaterialRequest();
                    state.switchMode(KistorLayout.Mode.SAKER);
                }
            }
        }
        return true;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (layout == null) return false;

        // In compact mode, listRect() and detailRect() are the SAME rectangle (only one pane is
        // rendered at a time), so routing must follow compactShowingDetail - never the rect alone,
        // or the hidden pane would silently eat every scroll event.
        if (state.mode == KistorLayout.Mode.MATERIAL) {
            return layout.listRect().contains(mouseX, mouseY) && state.materialScroll.scrollBy(scrollY);
        }
        ScrollState list = state.mode == KistorLayout.Mode.SAKER ? state.itemListScroll : state.storageListScroll;
        ScrollState detail = state.mode == KistorLayout.Mode.SAKER ? state.itemDetailScroll : state.storageDetailScroll;
        boolean hasSelection = state.mode == KistorLayout.Mode.SAKER ? state.selectedItemId != null : state.selectedStorage != null;

        if (layout.isCompact()) {
            if (!layout.listRect().contains(mouseX, mouseY)) return false;
            if (state.compactShowingDetail) {
                return hasSelection && detail.scrollBy(scrollY);
            }
            return list.scrollBy(scrollY);
        }
        if (layout.listRect().contains(mouseX, mouseY)) {
            list.scrollBy(scrollY);
            return true;
        }
        if (layout.detailRect().contains(mouseX, mouseY)) {
            return hasSelection && detail.scrollBy(scrollY);
        }
        return false;
    }

    /**
     * Test-only: drives the compact list/detail scroll-routing state directly, since the layout is
     * otherwise only computed inside the render path, which needs a live Font.
     */
    void setCompactStateForTesting(UiRect bounds, boolean compactShowingDetail, StoredContainerId selectedId) {
        this.state.mode = KistorLayout.Mode.FORVARING;
        this.state.compactShowingDetail = compactShowingDetail;
        this.state.selectedStorage = selectedId;
        this.layout = computeLayout(bounds, false, false);
    }

    int listScrollOffsetForTesting() {
        return state.mode == KistorLayout.Mode.SAKER ? state.itemListScroll.offset() : state.storageListScroll.offset();
    }

    KistorUiState stateForTesting() {
        return state;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (event == null) return false;
        String s = event.codepointAsString();
        if (s == null || s.isEmpty()) return false;

        if (state.editField != null) {
            if (state.editText.length() < state.editMaxLength()) {
                state.editText = state.editText + s;
            }
            return true;
        }
        if (state.searchFocused) {
            if (state.searchText.length() < KistorUiState.MAX_SEARCH_LENGTH) {
                state.searchText = state.searchText + s;
            }
            // The search is global: typing while the material list is open goes back to SAKER.
            if (state.mode == KistorLayout.Mode.MATERIAL) {
                state.switchMode(KistorLayout.Mode.SAKER);
                state.searchFocused = true;
            }
            state.itemListScroll.reset();
            state.storageListScroll.reset();
            return true;
        }
        return false;
    }

    /**
     * Handles a key press while the Kistor tab is active. Priority: an open inline editor takes
     * ESC (cancel) / Enter (save) / Backspace first, then the search field. Any other key while
     * an editor is open is consumed here (so it never leaks into unrelated global shortcuts) but
     * does nothing beyond that - character insertion happens via {@link #charTyped}.
     */
    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event == null) return false;
        int key = event.key();

        if (state.editField != null) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                state.cancelEdit();
                return true;
            }
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                CompanionSession session = CompanionSession.getInstance();
                KistorStorageView.commitEdit(state, session.getChestManager(), session.getCurrentStorageContext(), state.selectedStorage);
                return true;
            }
            if (key == GLFW.GLFW_KEY_BACKSPACE) {
                if (!state.editText.isEmpty()) {
                    state.editText = state.editText.substring(0, state.editText.length() - 1);
                }
                return true;
            }
            return true;
        }

        if (state.searchFocused) {
            if (key == GLFW.GLFW_KEY_BACKSPACE) {
                if (!state.searchText.isEmpty()) {
                    state.searchText = state.searchText.substring(0, state.searchText.length() - 1);
                }
                return true;
            }
            if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                state.searchFocused = false;
                return true;
            }
            return false;
        }

        return false;
    }
}
