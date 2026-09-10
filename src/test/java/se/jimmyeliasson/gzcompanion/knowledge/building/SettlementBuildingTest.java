package se.jimmyeliasson.gzcompanion.knowledge.building;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import se.jimmyeliasson.gzcompanion.knowledge.common.VerificationMetadata;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SettlementBuildingTest {

    private SettlementBuilding withFootprint(Integer minWidth, Integer minDepth, Integer minHeight) {
        return new SettlementBuilding("test", "Test", 1, 1000, "Bonus", List.of(), minWidth, minDepth, minHeight,
                VerificationMetadata.UNVERIFIED_DEFAULT);
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
}
