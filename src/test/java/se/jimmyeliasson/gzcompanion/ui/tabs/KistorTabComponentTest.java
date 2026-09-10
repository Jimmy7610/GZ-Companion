package se.jimmyeliasson.gzcompanion.ui.tabs;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import se.jimmyeliasson.gzcompanion.ui.layout.KistorLayout;
import se.jimmyeliasson.gzcompanion.ui.layout.UiRect;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Human QA blocker: a legitimately-opened chest with more than five distinct item types could
 * not be scrolled in the compact chest detail pane. Root cause was shared with Byggplaner/
 * Crafting - in compact mode, {@code listRect()} and {@code detailRect()} are the SAME
 * rectangle (only one pane renders at a time), so {@code mouseScrolled} used to always resolve
 * to the list branch first, silently eating every scroll event meant for the detail pane.
 *
 * <p>No selected container is used here (only the routing decision is under test) - resolving an
 * actual container requires a live {@code CompanionSession}/{@code ChestManager}, which is not
 * safe to construct in a headless unit test.
 */
class KistorTabComponentTest {

    private static final UiRect COMPACT_BOUNDS = new UiRect(0, 0, 200, 300);

    @Test
    @DisplayName("Compact layout aliases list and detail rects - the precondition the routing fix guards against")
    void compactLayoutAliasesListAndDetailRects() {
        KistorLayout layout = KistorLayout.calculate(COMPACT_BOUNDS);
        assertTrue(layout.isCompact());
        assertEquals(layout.listRect(), layout.detailRect());
    }

    @Test
    @DisplayName("Scrolling while the compact detail pane is showing must never fall through to the hidden list's scroll branch")
    void compactDetailScrollNeverMovesListOffset() {
        KistorTabComponent tab = new KistorTabComponent();
        tab.setCompactStateForTesting(COMPACT_BOUNDS, true, null);

        boolean handled = tab.mouseScrolled(100, 150, 0, -1);

        // With nothing selected, the detail pane has nothing to scroll - the old bug would still
        // have reached the list branch here (since listRect()==detailRect() in compact mode) and
        // scrolled the hidden list instead.
        assertFalse(handled);
        assertEquals(0, tab.listScrollOffsetForTesting(),
                "The list pane is hidden behind the detail pane in compact mode and must never scroll instead of it.");
    }
}
