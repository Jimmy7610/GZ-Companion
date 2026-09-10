package se.jimmyeliasson.gzcompanion.ui.layout;

/**
 * Responsive layout for the Byggplaner tab. Mirrors {@link CraftingLayout}'s search + 2-pane
 * (NORMAL/LARGE) vs 1-pane (COMPACT) list/detail structure exactly - there is no mode button
 * here, since Byggplaner has a single browse view (the detail pane itself hosts the Structure
 * Calculator and the local plan list).
 */
public record BuildingLayout(
    UiRect bounds,
    boolean isCompact,
    UiRect headerRect,
    UiRect searchRect,
    UiRect clearBtnRect,
    UiRect listRect,
    UiRect detailRect,
    UiRect backBtnRect
) {
    public static BuildingLayout calculate(UiRect bounds) {
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

        int contentTop = search.bottom() + 3;
        int contentBottom = y + height;
        int contentH = Math.max(10, contentBottom - contentTop);

        if (compact) {
            UiRect list = new UiRect(x, contentTop, width, contentH);
            UiRect detail = new UiRect(x, contentTop, width, contentH);
            UiRect back = new UiRect(x + 4, contentTop + 4, 60, 11);
            return new BuildingLayout(bounds, true, header, search, clearBtn, list, detail, back);
        }

        int gap = 4;
        int listW = (int) ((width - gap) * 0.38f);
        int detailW = Math.max(10, width - listW - gap);
        int detailX = x + listW + gap;

        UiRect list = new UiRect(x, contentTop, listW, contentH);
        UiRect detail = new UiRect(detailX, contentTop, detailW, contentH);
        UiRect back = new UiRect(detailX + 4, contentTop + 4, 50, 11);

        return new BuildingLayout(bounds, false, header, search, clearBtn, list, detail, back);
    }
}
