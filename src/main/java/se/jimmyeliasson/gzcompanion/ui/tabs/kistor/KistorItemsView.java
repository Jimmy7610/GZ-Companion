package se.jimmyeliasson.gzcompanion.ui.tabs.kistor;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import se.jimmyeliasson.gzcompanion.chest.index.ChestItemEntry;
import se.jimmyeliasson.gzcompanion.chest.index.ChestItemIndex;
import se.jimmyeliasson.gzcompanion.chest.index.ChestItemSearch;
import se.jimmyeliasson.gzcompanion.chest.index.ChestItemSearchResult;
import se.jimmyeliasson.gzcompanion.chest.index.ItemLocation;
import se.jimmyeliasson.gzcompanion.chest.model.ChestFreshness;
import se.jimmyeliasson.gzcompanion.chest.model.ChestGroupFilter;
import se.jimmyeliasson.gzcompanion.chest.model.ChestItemSortMode;
import se.jimmyeliasson.gzcompanion.chest.model.ChestTypeFilter;
import se.jimmyeliasson.gzcompanion.chest.model.StoredContainer;
import se.jimmyeliasson.gzcompanion.chest.nav.PlayerPose;
import se.jimmyeliasson.gzcompanion.ui.GZTheme;
import se.jimmyeliasson.gzcompanion.ui.TypographyScale;
import se.jimmyeliasson.gzcompanion.ui.layout.KistorLayout;
import se.jimmyeliasson.gzcompanion.ui.layout.TextUtil;
import se.jimmyeliasson.gzcompanion.ui.layout.UiRect;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * SAKER mode: "Where are my things?" - the aggregated last-known item index of the current
 * storage context, and per-item detail listing every known storage location that held it, each
 * with a HITTA action. Everything shown comes from already-captured local snapshots.
 */
public final class KistorItemsView {
    public static final int ROW_H = 22;
    public static final int LIST_HEADER_H = 12;
    static final int LOCATION_ROW_H = 23;
    static final int HITTA_BTN_W = 34;

    private record SearchKey(ChestItemIndex index, String query, ChestItemSortMode sort) {}

    private SearchKey lastSearchKey;
    private ChestItemSearchResult lastResult;

    /** Memoized per (index instance, query, sort): no re-search on frames where nothing changed. */
    ChestItemSearchResult results(KistorRenderContext ctx) {
        KistorUiState s = ctx.state();
        ChestTypeFilter type = s.typeFilter;
        ChestGroupFilter group = s.groupFilter;
        ChestItemIndex index = ctx.runtime().itemIndexCache().get(ctx.manager(), ctx.contextKey(), type, group);
        SearchKey key = new SearchKey(index, s.searchText, s.itemSort);
        if (!Objects.equals(key, lastSearchKey) || lastResult == null) {
            List<StoredContainer> scope = ctx.runtime().itemIndexCache().scopeContainers(ctx.manager(), ctx.contextKey(), type, group);
            lastResult = ChestItemSearch.search(ctx.contextKey(), index, scope, s.searchText, s.itemSort, ctx.manager()::itemDisplayName);
            lastSearchKey = key;
        }
        return lastResult;
    }

    public void render(KistorRenderContext ctx) {
        KistorLayout layout = ctx.layout();
        KistorUiState s = ctx.state();
        ChestItemSearchResult result = results(ctx);
        List<ChestItemEntry> entries = result.entries();

        if (s.selectedItemId == null || entries.stream().noneMatch(e -> e.itemId().equals(s.selectedItemId))) {
            s.selectedItemId = entries.isEmpty() ? null : entries.get(0).itemId();
            if (s.selectedItemId == null) s.compactShowingDetail = false;
        }

        if (entries.isEmpty()) {
            KistorEmptyStates.render(ctx, unionRect(layout), ctx.manager().getIndexedCount(ctx.contextKey()) == 0,
                    "Inga saker matchar sökningen.");
            return;
        }

        if (layout.isCompact()) {
            if (s.compactShowingDetail && s.selectedItemId != null) {
                renderDetail(ctx, entries);
            } else {
                renderList(ctx, result);
            }
        } else {
            renderList(ctx, result);
            renderDetail(ctx, entries);
        }
    }

    static UiRect unionRect(KistorLayout layout) {
        UiRect a = layout.listRect();
        UiRect b = layout.detailRect();
        int x = Math.min(a.x(), b.x());
        int right = Math.max(a.right(), b.right());
        int bottom = Math.max(a.bottom(), b.bottom());
        int bottomWithActions = layout.primaryActionRect().width() > 0 ? Math.max(bottom, layout.primaryActionRect().bottom()) : bottom;
        return new UiRect(x, a.y(), right - x, bottomWithActions - a.y());
    }

    // ------------------------------------------------------------------
    // List
    // ------------------------------------------------------------------

    private void renderList(KistorRenderContext ctx, ChestItemSearchResult result) {
        GuiGraphicsExtractor g = ctx.extractor();
        Font font = ctx.font();
        KistorUiState s = ctx.state();
        UiRect listRect = ctx.layout().listRect();
        UiRect viewport = ctx.layout().listContentRect();
        GZTheme.drawCard(g, listRect, GZTheme.COLOR_CARD_BG, GZTheme.COLOR_BORDER_SUBTLE);

        ScrollState scroll = s.itemListScroll;
        scroll.clamp();
        g.enableScissor(viewport.x(), viewport.y(), viewport.right(), viewport.bottom());
        int startY = viewport.y() + 2;
        int y = startY - scroll.offset();

        String header = result.scope() == ChestItemSearchResult.Scope.STORAGE_MATCH
                ? "SAKER I " + result.matchedStorageCount() + (result.matchedStorageCount() == 1 ? " FÖRVARING" : " FÖRVARINGAR")
                : "SAKER · SENAST KÄNT";
        TextUtil.drawScaledEllipsizedText(g, font, header, listRect.x() + 4, y, listRect.width() - 8,
                TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_MUTED, false);
        y += LIST_HEADER_H;

        for (ChestItemEntry entry : result.entries()) {
            UiRect row = new UiRect(listRect.x() + 2, y, listRect.width() - 4, ROW_H);
            if (row.bottom() >= viewport.y() && row.y() <= viewport.bottom()) {
                drawRow(ctx, row, entry);
                String itemId = entry.itemId();
                ctx.addVisibleHit(row, viewport, () -> {
                    s.selectedItemId = itemId;
                    s.compactShowingDetail = true;
                    s.itemDetailScroll.reset();
                });
            }
            y += ROW_H + 1;
        }
        g.disableScissor();
        scroll.recordContent((y + scroll.offset()) - startY + 2, viewport.height());
    }

    private void drawRow(KistorRenderContext ctx, UiRect row, ChestItemEntry entry) {
        GuiGraphicsExtractor g = ctx.extractor();
        Font font = ctx.font();
        boolean selected = entry.itemId().equals(ctx.state().selectedItemId) && !ctx.layout().isCompact();
        boolean hovered = ctx.hovered(row);
        int bg = selected ? GZTheme.COLOR_NAV_ACTIVE : (hovered ? GZTheme.COLOR_NAV_HOVER : 0);
        if (bg != 0) GZTheme.drawCard(g, row, bg, selected ? GZTheme.COLOR_BORDER_EMERALD : 0);

        KistorUi.drawItemIcon(ctx, entry.itemId(), row.x() + 3, row.y() + 3, 16);
        int textX = row.x() + 23;
        int textW = row.width() - 26;
        String countText = KistorUi.formatCount(entry.totalCount());
        int countW = TextUtil.scaledWidth(font, countText, TypographyScale.SMALL.getScale());
        TextUtil.drawScaledEllipsizedText(g, font, entry.displayName(), textX, row.y() + 3, textW - countW - 6,
                TypographyScale.SMALL.getScale(), GZTheme.COLOR_TEXT_PRIMARY, selected);
        TextUtil.drawScaledRightAlignedText(g, font, countText, row.right() - 4, row.y() + 3, countW + 2,
                TypographyScale.SMALL.getScale(), GZTheme.COLOR_MINT, false);
        String spread = "Finns i " + entry.containerCount() + (entry.containerCount() == 1 ? " förvaring" : " förvaringar");
        TextUtil.drawScaledEllipsizedText(g, font, spread, textX, row.y() + 12, textW,
                TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_MUTED, false);
    }

    /** Pure list scroll bound, for tests: header plus one pitch per row. */
    public static int listContentHeight(int rowCount) {
        return 2 + LIST_HEADER_H + rowCount * (ROW_H + 1) + 2;
    }

    // ------------------------------------------------------------------
    // Detail
    // ------------------------------------------------------------------

    private void renderDetail(KistorRenderContext ctx, List<ChestItemEntry> entries) {
        GuiGraphicsExtractor g = ctx.extractor();
        Font font = ctx.font();
        KistorLayout layout = ctx.layout();
        KistorUiState s = ctx.state();
        UiRect detail = layout.detailRect();
        GZTheme.drawCard(g, detail, GZTheme.COLOR_CARD_BG, GZTheme.COLOR_BORDER_SUBTLE);

        ChestItemEntry entry = entries.stream().filter(e -> e.itemId().equals(s.selectedItemId)).findFirst().orElse(null);
        if (entry == null) {
            TextUtil.drawCenteredText(g, font, "Välj en sak i listan", detail.x() + detail.width() / 2, detail.y() + detail.height() / 2 - 4,
                    detail.width(), GZTheme.COLOR_TEXT_MUTED, false);
            return;
        }
        // The full (unscoped) entry, so a storage-scoped search still shows the complete picture.
        ChestItemEntry full = ctx.runtime().itemIndexCache().get(ctx.manager(), ctx.contextKey(), s.typeFilter, s.groupFilter)
                .get(entry.itemId()).orElse(entry);

        if (layout.isCompact()) {
            UiRect back = layout.backBtnRect();
            KistorUi.drawButton(g, font, back, "< Saker", false, ctx.hovered(back), true);
            ctx.addHit(back, () -> s.compactShowingDetail = false);
        }

        UiRect viewport = layout.detailContentRect();
        ScrollState scroll = s.itemDetailScroll;
        scroll.clamp();
        g.enableScissor(viewport.x(), viewport.y(), viewport.right(), viewport.bottom());
        int pad = 5;
        int x = viewport.x() + pad;
        int maxW = viewport.width() - pad * 2;
        int startY = viewport.y() + 2;
        int y = startY - scroll.offset();

        KistorUi.drawItemIcon(ctx, full.itemId(), x, y, 16);
        TextUtil.drawScaledEllipsizedText(g, font, full.displayName().toUpperCase(java.util.Locale.ROOT), x + 20, y + 4, maxW - 20,
                TypographyScale.HEADING.getScale(), GZTheme.COLOR_MINT, true);
        y += 19;

        TextUtil.drawScaledEllipsizedText(g, font, "Senast känt totalt: " + KistorUi.formatCount(full.totalCount()), x, y, maxW,
                TypographyScale.SMALL.getScale(), GZTheme.COLOR_TEXT_PRIMARY, false);
        y += 10;
        TextUtil.drawScaledEllipsizedText(g, font, "Finns i " + full.containerCount() + (full.containerCount() == 1 ? " förvaring" : " förvaringar"),
                x, y, maxW, TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_SECONDARY, false);
        y += 9;
        if (ctx.showTechnicalIds()) {
            TextUtil.drawScaledEllipsizedText(g, font, full.itemId(), x, y, maxW, TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_MUTED, false);
            y += 9;
        }
        y += 3;

        KistorUi.drawSectionLabel(g, font, "SENAST KÄNDA PLATSER", x, y);
        y += 10;

        PlayerPose pose = ctx.playerPose().orElse(null);
        Optional<ItemLocation> nearest = ChestItemIndex.nearestSameDimension(full.locations(), pose);
        for (ItemLocation location : full.locations()) {
            UiRect row = new UiRect(x - 2, y, maxW + 4, LOCATION_ROW_H - 1);
            if (row.bottom() >= viewport.y() && row.y() <= viewport.bottom()) {
                boolean isNearest = nearest.isPresent() && nearest.get().id().equals(location.id());
                drawLocationRow(ctx, row, viewport, location, pose, isNearest);
            }
            y += LOCATION_ROW_H;
        }

        y += 3;
        y += TextUtil.drawScaledWrappedText(g, font, "Antal är senast kända, inte serverns aktuella lager. " + KistorUi.LAST_KNOWN_WARNING,
                x, y, maxW, TypographyScale.META.getScale(), 3, 1, GZTheme.COLOR_STATUS_YELLOW, false);
        y += 4;

        g.disableScissor();
        scroll.recordContent((y + scroll.offset()) - startY, viewport.height());

        renderPinnedAction(ctx, nearest, pose);
    }

    private void drawLocationRow(KistorRenderContext ctx, UiRect row, UiRect viewport, ItemLocation location, PlayerPose pose, boolean isNearest) {
        GuiGraphicsExtractor g = ctx.extractor();
        Font font = ctx.font();
        KistorUiState s = ctx.state();
        boolean navTarget = KistorActions.isNavigationTarget(ctx, location.id());

        UiRect btn = new UiRect(row.right() - HITTA_BTN_W - 3, row.y() + (row.height() - 11) / 2, HITTA_BTN_W, 11);
        boolean hoveredRow = ctx.hovered(row) && !ctx.hovered(btn);
        GZTheme.drawCard(g, row, hoveredRow ? GZTheme.COLOR_CARD_HOVER : GZTheme.COLOR_CARD_INNER,
                navTarget ? GZTheme.COLOR_BORDER_EMERALD : GZTheme.COLOR_BORDER_SUBTLE);

        int textX = row.x() + 4;
        int textRight = btn.x() - 4;
        String countText = KistorUi.formatCount(location.count());
        int countW = TextUtil.scaledWidth(font, countText, TypographyScale.SMALL.getScale());
        String title = (location.favorite() ? "★ " : "") + location.title();
        TextUtil.drawScaledEllipsizedText(g, font, title, textX, row.y() + 3, textRight - textX - countW - 4,
                TypographyScale.SMALL.getScale(), GZTheme.COLOR_TEXT_PRIMARY, false);
        TextUtil.drawScaledRightAlignedText(g, font, countText, textRight, row.y() + 3, countW + 2,
                TypographyScale.SMALL.getScale(), GZTheme.COLOR_MINT, false);

        ChestFreshness freshness = ChestFreshness.classify(location.lastOpenedAtMs(), ctx.nowMs());
        String meta = ChestFreshness.relativeTime(location.lastOpenedAtMs(), ctx.nowMs()) + " · "
                + KistorUi.distanceOrDimension(pose, location.dimensionKey(), location.anchor())
                + (isNearest ? " · NÄRMAST" : "") + (navTarget ? " · NAVIGERAR" : "");
        TextUtil.drawScaledEllipsizedText(g, font, meta, textX, row.y() + 12, textRight - textX,
                TypographyScale.META.getScale(), navTarget ? GZTheme.COLOR_MINT : KistorUi.freshnessColor(freshness), false);

        KistorUi.drawButton(g, font, btn, navTarget ? "Stoppa" : "Hitta", !navTarget, ctx.hovered(btn), true);
        // Button first, so it wins over the row's own "open storage" hit.
        ctx.addVisibleHit(btn, viewport, navTarget ? () -> KistorActions.stopNavigation(ctx) : () -> KistorActions.startNavigation(ctx, location.id()));
        ctx.addVisibleHit(row, viewport, () -> s.openStorage(location.id()));
    }

    private void renderPinnedAction(KistorRenderContext ctx, Optional<ItemLocation> nearest, PlayerPose pose) {
        UiRect btn = ctx.layout().primaryActionRect();
        if (btn.width() <= 0) return;
        if (nearest.isPresent()) {
            ItemLocation n = nearest.get();
            String label = "Hitta närmaste · " + KistorUi.distanceOrDimension(pose, n.dimensionKey(), n.anchor());
            KistorUi.drawButton(ctx.extractor(), ctx.font(), btn, label, true, ctx.hovered(btn), true);
            ctx.addHit(btn, () -> KistorActions.startNavigation(ctx, n.id()));
        } else {
            KistorUi.drawButton(ctx.extractor(), ctx.font(), btn, "Hitta närmaste · ingen i denna dimension", false, false, false);
        }
    }
}
