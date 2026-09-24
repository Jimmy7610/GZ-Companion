package se.jimmyeliasson.gzcompanion.ui.tabs.kistor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import se.jimmyeliasson.gzcompanion.chest.ChestManager;
import se.jimmyeliasson.gzcompanion.chest.index.ChestSnapshotDiff;
import se.jimmyeliasson.gzcompanion.chest.model.ChestFreshness;
import se.jimmyeliasson.gzcompanion.chest.model.ChestGroupFilter;
import se.jimmyeliasson.gzcompanion.chest.model.ChestSortMode;
import se.jimmyeliasson.gzcompanion.chest.model.ChestTypeFilter;
import se.jimmyeliasson.gzcompanion.chest.model.DimensionNames;
import se.jimmyeliasson.gzcompanion.chest.model.StorageShape;
import se.jimmyeliasson.gzcompanion.chest.model.StoredContainer;
import se.jimmyeliasson.gzcompanion.chest.model.StoredContainerId;
import se.jimmyeliasson.gzcompanion.chest.nav.ChestNavigationManager;
import se.jimmyeliasson.gzcompanion.ui.GZTheme;
import se.jimmyeliasson.gzcompanion.ui.TypographyScale;
import se.jimmyeliasson.gzcompanion.ui.layout.KistorLayout;
import se.jimmyeliasson.gzcompanion.ui.layout.TextUtil;
import se.jimmyeliasson.gzcompanion.ui.layout.UiRect;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * FÖRVARING mode: "Which storage have I opened, and what was in it?" - storage cards with the
 * most meaningful last-known items, pinned favorites first, and a full detail pane with local
 * metadata, "sedan förra öppningen" differences and actions (HITTA, rename, favorite, copy
 * coordinates, location note, group, forget).
 */
public final class KistorStorageView {
    public static final int ROW_H = 33;
    public static final int COMPACT_ROW_H = 25;
    static final int SECTION_H = 11;
    static final int ITEM_LINE_H = 12;
    static final int MAX_DIFF_LINES = 12;
    static final int MINI_ICON = 10;

    private record SearchKey(long revision, String contextKey, String query, ChestTypeFilter type, ChestSortMode sort, ChestGroupFilter group) {}

    private SearchKey lastKey;
    private List<StoredContainer> lastResults = List.of();

    /** Memoized per Chest Manager revision + query/filters. */
    List<StoredContainer> results(KistorRenderContext ctx) {
        KistorUiState s = ctx.state();
        SearchKey key = new SearchKey(ctx.manager().revision(), ctx.contextKey(), s.searchText, s.typeFilter, s.storageSort, s.groupFilter);
        if (!Objects.equals(key, lastKey)) {
            lastResults = ctx.manager().search(ctx.contextKey(), s.searchText, s.typeFilter, s.storageSort, s.groupFilter);
            lastKey = key;
        }
        return lastResults;
    }

    public void render(KistorRenderContext ctx) {
        KistorLayout layout = ctx.layout();
        KistorUiState s = ctx.state();
        List<StoredContainer> filtered = results(ctx);

        if (s.selectedStorage == null || filtered.stream().noneMatch(c -> c.id().equals(s.selectedStorage))) {
            // Keep an explicitly opened storage (e.g. from SAKER) selected even if the current
            // search would hide it; otherwise fall back to the first visible result.
            boolean stillExists = s.selectedStorage != null && ctx.manager().getContainer(ctx.contextKey(), s.selectedStorage).isPresent()
                    && s.compactShowingDetail;
            if (!stillExists) {
                s.selectedStorage = filtered.isEmpty() ? null : filtered.get(0).id();
                if (s.selectedStorage == null) s.compactShowingDetail = false;
            }
        }

        if (filtered.isEmpty() && s.selectedStorage == null) {
            KistorEmptyStates.render(ctx, KistorItemsView.unionRect(layout), ctx.manager().getIndexedCount(ctx.contextKey()) == 0,
                    "Inga sparade förvaringar matchar sökningen.");
            return;
        }

        if (layout.isCompact()) {
            if (s.compactShowingDetail && s.selectedStorage != null) {
                renderDetail(ctx);
            } else {
                renderList(ctx, filtered);
            }
        } else {
            renderList(ctx, filtered);
            renderDetail(ctx);
        }
    }

    // ------------------------------------------------------------------
    // List
    // ------------------------------------------------------------------

    private void renderList(KistorRenderContext ctx, List<StoredContainer> containers) {
        GuiGraphicsExtractor g = ctx.extractor();
        Font font = ctx.font();
        KistorUiState s = ctx.state();
        UiRect listRect = ctx.layout().listRect();
        UiRect viewport = ctx.layout().listContentRect();
        GZTheme.drawCard(g, listRect, GZTheme.COLOR_CARD_BG, GZTheme.COLOR_BORDER_SUBTLE);

        boolean compact = ctx.layout().isCompact();
        int rowH = compact ? COMPACT_ROW_H : ROW_H;
        List<StoredContainer> favorites = new ArrayList<>();
        List<StoredContainer> others = new ArrayList<>();
        for (StoredContainer c : containers) {
            (c.favorite() ? favorites : others).add(c);
        }

        ScrollState scroll = s.storageListScroll;
        scroll.clamp();
        g.enableScissor(viewport.x(), viewport.y(), viewport.right(), viewport.bottom());
        int startY = viewport.y() + 2;
        int y = startY - scroll.offset();

        if (!favorites.isEmpty()) {
            KistorUi.drawSectionLabel(g, font, "FÄSTA", listRect.x() + 4, y);
            y += SECTION_H;
            for (StoredContainer c : favorites) {
                y = drawListRow(ctx, viewport, listRect, c, y, rowH);
            }
            y += 3;
        }
        KistorUi.drawSectionLabel(g, font, favorites.isEmpty() ? "FÖRVARINGAR" : "ÖVRIGA", listRect.x() + 4, y);
        y += SECTION_H;
        for (StoredContainer c : others) {
            y = drawListRow(ctx, viewport, listRect, c, y, rowH);
        }
        g.disableScissor();
        scroll.recordContent((y + scroll.offset()) - startY + 2, viewport.height());
    }

    private int drawListRow(KistorRenderContext ctx, UiRect viewport, UiRect listRect, StoredContainer container, int y, int rowH) {
        UiRect row = new UiRect(listRect.x() + 2, y, listRect.width() - 4, rowH);
        if (row.bottom() >= viewport.y() && row.y() <= viewport.bottom()) {
            drawCard(ctx, row, container);
            StoredContainerId id = container.id();
            KistorUiState s = ctx.state();
            ctx.addVisibleHit(row, viewport, () -> {
                s.selectedStorage = id;
                s.compactShowingDetail = true;
                s.confirmingForget = false;
                s.storageDetailScroll.reset();
                s.cancelEdit();
            });
        }
        return y + rowH + 1;
    }

    private void drawCard(KistorRenderContext ctx, UiRect row, StoredContainer container) {
        GuiGraphicsExtractor g = ctx.extractor();
        Font font = ctx.font();
        boolean compact = ctx.layout().isCompact();
        boolean selected = container.id().equals(ctx.state().selectedStorage) && !compact;
        boolean hovered = ctx.hovered(row);
        boolean navTarget = KistorActions.isNavigationTarget(ctx, container.id());
        int bg = selected ? GZTheme.COLOR_NAV_ACTIVE : (hovered ? GZTheme.COLOR_NAV_HOVER : 0x200A1017);
        int border = selected || navTarget ? GZTheme.COLOR_BORDER_EMERALD : 0x22475569;
        GZTheme.drawCard(g, row, bg, border);

        ChestFreshness freshness = ChestFreshness.classify(container.lastOpenedAtMs(), ctx.nowMs());
        int badgeW = KistorUi.drawFreshnessBadgeRightAligned(g, font, row.right() - 3, row.y() + 3, freshness);

        String title = (container.favorite() ? "★ " : "") + container.displayTitle().toUpperCase(java.util.Locale.ROOT);
        if (container.hasLabel()) title += "  " + container.storageTypeText();
        TextUtil.drawScaledEllipsizedText(g, font, title, row.x() + 4, row.y() + 3, row.width() - badgeW - 10,
                TypographyScale.SMALL.getScale(), navTarget ? GZTheme.COLOR_MINT : GZTheme.COLOR_TEXT_PRIMARY, selected);

        drawTopItems(ctx, container, row.x() + 4, row.y() + 12, row.width() - 8);

        if (!compact) {
            String meta = DimensionNames.shortName(container.dimensionKey()) + " · Öppnad "
                    + ChestFreshness.relativeTime(container.lastOpenedAtMs(), ctx.nowMs())
                    + (container.group() != null ? " · " + container.group() : "")
                    + (navTarget ? " · NAVIGERAR" : "");
            TextUtil.drawScaledEllipsizedText(g, font, meta, row.x() + 4, row.y() + 24, row.width() - 8,
                    TypographyScale.META.getScale(), navTarget ? GZTheme.COLOR_MINT : GZTheme.COLOR_TEXT_MUTED, false);
        }
    }

    /** Most meaningful items first (largest last-known count), as many as fit, then "+N andra". */
    static List<StoredContainer.AggregatedItem> topItems(StoredContainer container) {
        List<StoredContainer.AggregatedItem> items = new ArrayList<>(container.aggregatedItems());
        items.sort(Comparator.comparingInt(StoredContainer.AggregatedItem::count).reversed().thenComparing(StoredContainer.AggregatedItem::itemId));
        return items;
    }

    private void drawTopItems(KistorRenderContext ctx, StoredContainer container, int x, int y, int maxW) {
        GuiGraphicsExtractor g = ctx.extractor();
        Font font = ctx.font();
        List<StoredContainer.AggregatedItem> items = topItems(container);
        if (items.isEmpty()) {
            TextUtil.drawScaledText(g, font, "Tom senast öppnad", x, y + 1, TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_MUTED, false);
            return;
        }
        float scale = TypographyScale.META.getScale();
        int reserve = TextUtil.scaledWidth(font, "+99 andra", scale) + 2;
        int cursor = x;
        int shown = 0;
        for (int i = 0; i < items.size(); i++) {
            StoredContainer.AggregatedItem item = items.get(i);
            String count = KistorUi.formatCount(item.count());
            int chipW = MINI_ICON + 1 + TextUtil.scaledWidth(font, count, scale) + 5;
            boolean last = i == items.size() - 1;
            int limit = x + maxW - (last ? 0 : reserve);
            if (cursor + chipW > limit) break;
            KistorUi.drawItemIcon(ctx, item.itemId(), cursor, y, MINI_ICON);
            TextUtil.drawScaledText(g, font, count, cursor + MINI_ICON + 1, y + 2, scale, GZTheme.COLOR_TEXT_SECONDARY, false);
            cursor += chipW;
            shown++;
        }
        int rest = items.size() - shown;
        if (rest > 0) {
            TextUtil.drawScaledText(g, font, "+" + rest + " andra", cursor, y + 2, scale, GZTheme.COLOR_TEXT_MUTED, false);
        }
    }

    // ------------------------------------------------------------------
    // Detail
    // ------------------------------------------------------------------

    private void renderDetail(KistorRenderContext ctx) {
        GuiGraphicsExtractor g = ctx.extractor();
        Font font = ctx.font();
        KistorLayout layout = ctx.layout();
        KistorUiState s = ctx.state();
        UiRect detail = layout.detailRect();
        GZTheme.drawCard(g, detail, GZTheme.COLOR_CARD_BG, GZTheme.COLOR_BORDER_SUBTLE);

        StoredContainer container = s.selectedStorage != null ? ctx.manager().getContainer(ctx.contextKey(), s.selectedStorage).orElse(null) : null;
        if (container == null) {
            TextUtil.drawCenteredText(g, font, "Välj en förvaring i listan", detail.x() + detail.width() / 2, detail.y() + detail.height() / 2 - 4,
                    detail.width(), GZTheme.COLOR_TEXT_MUTED, false);
            return;
        }

        if (layout.isCompact()) {
            UiRect back = layout.backBtnRect();
            KistorUi.drawButton(g, font, back, "< Förvaring", false, ctx.hovered(back), true);
            ctx.addHit(back, () -> {
                s.compactShowingDetail = false;
                s.cancelEdit();
            });
        }

        UiRect viewport = layout.detailContentRect();
        ScrollState scroll = s.storageDetailScroll;
        scroll.clamp();
        g.enableScissor(viewport.x(), viewport.y(), viewport.right(), viewport.bottom());
        int pad = 5;
        int x = viewport.x() + pad;
        int maxW = viewport.width() - pad * 2;
        int startY = viewport.y() + 2;
        int y = startY - scroll.offset();
        boolean navTarget = KistorActions.isNavigationTarget(ctx, container.id());

        // 1. Title, or the inline label editor.
        if (s.editField == KistorUiState.EditField.LABEL) {
            UiRect field = new UiRect(x, y, maxW, 12);
            KistorUi.drawTextField(g, font, field, s.editText, "Namnge förvaringen...", true, ctx.nowMs());
            ctx.addEditHit(field, () -> {});
            y += 14;
        } else {
            String title = (container.favorite() ? "★ " : "") + container.displayTitle().toUpperCase(java.util.Locale.ROOT);
            TextUtil.drawScaledEllipsizedText(g, font, title, x, y, maxW, TypographyScale.HEADING.getScale(), GZTheme.COLOR_MINT, true);
            y += 11;
        }

        // 2. Type · shape, dimension, coordinates.
        String typeLine = container.storageTypeText() + (container.shape() == StorageShape.UNKNOWN && container.kind().isChestFamily() ? " (kan vara dubbel)" : "")
                + " · " + DimensionNames.shortName(container.dimensionKey());
        TextUtil.drawScaledEllipsizedText(g, font, typeLine, x, y, maxW, TypographyScale.SMALL.getScale(),
                container.shape() == StorageShape.UNKNOWN ? GZTheme.COLOR_STATUS_YELLOW : GZTheme.COLOR_TEXT_SECONDARY, false);
        y += 10;
        TextUtil.drawScaledEllipsizedText(g, font, container.anchor().toDisplayString(), x, y, maxW,
                TypographyScale.SMALL.getScale(), GZTheme.COLOR_TEXT_SECONDARY, false);
        y += 10;

        // 3. Local metadata.
        TextUtil.drawScaledEllipsizedText(g, font, "Grupp: " + (container.group() != null ? container.group() : "ingen"), x, y, maxW,
                TypographyScale.META.getScale(), container.group() != null ? GZTheme.COLOR_TEXT_PRIMARY : GZTheme.COLOR_TEXT_MUTED, false);
        y += 9;
        if (container.locationNote() != null && s.editField != KistorUiState.EditField.NOTE) {
            y += TextUtil.drawScaledWrappedText(g, font, "Platsnotering: " + container.locationNote(), x, y, maxW,
                    TypographyScale.META.getScale(), 3, 1, GZTheme.COLOR_TEXT_PRIMARY, false);
            y += 1;
        }

        // 4. Freshness.
        ChestFreshness freshness = ChestFreshness.classify(container.lastOpenedAtMs(), ctx.nowMs());
        TextUtil.drawScaledEllipsizedText(g, font, freshness.badge() + " · Öppnad " + ChestFreshness.relativeTime(container.lastOpenedAtMs(), ctx.nowMs()),
                x, y, maxW, TypographyScale.META.getScale(), KistorUi.freshnessColor(freshness), false);
        y += 9;
        if (navTarget) {
            TextUtil.drawScaledEllipsizedText(g, font, "● NAVIGERAR HIT", x, y, maxW, TypographyScale.META.getScale(), GZTheme.COLOR_MINT, false);
            y += 9;
        }
        y += 3;

        // 5. Inline group / note editors.
        if (s.editField == KistorUiState.EditField.GROUP) {
            y = renderGroupEditor(ctx, container, x, y, maxW);
        } else if (s.editField == KistorUiState.EditField.NOTE) {
            y = renderNoteEditor(ctx, x, y, maxW);
        }

        // 6. Last-known contents.
        KistorUi.drawSectionLabel(g, font, "SENAST KÄNT INNEHÅLL", x, y);
        y += 10;
        List<StoredContainer.AggregatedItem> items = topItems(container);
        if (items.isEmpty()) {
            TextUtil.drawScaledText(g, font, "(Tom förvaring senast öppnad)", x, y, TypographyScale.SMALL.getScale(), GZTheme.COLOR_TEXT_MUTED, false);
            y += 10;
        } else {
            for (StoredContainer.AggregatedItem item : items) {
                if (y + ITEM_LINE_H >= viewport.y() && y <= viewport.bottom()) {
                    drawItemLine(ctx, item.itemId(), KistorUi.formatCount(item.count()), GZTheme.COLOR_TEXT_SECONDARY, x, y, maxW);
                }
                y += ITEM_LINE_H;
            }
        }
        y += 4;

        // 7. Sedan förra öppningen.
        if (container.hasPreviousSnapshot()) {
            KistorUi.drawSectionLabel(g, font, "SEDAN FÖRRA ÖPPNINGEN", x, y);
            y += 10;
            List<ChestSnapshotDiff.ItemDelta> deltas = ChestSnapshotDiff.sincePrevious(container);
            if (deltas.isEmpty()) {
                TextUtil.drawScaledEllipsizedText(g, font, "Inga ändringar sedan förra öppningen.", x, y, maxW,
                        TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_MUTED, false);
                y += 10;
            } else {
                int shown = Math.min(MAX_DIFF_LINES, deltas.size());
                for (int i = 0; i < shown; i++) {
                    ChestSnapshotDiff.ItemDelta d = deltas.get(i);
                    String amount = (d.isGain() ? "+" : "-") + KistorUi.formatCount(Math.abs((long) d.delta()));
                    if (y + ITEM_LINE_H >= viewport.y() && y <= viewport.bottom()) {
                        drawItemLine(ctx, d.itemId(), amount, d.isGain() ? GZTheme.COLOR_STATUS_GREEN : GZTheme.COLOR_STATUS_RED, x, y, maxW);
                    }
                    y += ITEM_LINE_H;
                }
                if (deltas.size() > shown) {
                    TextUtil.drawScaledText(g, font, "+" + (deltas.size() - shown) + " fler ändringar", x, y,
                            TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_MUTED, false);
                    y += 9;
                }
            }
            y += 4;
        }

        // 8. Last-known warning.
        y += TextUtil.drawScaledWrappedText(g, font, KistorUi.LAST_KNOWN_WARNING, x, y, maxW,
                TypographyScale.META.getScale(), 2, 1, GZTheme.COLOR_STATUS_YELLOW, false);
        y += 4;

        g.disableScissor();
        scroll.recordContent((y + scroll.offset()) - startY, viewport.height());

        renderActions(ctx, container, navTarget);
    }

    private void drawItemLine(KistorRenderContext ctx, String itemId, String amount, int amountColor, int x, int y, int maxW) {
        GuiGraphicsExtractor g = ctx.extractor();
        Font font = ctx.font();
        KistorUi.drawItemIcon(ctx, itemId, x, y, MINI_ICON);
        int amountW = TextUtil.scaledWidth(font, amount, TypographyScale.SMALL.getScale());
        TextUtil.drawScaledEllipsizedText(g, font, ctx.manager().itemDisplayName(itemId), x + MINI_ICON + 3, y + 1,
                maxW - MINI_ICON - 3 - amountW - 6, TypographyScale.SMALL.getScale(), GZTheme.COLOR_TEXT_PRIMARY, false);
        TextUtil.drawScaledRightAlignedText(g, font, amount, x + maxW, y + 1, amountW + 2, TypographyScale.SMALL.getScale(), amountColor, false);
    }

    private int renderGroupEditor(KistorRenderContext ctx, StoredContainer container, int x, int y, int maxW) {
        GuiGraphicsExtractor g = ctx.extractor();
        Font font = ctx.font();
        KistorUiState s = ctx.state();
        KistorUi.drawSectionLabel(g, font, "GRUPP · skriv ny eller välj befintlig", x, y);
        y += 10;
        UiRect field = new UiRect(x, y, maxW, 12);
        KistorUi.drawTextField(g, font, field, s.editText, "Ny grupp...", true, ctx.nowMs());
        ctx.addEditHit(field, () -> {});
        y += 14;

        List<String> chips = new ArrayList<>(ctx.manager().getGroups(ctx.contextKey()));
        int cursor = x;
        float scale = TypographyScale.META.getScale();
        StoredContainerId id = container.id();
        for (int i = 0; i <= chips.size(); i++) {
            boolean none = i == chips.size();
            String label = none ? "Ingen grupp" : chips.get(i);
            int w = Math.min(maxW, TextUtil.scaledWidth(font, label, scale) + 8);
            if (cursor + w > x + maxW) {
                cursor = x;
                y += 13;
            }
            UiRect chip = new UiRect(cursor, y, w, 11);
            boolean current = none ? container.group() == null : label.equalsIgnoreCase(Objects.requireNonNullElse(container.group(), ""));
            GZTheme.drawCard(g, chip, current ? GZTheme.COLOR_NAV_ACTIVE : (ctx.hovered(chip) ? GZTheme.COLOR_CARD_HOVER : GZTheme.COLOR_CARD_INNER),
                    current ? GZTheme.COLOR_BORDER_EMERALD : GZTheme.COLOR_BORDER_SUBTLE);
            TextUtil.drawScaledEllipsizedText(g, font, label, chip.x() + 4, chip.y() + 2, chip.width() - 6, scale,
                    none ? GZTheme.COLOR_TEXT_MUTED : GZTheme.COLOR_TEXT_PRIMARY, false);
            String value = none ? null : label;
            ctx.addEditHit(chip, () -> {
                ctx.manager().setGroup(ctx.contextKey(), id, value);
                s.cancelEdit();
            });
            cursor += w + 3;
        }
        y += 14;
        TextUtil.drawScaledText(g, font, "Enter sparar · Esc avbryter", x, y, scale, GZTheme.COLOR_TEXT_MUTED, false);
        return y + 12;
    }

    private int renderNoteEditor(KistorRenderContext ctx, int x, int y, int maxW) {
        GuiGraphicsExtractor g = ctx.extractor();
        Font font = ctx.font();
        KistorUiState s = ctx.state();
        KistorUi.drawSectionLabel(g, font, "PLATSNOTERING", x, y);
        y += 10;
        UiRect field = new UiRect(x, y, maxW, 12);
        KistorUi.drawTextField(g, font, field, s.editText, "T.ex. Källaren bakom smedjan", true, ctx.nowMs());
        ctx.addEditHit(field, () -> {});
        y += 14;
        TextUtil.drawScaledText(g, font, "Enter sparar · tomt tar bort · Esc avbryter", x, y, TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_MUTED, false);
        return y + 12;
    }

    // ------------------------------------------------------------------
    // Pinned actions
    // ------------------------------------------------------------------

    private void renderActions(KistorRenderContext ctx, StoredContainer container, boolean navTarget) {
        GuiGraphicsExtractor g = ctx.extractor();
        Font font = ctx.font();
        KistorLayout layout = ctx.layout();
        KistorUiState s = ctx.state();
        ChestManager manager = ctx.manager();
        String contextKey = ctx.contextKey();
        StoredContainerId id = container.id();
        long now = ctx.nowMs();

        UiRect primary = layout.primaryActionRect();
        KistorUi.drawButton(g, font, primary, navTarget ? "Stoppa" : "Hitta", !navTarget, ctx.hovered(primary), true);
        ctx.addHit(primary, navTarget ? () -> KistorActions.stopNavigation(ctx) : () -> KistorActions.startNavigation(ctx, id));

        UiRect rename = layout.renameBtnRect();
        boolean editingLabel = s.editField == KistorUiState.EditField.LABEL;
        KistorUi.drawButton(g, font, rename, editingLabel ? "Spara" : (container.hasLabel() ? "Byt namn" : "Namnge"), editingLabel, ctx.hovered(rename), true);
        ctx.addEditHit(rename, () -> {
            if (editingLabel) {
                commitEdit(ctx, id);
            } else {
                s.beginEdit(KistorUiState.EditField.LABEL, container.label());
                s.storageDetailScroll.reset();
            }
        });

        UiRect fav = layout.favoriteBtnRect();
        KistorUi.drawButton(g, font, fav, container.favorite() ? "Ta bort favorit" : "★ Fäst", false, ctx.hovered(fav), true);
        ctx.addHit(fav, () -> manager.setFavorite(contextKey, id, !container.favorite()));

        UiRect copy = layout.copyBtnRect();
        boolean copied = now < s.copyFeedbackExpiry;
        KistorUi.drawButton(g, font, copy, copied ? "Kopierat!" : "Koord.", false, ctx.hovered(copy), true);
        ctx.addHit(copy, () -> copyCoordinates(s, container, now));

        UiRect note = layout.noteBtnRect();
        boolean editingNote = s.editField == KistorUiState.EditField.NOTE;
        KistorUi.drawButton(g, font, note, editingNote ? "Spara" : "Notering", editingNote, ctx.hovered(note), true);
        ctx.addEditHit(note, () -> {
            if (editingNote) {
                commitEdit(ctx, id);
            } else {
                s.beginEdit(KistorUiState.EditField.NOTE, container.locationNote());
            }
        });

        UiRect group = layout.groupAssignBtnRect();
        boolean editingGroup = s.editField == KistorUiState.EditField.GROUP;
        KistorUi.drawButton(g, font, group, editingGroup ? "Spara" : "Grupp", editingGroup, ctx.hovered(group), true);
        ctx.addEditHit(group, () -> {
            if (editingGroup) {
                commitEdit(ctx, id);
            } else {
                s.beginEdit(KistorUiState.EditField.GROUP, container.group());
            }
        });

        UiRect forget = layout.forgetBtnRect();
        boolean armed = s.isForgetConfirmActive(now);
        KistorUi.drawQuietButton(g, font, forget, armed ? "Bekräfta!" : "Glöm", ctx.hovered(forget), armed);
        ctx.addHit(forget, () -> {
            if (s.isForgetConfirmActive(System.currentTimeMillis())) {
                if (ctx.runtime().navigation().isTarget(id)) {
                    ctx.runtime().navigation().stop(ChestNavigationManager.StopReason.TARGET_MISSING);
                }
                manager.forgetContainer(contextKey, id);
                s.confirmingForget = false;
                s.forgetConfirmExpiry = 0;
                s.selectedStorage = null;
                s.compactShowingDetail = false;
            } else {
                s.confirmingForget = true;
                s.forgetConfirmExpiry = System.currentTimeMillis() + 4000;
            }
        });
    }

    /** Commits the open inline editor (label / group / note) for {@code id}. */
    static void commitEdit(KistorRenderContext ctx, StoredContainerId id) {
        KistorUiState s = ctx.state();
        commitEdit(s, ctx.manager(), ctx.contextKey(), id);
    }

    public static void commitEdit(KistorUiState s, ChestManager manager, String contextKey, StoredContainerId id) {
        if (s.editField == null || manager == null || id == null) {
            s.cancelEdit();
            return;
        }
        switch (s.editField) {
            case LABEL -> manager.setLabel(contextKey, id, s.editText);
            case GROUP -> manager.setGroup(contextKey, id, s.editText);
            case NOTE -> manager.setLocationNote(contextKey, id, s.editText);
        }
        s.cancelEdit();
    }

    private static void copyCoordinates(KistorUiState s, StoredContainer container, long now) {
        try {
            Minecraft client = Minecraft.getInstance();
            if (client != null && client.keyboardHandler != null) {
                client.keyboardHandler.setClipboard(container.anchor().toCoordinateText());
                s.copyFeedbackExpiry = now + 2000;
            }
        } catch (Exception ignored) {
            // Clipboard access is a pure local OS convenience.
        }
    }
}
