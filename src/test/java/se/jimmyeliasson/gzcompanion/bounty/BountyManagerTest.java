package se.jimmyeliasson.gzcompanion.bounty;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import se.jimmyeliasson.gzcompanion.gamezone.net.GameZoneLiveDataRuntime;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link BountyManager}'s cache/fetch state machine - mirrors {@code LeaderboardManagerTest}'s
 * {@code FakeSource}-with-a-latch convention, simplified for a single homogeneous registry (no
 * per-board keying, no pending slot - see {@code BountyManager}'s own class doc comment for why
 * "coalesce, don't queue" is the whole policy here).
 */
class BountyManagerTest {

    private static final class FakeSource implements BountySource {
        final AtomicInteger callCount = new AtomicInteger();
        volatile Function<Void, BountyFetchResult> behavior =
                v -> new BountyFetchResult.Success(List.of(new BountyEntry("Alfa", null, 100, null, "ACTIVE", null, BountyExpiry.UNKNOWN)));
        volatile CountDownLatch blockUntil;
        volatile CountDownLatch enteredFetch;

        @Override
        public BountyFetchResult fetch() {
            callCount.incrementAndGet();
            CountDownLatch entered = enteredFetch;
            if (entered != null) entered.countDown();
            CountDownLatch latch = blockUntil;
            if (latch != null) {
                try {
                    latch.await(2, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return behavior.apply(null);
        }
    }

    private final GameZoneLiveDataRuntime runtime = new GameZoneLiveDataRuntime("0.1.0-test");

    private static void awaitStatus(BountyManager manager, BountyStatus expected, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (manager.getSnapshot().status() == expected) return;
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        fail("Timed out waiting for status " + expected + " - last seen: " + manager.getSnapshot().status());
    }

    private static Supplier<Instant> mutableClock(AtomicReference<Instant> now) {
        return now::get;
    }

    @Test
    @DisplayName("Constructing a manager performs zero fetch work")
    void constructingManagerCausesNoRequest() {
        FakeSource source = new FakeSource();
        BountyManager manager = new BountyManager(source, runtime);

        assertEquals(BountyStatus.IDLE, manager.getSnapshot().status());
        assertEquals(0, source.callCount.get(), "merely constructing a BountyManager must not perform any fetch");
    }

    @Test
    @DisplayName("A never-fetched registry starts IDLE with no entries")
    void neverFetchedIsIdle() {
        BountyManager manager = new BountyManager(new FakeSource(), runtime);
        BountySnapshot snapshot = manager.getSnapshot();
        assertEquals(BountyStatus.IDLE, snapshot.status());
        assertTrue(snapshot.entries().isEmpty());
    }

    @Test
    @DisplayName("First ensureFresh triggers exactly one fetch and lands on LOADED")
    void firstEnsureFreshTriggersOneFetch() {
        FakeSource source = new FakeSource();
        BountyManager manager = new BountyManager(source, runtime);

        manager.ensureFresh();
        awaitStatus(manager, BountyStatus.LOADED, 2000);

        assertEquals(1, source.callCount.get());
        assertFalse(manager.getSnapshot().entries().isEmpty());
    }

    @Test
    @DisplayName("Repeated ensureFresh calls within the 60s auto-refresh window never refetch (simulates render-loop calls)")
    void repeatedEnsureFreshWithinWindowDoesNotRefetch() {
        FakeSource source = new FakeSource();
        BountyManager manager = new BountyManager(source, runtime);

        manager.ensureFresh();
        awaitStatus(manager, BountyStatus.LOADED, 2000);

        for (int i = 0; i < 20; i++) {
            manager.ensureFresh(); // simulates 20 render frames while the tab stays open
        }

        assertEquals(1, source.callCount.get(), "cycling/re-rendering the tab must never spam requests");
    }

    @Test
    @DisplayName("ensureFresh refetches once the 60s auto-refresh interval has genuinely elapsed")
    void ensureFreshRefetchesAfterIntervalElapses() {
        FakeSource source = new FakeSource();
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-09-13T12:00:00Z"));
        BountyManager manager = new BountyManager(source, runtime, mutableClock(now));

        manager.ensureFresh();
        awaitStatus(manager, BountyStatus.LOADED, 2000);
        assertEquals(1, source.callCount.get());

        now.set(now.get().plusSeconds(30)); // still within the 60s window
        manager.ensureFresh();
        assertEquals(1, source.callCount.get(), "30s < 60s window - must not refetch yet");

        now.set(now.get().plusSeconds(31)); // now 61s total - window elapsed
        manager.ensureFresh();
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
        fail("Timed out waiting for callCount >= " + expected);
    }

    @Test
    @DisplayName("manualRefresh works on a never-fetched registry")
    void manualRefreshWorksOnFreshRegistry() {
        FakeSource source = new FakeSource();
        BountyManager manager = new BountyManager(source, runtime);

        assertTrue(manager.manualRefresh());
        awaitStatus(manager, BountyStatus.LOADED, 2000);
        assertEquals(1, source.callCount.get());
    }

    @Test
    @DisplayName("A second manualRefresh within the 12s manual cooldown window is a no-op")
    void manualRefreshCooldownBlocksRapidReclicks() {
        FakeSource source = new FakeSource();
        BountyManager manager = new BountyManager(source, runtime);

        assertTrue(manager.manualRefresh());
        awaitStatus(manager, BountyStatus.LOADED, 2000);

        assertFalse(manager.manualRefresh(), "well within the 12s cooldown");
        assertEquals(1, source.callCount.get());
        assertFalse(manager.canManualRefresh());
    }

    @Test
    @DisplayName("Only one fetch is ever active - a duplicate request while active coalesces (no pending queue)")
    void duplicateRequestWhileActiveCoalesces() throws InterruptedException {
        FakeSource source = new FakeSource();
        source.blockUntil = new CountDownLatch(1);
        source.enteredFetch = new CountDownLatch(1);
        BountyManager manager = new BountyManager(source, runtime);

        manager.ensureFresh(); // becomes active, blocks
        assertTrue(source.enteredFetch.await(2, TimeUnit.SECONDS));

        // Several more rapid requests while the first is still in flight - all must coalesce.
        for (int i = 0; i < 10; i++) {
            manager.ensureFresh();
        }
        boolean manualDuringActive = manager.manualRefresh();

        source.blockUntil.countDown();
        awaitStatus(manager, BountyStatus.LOADED, 2000);

        assertFalse(manualDuringActive, "a fetch already in flight must coalesce a concurrent manual trigger too - no pending slot exists");
        assertEquals(1, source.callCount.get(), "only the original fetch should have ever reached the network");
    }

    @Test
    @DisplayName("If the shared runtime rejects the submission, the snapshot reverts and the cooldown is NOT started")
    void runtimeRejectionRestoresSnapshotAndDoesNotStartCooldown() throws InterruptedException {
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
        BountyManager manager = new BountyManager(source, sharedRuntime);
        BountySnapshot before = manager.getSnapshot();

        manager.ensureFresh(); // must be rejected - the shared queue is completely full

        assertEquals(before, manager.getSnapshot(), "a rejected fetch must revert to the exact previous snapshot, never stuck LOADING");
        assertEquals(0, source.callCount.get());
        assertTrue(manager.canManualRefresh(), "a rejected job must not start the cooldown - a later request must be able to try immediately");

        blockWorker.countDown();
    }

    @Test
    @DisplayName("A failed refresh with prior cached entries becomes STALE and keeps the old entries - never LIVE")
    void staleCacheRemainsVisibleOnFailureAndIsNeverLive() {
        FakeSource source = new FakeSource();
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-09-13T12:00:00Z"));
        BountyManager manager = new BountyManager(source, runtime, mutableClock(now));

        manager.ensureFresh();
        awaitStatus(manager, BountyStatus.LOADED, 2000);
        List<BountyEntry> originalEntries = manager.getSnapshot().entries();

        now.set(now.get().plusSeconds(90));
        source.behavior = v -> new BountyFetchResult.Unavailable("GameZone är otillgänglig just nu.");
        manager.ensureFresh();
        awaitStatus(manager, BountyStatus.STALE, 2000);

        BountySnapshot snapshot = manager.getSnapshot();
        assertEquals(originalEntries, snapshot.entries(), "STALE must keep the previous successful entries");
        assertFalse(snapshot.status().isLive());
    }

    @Test
    @DisplayName("A successful fetch with ZERO active bounties is LOADED - a real success, never an error")
    void successfulEmptyResultIsLoadedNotError() {
        FakeSource source = new FakeSource();
        source.behavior = v -> new BountyFetchResult.Success(List.of());
        BountyManager manager = new BountyManager(source, runtime);

        manager.ensureFresh();
        awaitStatus(manager, BountyStatus.LOADED, 2000);

        BountySnapshot snapshot = manager.getSnapshot();
        assertEquals(BountyStatus.LOADED, snapshot.status());
        assertTrue(snapshot.entries().isEmpty());
        assertNull(snapshot.errorMessage());
    }

    @Test
    @DisplayName("A refresh failure following a genuine successful-but-empty result still becomes STALE (not UNAVAILABLE) - zero entries is still usable prior data")
    void failureAfterSuccessfulEmptyResultBecomesStale() {
        FakeSource source = new FakeSource();
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-09-13T12:00:00Z"));
        source.behavior = v -> new BountyFetchResult.Success(List.of());
        BountyManager manager = new BountyManager(source, runtime, mutableClock(now));

        manager.ensureFresh();
        awaitStatus(manager, BountyStatus.LOADED, 2000);
        assertTrue(manager.getSnapshot().entries().isEmpty());

        now.set(now.get().plusSeconds(90));
        source.behavior = v -> new BountyFetchResult.Unavailable("offline");
        manager.ensureFresh();
        awaitStatus(manager, BountyStatus.STALE, 2000);

        assertEquals(BountyStatus.STALE, manager.getSnapshot().status(),
                "a prior genuine empty SUCCESS must still count as usable data, not be treated the same as never having loaded");
    }

    @Test
    @DisplayName("A registry with no prior entries that fails becomes UNAVAILABLE, not STALE")
    void neverSucceededThatFailsBecomesUnavailable() {
        FakeSource source = new FakeSource();
        source.behavior = v -> new BountyFetchResult.Unavailable("offline");
        BountyManager manager = new BountyManager(source, runtime);

        manager.ensureFresh();
        awaitStatus(manager, BountyStatus.UNAVAILABLE, 2000);

        assertTrue(manager.getSnapshot().entries().isEmpty());
    }

    @Test
    @DisplayName("An INCOMPATIBLE result is reported distinctly from UNAVAILABLE")
    void incompatibleResultReported() {
        FakeSource source = new FakeSource();
        source.behavior = v -> new BountyFetchResult.Incompatible("structure changed");
        BountyManager manager = new BountyManager(source, runtime);

        manager.ensureFresh();
        awaitStatus(manager, BountyStatus.INCOMPATIBLE, 2000);
    }

    @Test
    @DisplayName("Diagnostics summary never includes clue text or full entry content - only counts and metadata")
    void diagnosticsSummaryIsMetadataOnly() {
        FakeSource source = new FakeSource();
        BountyManager manager = new BountyManager(source, runtime);
        manager.ensureFresh();
        awaitStatus(manager, BountyStatus.LOADED, 2000);

        BountyManager.DiagnosticsSummary summary = manager.getDiagnosticsSummary();
        assertEquals(1, summary.activeBountyCount());
        assertNotNull(summary.lastSuccessfulRefreshAt());
        assertNull(summary.lastErrorCategory());
        assertEquals(BountyManager.ADAPTER_VERSION, summary.adapterVersion());
    }

    @Test
    @DisplayName("No unbounded queue exists regardless of how many requests arrive while one is active")
    void noUnboundedQueueRegardlessOfRequestCount() throws InterruptedException {
        FakeSource source = new FakeSource();
        source.blockUntil = new CountDownLatch(1);
        source.enteredFetch = new CountDownLatch(1);
        BountyManager manager = new BountyManager(source, runtime);

        manager.ensureFresh();
        assertTrue(source.enteredFetch.await(2, TimeUnit.SECONDS));

        for (int i = 0; i < 100; i++) {
            manager.ensureFresh();
            manager.manualRefresh();
        }

        source.blockUntil.countDown();
        awaitStatus(manager, BountyStatus.LOADED, 2000);

        assertEquals(1, source.callCount.get(), "100 rapid requests while one fetch is active must still only ever produce 1 network fetch - no pending queue at all");
    }
}
