package se.jimmyeliasson.gzcompanion.guide;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import se.jimmyeliasson.gzcompanion.ui.layout.GuideLayout;
import se.jimmyeliasson.gzcompanion.ui.layout.UiRect;

import static org.junit.jupiter.api.Assertions.*;

class GuideLayoutTest {

    @Test
    @DisplayName("Should compute 2-pane side-by-side geometry in normal / large mode")
    void testTwoPaneLayout() {
        UiRect bounds = new UiRect(50, 40, 360, 200);
        GuideLayout layout = GuideLayout.calculate(bounds);

        assertFalse(layout.isCompact());
        assertNotNull(layout.headerRect());
        assertNotNull(layout.progressBarRect());
        assertNotNull(layout.leftNavRect());
        assertNotNull(layout.detailRect());
        assertNotNull(layout.markDoneBtnRect());
        assertNotNull(layout.resetBtnRect());

        // Left nav and detail pane should not overlap horizontally
        assertTrue(layout.leftNavRect().right() <= layout.detailRect().x(), "Left navigator and detail pane must not overlap");
        assertTrue(layout.detailRect().right() <= bounds.right(), "Detail pane must fit within bounds");
        assertTrue(layout.markDoneBtnRect().right() <= layout.resetBtnRect().x(), "Action buttons must not overlap");
    }

    @Test
    @DisplayName("Should compute single-pane geometry in compact mode")
    void testCompactLayout() {
        UiRect bounds = new UiRect(10, 10, 240, 180);
        GuideLayout layout = GuideLayout.calculate(bounds);

        assertTrue(layout.isCompact());
        assertEquals(bounds.width(), layout.leftNavRect().width());
        assertEquals(bounds.width(), layout.detailRect().width());
    }
}
