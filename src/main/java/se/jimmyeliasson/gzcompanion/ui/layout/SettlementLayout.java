package se.jimmyeliasson.gzcompanion.ui.layout;

/**
 * Responsive layout for the Settlement tab. A mode-cycle button (Översikt / Progression /
 * Material / Medlemmar) sits below the header. Only PROGRESSION uses the NORMAL/LARGE 2-pane vs
 * COMPACT 1-pane list/detail split (mirroring {@link CraftingLayout}); the other three modes
 * always render into the single full-width {@code panelRect}.
 */
public record SettlementLayout(
    UiRect bounds,
    boolean isCompact,
    UiRect headerRect,
    UiRect modeBtnRect,
    UiRect panelRect,
    UiRect listRect,
    UiRect detailRect,
    UiRect backBtnRect
) {
    public static SettlementLayout calculate(UiRect bounds) {
        int x = bounds.x();
        int y = bounds.y();
        int width = bounds.width();
        int height = bounds.height();

        boolean compact = width < 300;

        int headerH = 13;
        UiRect header = new UiRect(x, y, width, headerH);

        int modeBtnY = y + headerH + 2;
        int modeBtnH = 11;
        UiRect modeBtn = new UiRect(x, modeBtnY, width, modeBtnH);

        int contentTop = modeBtn.bottom() + 3;
        int contentBottom = y + height;
        int contentH = Math.max(10, contentBottom - contentTop);

        UiRect panel = new UiRect(x, contentTop, width, contentH);

        if (compact) {
            UiRect list = new UiRect(x, contentTop, width, contentH);
            UiRect detail = new UiRect(x, contentTop, width, contentH);
            UiRect back = new UiRect(x + 4, contentTop + 4, 60, 11);
            return new SettlementLayout(bounds, true, header, modeBtn, panel, list, detail, back);
        }

        int gap = 4;
        int listW = (int) ((width - gap) * 0.40f);
        int detailW = Math.max(10, width - listW - gap);
        int detailX = x + listW + gap;

        UiRect list = new UiRect(x, contentTop, listW, contentH);
        UiRect detail = new UiRect(detailX, contentTop, detailW, contentH);
        UiRect back = new UiRect(detailX + 4, contentTop + 4, 50, 11);

        return new SettlementLayout(bounds, false, header, modeBtn, panel, list, detail, back);
    }
}
