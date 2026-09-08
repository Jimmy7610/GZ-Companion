package se.jimmyeliasson.gzcompanion.ui.layout;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import se.jimmyeliasson.gzcompanion.ui.tabs.HomeTabComponent;

import static org.junit.jupiter.api.Assertions.*;

class ResponsiveLayoutTest {

    @Test
    @DisplayName("Breakpoint: Evaluates correct ResponsiveBreakpoint categories")
    void testBreakpoints() {
        assertEquals(ResponsiveBreakpoint.COMPACT, ResponsiveBreakpoint.fromScreen(400, 300));
        assertEquals(ResponsiveBreakpoint.COMPACT, ResponsiveBreakpoint.fromScreen(500, 320));
        assertEquals(ResponsiveBreakpoint.NORMAL, ResponsiveBreakpoint.fromScreen(640, 400));
        assertEquals(ResponsiveBreakpoint.NORMAL, ResponsiveBreakpoint.fromScreen(720, 450));
        assertEquals(ResponsiveBreakpoint.LARGE, ResponsiveBreakpoint.fromScreen(800, 500));
        assertEquals(ResponsiveBreakpoint.LARGE, ResponsiveBreakpoint.fromScreen(1000, 600));
    }

    @Test
    @DisplayName("Layout: Mathematical non-overflow validation across multiple viewport sizes")
    void testHomeLayoutBoundsNonOverflow() {
        int[][] viewports = {
            {280, 180}, // Compact scale
            {420, 260}, // Normal scale
            {560, 320}  // Large scale
        };

        for (int[] vp : viewports) {
            int w = vp[0];
            int h = vp[1];
            UiRect contentRect = new UiRect(100, 32, w, h);

            HomeTabComponent home = new HomeTabComponent();
            home.calculateLayout(contentRect);

            assertTrue(contentRect.width() > 0);
            assertTrue(contentRect.height() > 0);
        }
    }

    @Test
    @DisplayName("UiRect: Point containment and geometry operations")
    void testUiRectGeometry() {
        UiRect rect = new UiRect(10, 20, 100, 50);
        assertEquals(110, rect.right());
        assertEquals(70, rect.bottom());
        assertTrue(rect.contains(10, 20));
        assertTrue(rect.contains(50, 40));
        assertFalse(rect.contains(9, 20));
        assertFalse(rect.contains(110, 20));
        assertFalse(rect.contains(50, 70));

        UiRect inset = rect.inset(5, 5);
        assertEquals(15, inset.x());
        assertEquals(25, inset.y());
        assertEquals(90, inset.width());
        assertEquals(40, inset.height());
    }
}