package se.jimmyeliasson.gzcompanion.settlement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import se.jimmyeliasson.gzcompanion.gamezone.GameZoneLiveContext;
import se.jimmyeliasson.gzcompanion.gamezone.settlement.GameZoneSettlementIdentity;
import se.jimmyeliasson.gzcompanion.gamezone.status.GameZoneLiveStatus;
import se.jimmyeliasson.gzcompanion.knowledge.settlement.SettlementCatalog;
import se.jimmyeliasson.gzcompanion.knowledge.settlement.SettlementLevel;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** {@link SettlementLiveView#from} - combines {@link GameZoneLiveContext} with catalog alignment. */
class SettlementLiveViewTest {

    private static SettlementCatalog catalogWith(int level, String name) {
        SettlementLevel lvl = new SettlementLevel(level, name, 1000, List.of(), null, null, null, null);
        return new SettlementCatalog(List.of(lvl), null, List.of(), null, List.of());
    }

    @Test
    @DisplayName("A: a recognized, aligned live settlement exposes name/role/bonus/treasury/level all at once")
    void recognizedAlignedSettlementExposesEveryFact() {
        GameZoneLiveStatus status = new GameZoneLiveStatus(23, 100, 20.0, 10, "Småstad", 65_068L, 11_487_272L,
                "Trälskärsbukten", "MEMBER", 44.3);
        GameZoneSettlementIdentity identity = new GameZoneSettlementIdentity("Trälskärsbukten", "MEMBER", "TRÄ");
        GameZoneLiveContext ctx = new GameZoneLiveContext(true, status, identity, List.of("jbl76", "Olivre"));

        SettlementLiveView view = SettlementLiveView.from(ctx, catalogWith(10, "Småstad"));

        assertTrue(view.connected());
        assertTrue(view.settlementRecognized());
        assertEquals("Trälskärsbukten", view.settlementName());
        assertEquals("MEMBER", view.role());
        assertEquals(44.3, view.bonusPercent(), 0.001);
        assertEquals(11_487_272L, view.treasury());
        assertTrue(view.liveLevel().trusted());
        assertEquals(10, view.liveLevel().level());
        assertEquals(List.of("jbl76", "Olivre"), view.sameSettlementOnlineUsernames());
    }

    @Test
    @DisplayName("E: a live level that mismatches the catalog is still visible raw, but not trusted")
    void mismatchedLevelStillVisibleButNotTrusted() {
        GameZoneLiveStatus status = new GameZoneLiveStatus(1, 100, 20.0, 99, "Påhittad", null, null,
                "Trälskärsbukten", "MEMBER", null);
        GameZoneSettlementIdentity identity = new GameZoneSettlementIdentity("Trälskärsbukten", "MEMBER", "TRÄ");
        GameZoneLiveContext ctx = new GameZoneLiveContext(true, status, identity, List.of("jbl76"));

        SettlementLiveView view = SettlementLiveView.from(ctx, catalogWith(10, "Småstad"));

        assertTrue(view.settlementRecognized(), "settlement identity itself is unaffected by a level mismatch");
        assertEquals(99, view.liveLevel().level(), "the raw live level must still be shown honestly");
        assertFalse(view.liveLevel().trusted());
    }

    @Test
    @DisplayName("Not connected -> NONE, regardless of what the context would otherwise contain")
    void notConnectedIsNone() {
        SettlementLiveView view = SettlementLiveView.from(GameZoneLiveContext.DISCONNECTED, catalogWith(10, "Småstad"));
        assertEquals(SettlementLiveView.NONE, view);
    }

    @Test
    @DisplayName("Connected but no settlement recognized - still reports connected(), but not recognized(), no invented settlement facts")
    void connectedButNoSettlementRecognized() {
        GameZoneLiveStatus status = new GameZoneLiveStatus(1, 100, 20.0, null, null, null, null, null, null, null);
        GameZoneLiveContext ctx = new GameZoneLiveContext(true, status, GameZoneSettlementIdentity.UNKNOWN, List.of());

        SettlementLiveView view = SettlementLiveView.from(ctx, catalogWith(10, "Småstad"));

        assertTrue(view.connected());
        assertFalse(view.settlementRecognized());
        assertNull(view.settlementName());
        assertNull(view.role());
    }

    @Test
    @DisplayName("Missing bonus does not invalidate the rest of the view - treasury/level still show normally")
    void missingBonusDoesNotInvalidateOtherFields() {
        GameZoneLiveStatus status = new GameZoneLiveStatus(1, 100, 20.0, 10, "Småstad", null, 500L,
                "Trälskärsbukten", "MEMBER", null);
        GameZoneSettlementIdentity identity = new GameZoneSettlementIdentity("Trälskärsbukten", "MEMBER", "TRÄ");
        GameZoneLiveContext ctx = new GameZoneLiveContext(true, status, identity, List.of());

        SettlementLiveView view = SettlementLiveView.from(ctx, catalogWith(10, "Småstad"));

        assertNull(view.bonusPercent());
        assertEquals(500L, view.treasury());
        assertTrue(view.liveLevel().trusted());
    }
}
