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
}
