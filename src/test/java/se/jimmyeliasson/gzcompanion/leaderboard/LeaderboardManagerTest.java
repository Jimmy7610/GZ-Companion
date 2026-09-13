package se.jimmyeliasson.gzcompanion.leaderboard;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import se.jimmyeliasson.gzcompanion.gamezone.net.GameZoneLiveDataRuntime;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
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

    /** A fresh shared runtime per test instance (JUnit 5 creates a new test class instance per
     * test method by default) - proves nothing here depends on LeaderboardManager owning its own
     * executor anymore, and mirrors production's "one runtime, shared by every GameZone live-data
     * feature" design without letting tests leak scheduling state into each other. */
    private final GameZoneLiveDataRuntime runtime = new GameZoneLiveDataRuntime("0.1.0-test");

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
        LeaderboardManager manager = new LeaderboardManager(new FakeSource(), runtime);
        LeaderboardSnapshot snapshot = manager.getSnapshot(BOARD_A);
        assertEquals(LeaderboardStatus.IDLE, snapshot.status());
        assertTrue(snapshot.entries().isEmpty());
    }

    @Test
    @DisplayName("First ensureFresh triggers exactly one fetch and lands on LOADED")
    void firstEnsureFreshTriggersAFetch() {
        FakeSource source = new FakeSource();
        LeaderboardManager manager = new LeaderboardManager(source, runtime);

        manager.ensureFresh(BOARD_A);
        awaitStatus(manager, BOARD_A, LeaderboardStatus.LOADED, 2000);

        assertEquals(1, source.callCount.get());
        assertFalse(manager.getSnapshot(BOARD_A).entries().isEmpty());
    }

    @Test
    @DisplayName("Repeated ensureFresh calls within the auto-refresh window never refetch")
    void repeatedRenderCallsDoNotRefetchWithinWindow() {
        FakeSource source = new FakeSource();
        LeaderboardManager manager = new LeaderboardManager(source, runtime);

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
        LeaderboardManager manager = new LeaderboardManager(source, runtime);

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
        LeaderboardManager manager = new LeaderboardManager(source, runtime, mutableClock(now));

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
        LeaderboardManager manager = new LeaderboardManager(source, runtime, mutableClock(now));

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
        LeaderboardManager manager = new LeaderboardManager(source, runtime);

        manager.ensureFresh(BOARD_A);
        awaitStatus(manager, BOARD_A, LeaderboardStatus.UNAVAILABLE, 2000);

        assertTrue(manager.getSnapshot(BOARD_A).entries().isEmpty());
    }

    @Test
    @DisplayName("An INCOMPATIBLE result with no prior entries reports INCOMPATIBLE, distinct from UNAVAILABLE")
    void incompatibleResultReported() {
        FakeSource source = new FakeSource();
        source.behavior = def -> new LeaderboardFetchResult.Incompatible("structure changed");
        LeaderboardManager manager = new LeaderboardManager(source, runtime);

        manager.ensureFresh(BOARD_A);
        awaitStatus(manager, BOARD_A, LeaderboardStatus.INCOMPATIBLE, 2000);
    }

    @Test
    @DisplayName("manualRefresh on a never-fetched board triggers a fetch immediately")
    void manualRefreshWorksOnFreshBoard() {
        FakeSource source = new FakeSource();
        LeaderboardManager manager = new LeaderboardManager(source, runtime);

        boolean triggered = manager.manualRefresh(BOARD_A);

        assertTrue(triggered);
        awaitStatus(manager, BOARD_A, LeaderboardStatus.LOADED, 2000);
        assertEquals(1, source.callCount.get());
    }

    @Test
    @DisplayName("A second manualRefresh within the manual cooldown window is a no-op")
    void manualRefreshCooldownBlocksRapidReclicks() {
        FakeSource source = new FakeSource();
        LeaderboardManager manager = new LeaderboardManager(source, runtime);

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
        LeaderboardManager manager = new LeaderboardManager(source, runtime);

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
        LeaderboardManager manager = new LeaderboardManager(source, runtime);

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
        LeaderboardManager manager = new LeaderboardManager(source, runtime);
        manager.ensureFresh(BOARD_A);
        awaitStatus(manager, BOARD_A, LeaderboardStatus.LOADED, 2000);

        LeaderboardManager.DiagnosticsSummary summary = manager.getDiagnosticsSummary();
        assertEquals(1, summary.cachedBoardCount());
        assertNotNull(summary.lastSuccessfulRefreshAt());
        assertNull(summary.lastErrorCategory());
        assertEquals(LeaderboardManager.ADAPTER_VERSION, summary.adapterVersion());
    }

    // ------------------------------------------------------------------
    // Blocker 2 (2026-09-13 follow-up review): rapid selector cycling must never queue every
    // intermediate board's fetch - only the LATEST relevant request survives once the currently
    // active fetch finishes. See LeaderboardManager's own "latest relevant request wins" doc
    // comment for the full policy this proves.
    // ------------------------------------------------------------------

    private static final LeaderboardDefinition BOARD_C = GameZoneLeaderboardRegistry.byId("company_wealth").orElseThrow();
    private static final LeaderboardDefinition BOARD_D = GameZoneLeaderboardRegistry.byId("player_kills").orElseThrow();
    private static final LeaderboardDefinition BOARD_E = GameZoneLeaderboardRegistry.byId("player_deaths").orElseThrow();

    @Test
    @DisplayName("Rapid cycling A->B->C->D while A is in flight results in exactly 2 network fetches: A, then D - B and C never hit the network")
    void rapidCyclingCoalescesToOnlyTheLatestRequest() {
        FakeSource source = new FakeSource();
        List<String> fetchOrder = Collections.synchronizedList(new ArrayList<>());
        source.behavior = def -> {
            fetchOrder.add(def.id());
            return new LeaderboardFetchResult.Success(List.of(LeaderboardEntry.of(1, "Entity-" + def.id(), "1 coins", "Coins")));
        };
        source.blockUntil = new CountDownLatch(1); // blocks whichever fetch is actually running (A's)
        LeaderboardManager manager = new LeaderboardManager(source, runtime);

        // activeFetch is assigned SYNCHRONOUSLY inside ensureFresh (before the executor thread even
        // starts running), so each of these calls deterministically sees the previous one's effect
        // with no sleep/race needed - all on this single test thread.
        manager.ensureFresh(BOARD_A);   // becomes the active fetch; blocks inside FakeSource.fetch
        manager.ensureFresh(BOARD_B);   // A still active -> B becomes pending
        manager.ensureFresh(BOARD_C);   // C supersedes B in the pending slot
        manager.ensureFresh(BOARD_D);   // D supersedes C in the pending slot

        source.blockUntil.countDown(); // let A's fetch complete, which should immediately advance to D
        awaitStatus(manager, BOARD_D, LeaderboardStatus.LOADED, 2000);

        assertEquals(List.of(BOARD_A.id(), BOARD_D.id()), fetchOrder, "only A then D should have ever reached the network - B and C must be dropped, not queued");
        assertEquals(2, source.callCount.get(), "exactly 2 network fetches total, not 4");
        assertEquals(LeaderboardStatus.IDLE, manager.getSnapshot(BOARD_B).status(), "B must never be left LOADING - it was superseded before ever becoming the active fetch");
        assertEquals(LeaderboardStatus.IDLE, manager.getSnapshot(BOARD_C).status(), "C must never be left LOADING either");
        assertEquals(LeaderboardStatus.LOADED, manager.getSnapshot(BOARD_D).status());
    }

    @Test
    @DisplayName("Repeated ensureFresh for the SAME board while it is already the active fetch still coalesces (no duplicate pending entry, no extra network call)")
    void sameBoardCoalescingStillWorksViaEnsureFresh() {
        FakeSource source = new FakeSource();
        source.blockUntil = new CountDownLatch(1);
        LeaderboardManager manager = new LeaderboardManager(source, runtime);

        manager.ensureFresh(BOARD_A);
        manager.ensureFresh(BOARD_A); // same board, still active - must coalesce, not become "pending"
        manager.ensureFresh(BOARD_A);

        source.blockUntil.countDown();
        awaitStatus(manager, BOARD_A, LeaderboardStatus.LOADED, 2000);

        assertEquals(1, source.callCount.get());
    }

    @Test
    @DisplayName("A manual request claims the pending slot even over an already-pending AUTO request")
    void manualRequestOverridesPendingAutoRequest() {
        FakeSource source = new FakeSource();
        List<String> fetchOrder = Collections.synchronizedList(new ArrayList<>());
        source.behavior = def -> {
            fetchOrder.add(def.id());
            return new LeaderboardFetchResult.Success(List.of(LeaderboardEntry.of(1, "Entity", "1", "Coins")));
        };
        source.blockUntil = new CountDownLatch(1);
        LeaderboardManager manager = new LeaderboardManager(source, runtime);

        manager.ensureFresh(BOARD_A);           // active
        manager.ensureFresh(BOARD_B);           // pending (auto)
        boolean manualAccepted = manager.manualRefresh(BOARD_E); // manual - must override B in the pending slot

        source.blockUntil.countDown();
        awaitStatus(manager, BOARD_E, LeaderboardStatus.LOADED, 2000);

        assertTrue(manualAccepted);
        assertEquals(List.of(BOARD_A.id(), BOARD_E.id()), fetchOrder, "the manual request for E must win the pending slot over the earlier auto request for B");
        assertEquals(LeaderboardStatus.IDLE, manager.getSnapshot(BOARD_B).status());
    }

    @Test
    @DisplayName("An auto request never displaces an already-pending MANUAL request")
    void autoRequestNeverDisplacesPendingManualRequest() {
        FakeSource source = new FakeSource();
        List<String> fetchOrder = Collections.synchronizedList(new ArrayList<>());
        source.behavior = def -> {
            fetchOrder.add(def.id());
            return new LeaderboardFetchResult.Success(List.of(LeaderboardEntry.of(1, "Entity", "1", "Coins")));
        };
        source.blockUntil = new CountDownLatch(1);
        LeaderboardManager manager = new LeaderboardManager(source, runtime);

        manager.ensureFresh(BOARD_A);            // active
        manager.manualRefresh(BOARD_E);          // pending (manual)
        manager.ensureFresh(BOARD_B);            // auto - must NOT displace the pending manual request for E
        manager.ensureFresh(BOARD_C);            // auto - same, must not displace E either

        source.blockUntil.countDown();
        awaitStatus(manager, BOARD_E, LeaderboardStatus.LOADED, 2000);

        assertEquals(List.of(BOARD_A.id(), BOARD_E.id()), fetchOrder, "B and C must never displace the already-pending manual request for E");
        assertEquals(2, source.callCount.get());
    }

    @Test
    @DisplayName("No unbounded pending jobs are created regardless of how many boards are cycled through")
    void noUnboundedPendingJobsRegardlessOfCycleLength() {
        FakeSource source = new FakeSource();
        source.blockUntil = new CountDownLatch(1);
        LeaderboardManager manager = new LeaderboardManager(source, runtime);
        List<LeaderboardDefinition> allBoards = GameZoneLeaderboardRegistry.all();

        manager.ensureFresh(allBoards.get(0)); // active
        for (LeaderboardDefinition def : allBoards) {
            manager.ensureFresh(def); // 27 rapid requests - at most one may ever become "pending"
        }

        source.blockUntil.countDown();
        // Whichever board ended up as the final pending one must reach LOADED; total fetches must
        // be small (2: the active one plus exactly one advance), never anywhere near 27.
        long deadline = System.currentTimeMillis() + 2000;
        while (System.currentTimeMillis() < deadline && source.callCount.get() < 2) {
            try { Thread.sleep(5); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
        }
        assertEquals(2, source.callCount.get(), "cycling through all 27 boards while one fetch is active must still only ever produce 2 total network fetches");
    }

    // ------------------------------------------------------------------
    // Shared GameZone live-data runtime (2026-09-13 performance-foundation follow-up): proves
    // LeaderboardManager no longer owns its own ExecutorService, the shared runtime stays lazy
    // until an actual fetch happens, and multiple managers can share the exact same runtime
    // without creating extra workers or interfering with each other's scheduling.
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Constructing a LeaderboardManager does not create the shared GameZone worker thread and performs zero fetch work")
    void constructingManagerDoesNotTriggerAnyFetch() {
        GameZoneLiveDataRuntime freshRuntime = new GameZoneLiveDataRuntime("0.1.0-test");
        FakeSource source = new FakeSource();
        new LeaderboardManager(source, freshRuntime);

        assertFalse(freshRuntime.isExecutorInitializedForTesting(),
                "merely constructing a LeaderboardManager must not create the shared runtime's executor");
        assertEquals(0, source.callCount.get(), "merely constructing a LeaderboardManager must not perform any fetch");
    }

    @Test
    @DisplayName("The shared executor is created only once an actual fetch is requested, then reused, never recreated")
    void sharedExecutorIsCreatedLazilyThenMemoized() {
        GameZoneLiveDataRuntime freshRuntime = new GameZoneLiveDataRuntime("0.1.0-test");
        FakeSource source = new FakeSource();
        LeaderboardManager manager = new LeaderboardManager(source, freshRuntime);

        assertFalse(freshRuntime.isExecutorInitializedForTesting(), "no worker before any fetch is requested");

        manager.ensureFresh(BOARD_A);
        awaitStatus(manager, BOARD_A, LeaderboardStatus.LOADED, 2000);

        assertTrue(freshRuntime.isExecutorInitializedForTesting(), "the shared worker must exist once a fetch actually ran");
    }

    @Test
    @DisplayName("Two independent managers sharing the same runtime both dispatch through the identical single executor thread")
    void multipleConsumersShareTheSameExecutorThread() throws InterruptedException {
        GameZoneLiveDataRuntime sharedRuntime = new GameZoneLiveDataRuntime("0.1.0-test");
        List<Thread> observedThreads = Collections.synchronizedList(new ArrayList<>());

        FakeSource sourceOne = new FakeSource();
        sourceOne.behavior = def -> {
            observedThreads.add(Thread.currentThread());
            return new LeaderboardFetchResult.Success(List.of(LeaderboardEntry.of(1, "One", "1", "Coins")));
        };
        FakeSource sourceTwo = new FakeSource();
        sourceTwo.behavior = def -> {
            observedThreads.add(Thread.currentThread());
            return new LeaderboardFetchResult.Success(List.of(LeaderboardEntry.of(1, "Two", "1", "Coins")));
        };

        // Simulates two different GameZone live-data features (e.g. Leaderboards and a future
        // Bounty Board) each owning their own manager but sharing the ONE runtime, exactly as
        // CompanionSession wires Leaderboards today.
        LeaderboardManager managerOne = new LeaderboardManager(sourceOne, sharedRuntime);
        LeaderboardManager managerTwo = new LeaderboardManager(sourceTwo, sharedRuntime);

        managerOne.ensureFresh(BOARD_A);
        awaitStatus(managerOne, BOARD_A, LeaderboardStatus.LOADED, 2000);
        managerTwo.ensureFresh(BOARD_B);
        awaitStatus(managerTwo, BOARD_B, LeaderboardStatus.LOADED, 2000);

        assertEquals(2, observedThreads.size());
        assertEquals("gzcompanion-gamezone-live", observedThreads.get(0).getName());
        assertSame(observedThreads.get(0), observedThreads.get(1),
                "both managers must dispatch through the exact same shared worker thread, never one each");
    }

    @Test
    @DisplayName("Reopening/reconstructing a manager against the same runtime never creates a second worker")
    void reconstructingManagerNeverCreatesAnotherWorker() {
        GameZoneLiveDataRuntime sharedRuntime = new GameZoneLiveDataRuntime("0.1.0-test");
        List<Thread> observedThreads = Collections.synchronizedList(new ArrayList<>());

        FakeSource firstSource = new FakeSource();
        firstSource.behavior = def -> {
            observedThreads.add(Thread.currentThread());
            return new LeaderboardFetchResult.Success(List.of(LeaderboardEntry.of(1, "One", "1", "Coins")));
        };
        LeaderboardManager firstOpen = new LeaderboardManager(firstSource, sharedRuntime);
        firstOpen.ensureFresh(BOARD_A);
        awaitStatus(firstOpen, BOARD_A, LeaderboardStatus.LOADED, 2000);
        assertTrue(sharedRuntime.isExecutorInitializedForTesting());

        // Simulates the player closing and reopening the Companion UI - a fresh LeaderboardManager-
        // like consumer object is created, but it is handed the SAME session-scoped runtime.
        FakeSource secondSource = new FakeSource();
        secondSource.behavior = def -> {
            observedThreads.add(Thread.currentThread());
            return new LeaderboardFetchResult.Success(List.of(LeaderboardEntry.of(1, "Two", "1", "Coins")));
        };
        LeaderboardManager secondOpen = new LeaderboardManager(secondSource, sharedRuntime);
        secondOpen.ensureFresh(BOARD_B);
        awaitStatus(secondOpen, BOARD_B, LeaderboardStatus.LOADED, 2000);

        assertEquals(2, observedThreads.size());
        assertSame(observedThreads.get(0), observedThreads.get(1), "the same worker thread must be reused, never recreated");
    }

    @Test
    @DisplayName("If the shared runtime rejects a fetch (its bounded queue is saturated), the board reverts to its previous status instead of being stuck LOADING, and a later request can still succeed")
    void rejectedSubmissionRevertsSnapshotInsteadOfStickingInLoading() throws InterruptedException {
        GameZoneLiveDataRuntime sharedRuntime = new GameZoneLiveDataRuntime("0.1.0-test");

        // Saturate the shared runtime's bounded queue from outside LeaderboardManager entirely,
        // simulating several other GameZone live-data modules hammering it at once.
        CountDownLatch blockWorker = new CountDownLatch(1);
        CountDownLatch workerEntered = new CountDownLatch(1);
        assertTrue(sharedRuntime.submit(() -> {
            workerEntered.countDown();
            try {
                blockWorker.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));
        assertTrue(workerEntered.await(2, TimeUnit.SECONDS));
        for (int i = 0; i < GameZoneLiveDataRuntime.MAX_QUEUED_JOBS; i++) {
            assertTrue(sharedRuntime.submit(() -> {}));
        }

        FakeSource source = new FakeSource();
        LeaderboardManager manager = new LeaderboardManager(source, sharedRuntime);
        LeaderboardSnapshot before = manager.getSnapshot(BOARD_A);

        manager.ensureFresh(BOARD_A); // must be rejected - the shared queue is completely full

        assertEquals(before.status(), manager.getSnapshot(BOARD_A).status(),
                "a rejected fetch must revert to the board's previous status, never stay stuck LOADING");
        assertEquals(0, source.callCount.get(), "a rejected job must never actually run the fetch");

        blockWorker.countDown(); // let the blocking job and queued no-ops start draining

        // Deterministically wait for the shared queue to fully drain: keep retrying a marker
        // submission until accepted (proves there is finally room), then wait for it to actually
        // run (proves everything queued ahead of it, including the former blocker, is done).
        CountDownLatch drained = new CountDownLatch(1);
        long deadline = System.currentTimeMillis() + 2000;
        boolean markerAccepted = false;
        while (System.currentTimeMillis() < deadline && !markerAccepted) {
            markerAccepted = sharedRuntime.submit(drained::countDown);
            if (!markerAccepted) {
                Thread.sleep(5);
            }
        }
        assertTrue(markerAccepted, "the shared queue must eventually have room once the blocking job and queued no-ops drain");
        assertTrue(drained.await(2, TimeUnit.SECONDS));

        // A later request, once the shared runtime is no longer saturated, must still work normally.
        manager.ensureFresh(BOARD_A);
        awaitStatus(manager, BOARD_A, LeaderboardStatus.LOADED, 2000);
        assertEquals(1, source.callCount.get());
    }

    @Test
    @DisplayName("REQUIRED: manualRefresh returns false, not true, when the immediate active submission is actually rejected by the shared runtime")
    void manualRefreshReturnsFalseWhenImmediateSubmissionIsRejected() throws InterruptedException {
        GameZoneLiveDataRuntime sharedRuntime = new GameZoneLiveDataRuntime("0.1.0-test");

        CountDownLatch blockWorker = new CountDownLatch(1);
        CountDownLatch workerEntered = new CountDownLatch(1);
        assertTrue(sharedRuntime.submit(() -> {
            workerEntered.countDown();
            try {
                blockWorker.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));
        assertTrue(workerEntered.await(2, TimeUnit.SECONDS));
        for (int i = 0; i < GameZoneLiveDataRuntime.MAX_QUEUED_JOBS; i++) {
            assertTrue(sharedRuntime.submit(() -> {}));
        }

        FakeSource source = new FakeSource();
        LeaderboardManager manager = new LeaderboardManager(source, sharedRuntime);

        // BOARD_A has no activeFetch yet, so manualRefresh takes the IMMEDIATE-active path
        // (requestFetch's activeFetch == null branch) - the exact path that previously always
        // returned true regardless of whether the shared runtime actually accepted the job.
        boolean result = manager.manualRefresh(BOARD_A);

        assertFalse(result, "manualRefresh must return false when the immediate submission is genuinely rejected, honoring its own \"true means accepted\" contract");
        assertEquals(LeaderboardStatus.IDLE, manager.getSnapshot(BOARD_A).status(), "the board must revert cleanly, never stuck LOADING");
        assertEquals(0, source.callCount.get());

        blockWorker.countDown();
    }
}
