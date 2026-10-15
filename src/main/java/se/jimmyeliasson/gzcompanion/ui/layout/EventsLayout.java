package se.jimmyeliasson.gzcompanion.ui.layout;

/**
 * Layout for the Events tab, top to bottom: header (title + freshness badge), the highlighted
 * "NÄSTA EVENT" card, the scrollable day-grouped list, and a footer with status text and
 * "Uppdatera". {@link #bodyRect()} spans card + list and is used for whole-area states (loading,
 * error, nothing upcoming). Same header/footer proportions as the Bounties tab.
 */
public record EventsLayout(
    UiRect bounds,
    UiRect headerRect,
    UiRect nextCardRect,
    UiRect listRect,
    UiRect bodyRect,
    UiRect footerRect,
    UiRect footerTextRect,
    UiRect refreshBtnRect
) {
    static final int HEADER_H = 24;
    static final int CARD_H = 38;
    static final int FOOTER_H = 14;
    static final int GAP = 3;
    static final int REFRESH_BTN_W = 56;

    public static EventsLayout calculate(UiRect bounds) {
        int x = bounds.x();
        int width = bounds.width();
        UiRect header = new UiRect(x, bounds.y(), width, HEADER_H);
        UiRect card = new UiRect(x, header.bottom() + GAP, width, CARD_H);

        UiRect footer = new UiRect(x, bounds.bottom() - FOOTER_H, width, FOOTER_H);
        int btnW = Math.min(REFRESH_BTN_W, width / 2);
        UiRect refresh = new UiRect(footer.right() - btnW, footer.y(), btnW, footer.height() - 1);
        UiRect footerText = new UiRect(x, footer.y(), Math.max(1, width - btnW - 4), footer.height());

        int listTop = card.bottom() + GAP;
        UiRect list = new UiRect(x, listTop, width, Math.max(10, footer.y() - GAP - listTop));
        UiRect body = new UiRect(x, card.y(), width, Math.max(10, footer.y() - GAP - card.y()));
        return new EventsLayout(bounds, header, card, list, body, footer, footerText, refresh);
    }
}
