package se.jimmyeliasson.gzcompanion.ui.tabs;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import se.jimmyeliasson.gzcompanion.knowledge.settlement.SettlementCatalog;
import se.jimmyeliasson.gzcompanion.knowledge.settlement.SettlementLevel;
import se.jimmyeliasson.gzcompanion.settlement.EffectiveCurrentLevel;
import se.jimmyeliasson.gzcompanion.settlement.LiveLevelAlignment;
import se.jimmyeliasson.gzcompanion.settlement.LiveSettlementLevel;
import se.jimmyeliasson.gzcompanion.settlement.SettlementLiveView;
import se.jimmyeliasson.gzcompanion.settlement.storage.SettlementPlannerProfile;
import se.jimmyeliasson.gzcompanion.ui.layout.UiRect;

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

    // ------------------------------------------------------------------
    // Bugfix: Översikt must be scrollable when its content exceeds the panel - human QA found
    // NÄSTA NIVÅ and everything after it disappearing behind/below the footer with no way to
    // scroll to it. See SettlementTabComponent.renderOverview/estimateOverviewContentHeight.
    // ------------------------------------------------------------------

    private static SettlementLiveView liveViewWith(LiveLevelAlignment alignment) {
        LiveSettlementLevel level = new LiveSettlementLevel(10, "Småstad", alignment);
        return new SettlementLiveView(true, true, "Trälskärsbukten", "MEMBER", 44.3, 100L, level, List.of("jbl76"));
    }

    private static SettlementCatalog catalogWith(SettlementLevel... levels) {
        return new SettlementCatalog(List.of(levels), null, List.of(), null, List.of());
    }

    @Test
    @DisplayName("1: content shorter than the viewport -> max scroll is 0")
    void overviewMaxScrollIsZeroWhenContentFitsViewport() {
        assertEquals(0, SettlementTabComponent.clampScroll(0, 100, 200));
    }

    @Test
    @DisplayName("2: content taller than the viewport -> max scroll is greater than 0, and reaching it is possible")
    void overviewMaxScrollIsPositiveWhenContentExceedsViewport() {
        int clamped = SettlementTabComponent.clampScroll(10_000, 500, 100);
        assertEquals(400, clamped, "clamped to the true max (contentHeight - viewportHeight), not the requested overscroll");
        assertTrue(clamped > 0);
    }

    @Test
    @DisplayName("4: cannot scroll below 0 regardless of how far negative is requested")
    void overviewScrollNeverNegative() {
        assertEquals(0, SettlementTabComponent.clampScroll(-500, 500, 100));
    }

    @Test
    @DisplayName("5: cannot scroll past the true max regardless of how far past it is requested")
    void overviewScrollNeverPastMax() {
        assertEquals(400, SettlementTabComponent.clampScroll(999_999, 500, 100));
    }

    @Test
    @DisplayName("6: resizing to a taller viewport re-clamps an old excessive scroll position down to the new (smaller) max")
    void resizingToTallerViewportClampsOldExcessiveScroll() {
        // Was valid for a 100px-tall viewport against 500px of content (max scroll 400).
        int oldScroll = 400;
        // The window grows, so the SAME content now has a 450px-tall viewport - max scroll shrinks to 50.
        int reclamped = SettlementTabComponent.clampScroll(oldScroll, 500, 450);
        assertEquals(50, reclamped);
    }

    @Test
    @DisplayName("liveOverviewCardHeight/nextLevelCardHeight: ALIGNED (no mismatch warning) is shorter than MISMATCH (with warning)")
    void liveCardHeightAccountsForMismatchWarningLine() {
        int aligned = SettlementTabComponent.liveOverviewCardHeight(liveViewWith(LiveLevelAlignment.ALIGNED));
        int mismatch = SettlementTabComponent.liveOverviewCardHeight(liveViewWith(LiveLevelAlignment.MISMATCH));
        assertTrue(mismatch > aligned, "the extra mismatch-warning line must be accounted for in the estimated height");
    }

    @Test
    @DisplayName("nextLevelCardHeight: zero when no effective level is known, and accounts for an optional required-building line")
    void nextLevelCardHeightHandlesUnknownAndOptionalLine() {
        SettlementCatalog catalog = catalogWith(
                new SettlementLevel(10, "Småstad", 1000, List.of(), null, null, null, null),
                new SettlementLevel(11, "Stad", 2000, List.of(), "Stadshus", null, null, null)
        );

        assertEquals(0, SettlementTabComponent.nextLevelCardHeight(catalog, EffectiveCurrentLevel.NONE));

        int withBuildingReq = SettlementTabComponent.nextLevelCardHeight(catalog, new EffectiveCurrentLevel(10, true));
        assertTrue(withBuildingReq > 0);

        SettlementCatalog catalogNoReq = catalogWith(
                new SettlementLevel(10, "Småstad", 1000, List.of(), null, null, null, null),
                new SettlementLevel(11, "Stad", 2000, List.of(), null, null, null, null)
        );
        int withoutBuildingReq = SettlementTabComponent.nextLevelCardHeight(catalogNoReq, new EffectiveCurrentLevel(10, true));
        assertTrue(withBuildingReq > withoutBuildingReq, "the optional 'Kräver: ...' line must add to the estimated height");
    }

    @Test
    @DisplayName("3/8: mouse wheel over the Overview panel changes overviewScroll and is consumed; outside it, nothing changes and it is not consumed")
    void overviewMouseWheelOnlyConsumedInsidePanel() {
        SettlementTabComponent tab = new SettlementTabComponent();
        UiRect bounds = new UiRect(0, 0, 500, 300); // wide/NORMAL layout - panelRect spans the whole body
        // A miss-click purely to establish `layout` (mirrors how render() always runs before any
        // input event in practice) - hits nothing, so it is side-effect-free otherwise.
        tab.mouseClicked(-1000, -1000, 0, bounds, null);
        tab.setModeForTesting(SettlementTabComponent.Mode.OVERSIKT);
        tab.setOverviewScrollForTesting(50);

        boolean consumedOutside = tab.mouseScrolled(-500, -500, 0, -1);
        assertFalse(consumedOutside, "a scroll far outside the tab bounds must not be consumed");
        assertEquals(50, tab.getOverviewScrollForTesting(), "scrolling outside the panel must not change overviewScroll");

        boolean consumedInside = tab.mouseScrolled(bounds.width() / 2.0, bounds.height() / 2.0, 0, -1);
        assertTrue(consumedInside, "a scroll inside the Overview panel must be consumed");
        assertTrue(tab.getOverviewScrollForTesting() > 50, "scrolling down (negative scrollY) must increase overviewScroll");
    }

    @Test
    @DisplayName("7: switching mode away from and back to Overview does not corrupt Overview's or another mode's scroll state")
    void switchingModesDoesNotCorruptScrollState() {
        SettlementTabComponent tab = new SettlementTabComponent();
        tab.setOverviewScrollForTesting(77);
        tab.setMaterialScrollForTesting(33);

        tab.setModeForTesting(SettlementTabComponent.Mode.MATERIAL);
        assertEquals(77, tab.getOverviewScrollForTesting(), "leaving Overview must not reset or corrupt its own scroll state");
        assertEquals(33, tab.getMaterialScrollForTesting());

        tab.setModeForTesting(SettlementTabComponent.Mode.OVERSIKT);
        assertEquals(77, tab.getOverviewScrollForTesting(), "returning to Overview must not have lost its prior scroll position");
        assertEquals(33, tab.getMaterialScrollForTesting(), "an unrelated mode's scroll state must remain untouched throughout");
    }

    @Test
    @DisplayName("9: mouse wheel while in MATERIAL/MEDLEMMAR mode still only affects that mode's own scroll, unaffected by the Overview fix")
    void otherModesScrollBehaviorUnchangedByOverviewFix() {
        SettlementTabComponent tab = new SettlementTabComponent();
        UiRect bounds = new UiRect(0, 0, 500, 300);
        tab.mouseClicked(-1000, -1000, 0, bounds, null);

        tab.setModeForTesting(SettlementTabComponent.Mode.MATERIAL);
        tab.setOverviewScrollForTesting(0);
        boolean consumed = tab.mouseScrolled(bounds.width() / 2.0, bounds.height() / 2.0, 0, -1);

        assertTrue(consumed);
        assertEquals(0, tab.getOverviewScrollForTesting(), "scrolling in MATERIAL mode must never touch overviewScroll");
        assertTrue(tab.getMaterialScrollForTesting() > 0, "MATERIAL's own scroll must still respond exactly as before");
    }
}
