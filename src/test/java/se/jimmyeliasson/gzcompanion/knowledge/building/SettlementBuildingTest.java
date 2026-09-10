package se.jimmyeliasson.gzcompanion.knowledge.building;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import se.jimmyeliasson.gzcompanion.knowledge.common.VerificationMetadata;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SettlementBuildingTest {

    private SettlementBuilding withFootprint(Integer minWidth, Integer minDepth, Integer minHeight) {
        return building(1, null, minWidth, minDepth, minHeight);
    }

    private SettlementBuilding building(int levelRequirement, Integer progressionRequiredForUpgradeToLevel,
                                         Integer minWidth, Integer minDepth, Integer minHeight) {
        return new SettlementBuilding("test", "Test", levelRequirement, progressionRequiredForUpgradeToLevel, 1000, "Bonus",
                List.of(), minWidth, minDepth, minHeight, VerificationMetadata.UNVERIFIED_DEFAULT, VerificationMetadata.UNVERIFIED_DEFAULT);
    }

    @Test
    @DisplayName("fitsFootprint detects a width that is too small")
    void detectsWidthTooSmall() {
        SettlementBuilding building = withFootprint(19, 19, null);
        assertFalse(building.fitsFootprint(18, 19, 5));
    }

    @Test
    @DisplayName("fitsFootprint detects a depth that is too small")
    void detectsDepthTooSmall() {
        SettlementBuilding building = withFootprint(19, 19, null);
        assertFalse(building.fitsFootprint(19, 18, 5));
    }

    @Test
    @DisplayName("fitsFootprint passes at exactly the published minimum width/depth")
    void exactMinimumPasses() {
        SettlementBuilding building = withFootprint(19, 19, null);
        assertTrue(building.fitsFootprint(19, 19, 5));
    }

    @Test
    @DisplayName("A building with no published minimum height never fails on height")
    void unknownHeightDoesNotCreateFalseFailure() {
        SettlementBuilding building = withFootprint(19, 19, null);
        assertTrue(building.fitsFootprint(19, 19, 1), "A building with no documented height minimum must never fail on height alone.");
        assertTrue(building.fitsFootprint(100, 100, 0));
    }

    @Test
    @DisplayName("A building WITH a published minimum height fails when height is too short")
    void publishedHeightTooSmallFails() {
        SettlementBuilding building = withFootprint(21, 21, 18);
        assertFalse(building.fitsFootprint(21, 21, 17));
        assertTrue(building.fitsFootprint(21, 21, 18));
        assertTrue(building.fitsFootprint(21, 21, 19));
    }

    @Test
    @DisplayName("A building with no published footprint at all never fails - there is nothing to compare against")
    void noPublishedFootprintNeverFails() {
        SettlementBuilding building = withFootprint(null, null, null);
        assertTrue(building.fitsFootprint(1, 1, 1));
        assertFalse(building.hasPublishedMinimumFootprint());
    }

    // ------------------------------------------------------------------
    // hasLevelRequirementConflict() - the general, data-driven circularity rule.
    // Not a hardcoded id check: any building whose own page claims it becomes
    // available at (or after) the exact level the progression page says it must
    // already be complete BEFORE is presented as CONFLICT, never as settled truth.
    // ------------------------------------------------------------------

    @Test
    @DisplayName("No circular prerequisite is ever presented as settled verified truth: levelRequirement == progressionRequiredForUpgradeToLevel is a conflict")
    void equalLevelsIsCircularConflict() {
        SettlementBuilding building = building(2, 2, 11, 11, null);
        assertTrue(building.hasLevelRequirementConflict(), "A building 'available at level 2' that is ALSO 'required before reaching level 2' is circular.");
        assertFalse(building.isLevelRequirementVerified());
    }

    @Test
    @DisplayName("A building available strictly before its own required-for-upgrade level has no conflict")
    void levelStrictlyBeforeRequiredForIsNotAConflict() {
        SettlementBuilding building = building(6, 7, 17, 17, null);
        assertFalse(building.hasLevelRequirementConflict());
    }

    @Test
    @DisplayName("A building presented as available AFTER the level it's supposedly required for is also a conflict (not just equal levels)")
    void levelAfterRequiredForIsAlsoAConflict() {
        SettlementBuilding building = building(5, 4, 17, 17, null);
        assertTrue(building.hasLevelRequirementConflict());
    }

    @Test
    @DisplayName("A building never presented as a general upgrade gate (progressionRequiredForUpgradeToLevel == null) has nothing to compare and is never flagged")
    void nullProgressionRequirementNeverConflicts() {
        SettlementBuilding building = building(5, null, 17, 17, null);
        assertFalse(building.hasLevelRequirementConflict());
    }

    @Test
    @DisplayName("Stadskärna's real, bundled conflict (level 2 vs required-for 2) reproduces as a conflict via the general rule")
    void stadskarnaShapedConflictReproduces() {
        SettlementBuilding building = building(2, 2, 11, 11, null);
        assertTrue(building.hasLevelRequirementConflict());
    }

    @Test
    @DisplayName("Handelscentrum's real, bundled conflict (level 4 vs required-for 4) reproduces as a conflict via the general rule")
    void handelscentrumShapedConflictReproduces() {
        SettlementBuilding building = building(4, 4, 15, 15, null);
        assertTrue(building.hasLevelRequirementConflict());
    }

    @Test
    @DisplayName("isLevelRequirementVerified requires BOTH a VERIFIED levelRequirementVerification status AND no logical conflict")
    void isLevelRequirementVerifiedRequiresBothChecks() {
        VerificationMetadata verified = VerificationMetadata.of("VERIFIED", "TEST ONLY fixture", "test://fixture", "2026-09-10");
        SettlementBuilding consistent = new SettlementBuilding("test", "Test", 6, 7, 1000, "Bonus",
                List.of(), 17, 17, null, verified, verified);
        assertTrue(consistent.isLevelRequirementVerified());

        SettlementBuilding conflicted = new SettlementBuilding("test", "Test", 2, 2, 1000, "Bonus",
                List.of(), 11, 11, null, verified, verified);
        assertFalse(conflicted.isLevelRequirementVerified(), "Even a VERIFIED-status metadata must not count as verified when the numbers are logically circular.");
    }
}
