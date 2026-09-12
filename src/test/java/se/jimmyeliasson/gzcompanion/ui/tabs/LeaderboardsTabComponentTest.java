package se.jimmyeliasson.gzcompanion.ui.tabs;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import se.jimmyeliasson.gzcompanion.leaderboard.LeaderboardEntry;

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
}
