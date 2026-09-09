package se.jimmyeliasson.gzcompanion.ui.layout;

/**
 * Responsive layout calculation for the Kistor (Chest Manager) tab.
 * Supports 2-pane side-by-side mode (NORMAL / LARGE) and 1-pane mode (COMPACT), mirroring
 * {@link GuideLayout}.
 */
public record KistorLayout(
    UiRect bounds,
    boolean isCompact,
    UiRect headerRect,
    UiRect searchRect,
    UiRect listRect,
    UiRect detailRect,
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
        UiRect search = new UiRect(x, y + headerH + 2, width, searchH);

        int contentY = search.bottom() + 3;
        int contentH = Math.max(10, height - headerH - searchH - 5);

        if (compact) {
            UiRect list = new UiRect(x, contentY, width, contentH);
            UiRect detail = new UiRect(x, contentY, width, contentH);
            int btnH = 13;
            int pad = 4;
            UiRect forget = new UiRect(x + pad, y + height - btnH - pad, width - (pad * 2), btnH);
            UiRect back = new UiRect(x + pad, contentY + pad, 60, 11);
            return new KistorLayout(bounds, true, header, search, list, detail, forget, back);
        }

        int gap = 4;
        int listW = (int) ((width - gap) * 0.38f);
        int detailW = Math.max(10, width - listW - gap);

        UiRect list = new UiRect(x, contentY, listW, contentH);
        UiRect detail = new UiRect(x + listW + gap, contentY, detailW, contentH);

        int btnH = 13;
        int pad = 4;
        UiRect forget = new UiRect(detail.x() + pad, detail.bottom() - btnH - pad, detail.width() - (pad * 2), btnH);
        UiRect back = new UiRect(detail.x() + pad, contentY + pad, 50, 11);

        return new KistorLayout(bounds, false, header, search, list, detail, forget, back);
    }
}
