package se.jimmyeliasson.gzcompanion.knowledge.crafting;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ClientRecipeCachePolicyTest {

    @Test
    @DisplayName("Never refreshed yet (lastRefreshAtMs <= 0) always refreshes")
    void testNeverRefreshedAlwaysRefreshes() {
        assertTrue(ClientRecipeCachePolicy.shouldRefresh(0L, 1_000_000L, false));
        assertTrue(ClientRecipeCachePolicy.shouldRefresh(-1L, 1_000_000L, false));
    }

    @Test
    @DisplayName("A context change always forces a refresh regardless of elapsed time")
    void testContextChangeAlwaysForcesRefresh() {
        long now = 1_000_000L;
        assertTrue(ClientRecipeCachePolicy.shouldRefresh(now, now, true));
        assertTrue(ClientRecipeCachePolicy.shouldRefresh(now - 1, now, true));
    }

    @Test
    @DisplayName("Within the throttle interval with no context change, does not refresh")
    void testWithinIntervalDoesNotRefresh() {
        long lastRefresh = 1_000_000L;
        long now = lastRefresh + ClientRecipeCachePolicy.MIN_REFRESH_INTERVAL_MS - 1;
        assertFalse(ClientRecipeCachePolicy.shouldRefresh(lastRefresh, now, false));
    }

    @Test
    @DisplayName("Exactly at the throttle interval boundary, refreshes")
    void testAtIntervalBoundaryRefreshes() {
        long lastRefresh = 1_000_000L;
        long now = lastRefresh + ClientRecipeCachePolicy.MIN_REFRESH_INTERVAL_MS;
        assertTrue(ClientRecipeCachePolicy.shouldRefresh(lastRefresh, now, false));
    }

    @Test
    @DisplayName("Well past the throttle interval, refreshes")
    void testPastIntervalRefreshes() {
        long lastRefresh = 1_000_000L;
        long now = lastRefresh + ClientRecipeCachePolicy.MIN_REFRESH_INTERVAL_MS + 5000;
        assertTrue(ClientRecipeCachePolicy.shouldRefresh(lastRefresh, now, false));
    }
}
