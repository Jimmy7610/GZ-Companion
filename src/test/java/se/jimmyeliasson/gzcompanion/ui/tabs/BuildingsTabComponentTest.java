package se.jimmyeliasson.gzcompanion.ui.tabs;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import se.jimmyeliasson.gzcompanion.knowledge.building.SettlementBuilding;
import se.jimmyeliasson.gzcompanion.knowledge.common.VerificationMetadata;
import se.jimmyeliasson.gzcompanion.ui.layout.BuildingLayout;
import se.jimmyeliasson.gzcompanion.ui.layout.UiRect;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BuildingsTabComponentTest {

    /** Any width under 300 puts BuildingLayout into its 1-pane compact mode. */
    private static final UiRect COMPACT_BOUNDS = new UiRect(0, 0, 200, 300);

    private SettlementBuilding building(int levelRequirement, Integer progressionRequiredForUpgradeToLevel, long licenseCost) {
        return new SettlementBuilding("test", "Test", levelRequirement, progressionRequiredForUpgradeToLevel, licenseCost, "Bonus",
                List.of(), 11, 11, null, VerificationMetadata.UNVERIFIED_DEFAULT, VerificationMetadata.UNVERIFIED_DEFAULT);
    }

    @Test
    @DisplayName("A non-conflicting building's list row shows its settled level requirement")
    void nonConflictingBuildingShowsLevel() {
        SettlementBuilding bank = building(6, 7, 1000);
        assertEquals("Nivå 6 · 1000 Coins", BuildingsTabComponent.listRowLevelAndCostText(bank));
    }

    @Test
    @DisplayName("A building with a disputed level requirement never presents either number as settled truth in the list row")
    void conflictingBuildingHidesDisputedLevel() {
        SettlementBuilding stadskarna = building(2, 2, 5000);
        String text = BuildingsTabComponent.listRowLevelAndCostText(stadskarna);
        assertEquals("Nivåkonflikt · 5000 Coins", text);
        assertFalse(text.contains("Nivå 2"), "Must not present the disputed level as settled truth.");
    }

    // ------------------------------------------------------------------
    // Human QA blocker: in compact mode, listRect() and detailRect() are the SAME rectangle
    // (only one pane renders at a time), so mouseScrolled used to always resolve to the list
    // branch - silently eating every scroll event meant for the Stall detail pane (calculator +
    // local plans were unreachable). These tests exercise the real mouseScrolled routing.
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Compact layout aliases list and detail rects - the precondition the routing fix guards against")
    void compactLayoutAliasesListAndDetailRects() {
        BuildingLayout layout = BuildingLayout.calculate(COMPACT_BOUNDS);
        assertTrue(layout.isCompact());
        assertEquals(layout.listRect(), layout.detailRect());
    }

    @Test
    @DisplayName("Scrolling while the compact detail pane is showing moves the detail offset, not the list offset")
    void compactDetailScrollMovesDetailOffset() {
        BuildingsTabComponent tab = new BuildingsTabComponent();
        tab.setCompactStateForTesting(COMPACT_BOUNDS, true, "stall");

        boolean handled = tab.mouseScrolled(100, 150, 0, -1);

        assertTrue(handled, "A scroll inside the compact detail pane must be handled.");
        assertTrue(tab.detailScrollOffsetForTesting() > 0, "Detail scroll offset must move.");
        assertEquals(0, tab.listScrollOffsetForTesting(), "The hidden list must never scroll instead of the visible detail pane.");
    }

    @Test
    @DisplayName("Scrolling while the compact list is showing moves the list offset, not the detail offset")
    void compactListScrollMovesListOffset() {
        BuildingsTabComponent tab = new BuildingsTabComponent();
        tab.setCompactStateForTesting(COMPACT_BOUNDS, false, null);

        boolean handled = tab.mouseScrolled(100, 150, 0, -1);

        assertTrue(handled, "A scroll inside the compact list must be handled.");
        assertTrue(tab.listScrollOffsetForTesting() > 0, "List scroll offset must move.");
        assertEquals(0, tab.detailScrollOffsetForTesting());
    }

    @Test
    @DisplayName("Scrolling the compact detail pane without a selected building is a no-op, not a fallback list scroll")
    void compactDetailScrollWithoutSelectionDoesNothing() {
        BuildingsTabComponent tab = new BuildingsTabComponent();
        tab.setCompactStateForTesting(COMPACT_BOUNDS, true, null);

        boolean handled = tab.mouseScrolled(100, 150, 0, -1);

        assertFalse(handled);
        assertEquals(0, tab.listScrollOffsetForTesting());
        assertEquals(0, tab.detailScrollOffsetForTesting());
    }
}
