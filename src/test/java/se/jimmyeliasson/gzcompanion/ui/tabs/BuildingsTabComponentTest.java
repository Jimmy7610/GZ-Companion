package se.jimmyeliasson.gzcompanion.ui.tabs;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import se.jimmyeliasson.gzcompanion.building.storage.BuildingPlan;
import se.jimmyeliasson.gzcompanion.building.storage.BuildingRequirementKey;
import se.jimmyeliasson.gzcompanion.knowledge.building.BuildingRequirement;
import se.jimmyeliasson.gzcompanion.knowledge.building.GlobalBuildingRules;
import se.jimmyeliasson.gzcompanion.knowledge.building.SettlementBuilding;
import se.jimmyeliasson.gzcompanion.knowledge.common.VerificationMetadata;
import se.jimmyeliasson.gzcompanion.ui.layout.BuildingLayout;
import se.jimmyeliasson.gzcompanion.ui.layout.UiRect;
import se.jimmyeliasson.gzcompanion.ui.tabs.BuildingsTabComponent.WrappedTextHeightMeasurer;

import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BuildingsTabComponentTest {

    /** Any width under 300 puts BuildingLayout into its 1-pane compact mode. */
    private static final UiRect COMPACT_BOUNDS = new UiRect(0, 0, 200, 300);

    private SettlementBuilding building(int levelRequirement, Integer progressionRequiredForUpgradeToLevel, long licenseCost) {
        return new SettlementBuilding("test", "Test", levelRequirement, progressionRequiredForUpgradeToLevel, licenseCost, "Bonus",
                List.of(), 11, 11, null, VerificationMetadata.UNVERIFIED_DEFAULT, VerificationMetadata.UNVERIFIED_DEFAULT);
    }

    private SettlementBuilding buildingWithFootprint(Integer minWidth, Integer minDepth, Integer minHeight) {
        return new SettlementBuilding("test", "Test", 6, 7, 1000, "Bonus", List.of(),
                minWidth, minDepth, minHeight, VerificationMetadata.UNVERIFIED_DEFAULT, VerificationMetadata.UNVERIFIED_DEFAULT);
    }

    private SettlementBuilding buildingWith(String mainBonus, List<BuildingRequirement> specialRequirements, VerificationMetadata verification) {
        return new SettlementBuilding("test", "Test", 6, 7, 1000, mainBonus, specialRequirements,
                11, 11, null, verification, verification);
    }

    private BuildingPlan plan(String id) {
        return new BuildingPlan(id, "test", "Plan " + id, 19, 19, 5, EnumSet.noneOf(BuildingRequirementKey.class), 0L);
    }

    private GlobalBuildingRules rules() {
        return new GlobalBuildingRules(50, 50, true, "process", "note", VerificationMetadata.UNVERIFIED_DEFAULT);
    }

    /** Returns the text's own length as its "height" - deterministic and exactly reproducible in assertions, unlike a live Font. */
    private static final WrappedTextHeightMeasurer IDENTITY_MEASURER = (text, maxW, scale, maxLines, spacing) -> text.length();

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

    // ------------------------------------------------------------------
    // Human QA blocker: compact detail max-scroll came in shorter than the real rendered
    // content (flat guessed numbers like "h += 6 + 90" for the calculator), leaving "+ Ny plan"
    // and the last saved plan's controls unreachable no matter how far the player scrolled.
    // estimateDetailHeight now derives every wrapped-text block from the same measurement the
    // renderer uses (injected here as IDENTITY_MEASURER, since a live Minecraft Font cannot be
    // constructed in a headless test) and the plans section from the same fixed row pitch
    // renderPlanRow() actually returns.
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Plans section height for zero plans covers the label, the + Ny plan button, and the empty-state message")
    void plansSectionHeightZeroPlans() {
        assertEquals(10 + 14 + 9, BuildingsTabComponent.estimatePlansSectionHeight(0));
    }

    @Test
    @DisplayName("Plans section height for one plan covers the label, the button, and the FULL fixed-height row (not the empty-state estimate)")
    void plansSectionHeightOnePlan() {
        assertEquals(10 + 14 + 36, BuildingsTabComponent.estimatePlansSectionHeight(1));
    }

    @Test
    @DisplayName("Plans section height for several plans adds exactly one fixed 36px row per plan")
    void plansSectionHeightSeveralPlans() {
        int one = BuildingsTabComponent.estimatePlansSectionHeight(1);
        int three = BuildingsTabComponent.estimatePlansSectionHeight(3);
        assertEquals(2 * 36, three - one, "Two additional plans must add exactly two full rows, not a flat guess.");
    }

    @Test
    @DisplayName("estimateDetailHeight actually incorporates the plans section - going from zero to several plans changes the total by the exact plans-section delta")
    void estimateDetailHeightIncludesPlansSectionContribution() {
        BuildingsTabComponent tab = new BuildingsTabComponent();
        SettlementBuilding testBuilding = buildingWith("En bonus", List.of(), VerificationMetadata.UNVERIFIED_DEFAULT);
        int maxW = 180;

        int withZeroPlans = tab.estimateDetailHeight(IDENTITY_MEASURER, maxW, testBuilding, rules(), List.of());
        int withThreePlans = tab.estimateDetailHeight(IDENTITY_MEASURER, maxW, testBuilding, rules(),
                List.of(plan("p1"), plan("p2"), plan("p3")));

        int expectedDelta = BuildingsTabComponent.estimatePlansSectionHeight(3) - BuildingsTabComponent.estimatePlansSectionHeight(0);
        assertEquals(expectedDelta, withThreePlans - withZeroPlans,
                "Adding plans must grow the total estimate by exactly the plans-section delta - the previous bug effectively dropped this contribution.");
    }

    @Test
    @DisplayName("Compact Stall-shaped building with zero plans: the total estimate reaches through the + Ny plan button, not just the calculator")
    void totalEstimateReachesNewPlanButtonWithZeroPlans() {
        BuildingsTabComponent tab = new BuildingsTabComponent();
        SettlementBuilding stall = buildingWith("Stall-bonus", List.of(), VerificationMetadata.UNVERIFIED_DEFAULT);
        int maxW = 180;

        int total = tab.estimateDetailHeight(IDENTITY_MEASURER, maxW, stall, rules(), List.of());
        int calculatorHeight = tab.estimateStructureCalculatorHeight(IDENTITY_MEASURER, maxW, stall);
        int plansHeight = BuildingsTabComponent.estimatePlansSectionHeight(0);

        // The total must be at least everything-before-the-calculator, plus the calculator's own
        // gap+height, plus the plans section's own gap+height (which itself already includes the
        // "+ Ny plan" button) - i.e. the button's height must genuinely be part of the total, not
        // silently dropped the way the old flat "+90" estimate effectively did.
        assertTrue(total >= 6 + calculatorHeight + 6 + plansHeight,
                "The total estimate must reach past the calculator through the full plans section (including + Ny plan).");
    }

    @Test
    @DisplayName("Structure calculator height accounts for the PASS/FAIL wrapped message - a failing footprint changes the total")
    void calculatorHeightAccountsForPassFailMessage() {
        BuildingsTabComponent tab = new BuildingsTabComponent();
        int maxW = 180;

        // Default calculator dimensions are 7x7x5 - a building requiring 19x19 fails against
        // them; a building requiring 5x5 passes.
        SettlementBuilding failsFootprint = buildingWithFootprint(19, 19, null);
        SettlementBuilding passesFootprint = buildingWithFootprint(5, 5, null);

        String failMessage = BuildingsTabComponent.footprintStatusMessage(failsFootprint, true);
        String passMessage = BuildingsTabComponent.footprintStatusMessage(passesFootprint, false);
        assertNotEquals(failMessage, passMessage, "Sanity check: PASS and FAIL must actually render different text.");

        int failHeight = tab.estimateStructureCalculatorHeight(IDENTITY_MEASURER, maxW, failsFootprint);
        int passHeight = tab.estimateStructureCalculatorHeight(IDENTITY_MEASURER, maxW, passesFootprint);

        int baseHeight = 10 + (14 * 3) + (9 + 9 + 9 + 11);
        int disclaimerHeight = BuildingsTabComponent.CALCULATOR_DISCLAIMER_TEXT.length() + 2;
        assertEquals(baseHeight + failMessage.length() + 2 + disclaimerHeight, failHeight);
        assertEquals(baseHeight + passMessage.length() + 2 + disclaimerHeight, passHeight);
        assertNotEquals(failHeight, passHeight, "The PASS/FAIL message's real measured height must be reflected in the total, not a flat guess.");
    }

    @Test
    @DisplayName("A building with no published footprint never adds a PASS/FAIL block to the calculator height")
    void calculatorHeightSkipsFootprintMessageWhenUnpublished() {
        BuildingsTabComponent tab = new BuildingsTabComponent();
        int maxW = 180;
        SettlementBuilding noFootprint = buildingWithFootprint(null, null, null);

        int height = tab.estimateStructureCalculatorHeight(IDENTITY_MEASURER, maxW, noFootprint);
        int expected = 10 + (14 * 3) + (9 + 9 + 9 + 11) + BuildingsTabComponent.CALCULATOR_DISCLAIMER_TEXT.length() + 2;
        assertEquals(expected, height);
    }

    @Test
    @DisplayName("A building with a source conflict adds the exact measured conflict-warning height, not a flat guess")
    void detailHeightAccountsForConflictWarningMessage() {
        BuildingsTabComponent tab = new BuildingsTabComponent();
        int maxW = 180;
        SettlementBuilding conflicting = building(2, 2, 5000); // hasLevelRequirementConflict() == true
        SettlementBuilding normal = building(6, 7, 1000);

        int conflictingHeight = tab.estimateDetailHeight(IDENTITY_MEASURER, maxW, conflicting, rules(), List.of());
        int normalHeight = tab.estimateDetailHeight(IDENTITY_MEASURER, maxW, normal, rules(), List.of());

        int expectedExtra = (BuildingsTabComponent.CONFLICT_WARNING_TEXT.length() + 2 + 9 + 10) - 10;
        assertEquals(expectedExtra, conflictingHeight - normalHeight,
                "The conflict warning block's exact measured height must replace the plain Nivåkrav line's height.");
    }

    @Test
    @DisplayName("A building with a bonus adds the exact measured bonus-text height; a building without a bonus adds nothing")
    void detailHeightAccountsForBonusTextExactly() {
        BuildingsTabComponent tab = new BuildingsTabComponent();
        int maxW = 180;
        SettlementBuilding withBonus = buildingWith("En ganska lång bonusbeskrivning för detta test.", List.of(), VerificationMetadata.UNVERIFIED_DEFAULT);
        SettlementBuilding withoutBonus = buildingWith("", List.of(), VerificationMetadata.UNVERIFIED_DEFAULT);

        int withBonusHeight = tab.estimateDetailHeight(IDENTITY_MEASURER, maxW, withBonus, rules(), List.of());
        int withoutBonusHeight = tab.estimateDetailHeight(IDENTITY_MEASURER, maxW, withoutBonus, rules(), List.of());

        int expectedBonusContribution = 4 + 9 + withBonus.mainBonus().length() + 2;
        assertEquals(expectedBonusContribution, withBonusHeight - withoutBonusHeight);
    }

    @Test
    @DisplayName("max-scroll never ends before final content and never leaves excessive blank space: it is exactly total height minus visible height")
    void maxScrollIsExactlyTotalMinusVisibleHeight() {
        BuildingsTabComponent tab = new BuildingsTabComponent();
        SettlementBuilding testBuilding = buildingWith("Bonus", List.of(), VerificationMetadata.UNVERIFIED_DEFAULT);
        int maxW = 180;
        int total = tab.estimateDetailHeight(IDENTITY_MEASURER, maxW, testBuilding, rules(), List.of(plan("p1")));

        int shortVisibleHeight = total - 20; // content taller than the visible area
        assertEquals(20, BuildingsTabComponent.calculateMaxDetailScroll(total, shortVisibleHeight),
                "Scrolling to max must reveal exactly the remaining content - no more, no less.");

        int tallVisibleHeight = total + 50; // content shorter than the visible area
        assertEquals(0, BuildingsTabComponent.calculateMaxDetailScroll(total, tallVisibleHeight),
                "Content that already fits must never scroll into empty space.");
    }
}
