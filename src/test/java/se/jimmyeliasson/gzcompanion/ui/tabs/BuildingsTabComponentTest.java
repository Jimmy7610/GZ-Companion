package se.jimmyeliasson.gzcompanion.ui.tabs;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import se.jimmyeliasson.gzcompanion.knowledge.building.SettlementBuilding;
import se.jimmyeliasson.gzcompanion.knowledge.common.VerificationMetadata;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BuildingsTabComponentTest {

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
}
