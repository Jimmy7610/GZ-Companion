package se.jimmyeliasson.gzcompanion.ui.layout;

/**
 * Responsive layout calculation for the Kommandon tab. Mirrors {@link KistorLayout}'s 2-pane
 * (NORMAL/LARGE) vs 1-pane (COMPACT) structure and its fixed bottom action row, but with a
 * single category-cycle button (there is no separate sort concept for commands) and a single
 * "Kopiera kommando" action instead of Kistor's three.
 */
public record CommandsLayout(
    UiRect bounds,
    boolean isCompact,
    UiRect headerRect,
    UiRect searchRect,
    UiRect clearBtnRect,
    UiRect categoryBtnRect,
    UiRect listRect,
    UiRect detailRect,
    UiRect copyBtnRect,
    UiRect backBtnRect
) {
    public static CommandsLayout calculate(UiRect bounds) {
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
        UiRect categoryBtn = new UiRect(x, controlsY, width, controlsH);

        int contentTop = controlsY + controlsH + 3;

        int actionBtnH = 13;
        int actionPad = 4;
        int actionZoneH = actionPad + actionBtnH + actionPad;
        int contentBottom = y + height - actionZoneH;
        int contentH = Math.max(10, contentBottom - contentTop);

        if (compact) {
            UiRect list = new UiRect(x, contentTop, width, contentH);
            UiRect detail = new UiRect(x, contentTop, width, contentH);
            UiRect back = new UiRect(x + actionPad, contentTop + actionPad, 60, 11);
            UiRect copy = new UiRect(x + actionPad, contentBottom + actionPad, Math.max(10, width - (actionPad * 2)), actionBtnH);
            return new CommandsLayout(bounds, true, header, search, clearBtn, categoryBtn, list, detail, copy, back);
        }

        int gap = 4;
        int listW = (int) ((width - gap) * 0.42f);
        int detailW = Math.max(10, width - listW - gap);
        int detailX = x + listW + gap;

        UiRect list = new UiRect(x, contentTop, listW, contentH);
        UiRect detail = new UiRect(detailX, contentTop, detailW, contentH);
        UiRect back = new UiRect(detailX + actionPad, contentTop + actionPad, 50, 11);
        UiRect copy = new UiRect(detailX + actionPad, contentBottom + actionPad, Math.max(10, detailW - (actionPad * 2)), actionBtnH);

        return new CommandsLayout(bounds, false, header, search, clearBtn, categoryBtn, list, detail, copy, back);
    }
}
