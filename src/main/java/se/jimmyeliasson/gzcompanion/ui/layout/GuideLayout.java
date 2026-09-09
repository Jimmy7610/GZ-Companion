package se.jimmyeliasson.gzcompanion.ui.layout;

/**
 * Responsive layout calculation for the interactive Guide Tab.
 * Supports 2-pane side-by-side mode (NORMAL / LARGE) and 1-pane mode (COMPACT).
 */
public record GuideLayout(
    UiRect bounds,
    boolean isCompact,
    UiRect headerRect,
    UiRect progressBarRect,
    UiRect leftNavRect,
    UiRect detailRect,
    UiRect markDoneBtnRect,
    UiRect resetBtnRect,
    UiRect backBtnRect
) {
    public static GuideLayout calculate(UiRect bounds) {
        int x = bounds.x();
        int y = bounds.y();
        int width = bounds.width();
        int height = bounds.height();

        boolean compact = width < 300;

        // Top progress & header strip
        int headerH = 18;
        UiRect header = new UiRect(x, y, width, headerH);
        UiRect progressBar = new UiRect(x + 2, y + headerH - 3, width - 4, 2);

        int contentY = y + headerH + 3;
        int contentH = Math.max(10, height - headerH - 3);

        if (compact) {
            UiRect nav = new UiRect(x, contentY, width, contentH);
            UiRect detail = new UiRect(x, contentY, width, contentH);
            int btnH = 13;
            int pad = 4;
            UiRect markDone = new UiRect(x + pad, y + height - btnH - pad, (width - (pad * 3)) / 2, btnH);
            UiRect reset = new UiRect(markDone.right() + pad, markDone.y(), (width - (pad * 3)) / 2, btnH);
            UiRect back = new UiRect(x + pad, contentY + pad, 60, 11);
            return new GuideLayout(bounds, true, header, progressBar, nav, detail, markDone, reset, back);
        }

        // Two-pane split: 38% left navigator, 62% right detail
        int gap = 4;
        int leftW = (int) ((width - gap) * 0.38f);
        int rightW = Math.max(10, width - leftW - gap);

        UiRect leftNav = new UiRect(x, contentY, leftW, contentH);
        UiRect detail = new UiRect(x + leftW + gap, contentY, rightW, contentH);

        // Buttons at the bottom of the detail pane
        int btnH = 13;
        int pad = 4;
        int availBtnW = Math.max(10, detail.width() - (pad * 2));
        int btnY = detail.bottom() - btnH - pad;

        int markW = (int) ((availBtnW - pad) * 0.65f);
        int resetW = Math.max(5, availBtnW - markW - pad);

        UiRect markDone = new UiRect(detail.x() + pad, btnY, markW, btnH);
        UiRect reset = new UiRect(markDone.right() + pad, btnY, resetW, btnH);
        UiRect back = new UiRect(detail.x() + pad, contentY + pad, 50, 11);

        return new GuideLayout(bounds, false, header, progressBar, leftNav, detail, markDone, reset, back);
    }
}
