package se.jimmyeliasson.gzcompanion.ui.layout;

/**
 * Pure geometry for the Home tab's "UPPDATERA GZ COMPANION" panel.
 *
 * <p>Top to bottom: the title row (with the "x" close button top-right), a FIXED action row
 * directly under it, a thin separator, then the information content viewport. Only the content
 * viewport ever scrolls or clips - the action row is laid out from the top of the card, before
 * and independently of any content, so no amount of release-note text can push "Ladda ner" /
 * "Senare" (or "Stäng och uppdatera" / "Försök igen") out of view. The action row is reserved
 * in every state, so states without buttons (downloading/verifying) keep the same stable layout
 * and show a status line there instead.
 */
public record UpdatePanelLayout(
    UiRect card,
    int titleY,
    UiRect closeBtn,
    UiRect actionRow,
    UiRect primaryBtn,
    UiRect secondaryBtn,
    int separatorY,
    UiRect contentViewport
) {
    public static final int PAD = 6;
    public static final int TITLE_H = 14;
    public static final int BUTTON_H = 13;
    public static final int BUTTON_GAP = 4;
    public static final int PRIMARY_MAX_W = 120;
    public static final int SECONDARY_MAX_W = 72;
    static final int SEPARATOR_GAP = 4;

    public static UpdatePanelLayout calculate(UiRect bounds) {
        UiRect card = bounds.inset(6, 4);
        int innerX = card.x() + PAD;
        int innerW = Math.max(10, card.width() - PAD * 2);

        int titleY = card.y() + 4;
        UiRect close = new UiRect(card.right() - 16, card.y() + 3, 12, 10);

        UiRect actionRow = new UiRect(innerX, titleY + TITLE_H, innerW, BUTTON_H);
        // Fixed preferred widths; on a narrow card the two buttons split the row 60/40 instead.
        int primaryW = Math.min(PRIMARY_MAX_W, (innerW - BUTTON_GAP) * 3 / 5);
        int secondaryW = Math.min(SECONDARY_MAX_W, innerW - BUTTON_GAP - primaryW);
        UiRect primary = new UiRect(innerX, actionRow.y(), primaryW, BUTTON_H);
        UiRect secondary = new UiRect(primary.right() + BUTTON_GAP, actionRow.y(), Math.max(10, secondaryW), BUTTON_H);

        int separatorY = actionRow.bottom() + SEPARATOR_GAP;
        int contentTop = separatorY + 1 + SEPARATOR_GAP;
        int contentBottom = card.bottom() - 4;
        UiRect viewport = new UiRect(innerX, contentTop, innerW, Math.max(1, contentBottom - contentTop));
        return new UpdatePanelLayout(card, titleY, close, actionRow, primary, secondary, separatorY, viewport);
    }
}
