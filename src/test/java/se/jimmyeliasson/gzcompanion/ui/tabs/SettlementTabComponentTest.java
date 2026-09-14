package se.jimmyeliasson.gzcompanion.ui.tabs;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import se.jimmyeliasson.gzcompanion.knowledge.settlement.SettlementLevel;
import se.jimmyeliasson.gzcompanion.settlement.EffectiveCurrentLevel;
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

    // ------------------------------------------------------------------
    // Live settlement dashboard: progression row derivation, offline button relabeling,
    // live-online member sorting/matching.
    // ------------------------------------------------------------------

    @Test
    @DisplayName("G: progression row derivation - past/current/next/target, independently combinable")
    void progressionRowStateDerivesPastCurrentNextTarget() {
        EffectiveCurrentLevel effective = new EffectiveCurrentLevel(10, true);

        var past = SettlementTabComponent.progressionRowState(5, effective, 15);
        assertTrue(past.past());
        assertFalse(past.current());
        assertFalse(past.next());
        assertFalse(past.target());

        var current = SettlementTabComponent.progressionRowState(10, effective, 15);
        assertFalse(current.past());
        assertTrue(current.current());
        assertFalse(current.next());
        assertFalse(current.target());

        var next = SettlementTabComponent.progressionRowState(11, effective, 15);
        assertFalse(next.past());
        assertFalse(next.current());
        assertTrue(next.next());
        assertFalse(next.target());

        var target = SettlementTabComponent.progressionRowState(15, effective, 15);
        assertTrue(target.target());
        assertFalse(target.past());
        assertFalse(target.current());
        assertFalse(target.next());
    }

    @Test
    @DisplayName("G: a level can be BOTH next AND target at once - both flags set, never forced to pick only one")
    void progressionRowStateAllowsNextAndTargetTogether() {
        EffectiveCurrentLevel effective = new EffectiveCurrentLevel(10, true);
        var row = SettlementTabComponent.progressionRowState(11, effective, 11);

        assertTrue(row.next());
        assertTrue(row.target());
    }

    @Test
    @DisplayName("G: with no effective level known, only the target flag can ever be set - no invented past/current/next")
    void progressionRowStateWithNoEffectiveLevelOnlyTargetCanBeSet() {
        EffectiveCurrentLevel none = EffectiveCurrentLevel.NONE;
        var row = SettlementTabComponent.progressionRowState(15, none, 15);

        assertFalse(row.past());
        assertFalse(row.current());
        assertFalse(row.next());
        assertTrue(row.target());
    }

    @Test
    @DisplayName("progressionRowBadge: CURRENT wins over NEXT/TARGET when somehow overlapping, otherwise NEXT then TARGET")
    void progressionRowBadgePriority() {
        assertEquals("HÄR", SettlementTabComponent.progressionRowBadge(
                new SettlementTabComponent.ProgressionRowState(false, true, false, true)));
        assertEquals("NÄSTA", SettlementTabComponent.progressionRowBadge(
                new SettlementTabComponent.ProgressionRowState(false, false, true, true)));
        assertEquals("MÅL", SettlementTabComponent.progressionRowBadge(
                new SettlementTabComponent.ProgressionRowState(false, false, false, true)));
        assertEquals("", SettlementTabComponent.progressionRowBadge(
                new SettlementTabComponent.ProgressionRowState(true, false, false, false)),
                "PAST is deliberately silent - no badge");
    }

    @Test
    @DisplayName("offlineCurrentButtonLabel: distinct wording from the normal (non-live) current-level button labels, "
            + "so it never looks like it changes the real GameZone level")
    void offlineCurrentButtonLabelIsDistinctFromNormalLabel() {
        String offlineNotSet = SettlementTabComponent.offlineCurrentButtonLabel(false);
        String offlineSet = SettlementTabComponent.offlineCurrentButtonLabel(true);
        assertNotEquals(SettlementTabComponent.currentButtonLabel(false), offlineNotSet);
        assertNotEquals(SettlementTabComponent.currentButtonLabel(true), offlineSet);
        assertTrue(offlineNotSet.toUpperCase().contains("OFFLINE"));
        assertTrue(offlineSet.toUpperCase().contains("OFFLINE") || offlineSet.contains("manuell"));
    }

    @Test
    @DisplayName("J: online settlement members sort alphabetically, case-insensitively, deterministically")
    void sortedOnlineSettlementMembersSortsCaseInsensitively() {
        List<String> sorted = SettlementTabComponent.sortedOnlineSettlementMembers(List.of("zed", "Alpha", "beta"));
        assertEquals(List.of("Alpha", "beta", "zed"), sorted);
    }

    @Test
    @DisplayName("J: the local player is correctly identified case-insensitively, and no one else is")
    void isLocalPlayerMatchesCaseInsensitively() {
        assertTrue(SettlementTabComponent.isLocalPlayer("jbl76", "JBL76"));
        assertTrue(SettlementTabComponent.isLocalPlayer("JBL76", "jbl76"));
        assertFalse(SettlementTabComponent.isLocalPlayer("Olivre", "jbl76"));
        assertFalse(SettlementTabComponent.isLocalPlayer(null, "jbl76"));
        assertFalse(SettlementTabComponent.isLocalPlayer("jbl76", null));
    }
}
