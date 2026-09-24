package se.jimmyeliasson.gzcompanion.chest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;
import se.jimmyeliasson.gzcompanion.ui.layout.KistorLayout;
import se.jimmyeliasson.gzcompanion.ui.layout.KistorNavigationHudLayout;
import se.jimmyeliasson.gzcompanion.ui.layout.UiRect;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Kistor 2.0 responsive layout guarantees, as pure geometry. */
class Kistor2LayoutTest {

    static Stream<UiRect> boundsCases() {
        return Stream.of(new UiRect(0, 0, 200, 170), new UiRect(10, 10, 240, 180), new UiRect(50, 40, 360, 200), new UiRect(0, 0, 620, 320));
    }

    static List<UiRect> actionRects(KistorLayout l) {
        List<UiRect> rects = new ArrayList<>();
        for (UiRect r : List.of(l.primaryActionRect(), l.renameBtnRect(), l.favoriteBtnRect(), l.copyBtnRect(), l.noteBtnRect(),
                l.groupAssignBtnRect(), l.forgetBtnRect())) {
            if (r.width() > 0) rects.add(r);
        }
        return rects;
    }

    @ParameterizedTest
    @MethodSource("boundsCases")
    @DisplayName("Every mode/banner/detail combination: controls contained, no action button overlaps scrollable content or another button")
    void noOverlapInAnyCombination(UiRect bounds) {
        for (KistorLayout.Mode mode : KistorLayout.Mode.values()) {
            for (boolean nav : new boolean[]{false, true}) {
                for (boolean detail : new boolean[]{false, true}) {
                    KistorLayout l = KistorLayout.calculate(bounds, mode, nav, true, detail);
                    String c = mode + " nav=" + nav + " detail=" + detail + " " + bounds;
                    for (UiRect r : List.of(l.headerRect(), l.searchRect(), l.clearBtnRect(), l.filterBtnRect(), l.sortBtnRect(), l.groupBtnRect(),
                            l.modeSakerRect(), l.modeForvaringRect(), l.modeMaterialRect())) {
                        assertTrue(bounds.contains(r), "Contained: " + r + " " + c);
                    }
                    assertFalse(l.searchRect().intersects(l.listRect()), c);
                    assertFalse(l.groupBtnRect().intersects(l.listRect()), c);
                    assertFalse(l.modeSakerRect().intersects(l.modeForvaringRect()), c);
                    assertFalse(l.modeForvaringRect().intersects(l.modeMaterialRect()), c);
                    if (nav) {
                        assertTrue(bounds.contains(l.navBannerRect()), c);
                        assertTrue(bounds.contains(l.navStopBtnRect()), c);
                        assertFalse(l.navBannerRect().intersects(l.navStopBtnRect()), c);
                        assertFalse(l.navBannerRect().intersects(l.searchRect()), c);
                    } else {
                        assertEquals(0, l.navBannerRect().width(), c);
                    }
                    List<UiRect> actions = actionRects(l);
                    for (int i = 0; i < actions.size(); i++) {
                        assertTrue(bounds.contains(actions.get(i)), "Action contained " + c);
                        assertFalse(actions.get(i).intersects(l.listRect()), "Action vs list " + c);
                        assertFalse(actions.get(i).intersects(l.detailRect()), "Action vs detail " + c);
                        for (int j = i + 1; j < actions.size(); j++) {
                            assertFalse(actions.get(i).intersects(actions.get(j)), "Action vs action " + c);
                        }
                    }
                    if (l.isCompact()) {
                        assertEquals(l.listRect(), l.detailRect(), "Compact shows one logical pane at a time " + c);
                    }
                    assertTrue(l.detailContentRect().y() >= l.detailRect().y());
                    assertTrue(l.detailContentRect().bottom() <= l.detailRect().bottom());
                }
            }
        }
    }

    @Test
    @DisplayName("Action zones: FÖRVARING has two rows, SAKER one, MATERIAL none; a compact list without detail reserves none")
    void actionZonesPerMode() {
        UiRect wide = new UiRect(0, 0, 400, 240);
        assertEquals(7, actionRects(KistorLayout.calculate(wide, KistorLayout.Mode.FORVARING, false, false, true)).size());
        assertEquals(1, actionRects(KistorLayout.calculate(wide, KistorLayout.Mode.SAKER, false, false, true)).size());
        assertEquals(0, actionRects(KistorLayout.calculate(wide, KistorLayout.Mode.MATERIAL, false, true, true)).size());

        UiRect narrow = new UiRect(0, 0, 240, 200);
        KistorLayout listOnly = KistorLayout.calculate(narrow, KistorLayout.Mode.FORVARING, false, false, false);
        assertEquals(0, actionRects(listOnly).size());
        KistorLayout withDetail = KistorLayout.calculate(narrow, KistorLayout.Mode.FORVARING, false, false, true);
        assertTrue(listOnly.listRect().height() > withDetail.listRect().height(), "A compact list gets the space the action rows would use");
    }

    @Test
    @DisplayName("MATERIAL falls back to SAKER when no material request exists; MATERIAL uses one full-width pane")
    void materialMode() {
        UiRect wide = new UiRect(0, 0, 400, 240);
        assertEquals(KistorLayout.Mode.SAKER, KistorLayout.calculate(wide, KistorLayout.Mode.MATERIAL, false, false, true).mode());
        assertEquals(0, KistorLayout.calculate(wide, KistorLayout.Mode.SAKER, false, false, true).modeMaterialRect().width());
        KistorLayout material = KistorLayout.calculate(wide, KistorLayout.Mode.MATERIAL, false, true, true);
        assertEquals(wide.width(), material.listRect().width());
    }

    @Test
    @DisplayName("The navigation banner pushes content down instead of overlapping it")
    void bannerReservesSpace() {
        UiRect wide = new UiRect(0, 0, 400, 240);
        KistorLayout without = KistorLayout.calculate(wide, KistorLayout.Mode.SAKER, false, false, true);
        KistorLayout with = KistorLayout.calculate(wide, KistorLayout.Mode.SAKER, true, false, true);
        assertTrue(with.searchRect().y() > without.searchRect().y());
        assertTrue(with.listRect().y() > without.listRect().y());
    }

    @Test
    @DisplayName("Navigation HUD: top-center, below boss bars, clear of the toast card, never past a third of the screen")
    void hudLayout() {
        KistorNavigationHudLayout plain = KistorNavigationHudLayout.compute(427, 240, 0, null, true, 3);
        UiRect card = plain.card();
        assertEquals(427 / 2, card.x() + card.width() / 2, 1.0);
        assertTrue(card.y() < 12);

        KistorNavigationHudLayout bossed = KistorNavigationHudLayout.compute(427, 240, 2, null, true, 3);
        assertTrue(bossed.card().y() >= 2 * 19, "Sits below two boss bars");

        KistorNavigationHudLayout many = KistorNavigationHudLayout.compute(427, 240, 50, null, true, 3);
        assertTrue(many.card().y() <= 240 / 3 + 3);

        UiRect toast = new UiRect(320 - 166, 6, 160, 24);
        KistorNavigationHudLayout avoid = KistorNavigationHudLayout.compute(320, 240, 0, toast, true, 3);
        assertFalse(avoid.card().intersects(toast), "Narrow screen: pushed below the toast card");

        KistorNavigationHudLayout noArrow = KistorNavigationHudLayout.compute(427, 240, 0, null, false, 2);
        assertTrue(noArrow.card().height() < card.height());
        assertTrue(plain.arrowCenterY() < plain.line1Y(), "Arrow above the title");
    }
}
