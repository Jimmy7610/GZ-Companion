package se.jimmyeliasson.gzcompanion.ui.tabs.kistor;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import se.jimmyeliasson.gzcompanion.chest.index.ChestItemIndex;
import se.jimmyeliasson.gzcompanion.chest.index.ItemLocation;
import se.jimmyeliasson.gzcompanion.chest.material.ChestMaterialAvailability;
import se.jimmyeliasson.gzcompanion.chest.material.ChestMaterialRequest;
import se.jimmyeliasson.gzcompanion.chest.material.ChestPickupPlanner;
import se.jimmyeliasson.gzcompanion.chest.material.MaterialAvailability;
import se.jimmyeliasson.gzcompanion.chest.material.MaterialNeed;
import se.jimmyeliasson.gzcompanion.chest.material.PickupPlan;
import se.jimmyeliasson.gzcompanion.chest.model.ChestFreshness;
import se.jimmyeliasson.gzcompanion.chest.model.ChestGroupFilter;
import se.jimmyeliasson.gzcompanion.chest.model.ChestTypeFilter;
import se.jimmyeliasson.gzcompanion.chest.model.StoredContainerId;
import se.jimmyeliasson.gzcompanion.ui.GZTheme;
import se.jimmyeliasson.gzcompanion.ui.TypographyScale;
import se.jimmyeliasson.gzcompanion.ui.layout.TextUtil;
import se.jimmyeliasson.gzcompanion.ui.layout.UiRect;

import java.util.List;
import java.util.Objects;

/**
 * "Hitta material i kistor" + "Hämtningslista" for a material request handed over from Settlement
 * or Byggplaner. Uses ONLY existing last-known local chest snapshots, and only while the
 * "use last-known chest data in planners" setting is on. Planning assistance only: nothing here
 * withdraws items, clicks slots, moves the player or runs commands.
 */
public final class KistorMaterialView {
    static final int MATERIAL_ROW_H = 31;
    static final int LOCATION_LINE_H = 11;
    static final int MAX_LOCATIONS_PER_MATERIAL = 3;
    static final int SMALL_BTN_W = 30;

    private record PlanKey(ChestMaterialRequest request, ChestItemIndex index) {}

    private PlanKey lastKey;
    private List<MaterialAvailability> lastAvailability = List.of();
    private PickupPlan lastPlan;

    private void refresh(ChestMaterialRequest request, ChestItemIndex index) {
        PlanKey key = new PlanKey(request, index);
        if (Objects.equals(key, lastKey) && lastPlan != null) return;
        lastAvailability = ChestMaterialAvailability.compute(request, index);
        lastPlan = ChestPickupPlanner.plan(request, index);
        lastKey = key;
    }

    public void render(KistorRenderContext ctx, ChestMaterialRequest request) {
        GuiGraphicsExtractor g = ctx.extractor();
        Font font = ctx.font();
        KistorUiState s = ctx.state();
        UiRect area = ctx.layout().listRect();
        UiRect viewport = ctx.layout().listContentRect();
        GZTheme.drawCard(g, area, GZTheme.COLOR_CARD_BG, GZTheme.COLOR_BORDER_SUBTLE);

        ScrollState scroll = s.materialScroll;
        scroll.clamp();
        g.enableScissor(viewport.x(), viewport.y(), viewport.right(), viewport.bottom());
        int pad = 5;
        int x = viewport.x() + pad;
        int maxW = viewport.width() - pad * 2;
        int startY = viewport.y() + 3;
        int y = startY - scroll.offset();

        KistorUi.drawSectionLabel(g, font, s.materialShowPickupList ? "HÄMTNINGSLISTA" : "HITTA MATERIAL I KISTOR", x, y);
        y += 10;
        TextUtil.drawScaledEllipsizedText(g, font, request.title(), x, y, maxW, TypographyScale.HEADING.getScale(), GZTheme.COLOR_MINT, true);
        y += 11;
        TextUtil.drawScaledEllipsizedText(g, font, "Från " + request.sourceLabel() + " · lokalt estimat från senast kända kistor", x, y, maxW,
                TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_MUTED, false);
        y += 11;

        if (!ctx.chestDataInPlannersEnabled()) {
            y += TextUtil.drawScaledWrappedText(g, font,
                    "Kistdata i planerare är avstängd. Slå på \"Använd senast kända kistodata i planerare\" under Inställningar för att se var materialet senast fanns.",
                    x, y, maxW, TypographyScale.SMALL.getScale(), 4, 1, GZTheme.COLOR_STATUS_YELLOW, false) + 4;
        } else {
            // Planner lookups always use the full context (no Kistor type/group filter).
            ChestItemIndex index = ctx.runtime().itemIndexCache().get(ctx.manager(), ctx.contextKey(), ChestTypeFilter.ALL, ChestGroupFilter.ALL);
            refresh(request, index);
            if (s.materialShowPickupList) {
                y = renderPickupList(ctx, viewport, x, y, maxW);
            } else {
                y = renderAvailability(ctx, viewport, x, y, maxW);
            }
            y += 4;
            y += TextUtil.drawScaledWrappedText(g, font,
                    "Senast känt innehåll - inte GameZones live-inventarie. " + KistorUi.LAST_KNOWN_WARNING,
                    x, y, maxW, TypographyScale.META.getScale(), 3, 1, GZTheme.COLOR_STATUS_YELLOW, false);
            y += 4;
        }

        g.disableScissor();
        scroll.recordContent((y + scroll.offset()) - startY, viewport.height());
    }

    private int renderAvailability(KistorRenderContext ctx, UiRect viewport, int x, int y, int maxW) {
        GuiGraphicsExtractor g = ctx.extractor();
        Font font = ctx.font();
        int trackable = (int) lastAvailability.stream().filter(MaterialAvailability::isTrackable).count();
        int covered = ChestMaterialAvailability.coveredCount(lastAvailability);
        TextUtil.drawScaledEllipsizedText(g, font, covered + " av " + trackable + " material täcks enligt estimat", x, y, maxW,
                TypographyScale.SMALL.getScale(), covered == trackable ? GZTheme.COLOR_STATUS_GREEN : GZTheme.COLOR_TEXT_PRIMARY, false);
        y += 12;

        for (MaterialAvailability row : lastAvailability) {
            y = renderMaterialRow(ctx, viewport, row, x, y, maxW);
        }
        return y;
    }

    private int renderMaterialRow(KistorRenderContext ctx, UiRect viewport, MaterialAvailability row, int x, int y, int maxW) {
        GuiGraphicsExtractor g = ctx.extractor();
        Font font = ctx.font();
        MaterialNeed need = row.need();
        UiRect card = new UiRect(x - 2, y, maxW + 4, MATERIAL_ROW_H - 2);
        GZTheme.drawCard(g, card, GZTheme.COLOR_CARD_INNER, row.isCoveredByEstimate() ? 0x5522C55E : GZTheme.COLOR_BORDER_SUBTLE);

        if (need.isTrackable()) KistorUi.drawItemIcon(ctx, need.itemId(), x, y + 3, 16);
        int tx = x + 20;
        int tw = maxW - 20;
        TextUtil.drawScaledEllipsizedText(g, font, need.displayName(), tx, y + 2, tw, TypographyScale.SMALL.getScale(), GZTheme.COLOR_TEXT_PRIMARY, false);
        if (!need.isTrackable()) {
            TextUtil.drawScaledEllipsizedText(g, font, "Behövs " + KistorUi.formatCount(need.needed()) + " · kategori, kan inte matchas mot kistdata",
                    tx, y + 11, tw, TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_MUTED, false);
            return y + MATERIAL_ROW_H;
        }
        TextUtil.drawScaledEllipsizedText(g, font, "Behövs " + KistorUi.formatCount(need.needed()) + " · Senast känt i kistor " + KistorUi.formatCount(row.lastKnownTotal()),
                tx, y + 11, tw, TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_SECONDARY, false);
        String status = row.estimatedMissing() > 0
                ? "SAKNAS ENLIGT ESTIMAT " + KistorUi.formatCount(row.estimatedMissing())
                : "Täcks enligt estimat";
        TextUtil.drawScaledEllipsizedText(g, font, status, tx, y + 20, tw, TypographyScale.META.getScale(),
                row.estimatedMissing() > 0 ? GZTheme.COLOR_STATUS_YELLOW : GZTheme.COLOR_STATUS_GREEN, false);
        y += MATERIAL_ROW_H;

        List<ItemLocation> locations = row.locations();
        int shown = Math.min(MAX_LOCATIONS_PER_MATERIAL, locations.size());
        for (int i = 0; i < shown; i++) {
            ItemLocation location = locations.get(i);
            y = renderLocationLine(ctx, viewport, location.id(), location.title(), KistorUi.formatCount(location.count()),
                    KistorUi.distanceOrDimension(ctx.playerPose().orElse(null), location.dimensionKey(), location.anchor()), x + 8, y, maxW - 8);
        }
        if (locations.size() > shown) {
            TextUtil.drawScaledText(g, font, "+" + (locations.size() - shown) + " fler förvaringar", x + 8, y + 1,
                    TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_MUTED, false);
            y += LOCATION_LINE_H;
        }
        return y + 3;
    }

    /** One "storage · amount · [Hitta]" line; the text opens the storage, the button starts navigation. */
    private int renderLocationLine(KistorRenderContext ctx, UiRect viewport, StoredContainerId id, String title, String amount,
                                   String meta, int x, int y, int maxW) {
        GuiGraphicsExtractor g = ctx.extractor();
        Font font = ctx.font();
        UiRect btn = new UiRect(x + maxW - SMALL_BTN_W, y, SMALL_BTN_W, LOCATION_LINE_H - 1);
        UiRect textRect = new UiRect(x, y, maxW - SMALL_BTN_W - 3, LOCATION_LINE_H - 1);
        boolean navTarget = KistorActions.isNavigationTarget(ctx, id);
        String text = title + "  " + amount + "  · " + meta;
        TextUtil.drawScaledEllipsizedText(g, font, text, x, y + 2, textRect.width(), TypographyScale.META.getScale(),
                ctx.hovered(textRect) ? GZTheme.COLOR_TEXT_PRIMARY : (navTarget ? GZTheme.COLOR_MINT : GZTheme.COLOR_TEXT_SECONDARY), false);
        KistorUi.drawButton(g, font, btn, navTarget ? "Stopp" : "Hitta", !navTarget, ctx.hovered(btn), true);
        ctx.addVisibleHit(btn, viewport, navTarget ? () -> KistorActions.stopNavigation(ctx) : () -> KistorActions.startNavigation(ctx, id));
        ctx.addVisibleHit(textRect, viewport, () -> ctx.state().openStorage(id));
        return y + LOCATION_LINE_H;
    }

    private int renderPickupList(KistorRenderContext ctx, UiRect viewport, int x, int y, int maxW) {
        GuiGraphicsExtractor g = ctx.extractor();
        Font font = ctx.font();
        PickupPlan plan = lastPlan;
        y += TextUtil.drawScaledWrappedText(g, font,
                "Planeringshjälp: störst senast kända lager först (färre stopp), sedan senast öppnad. Inget hämtas automatiskt.",
                x, y, maxW, TypographyScale.META.getScale(), 3, 1, GZTheme.COLOR_TEXT_MUTED, false) + 4;

        if (plan.groups().isEmpty()) {
            TextUtil.drawScaledEllipsizedText(g, font, "Inget av materialet finns i dina sparade förvaringar.", x, y, maxW,
                    TypographyScale.SMALL.getScale(), GZTheme.COLOR_TEXT_MUTED, false);
            y += 12;
        }

        for (PickupPlan.Group group : plan.groups()) {
            UiRect header = new UiRect(x - 2, y, maxW + 4, 13);
            GZTheme.drawCard(g, header, GZTheme.COLOR_CARD_INNER, KistorActions.isNavigationTarget(ctx, group.storageId())
                    ? GZTheme.COLOR_BORDER_EMERALD : GZTheme.COLOR_BORDER_SUBTLE);
            UiRect btn = new UiRect(header.right() - SMALL_BTN_W - 2, y + 1, SMALL_BTN_W, 11);
            ChestFreshness freshness = ChestFreshness.classify(group.lastOpenedAtMs(), ctx.nowMs());
            String title = group.title().toUpperCase(java.util.Locale.ROOT) + "  · " + ChestFreshness.relativeTime(group.lastOpenedAtMs(), ctx.nowMs());
            UiRect titleRect = new UiRect(x, y, btn.x() - x - 3, 13);
            TextUtil.drawScaledEllipsizedText(g, font, title, x + 1, y + 3, titleRect.width(), TypographyScale.SMALL.getScale(),
                    ctx.hovered(titleRect) ? GZTheme.COLOR_TEXT_PRIMARY : KistorUi.freshnessColor(freshness) == GZTheme.COLOR_TEXT_MUTED
                            ? GZTheme.COLOR_TEXT_SECONDARY : GZTheme.COLOR_MINT, false);
            boolean navTarget = KistorActions.isNavigationTarget(ctx, group.storageId());
            KistorUi.drawButton(g, font, btn, navTarget ? "Stopp" : "Hitta", !navTarget, ctx.hovered(btn), true);
            StoredContainerId id = group.storageId();
            ctx.addVisibleHit(btn, viewport, navTarget ? () -> KistorActions.stopNavigation(ctx) : () -> KistorActions.startNavigation(ctx, id));
            ctx.addVisibleHit(titleRect, viewport, () -> ctx.state().openStorage(id));
            y += 15;

            for (PickupPlan.Line line : group.lines()) {
                String key = line.checklistKey(id);
                boolean checked = ctx.runtime().isPickupLineChecked(key);
                UiRect lineRect = new UiRect(x + 4, y, maxW - 4, 11);
                if (lineRect.bottom() >= viewport.y() && lineRect.y() <= viewport.bottom()) {
                    TextUtil.drawScaledText(g, font, checked ? "[x]" : "[ ]", x + 4, y + 2, TypographyScale.META.getScale(),
                            checked ? GZTheme.COLOR_STATUS_GREEN : GZTheme.COLOR_TEXT_SECONDARY, false);
                    KistorUi.drawItemIcon(ctx, line.itemId(), x + 20, y, 10);
                    TextUtil.drawScaledEllipsizedText(g, font, KistorUi.formatCount(line.amount()) + "  " + line.displayName(), x + 33, y + 2, maxW - 33,
                            TypographyScale.SMALL.getScale(), checked ? GZTheme.COLOR_TEXT_MUTED : GZTheme.COLOR_TEXT_PRIMARY, false);
                    ctx.addVisibleHit(lineRect, viewport, () -> ctx.runtime().togglePickupLine(key));
                }
                y += 12;
            }
            y += 4;
        }

        if (!plan.shortages().isEmpty()) {
            KistorUi.drawSectionLabel(g, font, "SAKNAS ENLIGT ESTIMAT", x, y);
            y += 10;
            for (PickupPlan.Shortage shortage : plan.shortages()) {
                KistorUi.drawItemIcon(ctx, shortage.itemId(), x + 4, y, 10);
                TextUtil.drawScaledEllipsizedText(g, font, KistorUi.formatCount(shortage.missing()) + "  " + shortage.displayName(), x + 17, y + 2, maxW - 17,
                        TypographyScale.SMALL.getScale(), GZTheme.COLOR_STATUS_YELLOW, false);
                y += 12;
            }
            y += 3;
        }
        if (!plan.untrackable().isEmpty()) {
            KistorUi.drawSectionLabel(g, font, "KAN INTE MATCHAS MOT KISTDATA", x, y);
            y += 10;
            for (MaterialNeed need : plan.untrackable()) {
                TextUtil.drawScaledEllipsizedText(g, font, KistorUi.formatCount(need.needed()) + "  " + need.displayName(), x + 4, y + 1, maxW - 4,
                        TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_MUTED, false);
                y += 10;
            }
        }
        return y;
    }
}
