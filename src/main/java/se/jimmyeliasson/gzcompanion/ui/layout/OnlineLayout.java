package se.jimmyeliasson.gzcompanion.ui.layout;

/**
 * Responsive layout calculation for the Online tab. Mirrors {@link CraftingLayout}'s search-box +
 * scrollable list + detail-panel structure (compact: list/detail share the same rect, switched by
 * a "showingDetail" boolean on the tab component with a "&lt; Lista" back button; wide: a
 * side-by-side split) - there is no mode-cycle button here, and an extra status row sits between
 * the header and the search box for the GameZoneMC connection status.
 */
public record OnlineLayout(
    UiRect bounds,
    boolean isCompact,
    UiRect headerRect,
    UiRect statusRect,
    UiRect searchRect,
    UiRect clearBtnRect,
    UiRect listRect,
    UiRect detailRect,
    UiRect backBtnRect
) {
    public static OnlineLayout calculate(UiRect bounds) {
        int x = bounds.x();
        int y = bounds.y();
        int width = bounds.width();
        int height = bounds.height();

        boolean compact = width < 300;

        int headerH = 13;
        UiRect header = new UiRect(x, y, width, headerH);

        int statusH = 11;
        UiRect status = new UiRect(x, header.bottom() + 1, width, statusH);

        int searchH = 13;
        int clearW = 11;
        int searchGap = 2;
        UiRect search = new UiRect(x, status.bottom() + 2, Math.max(10, width - clearW - searchGap), searchH);
        UiRect clearBtn = new UiRect(search.right() + searchGap, search.y(), clearW, searchH);

        int contentTop = search.bottom() + 3;
        int contentBottom = y + height;
        int contentH = Math.max(10, contentBottom - contentTop);

        if (compact) {
            UiRect list = new UiRect(x, contentTop, width, contentH);
            UiRect detail = new UiRect(x, contentTop, width, contentH);
            UiRect back = new UiRect(x + 4, contentTop + 4, 60, 11);
            return new OnlineLayout(bounds, true, header, status, search, clearBtn, list, detail, back);
        }

        int gap = 4;
        int listW = (int) ((width - gap) * 0.60f);
        int detailW = Math.max(10, width - listW - gap);
        int detailX = x + listW + gap;

        UiRect list = new UiRect(x, contentTop, listW, contentH);
        UiRect detail = new UiRect(detailX, contentTop, detailW, contentH);
        UiRect back = new UiRect(detailX + 4, contentTop + 4, 50, 11);

        return new OnlineLayout(bounds, false, header, status, search, clearBtn, list, detail, back);
    }
}
