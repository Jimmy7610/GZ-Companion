package se.jimmyeliasson.gzcompanion.ui.layout;

/**
 * Pure geometry for the Kistor navigation HUD card: top-center, BELOW any boss bars the client is
 * already drawing, and pushed below the Companion toast card if the two would overlap on a narrow
 * screen. Never taller than a few text lines, so it stays clear of the crosshair and hotbar.
 */
public record KistorNavigationHudLayout(UiRect card, boolean showArrow, int arrowCenterX, int arrowCenterY,
                                        int line1Y, int line2Y, int line3Y) {
    public static final int CARD_WIDTH = 132;
    /** Vanilla boss bar vertical pitch and first-bar offset (BossHealthOverlay). */
    static final int BOSS_BAR_PITCH = 19;
    static final int TOP_MARGIN = 3;
    static final int ARROW_AREA_H = 18;
    static final int LINE_H = 9;
    static final int PADDING = 3;

    /**
     * @param bossBarCount boss bars currently visible at the top of the screen.
     * @param avoidRect    another HUD card to stay clear of (the toast), or null.
     * @param showArrow    whether a direction arrow is drawn (never in the wrong dimension or once arrived).
     * @param lineCount    number of text lines below the arrow (1-3).
     */
    public static KistorNavigationHudLayout compute(int screenWidth, int screenHeight, int bossBarCount, UiRect avoidRect,
                                                    boolean showArrow, int lineCount) {
        int lines = Math.max(1, Math.min(3, lineCount));
        int width = Math.min(CARD_WIDTH, Math.max(60, screenWidth - 8));
        int height = PADDING + (showArrow ? ARROW_AREA_H : 0) + lines * LINE_H + PADDING;

        // Vanilla stops drawing boss bars past a third of the screen height; never go further.
        int maxBossOffset = Math.max(0, screenHeight / 3);
        int bossOffset = Math.min(maxBossOffset, Math.max(0, bossBarCount) * BOSS_BAR_PITCH);
        int x = (screenWidth - width) / 2;
        int y = TOP_MARGIN + bossOffset;

        UiRect card = new UiRect(x, y, width, height);
        if (avoidRect != null && card.intersects(avoidRect)) {
            card = card.withY(avoidRect.bottom() + 3);
        }

        int top = card.y() + PADDING;
        int arrowCenterX = card.x() + card.width() / 2;
        int arrowCenterY = top + ARROW_AREA_H / 2;
        int textTop = top + (showArrow ? ARROW_AREA_H : 0);
        return new KistorNavigationHudLayout(card, showArrow, arrowCenterX, arrowCenterY,
                textTop, textTop + LINE_H, textTop + 2 * LINE_H);
    }
}
