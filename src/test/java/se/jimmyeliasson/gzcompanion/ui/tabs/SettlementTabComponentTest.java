package se.jimmyeliasson.gzcompanion.ui.tabs;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import se.jimmyeliasson.gzcompanion.knowledge.settlement.SettlementLevel;
import se.jimmyeliasson.gzcompanion.settlement.storage.SettlementPlannerProfile;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Human QA findings:
 * (1) "Sätt som nuvarande"/"Sätt som mål" gave no visible feedback until returning to Overview -
 *     the button label/state must reflect the CURRENT profile immediately.
 * (2) "Låser upp: ..." was ellipsized in compact mode, hiding important gameplay information.
 */
class SettlementTabComponentTest {

    private SettlementLevel levelWithUnlock(String buildingName, String bonus) {
        return new SettlementLevel(6, "Test Level", 1000, List.of(), null, buildingName, bonus, null);
    }

    private SettlementPlannerProfile profile(Integer currentLevel, Integer targetLevel) {
        return new SettlementPlannerProfile(currentLevel, targetLevel, Map.of(), List.of());
    }

    @Test
    @DisplayName("A level matching the profile's current level is reported as current")
    void detectsCurrentLevel() {
        assertTrue(SettlementTabComponent.isCurrentLevel(profile(6, 10), 6));
        assertFalse(SettlementTabComponent.isCurrentLevel(profile(6, 10), 7));
        assertFalse(SettlementTabComponent.isCurrentLevel(profile(null, 10), 6), "No current level set - nothing can match");
    }

    @Test
    @DisplayName("A level matching the profile's target level is reported as target")
    void detectsTargetLevel() {
        assertTrue(SettlementTabComponent.isTargetLevel(profile(6, 10), 10));
        assertFalse(SettlementTabComponent.isTargetLevel(profile(6, 10), 6));
        assertFalse(SettlementTabComponent.isTargetLevel(profile(6, null), 10), "No target level set - nothing can match");
    }

    @Test
    @DisplayName("Button labels change to give immediate feedback once a level is actually set as current/target")
    void buttonLabelsReflectState() {
        assertEquals("Sätt som nuvarande", SettlementTabComponent.currentButtonLabel(false));
        assertEquals("✓ Nuvarande", SettlementTabComponent.currentButtonLabel(true));
        assertEquals("Sätt som mål", SettlementTabComponent.targetButtonLabel(false));
        assertEquals("✓ Mål", SettlementTabComponent.targetButtonLabel(true));
    }

    @Test
    @DisplayName("The unlock line includes the full building name and bonus text - nothing is silently dropped")
    void unlockLineIncludesFullText() {
        SettlementLevel level = levelWithUnlock("En Väldigt Lång Byggnadsnamn Som Tidigare Klipptes Av", "Ökar produktionen avsevärt");
        String line = SettlementTabComponent.unlockedBuildingLine(level);
        assertTrue(line.contains("En Väldigt Lång Byggnadsnamn Som Tidigare Klipptes Av"));
        assertTrue(line.contains("Ökar produktionen avsevärt"));
        assertFalse(line.contains("..."), "The underlying text must never be pre-truncated - wrapping, not ellipsis, keeps it readable.");
    }
}
