package se.jimmyeliasson.gzcompanion.ui.layout;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** The update panel's action row is fixed under the title, above everything that can scroll. */
class UpdatePanelLayoutTest {

    static Stream<UiRect> sizes() {
        return Stream.of(new UiRect(0, 0, 200, 150), new UiRect(10, 20, 260, 180), new UiRect(90, 40, 360, 220), new UiRect(0, 0, 620, 320));
    }

    @ParameterizedTest
    @MethodSource("sizes")
    @DisplayName("Title, then the fixed action row, then the separator, then the scrollable content - no overlaps, all inside the card")
    void orderingAndContainment(UiRect bounds) {
        UpdatePanelLayout l = UpdatePanelLayout.calculate(bounds);
        UiRect card = l.card();
        assertTrue(bounds.contains(card));
        for (UiRect r : new UiRect[]{l.closeBtn(), l.actionRow(), l.primaryBtn(), l.secondaryBtn(), l.contentViewport()}) {
            assertTrue(card.contains(r), "Inside the card: " + r + " for " + bounds);
        }
        assertEquals(l.titleY() + UpdatePanelLayout.TITLE_H, l.actionRow().y(), "Action row sits directly under the title");
        assertEquals(l.actionRow().y(), l.primaryBtn().y());
        assertEquals(l.actionRow().y(), l.secondaryBtn().y());
        assertTrue(l.actionRow().contains(l.primaryBtn()));
        assertTrue(l.actionRow().contains(l.secondaryBtn()));
        assertFalse(l.primaryBtn().intersects(l.secondaryBtn()), "Buttons never overlap");
        assertFalse(l.closeBtn().intersects(l.actionRow()), "Close button never overlaps the actions");
        assertTrue(l.separatorY() >= l.actionRow().bottom(), "Separator below the actions");
        assertTrue(l.contentViewport().y() > l.separatorY(), "Content starts below the separator");
        assertFalse(l.contentViewport().intersects(l.actionRow()), "Scrollable content can never cover the actions");
        assertTrue(l.contentViewport().height() > 0);
        assertTrue(l.primaryBtn().width() >= 60, "Primary label has room: " + l.primaryBtn().width());
        assertTrue(l.secondaryBtn().width() >= 40, "Secondary label has room: " + l.secondaryBtn().width());
    }

    @Test
    @DisplayName("The action row position depends only on the card, never on content length")
    void actionRowIndependentOfContent() {
        UiRect bounds = new UiRect(0, 0, 360, 220);
        UpdatePanelLayout a = UpdatePanelLayout.calculate(bounds);
        UpdatePanelLayout b = UpdatePanelLayout.calculate(bounds);
        assertEquals(a, b);
        assertTrue(a.actionRow().bottom() < bounds.y() + 50, "Buttons are near the top of the panel, always visible");
    }

    @Test
    @DisplayName("Button widths are capped on wide cards and still fit on narrow ones")
    void buttonWidths() {
        UpdatePanelLayout wide = UpdatePanelLayout.calculate(new UiRect(0, 0, 620, 320));
        assertEquals(UpdatePanelLayout.PRIMARY_MAX_W, wide.primaryBtn().width());
        assertEquals(UpdatePanelLayout.SECONDARY_MAX_W, wide.secondaryBtn().width());
        UpdatePanelLayout narrow = UpdatePanelLayout.calculate(new UiRect(0, 0, 200, 150));
        assertTrue(narrow.secondaryBtn().right() <= narrow.actionRow().right());
    }
}
