package se.jimmyeliasson.gzcompanion.ui.layout;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import se.jimmyeliasson.gzcompanion.ui.TabType;

import static org.junit.jupiter.api.Assertions.*;

class ResponsiveLayoutTest {

    @Test
    @DisplayName("Breakpoint: Evaluates correct ResponsiveBreakpoint categories")
    void testBreakpoints() {
        assertEquals(ResponsiveBreakpoint.COMPACT, ResponsiveBreakpoint.fromScreen(400, 300));
        assertEquals(ResponsiveBreakpoint.COMPACT, ResponsiveBreakpoint.fromScreen(500, 320));
        assertEquals(ResponsiveBreakpoint.NORMAL, ResponsiveBreakpoint.fromScreen(640, 360));
        assertEquals(ResponsiveBreakpoint.NORMAL, ResponsiveBreakpoint.fromScreen(640, 384));
        assertEquals(ResponsiveBreakpoint.NORMAL, ResponsiveBreakpoint.fromScreen(720, 450));
        assertEquals(ResponsiveBreakpoint.LARGE, ResponsiveBreakpoint.fromScreen(800, 500));
        assertEquals(ResponsiveBreakpoint.LARGE, ResponsiveBreakpoint.fromScreen(960, 540));
    }

    @Test
    @DisplayName("Layout: Strict containment and non-overflow validation across 7 realistic viewports")
    void testMainAndHomeLayoutContainment() {
        int[][] realisticViewports = {
            {320, 200}, // Ultra-compact GUI scale 4
            {400, 240}, // High GUI scale
            {480, 300}, // Intermediate scale
            {640, 360}, // Standard scaled window
            {640, 384}, // Target 1920x1152 scaled logical window
            {800, 450}, // 16:9 Scale 2
            {960, 540}  // High resolution
        };

        for (int[] vp : realisticViewports) {
            int sw = vp[0];
            int sh = vp[1];

            MainScreenLayout main = MainScreenLayout.calculate(sw, sh);
            UiRect screen = new UiRect(0, 0, sw, sh);

            // 1. Modal bounds check
            assertTrue(screen.contains(main.modalRect()), "Modal must stay strictly inside screen for " + sw + "x" + sh);
            assertTrue(main.modalRect().width() > 0);
            assertTrue(main.modalRect().height() > 0);

            // 2. Sidebar & Content bounds check
            assertTrue(main.modalRect().contains(main.sidebarRect()), "Sidebar must stay inside modal for " + sw + "x" + sh);
            assertTrue(main.modalRect().contains(main.contentRect()), "Content must stay inside modal for " + sw + "x" + sh);
            assertFalse(main.sidebarRect().intersects(main.contentRect()), "Sidebar and Content must never intersect");

            // 3. Tab buttons check
            for (UiRect tr : main.tabRects()) {
                assertNotNull(tr);
                assertTrue(main.sidebarRect().contains(tr), "Tab rect must stay inside sidebar for " + sw + "x" + sh);
            }
            for (int i = 0; i < main.tabRects().length - 1; i++) {
                assertFalse(main.tabRects()[i].intersects(main.tabRects()[i + 1]), "Adjacent tabs must not intersect");
            }

            // 4. Home Dashboard Card Geometry check
            HomeTabLayout home = HomeTabLayout.calculate(main.contentRect());
            assertTrue(main.contentRect().contains(home.welcomeRect()), "Welcome card must stay inside content for " + sw + "x" + sh);
            assertTrue(main.contentRect().contains(home.serverRect()), "Server card must stay inside content for " + sw + "x" + sh);
            assertTrue(main.contentRect().contains(home.versionRect()), "Version strip must stay inside content for " + sw + "x" + sh);
            assertTrue(main.contentRect().contains(home.objectiveRect()), "Objective card must stay inside content for " + sw + "x" + sh);
            assertTrue(main.contentRect().contains(home.moduleRect()), "Module card must stay inside content for " + sw + "x" + sh);

            // 5. Inter-card non-intersection check
            assertFalse(home.welcomeRect().intersects(home.serverRect()), "Welcome and Server cards must not intersect");
            assertFalse(home.welcomeRect().intersects(home.versionRect()), "Welcome and Version cards must not intersect");
            assertFalse(home.versionRect().intersects(home.objectiveRect()), "Version and Objective cards must not intersect");
            assertFalse(home.objectiveRect().intersects(home.moduleRect()), "Objective and Module cards must not intersect");

            // 6. Action buttons inside objective card check
            assertTrue(home.objectiveRect().contains(home.primaryButtonRect()), "Primary button must stay inside objective card");
            assertTrue(home.objectiveRect().contains(home.secondaryButton1Rect()), "Secondary button 1 must stay inside objective card");
            assertTrue(home.objectiveRect().contains(home.secondaryButton2Rect()), "Secondary button 2 must stay inside objective card");
            assertFalse(home.primaryButtonRect().intersects(home.secondaryButton1Rect()), "Primary and secondary buttons must not intersect");
            assertFalse(home.secondaryButton1Rect().intersects(home.secondaryButton2Rect()), "Secondary buttons must not intersect each other");
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

        UiRect inner = new UiRect(20, 30, 40, 20);
        assertTrue(rect.contains(inner));

        UiRect overlapping = new UiRect(50, 40, 100, 50);
        assertTrue(rect.intersects(overlapping));

        UiRect outside = new UiRect(200, 200, 50, 50);
        assertFalse(rect.intersects(outside));
    }
}