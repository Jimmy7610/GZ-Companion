package se.jimmyeliasson.gzcompanion.ui.tabs;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import se.jimmyeliasson.gzcompanion.leaderboard.LeaderboardEntry;
import se.jimmyeliasson.gzcompanion.ui.layout.UiRect;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure layout/logic tests for {@link LeaderboardsTabComponent} - the row-height policy, DU-highlight
 * matching, selector wraparound, and scroll clamp are all static and Font-free (see
 * {@code MarketWatchTabComponentTest} for this project's established convention of extracting such
 * logic so it's testable without a live Minecraft {@code Font}/{@code GuiGraphicsExtractor}).
 */
class LeaderboardsTabComponentTest {
    private static final LeaderboardEntry SIMPLE = LeaderboardEntry.of(1, "Alfa", "1 coins", "Coins");
    private static final LeaderboardEntry WITH_SECONDARY = new LeaderboardEntry(1, "Alfa", "5 bounties", "Bounties", "100 Coins", "");

    @Test
    @DisplayName("Rank #1 gets the tallest podium row height")
    void rank1IsTallestPodium() {
        int rank1 = LeaderboardsTabComponent.rowHeightFor(0, SIMPLE);
        int rank2 = LeaderboardsTabComponent.rowHeightFor(1, SIMPLE);
        int rank4 = LeaderboardsTabComponent.rowHeightFor(3, SIMPLE);
        assertTrue(rank1 > rank2, "#1 must be taller than #2/#3");
        assertTrue(rank2 > rank4, "#2/#3 must be taller than a compact #4-10 row");
    }

    @Test
    @DisplayName("Ranks #2 and #3 share the same podium row height, distinct from #1 and from compact rows")
    void ranks2And3ShareHeight() {
        assertEquals(LeaderboardsTabComponent.rowHeightFor(1, SIMPLE), LeaderboardsTabComponent.rowHeightFor(2, SIMPLE));
    }

    @Test
    @DisplayName("Compact rows (#4-10) with a secondary value get extra height for the second line")
    void compactRowWithSecondaryValueIsTaller() {
        int withoutSecondary = LeaderboardsTabComponent.rowHeightFor(4, SIMPLE);
        int withSecondary = LeaderboardsTabComponent.rowHeightFor(4, WITH_SECONDARY);
        assertTrue(withSecondary > withoutSecondary);
    }

    @Test
    @DisplayName("DU highlight matches the local player's name case-insensitively")
    void duHighlightMatchesCaseInsensitively() {
        LeaderboardEntry entry = LeaderboardEntry.of(1, "JagHatarKul", "1 coins", "Coins");
        assertTrue(LeaderboardsTabComponent.isLocalPlayerRow(entry, "jaghatarkul"));
        assertTrue(LeaderboardsTabComponent.isLocalPlayerRow(entry, "JAGHATARKUL"));
    }

    @Test
    @DisplayName("DU highlight never matches a different name, or a null/blank local username")
    void duHighlightDoesNotFalsePositive() {
        LeaderboardEntry entry = LeaderboardEntry.of(1, "JagHatarKul", "1 coins", "Coins");
        assertFalse(LeaderboardsTabComponent.isLocalPlayerRow(entry, "SomeoneElse"));
        assertFalse(LeaderboardsTabComponent.isLocalPlayerRow(entry, null));
        assertFalse(LeaderboardsTabComponent.isLocalPlayerRow(entry, ""));
        assertFalse(LeaderboardsTabComponent.isLocalPlayerRow(entry, "   "));
    }

    @Test
    @DisplayName("Cycling right from the last board wraps around to the first")
    void cycleRightWrapsFromLastToFirst() {
        assertEquals(0, LeaderboardsTabComponent.nextCycleIndex(9, 10, 1));
    }

    @Test
    @DisplayName("Cycling left from the first board wraps around to the last")
    void cycleLeftWrapsFromFirstToLast() {
        assertEquals(9, LeaderboardsTabComponent.nextCycleIndex(0, 10, -1));
    }

    @Test
    @DisplayName("Cycling in the middle of the list moves by exactly one in the requested direction")
    void cycleMovesByOneInMiddle() {
        assertEquals(5, LeaderboardsTabComponent.nextCycleIndex(4, 10, 1));
        assertEquals(3, LeaderboardsTabComponent.nextCycleIndex(4, 10, -1));
    }

    @Test
    @DisplayName("Scroll offset never goes negative")
    void scrollNeverNegative() {
        assertEquals(0, LeaderboardsTabComponent.clampScroll(-50, 200, 100));
    }

    @Test
    @DisplayName("Scroll offset is clamped so the list never scrolls past its actual content")
    void scrollClampedToContentHeight() {
        int clamped = LeaderboardsTabComponent.clampScroll(1000, 150, 100);
        assertEquals(50, clamped); // 150 total - 100 viewport = 50 max scroll
    }

    @Test
    @DisplayName("Content that fits entirely within the viewport needs no scroll at all")
    void contentFittingViewportNeedsNoScroll() {
        assertEquals(0, LeaderboardsTabComponent.clampScroll(30, 80, 100));
    }

    // ------------------------------------------------------------------
    // Blocker 1 (2026-09-13 follow-up review): picker overlay input priority.
    //
    // The picker used to share ONE hitTargets list with every underlying control, and
    // mouseClicked() checked that list in INSERTION order - since the selector's own hit rect was
    // registered before the picker's (renderSelector runs before renderPicker each frame), a click
    // on a picker row that visually overlapped the selector could be swallowed by the selector's
    // hit rect first, even though the picker row was drawn ON TOP of it. resolveClick() is the pure
    // decision point that now makes this impossible: while the picker is open, ONLY picker rows can
    // ever be hit, regardless of what rect a normal control occupies at that same point.
    // ------------------------------------------------------------------

    @Test
    @DisplayName("A picker row overlapping the selector's rect does NOT activate the selector - the picker row wins")
    void firstPickerRowDoesNotActivateUnderlyingCenterSelector() {
        UiRect selectorCenterRect = new UiRect(20, 40, 100, 18); // e.g. the real selector's center dropdown trigger
        UiRect pickerRow0 = new UiRect(20, 40, 100, 12); // the picker's first row, drawn directly over the selector

        var resolution = LeaderboardsTabComponent.resolveClick(
                true, List.of(pickerRow0), List.of(selectorCenterRect), 30, 45);

        assertInstanceOf(LeaderboardsTabComponent.ClickResolution.PickerRow.class, resolution,
                "a click inside the overlapping region must resolve to the picker row, never the selector underneath");
        assertEquals(0, ((LeaderboardsTabComponent.ClickResolution.PickerRow) resolution).index());
    }

    @Test
    @DisplayName("A picker row overlapping the selector resolves to the CORRECT row index, even when several rows overlap different underlying controls")
    void pickerRowOverlappingSelectorSelectsIntendedDefinition() {
        UiRect selectorCenterRect = new UiRect(20, 40, 100, 18);
        UiRect leftArrowRect = new UiRect(4, 40, 16, 18);
        List<UiRect> normalRects = List.of(leftArrowRect, selectorCenterRect);
        UiRect row0 = new UiRect(20, 40, 100, 12);
        UiRect row1 = new UiRect(20, 52, 100, 12);
        List<UiRect> pickerRects = List.of(row0, row1);

        var resolution = LeaderboardsTabComponent.resolveClick(true, pickerRects, normalRects, 25, 55);

        assertInstanceOf(LeaderboardsTabComponent.ClickResolution.PickerRow.class, resolution);
        assertEquals(1, ((LeaderboardsTabComponent.ClickResolution.PickerRow) resolution).index(), "row 1, not row 0 or the underlying selector, must be resolved");
    }

    @Test
    @DisplayName("Clicking outside every picker row (but still on the tab) closes the picker without activating anything underneath")
    void clickOutsidePickerClosesWithoutActivatingUnderlyingControl() {
        UiRect groupTabRect = new UiRect(0, 0, 50, 16); // some normal control far from the picker
        UiRect pickerRow0 = new UiRect(20, 40, 100, 12);

        var resolution = LeaderboardsTabComponent.resolveClick(
                true, List.of(pickerRow0), List.of(groupTabRect), 10, 8); // inside groupTabRect, outside the picker row

        assertInstanceOf(LeaderboardsTabComponent.ClickResolution.ClosePicker.class, resolution,
                "a click outside the picker must close it, never fall through to the control underneath");
    }

    @Test
    @DisplayName("Every underlying control is blocked while the picker is open, even a click exactly on its rect")
    void underlyingControlsAreBlockedWhilePickerIsOpen() {
        UiRect selectorCenterRect = new UiRect(20, 40, 100, 18);
        UiRect pickerRow0 = new UiRect(200, 200, 10, 10); // picker positioned elsewhere - no overlap this time

        // Click dead-center on the selector's own rect - with the picker open, this must NOT resolve to Normal.
        var resolution = LeaderboardsTabComponent.resolveClick(true, List.of(pickerRow0), List.of(selectorCenterRect), 25, 45);

        assertFalse(resolution instanceof LeaderboardsTabComponent.ClickResolution.Normal,
                "no normal control may ever be activated while the picker overlay is open");
        assertInstanceOf(LeaderboardsTabComponent.ClickResolution.ClosePicker.class, resolution);
    }

    @Test
    @DisplayName("Once the picker is closed, normal selector controls resolve exactly as before")
    void normalControlsStillWorkAfterPickerCloses() {
        UiRect selectorCenterRect = new UiRect(20, 40, 100, 18);

        var resolution = LeaderboardsTabComponent.resolveClick(false, List.of(), List.of(selectorCenterRect), 25, 45);

        assertInstanceOf(LeaderboardsTabComponent.ClickResolution.Normal.class, resolution);
        assertEquals(0, ((LeaderboardsTabComponent.ClickResolution.Normal) resolution).index());
    }

    @Test
    @DisplayName("A click that hits neither a picker row nor a normal control (picker closed) resolves to None")
    void clickMissingEverythingResolvesToNone() {
        var resolution = LeaderboardsTabComponent.resolveClick(false, List.of(), List.of(new UiRect(0, 0, 5, 5)), 500, 500);
        assertInstanceOf(LeaderboardsTabComponent.ClickResolution.None.class, resolution);
    }
}
