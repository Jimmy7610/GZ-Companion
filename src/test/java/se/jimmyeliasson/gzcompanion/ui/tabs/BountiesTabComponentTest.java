package se.jimmyeliasson.gzcompanion.ui.tabs;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import se.jimmyeliasson.gzcompanion.bounty.BountyStatus;
import se.jimmyeliasson.gzcompanion.ui.layout.BountyLayout;
import se.jimmyeliasson.gzcompanion.ui.layout.UiRect;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure, Font-free unit tests for {@link BountiesTabComponent}'s static layout/input helpers -
 * mirrors {@code LeaderboardsTabComponentTest}'s convention of testing UI logic directly without a
 * live Minecraft client.
 */
class BountiesTabComponentTest {

    // ------------------------------------------------------------------
    // clampIndex
    // ------------------------------------------------------------------

    @Test
    @DisplayName("clampIndex: zero entries always clamps to 0")
    void clampIndexZeroEntries() {
        assertEquals(0, BountiesTabComponent.clampIndex(0, 0));
        assertEquals(0, BountiesTabComponent.clampIndex(5, 0));
    }

    @Test
    @DisplayName("clampIndex: never negative, never past the last valid index")
    void clampIndexBounds() {
        assertEquals(0, BountiesTabComponent.clampIndex(-3, 5));
        assertEquals(4, BountiesTabComponent.clampIndex(10, 5));
        assertEquals(2, BountiesTabComponent.clampIndex(2, 5));
    }

    @Test
    @DisplayName("clampIndex: a single entry always clamps to index 0")
    void clampIndexSingleEntry() {
        assertEquals(0, BountiesTabComponent.clampIndex(0, 1));
        assertEquals(0, BountiesTabComponent.clampIndex(7, 1));
    }

    // ------------------------------------------------------------------
    // clampScroll
    // ------------------------------------------------------------------

    @Test
    @DisplayName("clampScroll: never negative")
    void clampScrollNeverNegative() {
        assertEquals(0, BountiesTabComponent.clampScroll(-10, 100, 50));
    }

    @Test
    @DisplayName("clampScroll: never past the point where empty space would appear below the last row")
    void clampScrollNeverPastMax() {
        assertEquals(50, BountiesTabComponent.clampScroll(1000, 100, 50));
    }

    @Test
    @DisplayName("clampScroll: content shorter than the viewport clamps to zero")
    void clampScrollContentShorterThanViewport() {
        assertEquals(0, BountiesTabComponent.clampScroll(20, 30, 100));
    }

    // ------------------------------------------------------------------
    // Empty-state messaging - "no active bounties" must never look like a failure
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Empty state: zero active bounties (a genuine success) is presented as INGA AKTIVA BOUNTIES, never an error")
    void emptyStateForGenuineZeroBounties() {
        assertEquals("INGA AKTIVA BOUNTIES", BountiesTabComponent.emptyStateHeadline(BountyStatus.LOADED));
        assertTrue(BountiesTabComponent.emptyStateBody(BountyStatus.LOADED).contains("ingen aktiv jakt"));
    }

    @Test
    @DisplayName("Empty state: STALE with no cached entries gets its OWN distinct wording - it must NEVER claim "
            + "'there is no active hunt right now', since the CURRENT state is unknown (the refresh that would "
            + "confirm it failed) and a bounty may have appeared since the last successful (empty) fetch")
    void emptyStateForStaleWithNoEntries() {
        String headline = BountiesTabComponent.emptyStateHeadline(BountyStatus.STALE);
        String body = BountiesTabComponent.emptyStateBody(BountyStatus.STALE);

        assertNotEquals("INGA AKTIVA BOUNTIES", headline, "STALE-empty must not reuse the genuine-current-zero headline");
        assertFalse(body.contains("ingen aktiv jakt just nu"),
                "STALE-empty must never assert there is currently no active hunt - that was only true as of the last successful fetch");
    }

    @Test
    @DisplayName("Empty state: LOADED with zero entries (a genuine, current, successful result) IS presented as "
            + "'no active hunt right now' - this is the one status allowed to say that")
    void emptyStateForLoadedIsGenuineCurrentZero() {
        assertEquals("INGA AKTIVA BOUNTIES", BountiesTabComponent.emptyStateHeadline(BountyStatus.LOADED));
        assertTrue(BountiesTabComponent.emptyStateBody(BountyStatus.LOADED).contains("ingen aktiv jakt just nu"));
    }

    @Test
    @DisplayName("Empty state: LOADING shows a loading headline, not the empty-success message")
    void emptyStateForLoading() {
        assertEquals("HÄMTAR BOUNTIES...", BountiesTabComponent.emptyStateHeadline(BountyStatus.LOADING));
        assertEquals("", BountiesTabComponent.emptyStateBody(BountyStatus.LOADING));
    }

    @Test
    @DisplayName("Empty state: UNAVAILABLE/ERROR/INCOMPATIBLE show a genuine failure message, distinct from the "
            + "empty-success one AND distinct from the STALE-empty wording")
    void emptyStateForRealFailures() {
        assertEquals("KUNDE INTE HÄMTA", BountiesTabComponent.emptyStateHeadline(BountyStatus.UNAVAILABLE));
        assertEquals("KUNDE INTE HÄMTA", BountiesTabComponent.emptyStateHeadline(BountyStatus.ERROR));
        assertEquals("OTILLGÄNGLIG", BountiesTabComponent.emptyStateHeadline(BountyStatus.INCOMPATIBLE));
        assertFalse(BountiesTabComponent.emptyStateBody(BountyStatus.UNAVAILABLE).contains("ingen aktiv jakt"));

        assertNotEquals(BountiesTabComponent.emptyStateHeadline(BountyStatus.UNAVAILABLE), BountiesTabComponent.emptyStateHeadline(BountyStatus.STALE));
        assertNotEquals(BountiesTabComponent.emptyStateHeadline(BountyStatus.INCOMPATIBLE), BountiesTabComponent.emptyStateHeadline(BountyStatus.STALE));
    }

    @Test
    @DisplayName("Empty state: all four distinct statuses (LOADED/STALE/UNAVAILABLE/INCOMPATIBLE) produce four distinct headlines")
    void emptyStateHeadlinesAreAllDistinctAcrossStatuses() {
        String loaded = BountiesTabComponent.emptyStateHeadline(BountyStatus.LOADED);
        String stale = BountiesTabComponent.emptyStateHeadline(BountyStatus.STALE);
        String unavailable = BountiesTabComponent.emptyStateHeadline(BountyStatus.UNAVAILABLE);
        String incompatible = BountiesTabComponent.emptyStateHeadline(BountyStatus.INCOMPATIBLE);

        assertEquals(4, java.util.Set.of(loaded, stale, unavailable, incompatible).size(),
                "each of these four states communicates a genuinely different fact and must not share wording");
    }

    // ------------------------------------------------------------------
    // rowIndexAt - click-to-row resolution
    // ------------------------------------------------------------------

    @Test
    @DisplayName("rowIndexAt: resolves the correct row for a click within bounds")
    void rowIndexAtResolvesCorrectRow() {
        UiRect listRect = new UiRect(0, 0, 100, 200);
        // rows start at listRect.y() + 2, each ROW_H (22) tall
        assertEquals(0, BountiesTabComponent.rowIndexAt(listRect, 5, 0, 5));
        assertEquals(1, BountiesTabComponent.rowIndexAt(listRect, 25, 0, 5));
        assertEquals(2, BountiesTabComponent.rowIndexAt(listRect, 47, 0, 5));
    }

    @Test
    @DisplayName("rowIndexAt: a click above the list (in padding) resolves to no row")
    void rowIndexAtAboveList() {
        UiRect listRect = new UiRect(0, 10, 100, 200);
        assertEquals(-1, BountiesTabComponent.rowIndexAt(listRect, 0, 0, 5));
    }

    @Test
    @DisplayName("rowIndexAt: a click past the last entry resolves to no row")
    void rowIndexAtPastLastEntry() {
        UiRect listRect = new UiRect(0, 0, 100, 200);
        assertEquals(-1, BountiesTabComponent.rowIndexAt(listRect, 500, 0, 3));
    }

    @Test
    @DisplayName("rowIndexAt: accounts for scroll offset correctly")
    void rowIndexAtAccountsForScroll() {
        UiRect listRect = new UiRect(0, 0, 100, 200);
        // Scrolled down by one full row - what was row 1 at scroll=0 is now at the top.
        assertEquals(1, BountiesTabComponent.rowIndexAt(listRect, 2, 22, 5));
    }

    @Test
    @DisplayName("rowIndexAt: zero entries never resolves to a row")
    void rowIndexAtZeroEntries() {
        UiRect listRect = new UiRect(0, 0, 100, 200);
        assertEquals(-1, BountiesTabComponent.rowIndexAt(listRect, 10, 0, 0));
    }

    // ------------------------------------------------------------------
    // BountyLayout - wide vs. compact
    // ------------------------------------------------------------------

    @Test
    @DisplayName("BountyLayout: a narrow content area (e.g. ~700x450 window minus sidebar) uses compact single-pane layout")
    void layoutIsCompactBelowBreakpoint() {
        BountyLayout layout = BountyLayout.calculate(new UiRect(0, 0, 250, 300));
        assertTrue(layout.isCompact());
        assertEquals(layout.listRect(), layout.detailRect(), "compact mode shares one body rect toggled between list/detail");
    }

    @Test
    @DisplayName("BountyLayout: a wide content area uses the list+detail split layout")
    void layoutIsWideAboveBreakpoint() {
        BountyLayout layout = BountyLayout.calculate(new UiRect(0, 0, 500, 300));
        assertFalse(layout.isCompact());
        assertNotEquals(layout.listRect(), layout.detailRect());
        assertTrue(layout.listRect().x() < layout.detailRect().x(), "list must be to the left of detail");
    }

    @Test
    @DisplayName("BountyLayout: list and detail panes never overlap in wide mode")
    void layoutPanesDoNotOverlapWide() {
        BountyLayout layout = BountyLayout.calculate(new UiRect(0, 0, 600, 400));
        assertTrue(layout.listRect().right() <= layout.detailRect().x());
    }
}
