package se.jimmyeliasson.gzcompanion.ui;

import se.jimmyeliasson.gzcompanion.ui.layout.UiRect;

/**
 * Pure coordinate and hit-testing helper for screen interactions.
 * Ensures consistent hit-testing across all GUI scaling levels.
 */
public final class UiInput {
    private UiInput() {}

    /**
     * Resolves which tab (if any) contains the given mouse coordinates.
     */
    public static TabType findClickedTab(UiRect[] tabRects, double mouseX, double mouseY) {
        if (tabRects == null) return null;
        TabType[] tabs = TabType.values();
        for (int i = 0; i < tabs.length && i < tabRects.length; i++) {
            if (tabRects[i] != null && tabRects[i].contains(mouseX, mouseY)) {
                return tabs[i];
            }
        }
        return null;
    }

    /**
     * Tests if a coordinate point is strictly within a UiRect.
     */
    public static boolean isPointInside(UiRect rect, double mouseX, double mouseY) {
        return rect != null && rect.contains(mouseX, mouseY);
    }
}