package se.jimmyeliasson.gzcompanion.ui.tabs;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers selection repair after the entry list changes (a recipe-book cache refresh, a search
 * term, or a mode switch narrowing the list) - a stale {@code selectedEntryId} must never be left
 * pointing at an entry that can no longer render a detail pane.
 */
class CraftingTabSelectionTest {

    @Test
    @DisplayName("Selection is kept when it's still present in the (possibly narrowed) entry list")
    void testSelectionKeptWhenStillPresent() {
        List<CraftingTabComponent.ListEntry> entries = List.of(
                new CraftingTabComponent.ListEntry(null, "a", "A", "meta", 0),
                new CraftingTabComponent.ListEntry(null, "b", "B", "meta", 0)
        );
        String result = CraftingTabComponent.resolveSelectionAfterListChange("b", entries);
        assertEquals("b", result);
    }

    @Test
    @DisplayName("A stale selection (no longer in the list) falls back to the first currently-visible result")
    void testStaleSelectionFallsBackToFirstResult() {
        List<CraftingTabComponent.ListEntry> entries = List.of(
                new CraftingTabComponent.ListEntry(null, "x", "X", "meta", 0),
                new CraftingTabComponent.ListEntry(null, "y", "Y", "meta", 0)
        );
        String result = CraftingTabComponent.resolveSelectionAfterListChange("stale-id-not-in-list", entries);
        assertEquals("x", result, "Must fall back to the first visible result, respecting the current list order (search/filter already applied)");
    }

    @Test
    @DisplayName("A null selection with a non-empty list selects the first result")
    void testNullSelectionSelectsFirstResult() {
        List<CraftingTabComponent.ListEntry> entries = List.of(new CraftingTabComponent.ListEntry(null, "a", "A", "meta", 0));
        assertEquals("a", CraftingTabComponent.resolveSelectionAfterListChange(null, entries));
    }

    @Test
    @DisplayName("An empty entry list (e.g. search produced zero results) clears the selection deterministically")
    void testEmptyListClearsSelection() {
        assertNull(CraftingTabComponent.resolveSelectionAfterListChange("anything", List.of()));
        assertNull(CraftingTabComponent.resolveSelectionAfterListChange(null, List.of()));
    }

    @Test
    @DisplayName("calculateDetailMaxScroll returns 0 when nothing is selected")
    void testMaxScrollZeroWhenNoSelection() {
        CraftingTabComponent tab = new CraftingTabComponent();
        tab.setSelectedEntryIdForTesting(null);
        int maxScroll = tab.calculateDetailMaxScroll(null, new se.jimmyeliasson.gzcompanion.ui.layout.UiRect(0, 0, 100, 100), null, null);
        assertEquals(0, maxScroll);
    }

    @Test
    @DisplayName("calculateDetailMaxScroll returns 0 when the selected id can no longer be resolved to any entry")
    void testMaxScrollZeroWhenSelectionUnresolvable() {
        CraftingTabComponent tab = new CraftingTabComponent();
        tab.setSelectedEntryIdForTesting("gz:does-not-exist");
        int maxScroll = tab.calculateDetailMaxScroll(null, new se.jimmyeliasson.gzcompanion.ui.layout.UiRect(0, 0, 100, 100), null, null);
        assertEquals(0, maxScroll, "craftingBase is null (module unavailable) - the gz: branch can't resolve anything and must return 0, not throw");
    }

    @Test
    @DisplayName("calculateDetailMaxScroll returns 0 for a stale client recipe key no longer in the cache")
    void testMaxScrollZeroForStaleClientKey() {
        CraftingTabComponent tab = new CraftingTabComponent();
        tab.setSelectedEntryIdForTesting("client:recipe#999");
        int maxScroll = tab.calculateDetailMaxScroll(null, new se.jimmyeliasson.gzcompanion.ui.layout.UiRect(0, 0, 100, 100), null, null);
        assertEquals(0, maxScroll);
    }
}
