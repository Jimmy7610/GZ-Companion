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
}
