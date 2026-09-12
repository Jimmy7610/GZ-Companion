package se.jimmyeliasson.gzcompanion.diagnostics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import se.jimmyeliasson.gzcompanion.gamezone.settlement.GameZoneSettlementIdentity;
import se.jimmyeliasson.gzcompanion.minecraft.OnlinePlayerSnapshot;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Pure assembly/formatting logic for the TEMPORARY Online diagnostics panel - no Minecraft client needed. */
class OnlineDiagnosticsSnapshotTest {

    @Test
    @DisplayName("capture() finds the local player and counts players with a non-null tabDisplayText")
    void captureFindsLocalPlayerAndCountsTabDisplays() {
        List<OnlinePlayerSnapshot> players = List.of(
                new OnlinePlayerSnapshot("jbl76", true, 20, "[TRA] jbl76 [2]"),
                new OnlinePlayerSnapshot("Olivre", false, 40, "[TRA] Olivre [5]"),
                new OnlinePlayerSnapshot("NoDisplay", false, 50, null)
        );
        GameZoneSettlementIdentity identity = new GameZoneSettlementIdentity("Trälskärsbukten", "MEMBER", "TRA");

        OnlineDiagnosticsSnapshot snapshot = OnlineDiagnosticsSnapshot.capture(true, "Trälskärsbukten · MEMBER · +44.3%", players, identity, 2);

        assertTrue(snapshot.connected());
        assertEquals("jbl76", snapshot.localUsername());
        assertTrue(snapshot.localSnapshotFound());
        assertEquals("[TRA] jbl76 [2]", snapshot.localDisplayText());
        assertEquals("Trälskärsbukten", snapshot.settlementName());
        assertEquals("MEMBER", snapshot.role());
        assertEquals("TRA", snapshot.settlementPrefix());
        assertEquals(2, snapshot.sameSettlementCount());
        assertEquals(3, snapshot.totalOnlineCount());
        assertEquals(2, snapshot.playersWithTabDisplayCount());
    }

    @Test
    @DisplayName("capture() never throws and safely reports 'not found' with no local player or players list")
    void captureHandlesMissingLocalPlayerSafely() {
        OnlineDiagnosticsSnapshot snapshot = OnlineDiagnosticsSnapshot.capture(false, null, List.of(), GameZoneSettlementIdentity.UNKNOWN, 0);

        assertFalse(snapshot.connected());
        assertFalse(snapshot.localSnapshotFound());
        assertNull(snapshot.localUsername());
        assertNull(snapshot.headerText());
        assertNull(snapshot.settlementName());
        assertEquals(0, snapshot.totalOnlineCount());
    }

    @Test
    @DisplayName("toCopyText() includes every required field in plain key=value form")
    void copyTextIncludesAllRequiredFields() {
        OnlineDiagnosticsSnapshot snapshot = new OnlineDiagnosticsSnapshot(
                true, "jbl76", true, "Trälskärsbukten · MEMBER · +44.3%", "[TRA] jbl76 [2]",
                "Trälskärsbukten", "MEMBER", "TRA", 3, 27, 27);

        String copyText = snapshot.toCopyText();

        assertTrue(copyText.contains("connected=true"));
        assertTrue(copyText.contains("localUsername=jbl76"));
        assertTrue(copyText.contains("header=Trälskärsbukten · MEMBER · +44.3%"));
        assertTrue(copyText.contains("localDisplay=[TRA] jbl76 [2]"));
        assertTrue(copyText.contains("parsedSettlement=Trälskärsbukten"));
        assertTrue(copyText.contains("parsedRole=MEMBER"));
        assertTrue(copyText.contains("parsedPrefix=TRA"));
        assertTrue(copyText.contains("sameSettlementCount=3"));
        assertTrue(copyText.contains("playersWithTabDisplay=27/27"));
    }

    @Test
    @DisplayName("toCopyText() and toDisplayText() never throw when every field is unknown/null")
    void textMethodsAreSafeWithAllUnknownFields() {
        OnlineDiagnosticsSnapshot snapshot = new OnlineDiagnosticsSnapshot(
                false, null, false, null, null, null, null, null, 0, 0, 0);

        assertDoesNotThrow(snapshot::toCopyText);
        assertDoesNotThrow(snapshot::toDisplayText);
        assertTrue(snapshot.toCopyText().contains("localUsername=null"));
    }
}
