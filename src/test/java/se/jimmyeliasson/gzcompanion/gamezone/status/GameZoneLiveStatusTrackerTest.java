package se.jimmyeliasson.gzcompanion.gamezone.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Live invalidation behavior - no live Minecraft client needed, only synthetic raw TAB header
 * text, matching this codebase's established pure-logic-testing convention.
 */
class GameZoneLiveStatusTrackerTest {

    @Test
    @DisplayName("Disconnecting immediately clears the LIVE status - never a stale previous value")
    void disconnectClearsLiveState() {
        GameZoneLiveStatusTracker tracker = new GameZoneLiveStatusTracker();
        tracker.update(true, "23/100 • TPS 20,0 • 10 - Småstad");
        assertEquals(23, tracker.current().onlinePlayers());

        GameZoneLiveStatus afterDisconnect = tracker.update(false, null);
        assertEquals(GameZoneLiveStatus.UNKNOWN, afterDisconnect);
        assertFalse(tracker.current().hasAnyData());
    }

    @Test
    @DisplayName("Reconnecting reparses fresh rather than reusing anything from before the disconnect")
    void reconnectReparsesFresh() {
        GameZoneLiveStatusTracker tracker = new GameZoneLiveStatusTracker();
        tracker.update(true, "23/100 • TPS 20,0 • 10 - Småstad");
        tracker.update(false, null);
        assertFalse(tracker.current().hasAnyData());

        GameZoneLiveStatus afterReconnect = tracker.update(true, "50/100 • TPS 19.5 • 12 - Storstad");
        assertEquals(50, afterReconnect.onlinePlayers());
        assertEquals(12, afterReconnect.cityLevel());
    }

    @Test
    @DisplayName("A header change (e.g. player count, coins) updates the tracked values")
    void headerChangeUpdatesValues() {
        GameZoneLiveStatusTracker tracker = new GameZoneLiveStatusTracker();
        GameZoneLiveStatus first = tracker.update(true, "23/100 • TPS 20,0 • 10 - Småstad\nCoins 65 068 • Stadskassa 11 487 272");
        assertEquals(23, first.onlinePlayers());
        assertEquals(65068L, first.coins());

        GameZoneLiveStatus second = tracker.update(true, "24/100 • TPS 20,0 • 10 - Småstad\nCoins 65 200 • Stadskassa 11 487 272");
        assertEquals(24, second.onlinePlayers());
        assertEquals(65200L, second.coins());
    }

    @Test
    @DisplayName("No stale previous-session values ever leak into a fresh connection with a different server/city")
    void noStaleValuesLeakAcrossReconnect() {
        GameZoneLiveStatusTracker tracker = new GameZoneLiveStatusTracker();
        tracker.update(true, "23/100 • TPS 20,0 • 10 - Småstad\nTrälskärsbukten • MEMBER • +44.3%");
        tracker.update(false, null);

        GameZoneLiveStatus afterReconnect = tracker.update(true, "5/50 • TPS 19.9 • 2 - Nybygge");
        assertEquals(5, afterReconnect.onlinePlayers());
        assertEquals("Nybygge", afterReconnect.cityName());
        assertNull(afterReconnect.settlementName(), "The previous session's settlement must never leak into a fresh connection where the new header carries no settlement line.");
    }

    @Test
    @DisplayName("An unchanged header does not need to be reparsed to keep returning the same status")
    void unchangedHeaderKeepsSameStatus() {
        GameZoneLiveStatusTracker tracker = new GameZoneLiveStatusTracker();
        String header = "23/100 • TPS 20,0 • 10 - Småstad";
        GameZoneLiveStatus first = tracker.update(true, header);
        GameZoneLiveStatus second = tracker.update(true, header);
        assertEquals(first, second);
    }
}
