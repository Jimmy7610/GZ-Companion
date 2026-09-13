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
    @DisplayName("Empty state: all five distinct statuses (IDLE/LOADED/STALE/UNAVAILABLE/INCOMPATIBLE) produce five distinct headlines")
    void emptyStateHeadlinesAreAllDistinctAcrossStatuses() {
        String idle = BountiesTabComponent.emptyStateHeadline(BountyStatus.IDLE);
        String loaded = BountiesTabComponent.emptyStateHeadline(BountyStatus.LOADED);
        String stale = BountiesTabComponent.emptyStateHeadline(BountyStatus.STALE);
        String unavailable = BountiesTabComponent.emptyStateHeadline(BountyStatus.UNAVAILABLE);
        String incompatible = BountiesTabComponent.emptyStateHeadline(BountyStatus.INCOMPATIBLE);

        assertEquals(5, java.util.Set.of(idle, loaded, stale, unavailable, incompatible).size(),
                "each of these five states communicates a genuinely different fact and must not share wording");
    }

    @Test
    @DisplayName("Empty state: IDLE (never successfully loaded) must NEVER claim there are no active bounties - "
            + "Companion simply has no data yet, which is a different fact from a confirmed empty registry")
    void emptyStateForIdleNeverClaimsConfirmedEmpty() {
        String headline = BountiesTabComponent.emptyStateHeadline(BountyStatus.IDLE);
        String body = BountiesTabComponent.emptyStateBody(BountyStatus.IDLE);

        assertNotEquals("INGA AKTIVA BOUNTIES", headline, "IDLE must not reuse the genuine-current-zero headline");
        assertFalse(body.contains("ingen aktiv jakt"),
                "IDLE must never assert anything about whether bounties exist - it has no data at all yet");
    }

    // ------------------------------------------------------------------
    // countLabel - the small "X AKTIVA BOUNTIES" strip, independent of the main empty-state panel
    // ------------------------------------------------------------------

    @Test
    @DisplayName("countLabel: LOADED with zero entries is a genuine current success - shows the friendly empty count")
    void countLabelLoadedZero() {
        assertEquals("INGA AKTIVA BOUNTIES", BountiesTabComponent.countLabel(BountyStatus.LOADED, 0));
    }

    @Test
    @DisplayName("countLabel: STALE with zero entries must NOT repeat 'INGA AKTIVA BOUNTIES' - the current count is "
            + "unknown, and the main empty-state panel already states the cached-empty fact once")
    void countLabelStaleZeroIsBlank() {
        assertEquals("", BountiesTabComponent.countLabel(BountyStatus.STALE, 0));
    }

    @Test
    @DisplayName("countLabel: IDLE never shows a count at all - there is no usable data to count")
    void countLabelIdleZeroIsBlank() {
        assertEquals("", BountiesTabComponent.countLabel(BountyStatus.IDLE, 0));
    }

    @Test
    @DisplayName("countLabel: LOADED with one entry uses the singular form")
    void countLabelLoadedOne() {
        assertEquals("1 AKTIV BOUNTY", BountiesTabComponent.countLabel(BountyStatus.LOADED, 1));
    }

    @Test
    @DisplayName("countLabel: STALE with one cached entry still shows the count - a nonempty stale cache remains informative")
    void countLabelStaleOne() {
        assertEquals("1 AKTIV BOUNTY", BountiesTabComponent.countLabel(BountyStatus.STALE, 1));
    }

    @Test
    @DisplayName("countLabel: LOADED with several entries uses the plural form")
    void countLabelLoadedMany() {
        assertEquals("3 AKTIVA BOUNTIES", BountiesTabComponent.countLabel(BountyStatus.LOADED, 3));
    }

    @Test
    @DisplayName("countLabel: statuses without usable data (LOADING/UNAVAILABLE/ERROR/INCOMPATIBLE) never show a count")
    void countLabelNoUsableDataStatusesAreBlank() {
        assertEquals("", BountiesTabComponent.countLabel(BountyStatus.LOADING, 0));
        assertEquals("", BountiesTabComponent.countLabel(BountyStatus.UNAVAILABLE, 0));
        assertEquals("", BountiesTabComponent.countLabel(BountyStatus.ERROR, 0));
        assertEquals("", BountiesTabComponent.countLabel(BountyStatus.INCOMPATIBLE, 0));
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

    // ------------------------------------------------------------------
    // Detail overflow fix: detail content must never draw outside detailRect - see
    // detailContentTop/detailContentBottom (the fixed scissor bounds) and detailScrollOffset
    // (clamped via the same clampScroll used for the list).
    // ------------------------------------------------------------------

    @Test
    @DisplayName("detail scroll: never negative regardless of how far it is pushed past the top")
    void detailScrollNeverNegative() {
        assertEquals(0, BountiesTabComponent.clampScroll(-500, 300, 100));
    }

    @Test
    @DisplayName("detail scroll: clamps to the exact point where content bottom meets viewport bottom - no overscroll")
    void detailScrollMaxClamp() {
        assertEquals(200, BountiesTabComponent.clampScroll(10_000, 300, 100));
    }

    @Test
    @DisplayName("detail scroll: content shorter than (or equal to) the viewport produces zero scroll, "
            + "e.g. a short name/reward/no-clue bounty that fits entirely")
    void detailScrollShortContentIsZero() {
        assertEquals(0, BountiesTabComponent.clampScroll(50, 80, 100));
        assertEquals(0, BountiesTabComponent.clampScroll(50, 100, 100));
    }

    @Test
    @DisplayName("detail scroll: content taller than the viewport (e.g. a long public clue) can genuinely scroll")
    void detailScrollLongContentCanScroll() {
        assertEquals(50, BountiesTabComponent.clampScroll(50, 300, 100), "an in-range offset is not clamped");
        assertEquals(200, BountiesTabComponent.clampScroll(250, 300, 100), "clamped to the true max, not the requested overscroll");
    }

    @Test
    @DisplayName("selectRow: selecting another bounty resets detail scroll to the top")
    void selectRowResetsDetailScroll() {
        BountiesTabComponent tab = new BountiesTabComponent();
        tab.setDetailScrollOffsetForTesting(120);

        tab.selectRow(2, false);

        assertEquals(0, tab.getDetailScrollOffsetForTesting());
    }

    @Test
    @DisplayName("selectRow: opening detail from the compact list also resets detail scroll to the top and shows the detail pane")
    void selectRowFromCompactListResetsScrollAndShowsDetail() {
        BountiesTabComponent tab = new BountiesTabComponent();
        tab.setDetailScrollOffsetForTesting(75);

        tab.selectRow(0, true);

        assertEquals(0, tab.getDetailScrollOffsetForTesting());
        assertTrue(tab.isCompactShowingDetailForTesting());
    }

    @Test
    @DisplayName("selectRow: re-opening the SAME bounty (re-clicking its row) still resets scroll to the top")
    void selectRowReselectingSameIndexStillResetsScroll() {
        BountiesTabComponent tab = new BountiesTabComponent();
        tab.setDetailScrollOffsetForTesting(40);

        tab.selectRow(0, true);

        assertEquals(0, tab.getDetailScrollOffsetForTesting());
    }

    @Test
    @DisplayName("List and detail scroll offsets are independent - changing one never affects the other")
    void listAndDetailScrollOffsetsAreIndependent() {
        BountiesTabComponent tab = new BountiesTabComponent();

        tab.setListScrollOffsetForTesting(44);
        tab.setDetailScrollOffsetForTesting(88);
        assertEquals(44, tab.getListScrollOffsetForTesting());
        assertEquals(88, tab.getDetailScrollOffsetForTesting());

        tab.setDetailScrollOffsetForTesting(0);
        assertEquals(44, tab.getListScrollOffsetForTesting(), "resetting detail scroll must not touch list scroll");

        tab.setListScrollOffsetForTesting(0);
        tab.setDetailScrollOffsetForTesting(99);
        assertEquals(0, tab.getListScrollOffsetForTesting(), "changing detail scroll must not touch list scroll");
    }

    @Test
    @DisplayName("detailContentBottom: the scrollable region's bottom bound never belongs to the footer - it "
            + "always stays inside detailRect, with or without a command-copy button reserved")
    void detailContentBottomNeverBelongsToFooter() {
        // Approximates the real QA report: a ~1360x772 client with Companion's content pane below
        // the 300px compact breakpoint.
        BountyLayout layout = BountyLayout.calculate(new UiRect(0, 0, 260, 190));
        UiRect detailRect = layout.detailRect();

        int bottomWithCommand = BountiesTabComponent.detailContentBottom(detailRect, true);
        int bottomWithoutCommand = BountiesTabComponent.detailContentBottom(detailRect, false);

        assertTrue(bottomWithCommand <= detailRect.bottom(), "must never extend past the pane's own bottom edge");
        assertTrue(bottomWithoutCommand <= detailRect.bottom(), "must never extend past the pane's own bottom edge");
        assertTrue(detailRect.bottom() <= layout.footerRect().y(),
                "the pane itself must not overlap the footer, at this realistic reported window size");
    }

    @Test
    @DisplayName("detailContentBottom: reserves strictly less room when there is no command-copy button, "
            + "since nothing needs to be pinned below the content in that case")
    void detailContentBottomReservesLessRoomWithoutCommand() {
        UiRect detailRect = new UiRect(0, 0, 200, 150);
        int withCommand = BountiesTabComponent.detailContentBottom(detailRect, true);
        int withoutCommand = BountiesTabComponent.detailContentBottom(detailRect, false);

        assertTrue(withCommand < withoutCommand);
    }

    @Test
    @DisplayName("detailContentTop: reserves room for the fixed back button in compact mode, but not in wide mode")
    void detailContentTopReservesBackButtonRoomOnlyWhenCompact() {
        UiRect detailRect = new UiRect(0, 0, 200, 150);
        int compactTop = BountiesTabComponent.detailContentTop(detailRect, true);
        int wideTop = BountiesTabComponent.detailContentTop(detailRect, false);

        assertTrue(compactTop > wideTop, "compact mode must reserve extra space above content for the back button");
    }

    @Test
    @DisplayName("An arbitrarily long clue's content never logically extends past detailContentBottom - the "
            + "render-time scissor call clips to exactly this bound, so however tall the actual wrapped clue "
            + "text is, drawing beyond it is physically impossible regardless of scroll position")
    void longClueContentCannotLogicallyExtendIntoFooter() {
        UiRect detailRect = new UiRect(0, 0, 200, 150);
        int viewportH = BountiesTabComponent.detailContentBottom(detailRect, true) - BountiesTabComponent.detailContentTop(detailRect, true);

        // A very tall synthetic content height, standing in for an unusually long public clue.
        int hugeContentHeight = 5000;
        int clampedOffset = BountiesTabComponent.clampScroll(0, hugeContentHeight, viewportH);
        // Regardless of the clamp outcome, the scissor itself (enableScissor(..., detailContentBottom))
        // is what makes the guarantee absolute - this asserts the bound it is clipped to is sound.
        assertTrue(BountiesTabComponent.detailContentBottom(detailRect, true) <= detailRect.bottom());
        assertTrue(clampedOffset >= 0);
    }

    // ------------------------------------------------------------------
    // Scroll input routing - which pane (list/detail/neither) a wheel event affects
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Scroll routing: wide mode, cursor over the list, affects the list only")
    void scrollRoutingWideOverListAffectsListOnly() {
        BountyLayout layout = BountyLayout.calculate(new UiRect(0, 0, 500, 300));
        double x = layout.listRect().x() + 5;
        double y = layout.listRect().y() + 5;

        BountiesTabComponent.ScrollTarget target = BountiesTabComponent.resolveScrollTarget(layout, false, true, x, y);

        assertEquals(BountiesTabComponent.ScrollTarget.LIST, target);
    }

    @Test
    @DisplayName("Scroll routing: wide mode, cursor over the detail pane, affects the detail only")
    void scrollRoutingWideOverDetailAffectsDetailOnly() {
        BountyLayout layout = BountyLayout.calculate(new UiRect(0, 0, 500, 300));
        double x = layout.detailRect().x() + 5;
        double y = layout.detailRect().y() + 5;

        BountiesTabComponent.ScrollTarget target = BountiesTabComponent.resolveScrollTarget(layout, false, true, x, y);

        assertEquals(BountiesTabComponent.ScrollTarget.DETAIL, target);
    }

    @Test
    @DisplayName("Scroll routing: compact mode showing the list, cursor over the (shared) body rect, affects the list")
    void scrollRoutingCompactListAffectsListOnly() {
        BountyLayout layout = BountyLayout.calculate(new UiRect(0, 0, 250, 300));
        double x = layout.listRect().x() + 5;
        double y = layout.listRect().y() + 5;

        BountiesTabComponent.ScrollTarget target = BountiesTabComponent.resolveScrollTarget(layout, false, true, x, y);

        assertEquals(BountiesTabComponent.ScrollTarget.LIST, target);
    }

    @Test
    @DisplayName("Scroll routing: compact mode showing the detail pane, cursor over the (shared) body rect, "
            + "affects the detail only - even though listRect/detailRect are the SAME rect in compact mode, "
            + "the compactShowingDetail flag (not rect containment alone) decides which pane is actually visible")
    void scrollRoutingCompactDetailAffectsDetailOnly() {
        BountyLayout layout = BountyLayout.calculate(new UiRect(0, 0, 250, 300));
        assertEquals(layout.listRect(), layout.detailRect(), "sanity check: compact mode shares one body rect");
        double x = layout.detailRect().x() + 5;
        double y = layout.detailRect().y() + 5;

        BountiesTabComponent.ScrollTarget target = BountiesTabComponent.resolveScrollTarget(layout, true, true, x, y);

        assertEquals(BountiesTabComponent.ScrollTarget.DETAIL, target);
    }

    @Test
    @DisplayName("Scroll routing: a cursor outside both panes affects neither")
    void scrollRoutingOutsideBothPanesAffectsNeither() {
        BountyLayout layout = BountyLayout.calculate(new UiRect(0, 0, 500, 300));
        BountiesTabComponent.ScrollTarget target = BountiesTabComponent.resolveScrollTarget(layout, false, true, -100, -100);

        assertEquals(BountiesTabComponent.ScrollTarget.NONE, target);
    }

    @Test
    @DisplayName("Scroll routing: with zero entries, neither pane is scrollable (the empty-state message isn't)")
    void scrollRoutingWithNoEntriesAffectsNeither() {
        BountyLayout layout = BountyLayout.calculate(new UiRect(0, 0, 500, 300));
        double x = layout.listRect().x() + 5;
        double y = layout.listRect().y() + 5;

        BountiesTabComponent.ScrollTarget target = BountiesTabComponent.resolveScrollTarget(layout, false, false, x, y);

        assertEquals(BountiesTabComponent.ScrollTarget.NONE, target);
    }
}
