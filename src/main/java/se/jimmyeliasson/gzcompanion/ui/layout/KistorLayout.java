package se.jimmyeliasson.gzcompanion.ui.layout;

/**
 * Responsive layout calculation for the Kistor (Chest Manager) tab.
 * Supports 2-pane side-by-side mode (NORMAL / LARGE) and 1-pane mode (COMPACT), mirroring
 * {@link GuideLayout}. Pinned action buttons (rename/copy/forget) sit in their own reserved row
 * below the detail pane, outside the scrollable detail content area, so they remain usable
 * regardless of scroll position and never overlap the list/detail panes.
 */
public record KistorLayout(
    UiRect bounds,
    boolean isCompact,
    UiRect headerRect,
    UiRect searchRect,
    UiRect clearBtnRect,
    UiRect filterBtnRect,
    UiRect sortBtnRect,
    UiRect listRect,
    UiRect detailRect,
    UiRect renameBtnRect,
    UiRect copyBtnRect,
    UiRect forgetBtnRect,
    UiRect backBtnRect
) {
    public static KistorLayout calculate(UiRect bounds) {
        int x = bounds.x();
        int y = bounds.y();
        int width = bounds.width();
        int height = bounds.height();

        boolean compact = width < 300;

        int headerH = 13;
        UiRect header = new UiRect(x, y, width, headerH);

        int searchH = 13;
        int clearW = 11;
        int searchGap = 2;
        UiRect search = new UiRect(x, y + headerH + 2, Math.max(10, width - clearW - searchGap), searchH);
        UiRect clearBtn = new UiRect(search.right() + searchGap, search.y(), clearW, searchH);

        int controlsY = search.bottom() + 2;
        int controlsH = 11;
        int controlsGap = 3;
        int filterBtnW = Math.max(30, (width - controlsGap) / 2);
        UiRect filterBtn = new UiRect(x, controlsY, filterBtnW, controlsH);
        UiRect sortBtn = new UiRect(filterBtn.right() + controlsGap, controlsY, Math.max(10, width - filterBtnW - controlsGap), controlsH);

        int contentTop = controlsY + controlsH + 3;

        // Reserve a fixed bottom action row (rename / copy coordinates / forget) BEFORE computing
        // pane heights, so the panes never overlap it.
        int actionBtnH = 13;
        int actionPad = 4;
        int actionZoneH = actionPad + actionBtnH + actionPad;
        int contentBottom = y + height - actionZoneH;
        int contentH = Math.max(10, contentBottom - contentTop);

        if (compact) {
            UiRect list = new UiRect(x, contentTop, width, contentH);
            UiRect detail = new UiRect(x, contentTop, width, contentH);
            UiRect back = new UiRect(x + actionPad, contentTop + actionPad, 60, 11);

            UiRect[] actionBtns = actionRow(x, width, contentBottom, actionPad, actionBtnH);
            return new KistorLayout(bounds, true, header, search, clearBtn, filterBtn, sortBtn, list, detail,
                    actionBtns[0], actionBtns[1], actionBtns[2], back);
        }

        int gap = 4;
        int listW = (int) ((width - gap) * 0.38f);
        int detailW = Math.max(10, width - listW - gap);
        int detailX = x + listW + gap;

        UiRect list = new UiRect(x, contentTop, listW, contentH);
        UiRect detail = new UiRect(detailX, contentTop, detailW, contentH);
        UiRect back = new UiRect(detail.x() + actionPad, contentTop + actionPad, 50, 11);

        UiRect[] actionBtns = actionRow(detailX, detailW, contentBottom, actionPad, actionBtnH);
        return new KistorLayout(bounds, false, header, search, clearBtn, filterBtn, sortBtn, list, detail,
                actionBtns[0], actionBtns[1], actionBtns[2], back);
    }

    private static UiRect[] actionRow(int rowX, int rowWidth, int contentBottom, int pad, int btnH) {
        int btnY = contentBottom + pad;
        int gap = 3;
        int btnW = Math.max(10, (rowWidth - (pad * 2) - (gap * 2)) / 3);
        UiRect rename = new UiRect(rowX + pad, btnY, btnW, btnH);
        UiRect copy = new UiRect(rename.right() + gap, btnY, btnW, btnH);
        int forgetX = copy.right() + gap;
        UiRect forget = new UiRect(forgetX, btnY, Math.max(10, rowX + rowWidth - pad - forgetX), btnH);
        return new UiRect[]{rename, copy, forget};
    }
}
