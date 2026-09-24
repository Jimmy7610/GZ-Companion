package se.jimmyeliasson.gzcompanion.ui.tabs.kistor;

/**
 * Scroll offset for one Kistor pane. The maximum is recorded from the content height ACTUALLY
 * drawn in the last frame (measure-while-drawing), so the scroll range can never drift from what
 * is rendered - the class of bug several earlier tabs hit when a separate height estimate and the
 * real renderer disagreed.
 */
public final class ScrollState {
    public static final int WHEEL_STEP = 14;

    private int offset = 0;
    private int maxScroll = 0;

    public int offset() {
        return offset;
    }

    public int maxScroll() {
        return maxScroll;
    }

    /** Records the drawn content height against the visible viewport height and clamps. */
    public void recordContent(int contentHeight, int viewportHeight) {
        this.maxScroll = Math.max(0, contentHeight - Math.max(1, viewportHeight));
        clamp();
    }

    public void clamp() {
        offset = Math.max(0, Math.min(offset, maxScroll));
    }

    /** @return true if the offset changed (or could have - i.e. there is anything to scroll). */
    public boolean scrollBy(double wheelY) {
        if (maxScroll <= 0) return false;
        offset = Math.max(0, Math.min(maxScroll, offset - (int) Math.round(wheelY * WHEEL_STEP)));
        return true;
    }

    public void reset() {
        offset = 0;
    }
}
