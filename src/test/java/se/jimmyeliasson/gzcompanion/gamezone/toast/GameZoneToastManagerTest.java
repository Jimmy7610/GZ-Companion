package se.jimmyeliasson.gzcompanion.gamezone.toast;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GameZoneToastManagerTest {

    @Test
    @DisplayName("A toast is shown when offered and both settings are enabled")
    void testToastShownWhenEnabled() {
        GameZoneToastManager manager = new GameZoneToastManager();
        boolean shown = manager.offer("key1", "Title", "Body", 1000L);
        assertTrue(shown);
        assertTrue(manager.currentToast(1000L).isPresent());
    }

    @Test
    @DisplayName("Repeated identical events within the dedupe window only show once")
    void testDedupeWithinWindow() {
        GameZoneToastManager manager = new GameZoneToastManager();
        assertTrue(manager.offer("key1", "Title", "Body", 1000L));
        assertFalse(manager.offer("key1", "Title", "Body", 1000L + 100), "Same key within the window must be suppressed");
        assertFalse(manager.offer("key1", "Title", "Body", 1000L + GameZoneToastManager.DEFAULT_DEDUPE_WINDOW_MS - 1));
    }

    @Test
    @DisplayName("The same key is shown again once the dedupe window has elapsed")
    void testDedupeWindowElapses() {
        GameZoneToastManager manager = new GameZoneToastManager();
        manager.offer("key1", "Title", "Body", 1000L);
        boolean shownAgain = manager.offer("key1", "Title", "Body", 1000L + GameZoneToastManager.DEFAULT_DEDUPE_WINDOW_MS);
        assertTrue(shownAgain);
    }

    @Test
    @DisplayName("Turning off all Companion notifications suppresses toasts")
    void testNotificationsSettingOff() {
        GameZoneToastManager manager = new GameZoneToastManager();
        manager.setNotificationsEnabledSupplier(() -> false);
        assertFalse(manager.offer("key1", "Title", "Body", 1000L));
        assertTrue(manager.currentToast(1000L).isEmpty());
    }

    @Test
    @DisplayName("Turning off only GameZone event toasts suppresses toasts, independent of the general notifications toggle")
    void testGameZoneToastsSettingOff() {
        GameZoneToastManager manager = new GameZoneToastManager();
        manager.setGameZoneToastsEnabledSupplier(() -> false);
        assertFalse(manager.offer("key1", "Title", "Body", 1000L));
    }

    @Test
    @DisplayName("A toast expires after its duration and is no longer returned")
    void testToastExpires() {
        GameZoneToastManager manager = new GameZoneToastManager();
        manager.offer("key1", "Title", "Body", 1000L);
        assertTrue(manager.currentToast(1000L + GameZoneToastManager.DEFAULT_TOAST_DURATION_MS - 1).isPresent());
        assertTrue(manager.currentToast(1000L + GameZoneToastManager.DEFAULT_TOAST_DURATION_MS).isEmpty());
    }

    @Test
    @DisplayName("No active toast by default when nothing has been offered")
    void testNoToastByDefault() {
        GameZoneToastManager manager = new GameZoneToastManager();
        assertTrue(manager.currentToast(1000L).isEmpty());
        assertEquals(0, manager.queuedCount());
    }

    @Test
    @DisplayName("clear() removes queued toasts and resets dedupe history")
    void testClearResetsState() {
        GameZoneToastManager manager = new GameZoneToastManager();
        manager.offer("key1", "Title", "Body", 1000L);
        manager.clear();
        assertEquals(0, manager.queuedCount());
        assertTrue(manager.offer("key1", "Title", "Body", 1001L), "After clear(), the same key must be allowed again immediately");
    }

    @Test
    @DisplayName("Dedupe keys older than the dedupe window are pruned rather than kept forever")
    void testStaleDedupeKeysArePruned() {
        GameZoneToastManager manager = new GameZoneToastManager();
        manager.offer("stale-key", "Title", "Body", 1000L);
        assertEquals(1, manager.dedupeMapSizeForTesting());

        // A later, unrelated offer (any key) past the dedupe window must opportunistically prune
        // the now-stale entry - it can never again suppress a future offer, so keeping it would
        // only ever grow the map without bound over a long session.
        manager.offer("new-key", "Title", "Body", 1000L + GameZoneToastManager.DEFAULT_DEDUPE_WINDOW_MS + 1);

        assertEquals(1, manager.dedupeMapSizeForTesting(), "Only the still-fresh 'new-key' entry should remain; 'stale-key' must have been pruned.");
    }

    @Test
    @DisplayName("Many distinct event keys within the dedupe window do not get pruned prematurely")
    void testFreshDedupeKeysAreNotPrunedEarly() {
        GameZoneToastManager manager = new GameZoneToastManager();
        for (int i = 0; i < 20; i++) {
            manager.offer("key-" + i, "Title", "Body", 1000L + i);
        }
        assertEquals(20, manager.dedupeMapSizeForTesting(), "Distinct keys still inside the dedupe window must all be retained.");
    }

    @Test
    @DisplayName("The dedupe map stays bounded across a long session of many distinct, non-overlapping event keys")
    void testDedupeMapStaysBoundedOverLongSession() {
        GameZoneToastManager manager = new GameZoneToastManager();
        long windowMs = GameZoneToastManager.DEFAULT_DEDUPE_WINDOW_MS;
        // Simulate 500 completely distinct events spread far enough apart in time that none of
        // them are dedupe-relevant to each other - a naive implementation would grow this map to
        // 500 entries; pruning should keep it small at any single point in time.
        for (int i = 0; i < 500; i++) {
            manager.offer("session-key-" + i, "Title", "Body", 1000L + (i * (windowMs + 10)));
        }
        assertTrue(manager.dedupeMapSizeForTesting() <= 2,
                "The dedupe map must not accumulate every distinct key ever seen across a long session; got " + manager.dedupeMapSizeForTesting());
    }
}
