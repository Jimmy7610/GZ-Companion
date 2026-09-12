package se.jimmyeliasson.gzcompanion.leaderboard;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link LeaderboardManager}'s cache/fetch state machine - the {@link FakeSource} lets each test
 * control exactly what a fetch returns (and count how many times it was actually called) without
 * any real network I/O, mirroring {@code UpdateManagerTest}'s own conventions (including relying on
 * real elapsed time only ever being used to prove a SECOND immediate call is still within a cooldown
 * window - never waiting out a real 60-second/12-second window, which would make this suite far too
 * slow).
 */
class LeaderboardManagerTest {

    private static final LeaderboardDefinition BOARD_A = GameZoneLeaderboardRegistry.byId("player_coins").orElseThrow();
    private static final LeaderboardDefinition BOARD_B = GameZoneLeaderboardRegistry.byId("settlement_treasury").orElseThrow();

    private static final class FakeSource implements LeaderboardSource {
        final AtomicInteger callCount = new AtomicInteger();
        final Map<String, AtomicInteger> callCountByBoard = new ConcurrentHashMap<>();
        volatile Function<LeaderboardDefinition, LeaderboardFetchResult> behavior =
                def -> new LeaderboardFetchResult.Success(List.of(LeaderboardEntry.of(1, "Alfa", "1 coins", "Coins")));
        volatile CountDownLatch blockUntil; // if set, fetch() blocks here before returning - simulates a slow in-flight request

        @Override
        public LeaderboardFetchResult fetch(LeaderboardDefinition definition) {
            callCount.incrementAndGet();
            callCountByBoard.computeIfAbsent(definition.id(), k -> new AtomicInteger()).incrementAndGet();
            CountDownLatch latch = blockUntil;
            if (latch != null) {
                try {
                    latch.await(2, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return behavior.apply(definition);
        }
    }

    private static void awaitStatus(LeaderboardManager manager, LeaderboardDefinition def, LeaderboardStatus expected, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (manager.getSnapshot(def).status() == expected) return;
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        fail("Timed out waiting for status " + expected + " - last seen: " + manager.getSnapshot(def).status());
    }

    @Test
    @DisplayName("A never-fetched board starts IDLE with no entries")
    void neverFetchedBoardIsIdle() {
        LeaderboardManager manager = new LeaderboardManager(new FakeSource());
        LeaderboardSnapshot snapshot = manager.getSnapshot(BOARD_A);
        assertEquals(LeaderboardStatus.IDLE, snapshot.status());
        assertTrue(snapshot.entries().isEmpty());
    }

    @Test
    @DisplayName("First ensureFresh triggers exactly one fetch and lands on LOADED")
    void firstEnsureFreshTriggersAFetch() {
        FakeSource source = new FakeSource();
        LeaderboardManager manager = new LeaderboardManager(source);

        manager.ensureFresh(BOARD_A);
        awaitStatus(manager, BOARD_A, LeaderboardStatus.LOADED, 2000);

        assertEquals(1, source.callCount.get());
        assertFalse(manager.getSnapshot(BOARD_A).entries().isEmpty());
    }

    @Test
    @DisplayName("Repeated ensureFresh calls within the auto-refresh window never refetch")
    void repeatedRenderCallsDoNotRefetchWithinWindow() {
        FakeSource source = new FakeSource();
        LeaderboardManager manager = new LeaderboardManager(source);

        manager.ensureFresh(BOARD_A);
        awaitStatus(manager, BOARD_A, LeaderboardStatus.LOADED, 2000);

        for (int i = 0; i < 20; i++) {
            manager.ensureFresh(BOARD_A); // simulates 20 render frames while the tab stays open
        }

        assertEquals(1, source.callCount.get(), "cycling/re-rendering the same already-fresh board must never spam requests");
    }

    @Test
    @DisplayName("Switching to a different, already-cached board never triggers a fetch and never mixes data")
    void switchingToADifferentCachedBoardIsInstant() {
        FakeSource source = new FakeSource();
        source.behavior = def -> new LeaderboardFetchResult.Success(
                List.of(LeaderboardEntry.of(1, "Entity-" + def.id(), "1 coins", "Coins")));
        LeaderboardManager manager = new LeaderboardManager(source);

        manager.ensureFresh(BOARD_A);
        awaitStatus(manager, BOARD_A, LeaderboardStatus.LOADED, 2000);
        manager.ensureFresh(BOARD_B);
        awaitStatus(manager, BOARD_B, LeaderboardStatus.LOADED, 2000);

        assertEquals("Entity-" + BOARD_A.id(), manager.getSnapshot(BOARD_A).entries().get(0).displayName());
        assertEquals("Entity-" + BOARD_B.id(), manager.getSnapshot(BOARD_B).entries().get(0).displayName());

        // Re-selecting A again - still within its own window, must be instant (no new fetch).
        int before = source.callCountByBoard.get(BOARD_A.id()).get();
        manager.ensureFresh(BOARD_A);
        assertEquals(before, source.callCountByBoard.get(BOARD_A.id()).get());
    }

    /** A test-controllable clock - {@link LeaderboardManager}'s package-private constructor accepts
     * this so cooldown-EXPIRY behavior can be proven deterministically, without ever sleeping a
     * real 60/12 seconds. */
    private static Supplier<Instant> mutableClock(AtomicReference<Instant> now) {
        return now::get;
    }

    @Test
    @DisplayName("A failed refresh with prior cached entries becomes STALE and keeps the old entries - never LIVE")
    void staleCacheRemainsVisibleOnNetworkFailureAndIsNeverLive() {
        FakeSource source = new FakeSource();
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-09-13T12:00:00Z"));
        LeaderboardManager manager = new LeaderboardManager(source, mutableClock(now));

        manager.ensureFresh(BOARD_A);
        awaitStatus(manager, BOARD_A, LeaderboardStatus.LOADED, 2000);
        List<LeaderboardEntry> originalEntries = manager.getSnapshot(BOARD_A).entries();

        // Advance the fake clock well past both the manual cooldown and the auto-refresh interval,
        // then flip the source to failing - proves STALE (not a fresh failure state) once the
        // now-overdue board is refreshed again.
        now.set(now.get().plusSeconds(90));
        source.behavior = def -> new LeaderboardFetchResult.Unavailable("GameZone är otillgänglig just nu.");
        manager.ensureFresh(BOARD_A);
        awaitStatus(manager, BOARD_A, LeaderboardStatus.STALE, 2000);

        LeaderboardSnapshot snapshot = manager.getSnapshot(BOARD_A);
        assertEquals(LeaderboardStatus.STALE, snapshot.status());
        assertEquals(originalEntries, snapshot.entries(), "STALE must keep the previous successful entries, never clear them");
        assertFalse(snapshot.status().isLive(), "STALE must never be reported as LIVE");
    }

    @Test
    @DisplayName("ensureFresh refetches once the auto-refresh interval has genuinely elapsed")
    void ensureFreshRefetchesAfterIntervalElapses() {
        FakeSource source = new FakeSource();
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-09-13T12:00:00Z"));
        LeaderboardManager manager = new LeaderboardManager(source, mutableClock(now));

        manager.ensureFresh(BOARD_A);
        awaitStatus(manager, BOARD_A, LeaderboardStatus.LOADED, 2000);
        assertEquals(1, source.callCount.get());

        now.set(now.get().plusSeconds(30)); // still within the 60s window
        manager.ensureFresh(BOARD_A);
        assertEquals(1, source.callCount.get(), "30s < 60s window - must not refetch yet");

        now.set(now.get().plusSeconds(31)); // now 61s total - window has elapsed
        manager.ensureFresh(BOARD_A);
        awaitCallCount(source, 2, 2000);
        assertEquals(2, source.callCount.get());
    }

    private static void awaitCallCount(FakeSource source, int expected, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (source.callCount.get() >= expected) return;
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        fail("Timed out waiting for callCount >= " + expected + " - last seen: " + source.callCount.get());
    }

    @Test
    @DisplayName("A board with no prior entries that fails becomes UNAVAILABLE, not STALE")
    void neverSucceededBoardThatFailsBecomesUnavailable() {
        FakeSource source = new FakeSource();
        source.behavior = def -> new LeaderboardFetchResult.Unavailable("offline");
        LeaderboardManager manager = new LeaderboardManager(source);

        manager.ensureFresh(BOARD_A);
        awaitStatus(manager, BOARD_A, LeaderboardStatus.UNAVAILABLE, 2000);

        assertTrue(manager.getSnapshot(BOARD_A).entries().isEmpty());
    }

    @Test
    @DisplayName("An INCOMPATIBLE result with no prior entries reports INCOMPATIBLE, distinct from UNAVAILABLE")
    void incompatibleResultReported() {
        FakeSource source = new FakeSource();
        source.behavior = def -> new LeaderboardFetchResult.Incompatible("structure changed");
        LeaderboardManager manager = new LeaderboardManager(source);

        manager.ensureFresh(BOARD_A);
        awaitStatus(manager, BOARD_A, LeaderboardStatus.INCOMPATIBLE, 2000);
    }

    @Test
    @DisplayName("manualRefresh on a never-fetched board triggers a fetch immediately")
    void manualRefreshWorksOnFreshBoard() {
        FakeSource source = new FakeSource();
        LeaderboardManager manager = new LeaderboardManager(source);

        boolean triggered = manager.manualRefresh(BOARD_A);

        assertTrue(triggered);
        awaitStatus(manager, BOARD_A, LeaderboardStatus.LOADED, 2000);
        assertEquals(1, source.callCount.get());
    }

    @Test
    @DisplayName("A second manualRefresh within the manual cooldown window is a no-op")
    void manualRefreshCooldownBlocksRapidReclicks() {
        FakeSource source = new FakeSource();
        LeaderboardManager manager = new LeaderboardManager(source);

        assertTrue(manager.manualRefresh(BOARD_A));
        awaitStatus(manager, BOARD_A, LeaderboardStatus.LOADED, 2000);

        boolean secondTriggered = manager.manualRefresh(BOARD_A); // immediately after - well within the 12s cooldown
        assertFalse(secondTriggered);
        assertEquals(1, source.callCount.get());
        assertFalse(manager.canManualRefresh(BOARD_A));
    }

    @Test
    @DisplayName("Two concurrent triggers for the same board are coalesced into exactly one in-flight fetch")
    void concurrentRefreshIsCoalesced() throws InterruptedException {
        FakeSource source = new FakeSource();
        source.blockUntil = new CountDownLatch(1);
        LeaderboardManager manager = new LeaderboardManager(source);

        boolean first = manager.manualRefresh(BOARD_A);
        // A second trigger while the first fetch is still blocked in-flight must not start another.
        Thread.sleep(20);
        boolean second = manager.manualRefresh(BOARD_A);

        assertTrue(first);
        assertFalse(second, "a fetch already in flight must coalesce a concurrent trigger, never start a second one");

        source.blockUntil.countDown();
        awaitStatus(manager, BOARD_A, LeaderboardStatus.LOADED, 2000);
        assertEquals(1, source.callCount.get());
    }

    @Test
    @DisplayName("One board's failure never affects another board's independent cache")
    void oneBoardFailureDoesNotAffectAnother() {
        FakeSource source = new FakeSource();
        source.behavior = def -> def.id().equals(BOARD_A.id())
                ? new LeaderboardFetchResult.Unavailable("A is down")
                : new LeaderboardFetchResult.Success(List.of(LeaderboardEntry.of(1, "Beta-entity", "1 coins", "Coins")));
        LeaderboardManager manager = new LeaderboardManager(source);

        manager.ensureFresh(BOARD_A);
        manager.ensureFresh(BOARD_B);
        awaitStatus(manager, BOARD_A, LeaderboardStatus.UNAVAILABLE, 2000);
        awaitStatus(manager, BOARD_B, LeaderboardStatus.LOADED, 2000);

        assertEquals(LeaderboardStatus.UNAVAILABLE, manager.getSnapshot(BOARD_A).status());
        assertEquals(LeaderboardStatus.LOADED, manager.getSnapshot(BOARD_B).status());
        assertFalse(manager.getSnapshot(BOARD_B).entries().isEmpty());
    }

    @Test
    @DisplayName("Diagnostics summary never includes full entry data - only counts and metadata")
    void diagnosticsSummaryIsMetadataOnly() {
        FakeSource source = new FakeSource();
        LeaderboardManager manager = new LeaderboardManager(source);
        manager.ensureFresh(BOARD_A);
        awaitStatus(manager, BOARD_A, LeaderboardStatus.LOADED, 2000);

        LeaderboardManager.DiagnosticsSummary summary = manager.getDiagnosticsSummary();
        assertEquals(1, summary.cachedBoardCount());
        assertNotNull(summary.lastSuccessfulRefreshAt());
        assertNull(summary.lastErrorCategory());
        assertEquals(LeaderboardManager.ADAPTER_VERSION, summary.adapterVersion());
    }
}
