package se.jimmyeliasson.gzcompanion.ui.layout;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The MarketWatch add/edit form, including the X/Y/Z location row, at every supported Companion
 * size: real MainScreenLayout -> MarketWatchLayout list card, with and without the bottom
 * reference banner, with and without "Ta bort plats".
 */
class MarketWatchEditFormLayoutTest {
    /** ~MC font width at META scale (0.8 x ~6 px) - generous, so real labels are never wider. */
    private static int measure(String s) {
        return (int) Math.ceil(s.length() * 5.0);
    }

    static final int USE_MY_POS_W = measure("Använd min plats") + 10;
    static final int REMOVE_W = measure("Ta bort plats") + 10;
    static final int BANNER_H = 10;

    /** GUI-scaled screens covering COMPACT (incl. the 320x240 minimum), NORMAL and LARGE. */
    static Stream<int[]> screens() {
        return Stream.of(new int[]{320, 240}, new int[]{427, 240}, new int[]{480, 270}, new int[]{569, 320},
                new int[]{640, 360}, new int[]{854, 480}, new int[]{960, 540}, new int[]{1280, 720});
    }

    private static UiRect listRect(int[] screen, boolean banner) {
        UiRect content = MainScreenLayout.calculate(screen[0], screen[1]).contentRect();
        UiRect bounds = banner ? new UiRect(content.x(), content.y(), content.width(), content.height() - BANNER_H) : content;
        return MarketWatchLayout.calculate(bounds).listRect();
    }

    private static List<UiRect> controls(MarketWatchEditFormLayout f) {
        List<UiRect> rects = new ArrayList<>(List.of(f.itemRect(), f.categoryRect(), f.noteRect(), f.xFieldRect(), f.yFieldRect(),
                f.zFieldRect(), f.useMyPositionBtnRect(), f.statusRect(), f.saveBtnRect(), f.cancelBtnRect()));
        if (f.removeLocationBtnRect().width() > 0) rects.add(f.removeLocationBtnRect());
        return rects;
    }

    @ParameterizedTest
    @MethodSource("screens")
    @DisplayName("No control overlaps another; every control stays inside the card's width; the X/Y/Z row is usable")
    void noOverlapAndUsableFields(int[] screen) {
        for (boolean banner : new boolean[]{false, true}) {
            for (int removeW : new int[]{0, REMOVE_W}) {
                UiRect area = listRect(screen, banner);
                MarketWatchEditFormLayout f = MarketWatchEditFormLayout.calculate(area, 0, USE_MY_POS_W, removeW);
                String c = screen[0] + "x" + screen[1] + " banner=" + banner + " remove=" + (removeW > 0);
                List<UiRect> rects = controls(f);
                rects.addAll(List.of(f.xLabelRect(), f.yLabelRect(), f.zLabelRect()));
                for (int i = 0; i < rects.size(); i++) {
                    UiRect r = rects.get(i);
                    assertTrue(r.x() >= area.x() && r.right() <= area.right(), "Inside card width: " + r + " " + c);
                    for (int j = i + 1; j < rects.size(); j++) {
                        assertFalse(r.intersects(rects.get(j)), "Overlap " + r + " / " + rects.get(j) + " " + c);
                    }
                }
                // "-1234" plus a cursor must be readable in each coordinate field even at 320x240.
                for (UiRect field : List.of(f.xFieldRect(), f.yFieldRect(), f.zFieldRect())) {
                    assertTrue(field.width() >= measure("-1234_") + 4, "Coordinate field too narrow (" + field.width() + ") " + c);
                }
                assertTrue(f.useMyPositionBtnRect().width() >= USE_MY_POS_W, "'Använd min plats' is never truncated " + c);
                assertTrue(f.statusRect().width() >= 60, "Status/error line keeps room " + c);
                // Row order: item, category, note, location, (stacked: location buttons), status, Spara/Avbryt.
                assertTrue(f.itemRect().bottom() <= f.categoryRect().y() && f.categoryRect().bottom() <= f.noteRect().y()
                        && f.noteRect().bottom() <= f.xFieldRect().y() && f.xFieldRect().bottom() <= f.statusRect().y()
                        && f.statusRect().bottom() <= f.saveBtnRect().y(), c);
                if (f.stacked()) {
                    assertTrue(f.xFieldRect().bottom() <= f.useMyPositionBtnRect().y() && f.useMyPositionBtnRect().bottom() <= f.statusRect().y(), c);
                } else {
                    assertEquals(f.xFieldRect().y(), f.useMyPositionBtnRect().y(), "Single row: X/Y/Z beside 'Använd min plats' " + c);
                }
            }
        }
    }

    @ParameterizedTest
    @MethodSource("screens")
    @DisplayName("NORMAL/LARGE fit without scrolling; where the form is taller (COMPACT) every control can be scrolled fully into view")
    void fitsOrScrollsToEveryControl(int[] screen) {
        boolean compact = ResponsiveBreakpoint.fromScreen(screen[0], screen[1]) == ResponsiveBreakpoint.COMPACT;
        for (boolean banner : new boolean[]{false, true}) {
            UiRect area = listRect(screen, banner);
            String c = screen[0] + "x" + screen[1] + " banner=" + banner;
            MarketWatchEditFormLayout top = MarketWatchEditFormLayout.calculate(area, 0, USE_MY_POS_W, REMOVE_W);
            if (!compact) {
                assertEquals(0, top.maxScroll(), "Fits without scrolling " + c);
            }
            for (int i = 0; i < controls(top).size(); i++) {
                MarketWatchEditFormLayout atTop = MarketWatchEditFormLayout.calculate(area, 0, USE_MY_POS_W, REMOVE_W);
                int s = atTop.scrollToReveal(controls(atTop).get(i));
                MarketWatchEditFormLayout revealed = MarketWatchEditFormLayout.calculate(area, s, USE_MY_POS_W, REMOVE_W);
                UiRect r = controls(revealed).get(i);
                assertTrue(revealed.viewport().contains(r.x(), r.y()) && r.bottom() <= revealed.viewport().bottom(),
                        "Control " + i + " reachable " + r + " in " + revealed.viewport() + " " + c);
            }
            MarketWatchEditFormLayout bottom = MarketWatchEditFormLayout.calculate(area, Integer.MAX_VALUE, USE_MY_POS_W, REMOVE_W);
            assertEquals(top.maxScroll(), bottom.scroll(), "Scroll is clamped " + c);
            assertTrue(bottom.saveBtnRect().bottom() <= bottom.viewport().bottom(), "Spara/Avbryt fully visible at the end " + c);
        }
    }

    @Test
    @DisplayName("Only the narrowest cards stack the location row; normal COMPACT, NORMAL and LARGE keep X/Y/Z beside the button")
    void stackingOnlyWhenNarrow() {
        assertTrue(MarketWatchEditFormLayout.calculate(listRect(new int[]{320, 240}, false), 0, USE_MY_POS_W, REMOVE_W).stacked());
        for (int[] s : new int[][]{{427, 240}, {480, 270}, {640, 360}, {960, 540}}) {
            assertFalse(MarketWatchEditFormLayout.calculate(listRect(s, false), 0, USE_MY_POS_W, REMOVE_W).stacked(), s[0] + "x" + s[1]);
        }
    }

    @Test
    @DisplayName("Hit targets are clipped to the visible viewport; fully hidden controls get none")
    void visiblePartClips() {
        UiRect area = new UiRect(0, 0, 187, 85);
        MarketWatchEditFormLayout f = MarketWatchEditFormLayout.calculate(area, 0, USE_MY_POS_W, REMOVE_W);
        assertTrue(f.maxScroll() > 0, "The smallest card needs scrolling");
        assertNull(f.visiblePart(f.saveBtnRect()), "Spara is below the visible area before scrolling");
        UiRect item = f.visiblePart(f.itemRect());
        assertEquals(f.itemRect(), item, "A fully visible control keeps its whole rect");
        assertNull(f.visiblePart(new UiRect(0, 0, 0, 0)), "Hidden (zero-width) controls never get a hit target");
    }
}
