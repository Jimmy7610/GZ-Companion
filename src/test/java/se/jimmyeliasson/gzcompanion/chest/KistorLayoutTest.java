package se.jimmyeliasson.gzcompanion.chest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import se.jimmyeliasson.gzcompanion.ui.layout.KistorLayout;
import se.jimmyeliasson.gzcompanion.ui.layout.UiRect;
import se.jimmyeliasson.gzcompanion.ui.tabs.KistorTabComponent;

import static org.junit.jupiter.api.Assertions.*;

class KistorLayoutTest {

    @Test
    @DisplayName("Should compute 2-pane side-by-side geometry that stays contained and non-overlapping in normal/large mode")
    void testTwoPaneLayoutContained() {
        UiRect bounds = new UiRect(50, 40, 360, 200);
        KistorLayout layout = KistorLayout.calculate(bounds);

        assertFalse(layout.isCompact());
        assertTrue(bounds.contains(layout.headerRect()));
        assertTrue(bounds.contains(layout.searchRect()));
        assertTrue(bounds.contains(layout.listRect()));
        assertTrue(bounds.contains(layout.detailRect()));
        assertTrue(bounds.contains(layout.forgetBtnRect()), "Forget button geometry must stay contained within bounds");

        assertTrue(layout.listRect().right() <= layout.detailRect().x(), "List and detail pane must not overlap");
        assertFalse(layout.listRect().intersects(layout.detailRect()));

        // Search controls (clear/filter/sort) and detail action row must all stay contained
        // and must never overlap the scrollable list/detail panes.
        assertTrue(bounds.contains(layout.clearBtnRect()));
        assertTrue(bounds.contains(layout.filterBtnRect()));
        assertTrue(bounds.contains(layout.sortBtnRect()));
        assertTrue(bounds.contains(layout.renameBtnRect()));
        assertTrue(bounds.contains(layout.copyBtnRect()));

        assertFalse(layout.listRect().intersects(layout.renameBtnRect()), "Action row must not overlap the list pane");
        assertFalse(layout.detailRect().intersects(layout.renameBtnRect()), "Action row must not overlap the detail pane");
        assertFalse(layout.detailRect().intersects(layout.copyBtnRect()));
        assertFalse(layout.detailRect().intersects(layout.forgetBtnRect()));

        assertTrue(layout.renameBtnRect().right() <= layout.copyBtnRect().x(), "Action buttons must not overlap each other");
        assertTrue(layout.copyBtnRect().right() <= layout.forgetBtnRect().x());
    }

    @Test
    @DisplayName("Should use a single full-width pane in compact mode")
    void testCompactLayoutSinglePane() {
        UiRect bounds = new UiRect(10, 10, 240, 180);
        KistorLayout layout = KistorLayout.calculate(bounds);

        assertTrue(layout.isCompact());
        assertEquals(bounds.width(), layout.listRect().width());
        assertEquals(bounds.width(), layout.detailRect().width());
        assertTrue(bounds.contains(layout.backBtnRect()));
    }

    @Test
    @DisplayName("Max list scroll should clamp to zero when content fits and be positive when it overflows")
    void testMaxListScrollClamping() {
        UiRect tallList = new UiRect(0, 0, 150, 1000);
        assertEquals(0, KistorTabComponent.calculateMaxListScroll(tallList, 3), "Everything fits - no scroll needed");

        UiRect shortList = new UiRect(0, 0, 150, 60);
        assertTrue(KistorTabComponent.calculateMaxListScroll(shortList, 20) > 0, "Overflowing content must allow positive scroll");

        assertEquals(0, KistorTabComponent.calculateMaxListScroll(shortList, 0));
    }

    @Test
    @DisplayName("A row rect extending outside the list viewport must not be considered inside it")
    void testOffscreenRowNotClickable() {
        UiRect listRect = new UiRect(50, 40, 150, 100);
        UiRect rowAboveViewport = new UiRect(52, 20, 146, 14);
        UiRect rowBelowViewport = new UiRect(52, 135, 146, 14);

        int clickAboveY = 25;
        int clickBelowY = 140;

        assertTrue(rowAboveViewport.contains(60, clickAboveY));
        assertFalse(listRect.contains(60, clickAboveY), "A row scrolled above the list viewport must not be clickable there");

        assertTrue(rowBelowViewport.contains(60, clickBelowY));
        assertFalse(listRect.contains(60, clickBelowY), "A row scrolled below the list viewport must not be clickable there");
    }

    @Test
    @DisplayName("Compact rows are shorter than normal rows, so compact scroll never under-estimates content height")
    void testCompactRowsShorterThanNormalRows() {
        UiRect list = new UiRect(0, 0, 150, 40);
        int normalScroll = KistorTabComponent.calculateMaxListScroll(list, 10, false);
        int compactScroll = KistorTabComponent.calculateMaxListScroll(list, 10, true);
        assertTrue(normalScroll >= compactScroll, "Taller normal rows must require at least as much scroll as compact rows for the same content");
    }

    @Test
    @DisplayName("Compact mode still exposes clear/filter/sort/action controls, fully contained")
    void testCompactControlsContained() {
        UiRect bounds = new UiRect(10, 10, 240, 180);
        KistorLayout layout = KistorLayout.calculate(bounds);

        assertTrue(bounds.contains(layout.clearBtnRect()));
        assertTrue(bounds.contains(layout.filterBtnRect()));
        assertTrue(bounds.contains(layout.sortBtnRect()));
        assertTrue(bounds.contains(layout.renameBtnRect()));
        assertTrue(bounds.contains(layout.copyBtnRect()));
        assertTrue(bounds.contains(layout.forgetBtnRect()));

        assertFalse(layout.listRect().intersects(layout.renameBtnRect()), "Action row must not overlap the single compact pane");
    }
}
