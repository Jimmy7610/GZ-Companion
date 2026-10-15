package se.jimmyeliasson.gzcompanion.ui.layout;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.DisplayName;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** The Events tab layout at every supported Companion size, from the real MainScreenLayout content area. */
class EventsLayoutTest {
    /** GUI-scaled screens covering the 320x240 minimum, COMPACT, NORMAL and LARGE. */
    static Stream<int[]> screens() {
        return Stream.of(new int[]{320, 240}, new int[]{427, 240}, new int[]{480, 270}, new int[]{569, 320},
                new int[]{640, 360}, new int[]{854, 480}, new int[]{960, 540}, new int[]{1280, 720});
    }

    @ParameterizedTest
    @MethodSource("screens")
    @DisplayName("Header, next-event card, list and footer stack inside the content area without overlapping")
    void fitsAndDoesNotOverlap(int[] screen) {
        UiRect content = MainScreenLayout.calculate(screen[0], screen[1]).contentRect();
        EventsLayout l = EventsLayout.calculate(content);
        String c = screen[0] + "x" + screen[1];
        for (UiRect r : List.of(l.headerRect(), l.nextCardRect(), l.listRect(), l.bodyRect(), l.footerRect(), l.footerTextRect(), l.refreshBtnRect())) {
            assertTrue(content.contains(r), r + " inside " + content + " " + c);
        }
        List<UiRect> stack = List.of(l.headerRect(), l.nextCardRect(), l.listRect(), l.footerRect());
        for (int i = 0; i + 1 < stack.size(); i++) {
            assertTrue(stack.get(i).bottom() <= stack.get(i + 1).y(), "Row " + i + " above row " + (i + 1) + " " + c);
        }
        assertFalse(l.footerTextRect().intersects(l.refreshBtnRect()), "Status text never runs under Uppdatera " + c);
        assertEquals(l.nextCardRect().y(), l.bodyRect().y(), c);
        assertEquals(l.listRect().bottom(), l.bodyRect().bottom(), c);
        // At least a day heading and four event rows are visible before scrolling, even at 320x240.
        assertTrue(l.listRect().height() >= 12 + 4 * 11 + 6, "List height " + l.listRect().height() + " " + c);
        assertTrue(l.refreshBtnRect().width() >= 50, "Uppdatera stays readable " + c);
        assertTrue(l.footerTextRect().width() >= 120, "Room for 'Visar senast hämtade schema' " + c);
    }
}
