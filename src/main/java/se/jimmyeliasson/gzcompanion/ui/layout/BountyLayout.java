package se.jimmyeliasson.gzcompanion.ui.layout;

/**
 * Responsive layout calculation for the Bounty Board tab - mirrors {@link GuideLayout}'s
 * "2-pane side-by-side on normal/wide, 1-pane list/detail on compact" pattern, per this project's
 * explicit design direction that Bounty Board should feel like the same application as Guide.
 */
public record BountyLayout(
        UiRect bounds,
        boolean isCompact,
        UiRect headerRect,
        UiRect countRect,
        UiRect listRect,
        UiRect detailRect,
        UiRect footerRect,
        UiRect refreshBtnRect,
        UiRect backBtnRect
) {
    private static final int HEADER_H = 24;
    private static final int COUNT_H = 11;
    private static final int FOOTER_H = 14;
    private static final int GAP = 3;
    private static final int COMPACT_BREAKPOINT = 300;

    public static BountyLayout calculate(UiRect bounds) {
        int x = bounds.x();
        int y = bounds.y();
        int width = bounds.width();

        boolean compact = width < COMPACT_BREAKPOINT;

        UiRect header = new UiRect(x, y, width, HEADER_H);
        UiRect count = new UiRect(x, header.bottom() + GAP, width, COUNT_H);

        int footerY = bounds.bottom() - FOOTER_H;
        UiRect footer = new UiRect(x, footerY, width, FOOTER_H);
        UiRect refreshBtn = new UiRect(footer.right() - 56, footer.y(), 56, footer.height() - 1);

        int bodyY = count.bottom() + GAP;
        int bodyH = Math.max(10, footerY - GAP - bodyY);

        if (compact) {
            UiRect body = new UiRect(x, bodyY, width, bodyH);
            UiRect back = new UiRect(x + 2, bodyY + 2, 44, 11);
            return new BountyLayout(bounds, true, header, count, body, body, footer, refreshBtn, back);
        }

        int gap = 4;
        int listW = (int) ((width - gap) * 0.4f);
        int detailW = Math.max(10, width - listW - gap);
        UiRect list = new UiRect(x, bodyY, listW, bodyH);
        UiRect detail = new UiRect(x + listW + gap, bodyY, detailW, bodyH);
        UiRect back = new UiRect(detail.x() + 2, bodyY + 2, 44, 11); // unused in wide mode, kept non-null

        return new BountyLayout(bounds, false, header, count, list, detail, footer, refreshBtn, back);
    }
}
