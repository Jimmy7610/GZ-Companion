package se.jimmyeliasson.gzcompanion.ui.layout;

/**
 * Responsive layout calculation for the Kistor 2.0 tab.
 *
 * <p>Rows, top to bottom: header (title + SAKER/FÖRVARING[/MATERIAL] mode control), an optional
 * "NAVIGERAR" banner with its own STOPPA button, the global search field, a row of three compact
 * controls, then the content panes. Normal/large widths use a list pane beside a detail pane;
 * compact widths show ONE pane at a time (list and detail are the same rectangle) with a back
 * button. Pinned action buttons sit in their own reserved zone below the detail pane - outside
 * every scrollable area - so they can never overlap scrolled content. The zone's size depends on
 * the mode (FÖRVARING: two rows, SAKER: one row, MATERIAL: none), and a compact list that is not
 * showing a detail pane reserves no action zone at all.
 */
public record KistorLayout(
    UiRect bounds,
    boolean isCompact,
    Mode mode,
    UiRect headerRect,
    UiRect modeSakerRect,
    UiRect modeForvaringRect,
    UiRect modeMaterialRect,
    UiRect navBannerRect,
    UiRect navStopBtnRect,
    UiRect searchRect,
    UiRect clearBtnRect,
    UiRect filterBtnRect,
    UiRect sortBtnRect,
    UiRect groupBtnRect,
    UiRect listRect,
    UiRect detailRect,
    UiRect primaryActionRect,
    UiRect renameBtnRect,
    UiRect favoriteBtnRect,
    UiRect copyBtnRect,
    UiRect noteBtnRect,
    UiRect groupAssignBtnRect,
    UiRect forgetBtnRect,
    UiRect backBtnRect
) {
    public enum Mode { SAKER, FORVARING, MATERIAL }

    public static final UiRect NONE = new UiRect(0, 0, 0, 0);

    public static final int COMPACT_WIDTH_THRESHOLD = 300;
    static final int HEADER_H = 13;
    static final int BANNER_H = 13;
    static final int SEARCH_H = 13;
    static final int CONTROLS_H = 11;
    static final int ACTION_BTN_H = 11;
    static final int ACTION_PAD = 3;
    static final int ACTION_ROW_GAP = 2;
    static final int SEG_SAKER_W = 38;
    static final int SEG_FORVARING_W = 58;
    static final int SEG_MATERIAL_W = 50;
    static final int SEG_GAP = 2;
    static final int STOP_BTN_W = 48;
    /** Height of the compact back-button strip at the top of a compact detail pane. */
    public static final int COMPACT_BACK_STRIP_H = 16;

    /** Legacy default: FÖRVARING, no navigation banner, detail showing. */
    public static KistorLayout calculate(UiRect bounds) {
        return calculate(bounds, Mode.FORVARING, false, false, true);
    }

    /**
     * @param navigating         reserve the "NAVIGERAR" banner row.
     * @param materialAvailable  show the MATERIAL mode segment (a planner request is active).
     * @param showingDetail      compact only: whether the detail pane (and its action zone) is showing.
     */
    public static KistorLayout calculate(UiRect bounds, Mode mode, boolean navigating, boolean materialAvailable, boolean showingDetail) {
        Mode safeMode = mode != null ? mode : Mode.SAKER;
        if (safeMode == Mode.MATERIAL && !materialAvailable) safeMode = Mode.SAKER;

        int x = bounds.x();
        int y = bounds.y();
        int width = bounds.width();
        int height = bounds.height();
        boolean compact = width < COMPACT_WIDTH_THRESHOLD;

        UiRect header = new UiRect(x, y, width, HEADER_H);
        int segY = y + 1;
        int segH = HEADER_H - 2;
        int segRight = x + width;
        UiRect segMaterial = NONE;
        if (materialAvailable) {
            segMaterial = new UiRect(segRight - SEG_MATERIAL_W, segY, SEG_MATERIAL_W, segH);
            segRight = segMaterial.x() - SEG_GAP;
        }
        UiRect segForvaring = new UiRect(segRight - SEG_FORVARING_W, segY, SEG_FORVARING_W, segH);
        UiRect segSaker = new UiRect(segForvaring.x() - SEG_GAP - SEG_SAKER_W, segY, SEG_SAKER_W, segH);

        int rowY = header.bottom() + 2;
        UiRect banner = NONE;
        UiRect stopBtn = NONE;
        if (navigating) {
            stopBtn = new UiRect(x + width - STOP_BTN_W, rowY, STOP_BTN_W, BANNER_H);
            banner = new UiRect(x, rowY, Math.max(10, width - STOP_BTN_W - 2), BANNER_H);
            rowY = banner.bottom() + 2;
        }

        int clearW = 11;
        int searchGap = 2;
        UiRect search = new UiRect(x, rowY, Math.max(10, width - clearW - searchGap), SEARCH_H);
        UiRect clearBtn = new UiRect(search.right() + searchGap, search.y(), clearW, SEARCH_H);

        int controlsY = search.bottom() + 2;
        int controlsGap = 3;
        int ctrlW = Math.max(20, (width - controlsGap * 2) / 3);
        UiRect filterBtn = new UiRect(x, controlsY, ctrlW, CONTROLS_H);
        UiRect sortBtn = new UiRect(filterBtn.right() + controlsGap, controlsY, ctrlW, CONTROLS_H);
        int groupX = sortBtn.right() + controlsGap;
        UiRect groupBtn = new UiRect(groupX, controlsY, Math.max(10, x + width - groupX), CONTROLS_H);

        int contentTop = controlsY + CONTROLS_H + 3;

        int actionRows = switch (safeMode) {
            case FORVARING -> 2;
            case SAKER -> 1;
            case MATERIAL -> 0;
        };
        if (compact && !showingDetail) actionRows = 0;
        int actionZoneH = actionRows == 0 ? 0 : ACTION_PAD + actionRows * ACTION_BTN_H + (actionRows - 1) * ACTION_ROW_GAP + ACTION_PAD;
        int contentBottom = y + height - actionZoneH;
        int contentH = Math.max(10, contentBottom - contentTop);

        UiRect list;
        UiRect detail;
        int actionX;
        int actionW;
        if (compact || safeMode == Mode.MATERIAL) {
            list = new UiRect(x, contentTop, width, contentH);
            detail = list;
            actionX = x;
            actionW = width;
        } else {
            int gap = 4;
            int listW = (int) ((width - gap) * 0.40f);
            int detailW = Math.max(10, width - listW - gap);
            int detailX = x + listW + gap;
            list = new UiRect(x, contentTop, listW, contentH);
            detail = new UiRect(detailX, contentTop, detailW, contentH);
            actionX = detailX;
            actionW = detailW;
        }
        UiRect back = new UiRect(detail.x() + 3, contentTop + 3, 52, 11);

        UiRect primary = NONE, rename = NONE, favorite = NONE, copy = NONE, note = NONE, groupAssign = NONE, forget = NONE;
        if (actionRows > 0) {
            int row1Y = contentBottom + ACTION_PAD;
            int innerX = actionX + ACTION_PAD;
            int innerW = Math.max(10, actionW - ACTION_PAD * 2);
            int gap = 3;
            if (safeMode == Mode.FORVARING) {
                // 30% HITTA, 30% rename, 40% favorite (its "Ta bort favorit" label is the longest).
                int primaryW = Math.max(10, (innerW - gap * 2) * 3 / 10);
                primary = new UiRect(innerX, row1Y, primaryW, ACTION_BTN_H);
                rename = new UiRect(primary.right() + gap, row1Y, primaryW, ACTION_BTN_H);
                int favX = rename.right() + gap;
                favorite = new UiRect(favX, row1Y, Math.max(10, innerX + innerW - favX), ACTION_BTN_H);

                int row2Y = row1Y + ACTION_BTN_H + ACTION_ROW_GAP;
                int quarterW = Math.max(10, (innerW - gap * 3) / 4);
                copy = new UiRect(innerX, row2Y, quarterW, ACTION_BTN_H);
                note = new UiRect(copy.right() + gap, row2Y, quarterW, ACTION_BTN_H);
                groupAssign = new UiRect(note.right() + gap, row2Y, quarterW, ACTION_BTN_H);
                int forgetX = groupAssign.right() + gap;
                forget = new UiRect(forgetX, row2Y, Math.max(10, innerX + innerW - forgetX), ACTION_BTN_H);
            } else {
                primary = new UiRect(innerX, row1Y, innerW, ACTION_BTN_H);
            }
        }

        return new KistorLayout(bounds, compact, safeMode, header, segSaker, segForvaring, segMaterial, banner, stopBtn,
                search, clearBtn, filterBtn, sortBtn, groupBtn, list, detail,
                primary, rename, favorite, copy, note, groupAssign, forget, back);
    }

    /** The scrollable content area of the detail pane (below the compact back strip, if any). */
    public UiRect detailContentRect() {
        int top = detailRect.y() + (isCompact ? COMPACT_BACK_STRIP_H : 3);
        return new UiRect(detailRect.x() + 1, top, Math.max(1, detailRect.width() - 2), Math.max(1, detailRect.bottom() - top - 1));
    }

    /** The scrollable content area of the list pane. */
    public UiRect listContentRect() {
        return new UiRect(listRect.x() + 1, listRect.y() + 1, Math.max(1, listRect.width() - 2), Math.max(1, listRect.height() - 2));
    }
}
