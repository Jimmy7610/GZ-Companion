package se.jimmyeliasson.gzcompanion.ui.layout;

/**
 * Geometry of the MarketWatch add/edit note form inside the list card. Rows, top to bottom:
 * title, item, category, note, the location row ({@code X [..] Y [..] Z [..] [Använd min plats]}),
 * the location status line (with "Ta bort plats" when there is something to remove), and
 * Spara/Avbryt. When the card is too narrow for readable coordinate fields beside the button
 * (the smallest COMPACT sizes), the location is {@link #stacked()}: X/Y/Z get the full width and
 * "Använd min plats" / "Ta bort plats" move to their own row above the status line.
 *
 * <p>On the smallest Companion sizes the form is taller than the card, so it scrolls: every rect
 * here is already shifted by the (clamped) {@code scroll}, and {@link #viewport()} is the visible
 * area that drawing is clipped to and hit targets are clamped to. Button widths are measured by the
 * caller (they need a live Font) and passed in.
 */
public record MarketWatchEditFormLayout(
    UiRect viewport,
    int scroll,
    int maxScroll,
    int contentHeight,
    UiRect titleRect,
    UiRect itemRect,
    UiRect categoryRect,
    UiRect noteRect,
    UiRect xLabelRect,
    UiRect xFieldRect,
    UiRect yLabelRect,
    UiRect yFieldRect,
    UiRect zLabelRect,
    UiRect zFieldRect,
    UiRect useMyPositionBtnRect,
    UiRect statusRect,
    UiRect removeLocationBtnRect,
    UiRect saveBtnRect,
    UiRect cancelBtnRect,
    boolean stacked
) {
    public static final int ROW_H = 11;
    static final int PAD = 4;
    static final int LABEL_W = 6;
    static final int GAP = 2;
    /** Narrowest coordinate field that still shows "-1234" plus the cursor at META scale. */
    static final int MIN_FIELD_W = 34;

    /**
     * @param area                 the list card.
     * @param scroll               requested scroll offset; clamped to {@code [0, maxScroll]}.
     * @param useMyPositionBtnW    measured width of "Använd min plats".
     * @param removeLocationBtnW   measured width of "Ta bort plats", or 0 to hide it.
     */
    public static MarketWatchEditFormLayout calculate(UiRect area, int scroll, int useMyPositionBtnW, int removeLocationBtnW) {
        UiRect viewport = new UiRect(area.x() + 1, area.y() + 1, Math.max(1, area.width() - 2), Math.max(1, area.height() - 2));
        int x = area.x() + PAD;
        int maxW = Math.max(1, area.width() - 2 * PAD);

        // Offsets relative to the top of the (unscrolled) content.
        int titleY = PAD;
        int itemY = titleY + 12;
        int categoryY = itemY + 13;
        int noteY = categoryY + 14;
        int coordY = noteY + 14;

        int btnW = Math.min(useMyPositionBtnW, maxW / 2);
        boolean stacked = fieldWidth(maxW - btnW - GAP * 2) < MIN_FIELD_W;
        int locationButtonsY = coordY + 13;
        int statusY = stacked ? locationButtonsY + 14 : coordY + 13;
        int buttonsY = statusY + 14;
        int contentHeight = buttonsY + ROW_H + PAD;

        int maxScroll = Math.max(0, contentHeight - (area.height() - 2));
        int s = Math.max(0, Math.min(scroll, maxScroll));
        int top = area.y() - s;

        // Location row: three labelled fields share what "Använd min plats" leaves (or the full width when stacked).
        int fieldW = fieldWidth(stacked ? maxW : maxW - btnW - GAP * 2);
        int cx = x;
        UiRect[] labels = new UiRect[3];
        UiRect[] fields = new UiRect[3];
        for (int i = 0; i < 3; i++) {
            labels[i] = new UiRect(cx, top + coordY, LABEL_W, ROW_H);
            cx += LABEL_W + GAP;
            fields[i] = new UiRect(cx, top + coordY, fieldW, ROW_H);
            cx += fieldW + GAP;
        }
        int removeW = Math.min(removeLocationBtnW, maxW / 2);
        UiRect useMyPos;
        UiRect remove;
        UiRect status;
        if (stacked) {
            useMyPos = new UiRect(x, top + locationButtonsY, btnW, ROW_H);
            remove = removeW > 0 ? new UiRect(x + maxW - removeW, top + locationButtonsY, removeW, ROW_H) : new UiRect(0, 0, 0, 0);
            status = new UiRect(x, top + statusY, maxW, ROW_H);
        } else {
            useMyPos = new UiRect(x + maxW - btnW, top + coordY, btnW, ROW_H);
            remove = removeW > 0 ? new UiRect(x + maxW - removeW, top + statusY, removeW, ROW_H) : new UiRect(0, 0, 0, 0);
            status = new UiRect(x, top + statusY, Math.max(1, maxW - (removeW > 0 ? removeW + GAP * 2 : 0)), ROW_H);
        }

        int halfW = Math.max(1, (maxW - PAD) / 2);
        return new MarketWatchEditFormLayout(viewport, s, maxScroll, contentHeight,
                new UiRect(x, top + titleY, maxW, 9),
                new UiRect(x, top + itemY, maxW, ROW_H),
                new UiRect(x, top + categoryY, maxW, ROW_H),
                new UiRect(x, top + noteY, maxW, ROW_H),
                labels[0], fields[0], labels[1], fields[1], labels[2], fields[2],
                useMyPos, status, remove,
                new UiRect(x, top + buttonsY, halfW, ROW_H),
                new UiRect(x + halfW + PAD, top + buttonsY, halfW, ROW_H),
                stacked);
    }

    /** Width of each of the three labelled coordinate fields sharing {@code rowW}. */
    private static int fieldWidth(int rowW) {
        return Math.max(8, (rowW - 3 * (LABEL_W + GAP) - 2 * GAP) / 3);
    }

    /** The scroll offset that brings {@code rect} (in current, scrolled coordinates) fully into view. */
    public int scrollToReveal(UiRect rect) {
        if (rect.y() < viewport.y()) return Math.max(0, scroll - (viewport.y() - rect.y()));
        if (rect.bottom() > viewport.bottom()) return Math.min(maxScroll, scroll + (rect.bottom() - viewport.bottom()));
        return scroll;
    }

    /** {@code rect} clipped to the visible viewport, or null when none of it is visible. */
    public UiRect visiblePart(UiRect rect) {
        if (rect.width() <= 0) return null;
        int top = Math.max(rect.y(), viewport.y());
        int bottom = Math.min(rect.bottom(), viewport.bottom());
        return bottom > top ? new UiRect(rect.x(), top, rect.width(), bottom - top) : null;
    }
}
