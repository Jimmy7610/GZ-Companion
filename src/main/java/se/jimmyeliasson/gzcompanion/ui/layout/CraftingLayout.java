package se.jimmyeliasson.gzcompanion.ui.layout;

/**
 * Responsive layout calculation for the Crafting tab. Mirrors {@link KistorLayout}'s 2-pane
 * (NORMAL/LARGE) vs 1-pane (COMPACT) structure, with a mode-cycle button (Alla / Recept /
 * GameZone-föremål) instead of Kistor's filter+sort pair. There is no bottom action row - unlike
 * Kistor/Kommandon this tab has no per-entry action (no rename, no copy).
 */
public record CraftingLayout(
    UiRect bounds,
    boolean isCompact,
    UiRect headerRect,
    UiRect searchRect,
    UiRect clearBtnRect,
    UiRect modeBtnRect,
    UiRect listRect,
    UiRect detailRect,
    UiRect backBtnRect
) {
    public static CraftingLayout calculate(UiRect bounds) {
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
        UiRect modeBtn = new UiRect(x, controlsY, width, controlsH);

        int contentTop = controlsY + controlsH + 3;
        int contentBottom = y + height;
        int contentH = Math.max(10, contentBottom - contentTop);

        if (compact) {
            UiRect list = new UiRect(x, contentTop, width, contentH);
            UiRect detail = new UiRect(x, contentTop, width, contentH);
            UiRect back = new UiRect(x + 4, contentTop + 4, 60, 11);
            return new CraftingLayout(bounds, true, header, search, clearBtn, modeBtn, list, detail, back);
        }

        int gap = 4;
        int listW = (int) ((width - gap) * 0.40f);
        int detailW = Math.max(10, width - listW - gap);
        int detailX = x + listW + gap;

        UiRect list = new UiRect(x, contentTop, listW, contentH);
        UiRect detail = new UiRect(detailX, contentTop, detailW, contentH);
        UiRect back = new UiRect(detailX + 4, contentTop + 4, 50, 11);

        return new CraftingLayout(bounds, false, header, search, clearBtn, modeBtn, list, detail, back);
    }
}
