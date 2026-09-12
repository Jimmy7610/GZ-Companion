package se.jimmyeliasson.gzcompanion.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import se.jimmyeliasson.gzcompanion.ui.layout.HomeTabLayout;
import se.jimmyeliasson.gzcompanion.ui.layout.MainScreenLayout;
import se.jimmyeliasson.gzcompanion.ui.layout.UiRect;

import static org.junit.jupiter.api.Assertions.*;

class UiInputTest {

    @Test
    @DisplayName("UiInput: Hit testing resolves correct TabType for every tab")
    void testSidebarTabHitTesting() {
        int[][] testViewports = {
            {320, 200},
            {480, 300},
            {640, 360},
            {640, 384},
            {800, 450},
            {960, 540}
        };

        TabType[] allTabs = TabType.values();

        for (int[] vp : testViewports) {
            MainScreenLayout layout = MainScreenLayout.calculate(vp[0], vp[1]);
            UiRect[] tabRects = layout.tabRects();
            assertEquals(allTabs.length, tabRects.length);

            for (int i = 0; i < allTabs.length; i++) {
                UiRect tr = tabRects[i];
                TabType expectedTab = allTabs[i];

                // Point at center of tab must hit expectedTab
                double centerX = tr.x() + (tr.width() / 2.0);
                double centerY = tr.y() + (tr.height() / 2.0);
                TabType found = UiInput.findClickedTab(tabRects, centerX, centerY);
                assertEquals(expectedTab, found, "Center of tab " + expectedTab + " must be detected in " + vp[0] + "x" + vp[1]);

                // Point strictly outside top-left of sidebar must return null
                assertNull(UiInput.findClickedTab(tabRects, tr.x() - 5, tr.y() - 5));

                // Point strictly outside right of tab must return null
                assertNull(UiInput.findClickedTab(tabRects, tr.right() + 5, centerY));
            }
        }
    }

    @Test
    @DisplayName("UiInput: Guide tab hit testing explicitly verified inside and immediately outside bounds")
    void testGuideTabSpecificHitTest() {
        MainScreenLayout layout = MainScreenLayout.calculate(640, 384);
        UiRect[] tabRects = layout.tabRects();
        UiRect guideRect = tabRects[TabType.GUIDE.ordinal()];
        assertNotNull(guideRect);

        // Point inside Guide tab
        double inX = guideRect.x() + 10;
        double inY = guideRect.y() + 5;
        assertTrue(UiInput.isPointInside(guideRect, inX, inY));
        assertEquals(TabType.GUIDE, UiInput.findClickedTab(tabRects, inX, inY));

        // Points immediately outside Guide tab boundary
        assertFalse(UiInput.isPointInside(guideRect, guideRect.x() - 1, inY));
        assertFalse(UiInput.isPointInside(guideRect, guideRect.right() + 1, inY));
        assertFalse(UiInput.isPointInside(guideRect, inX, guideRect.y() - 1));
        assertFalse(UiInput.isPointInside(guideRect, inX, guideRect.bottom() + 1));
    }

    @Test
    @DisplayName("UiInput: All tab rectangles have disjoint, distinct clickable areas")
    void testTabsDisjointHitAreas() {
        MainScreenLayout layout = MainScreenLayout.calculate(640, 384);
        UiRect[] tabRects = layout.tabRects();

        for (int i = 0; i < tabRects.length; i++) {
            for (int j = i + 1; j < tabRects.length; j++) {
                assertFalse(tabRects[i].intersects(tabRects[j]), "Tab " + i + " and Tab " + j + " must not overlap");
            }
        }
    }

    @Test
    @DisplayName("UiInput: Action buttons inside Objective card stay strictly contained and clickable")
    void testHomeActionButtonsHitTesting() {
        MainScreenLayout main = MainScreenLayout.calculate(640, 384);
        HomeTabLayout home = HomeTabLayout.calculate(main.contentRect());

        UiRect primary = home.primaryButtonRect();
        UiRect sec1 = home.secondaryButton1Rect();
        UiRect sec2 = home.secondaryButton2Rect();

        assertTrue(UiInput.isPointInside(home.objectiveRect(), primary.x() + 2, primary.y() + 2));
        assertTrue(UiInput.isPointInside(home.objectiveRect(), sec1.x() + 2, sec1.y() + 2));
        assertTrue(UiInput.isPointInside(home.objectiveRect(), sec2.x() + 2, sec2.y() + 2));

        assertTrue(UiInput.isPointInside(primary, primary.x() + 5, primary.y() + 5));
        assertTrue(UiInput.isPointInside(sec1, sec1.x() + 5, sec1.y() + 5));
        assertTrue(UiInput.isPointInside(sec2, sec2.x() + 5, sec2.y() + 5));

        assertFalse(primary.intersects(sec1));
        assertFalse(primary.intersects(sec2));
        assertFalse(sec1.intersects(sec2));
    }
}