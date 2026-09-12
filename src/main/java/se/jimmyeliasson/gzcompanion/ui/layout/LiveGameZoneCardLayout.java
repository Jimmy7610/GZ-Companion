package se.jimmyeliasson.gzcompanion.ui.layout;

/**
 * Geometry for the Home tab's "LIVE GAMEZONE" card (see docs/LIVE-GAMEZONE-STATUS.md) - mirrors
 * the same "calculate once, reuse for both height measurement and rendering" convention already
 * used by {@link HomeTabLayout} and {@code OnlineLayout}, so the estimated content height used for
 * scrolling can never drift from what is actually drawn.
 *
 * <p>Three sub-cards (STAD / EKONOMI / SERVER) sit side-by-side in a wide window and stack
 * vertically in a compact one; either way each gets the same fixed content budget. When no
 * sub-card section has any data at all, {@code showSubCards} is false and the card is just the
 * header + settlement block (see {@link #calculate}).
 */
public record LiveGameZoneCardLayout(
    UiRect bounds,
    boolean isCompact,
    boolean showSubCards,
    UiRect headerRect,
    UiRect settlementRect,
    UiRect stadRect,
    UiRect ekonomiRect,
    UiRect serverRect,
    int contentHeight
) {
    private static final int PAD = 4;
    private static final int HEADER_H = 9;
    private static final int SETTLEMENT_H_WITH_DATA = 19;
    private static final int SETTLEMENT_H_NO_DATA = 10;
    private static final int SUBCARD_H = 28;
    private static final int GAP = 3;
    private static final int COMPACT_BREAKPOINT = 320;

    public static LiveGameZoneCardLayout calculate(UiRect area, boolean hasSettlement, boolean showSubCards) {
        int x = area.x();
        int y = area.y();
        int width = area.width();
        boolean compact = width < COMPACT_BREAKPOINT;

        int innerX = x + PAD;
        int innerW = Math.max(10, width - (PAD * 2));

        UiRect header = new UiRect(innerX, y + PAD, innerW, HEADER_H);
        int settlementH = hasSettlement ? SETTLEMENT_H_WITH_DATA : SETTLEMENT_H_NO_DATA;
        UiRect settlement = new UiRect(innerX, header.bottom() + 2, innerW, settlementH);

        int subTop = settlement.bottom() + GAP;
        UiRect stad;
        UiRect ekonomi;
        UiRect server;
        int contentBottom;

        if (!showSubCards) {
            stad = new UiRect(innerX, subTop, 0, 0);
            ekonomi = new UiRect(innerX, subTop, 0, 0);
            server = new UiRect(innerX, subTop, 0, 0);
            contentBottom = settlement.bottom();
        } else if (compact) {
            stad = new UiRect(innerX, subTop, innerW, SUBCARD_H);
            ekonomi = new UiRect(innerX, stad.bottom() + GAP, innerW, SUBCARD_H);
            server = new UiRect(innerX, ekonomi.bottom() + GAP, innerW, SUBCARD_H);
            contentBottom = server.bottom();
        } else {
            int colGap = 3;
            int colW = Math.max(10, (innerW - (colGap * 2)) / 3);
            stad = new UiRect(innerX, subTop, colW, SUBCARD_H);
            ekonomi = new UiRect(stad.right() + colGap, subTop, colW, SUBCARD_H);
            int serverW = Math.max(10, (innerX + innerW) - (ekonomi.right() + colGap));
            server = new UiRect(ekonomi.right() + colGap, subTop, serverW, SUBCARD_H);
            contentBottom = stad.bottom();
        }

        int contentHeight = (contentBottom - y) + PAD;
        return new LiveGameZoneCardLayout(area, compact, showSubCards, header, settlement, stad, ekonomi, server, contentHeight);
    }
}
