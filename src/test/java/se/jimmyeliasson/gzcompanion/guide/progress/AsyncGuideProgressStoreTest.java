package se.jimmyeliasson.gzcompanion.guide.progress;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import se.jimmyeliasson.gzcompanion.storage.LocalPersistenceRuntime;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link AsyncGuideProgressStore}'s coalescing/failure/reset/flush guarantees - the fix for the
 * alpha.4 audit's "Guide progress persistence can synchronously write to disk from the Minecraft
 * client tick thread" finding. Mirrors {@code LeaderboardManagerTest}'s FakeSource-with-a-latch
 * convention for deterministic (never sleep-and-hope) concurrency tests.
 */
class AsyncGuideProgressStoreTest {

    private static GuideProgressData dataFor(String marker) {
        StepCompletionRecord record = new StepCompletionRecord(marker, se.jimmyeliasson.gzcompanion.guide.model.GuideCompletionSource.MANUAL, 1L);
        ContextProgress ctx = new ContextProgress(marker, Map.of(marker, record));
        return new GuideProgressData(1, Map.of("ctx", ctx));
    }

    private static final class FakeStore implements GuideProgressStore {
        final List<String> savedMarkers = Collections.synchronizedList(new ArrayList<>());
        final AtomicInteger saveCallCount = new AtomicInteger();
        final AtomicInteger resetCallCount = new AtomicInteger();
        volatile CountDownLatch enteredSave; // counted down the instant save() is invoked
        volatile CountDownLatch blockUntil;  // save() blocks here before returning, if set
        volatile Function<GuideProgressData, RuntimeException> failWith = data -> null;

        @Override
        public GuideProgressData load() {
            return GuideProgressData.empty();
        }

        @Override
        public void save(GuideProgressData data) {
            saveCallCount.incrementAndGet();
            String marker = data.contexts().get("ctx").selectedStepId();
            savedMarkers.add(marker);
            CountDownLatch entered = enteredSave;
            if (entered != null) entered.countDown();
            CountDownLatch latch = blockUntil;
            if (latch != null) {
                try {
                    latch.await(2, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            RuntimeException toThrow = failWith.apply(data);
            if (toThrow != null) throw toThrow;
        }

        @Override
        public void resetContext(GuideContext context) {
            resetCallCount.incrementAndGet();
        }
    }

    private static void awaitCallCount(AtomicInteger counter, int expected, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (counter.get() >= expected) return;
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        fail("Timed out waiting for count >= " + expected + " - last seen: " + counter.get());
    }

    @Test
    @DisplayName("save() returns immediately without waiting for the delegate's write to complete")
    void saveDoesNotBlockTheCallingThread() throws InterruptedException {
        FakeStore delegate = new FakeStore();
        delegate.blockUntil = new CountDownLatch(1);
        AsyncGuideProgressStore store = new AsyncGuideProgressStore(delegate, new LocalPersistenceRuntime());

        long start = System.nanoTime();
        store.save(dataFor("A"));
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertTrue(elapsedMs < 500, "save() must return almost immediately, not wait for the blocked delegate write; took " + elapsedMs + "ms");
        delegate.blockUntil.countDown();
    }

    @Test
    @DisplayName("A single save() eventually reaches the delegate exactly once")
    void singleSaveEventuallyReachesDelegate() {
        FakeStore delegate = new FakeStore();
        AsyncGuideProgressStore store = new AsyncGuideProgressStore(delegate, new LocalPersistenceRuntime());

        store.save(dataFor("A"));

        awaitCallCount(delegate.saveCallCount, 1, 2000);
        assertEquals(List.of("A"), delegate.savedMarkers);
    }

    @Test
    @DisplayName("Rapid save requests while one is active coalesce to the latest state - not every intermediate one")
    void rapidSavesCoalesceToLatestState() {
        FakeStore delegate = new FakeStore();
        delegate.enteredSave = new CountDownLatch(1);
        delegate.blockUntil = new CountDownLatch(1);
        AsyncGuideProgressStore store = new AsyncGuideProgressStore(delegate, new LocalPersistenceRuntime());

        store.save(dataFor("A")); // becomes the active save, blocks inside delegate.save
        try {
            assertTrue(delegate.enteredSave.await(2, TimeUnit.SECONDS), "A's save must have actually started");
        } catch (InterruptedException e) {
            fail(e);
        }

        // While A is still blocked, request several more rapid state changes.
        store.save(dataFor("B"));
        store.save(dataFor("C"));
        store.save(dataFor("D"));

        delegate.blockUntil.countDown(); // let A finish, which should advance directly to D
        awaitCallCount(delegate.saveCallCount, 2, 2000);

        assertEquals(List.of("A", "D"), delegate.savedMarkers,
                "only A (already in flight) then the LATEST state D should ever reach the delegate - B and C must be dropped, not queued");
    }

    @Test
    @DisplayName("No unbounded pending queue regardless of how many rapid state changes occur")
    void noUnboundedPendingQueueRegardlessOfChangeCount() {
        FakeStore delegate = new FakeStore();
        delegate.enteredSave = new CountDownLatch(1);
        delegate.blockUntil = new CountDownLatch(1);
        AsyncGuideProgressStore store = new AsyncGuideProgressStore(delegate, new LocalPersistenceRuntime());

        store.save(dataFor("first"));
        try {
            assertTrue(delegate.enteredSave.await(2, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            fail(e);
        }

        for (int i = 0; i < 50; i++) {
            store.save(dataFor("state-" + i));
        }

        delegate.blockUntil.countDown();
        awaitCallCount(delegate.saveCallCount, 2, 2000);

        assertEquals(2, delegate.saveCallCount.get(), "50 rapid state changes while one save is active must still only ever produce 2 delegate writes");
    }

    @Test
    @DisplayName("A failing delegate save does not crash and a later save recovers")
    void failedSaveDoesNotCrashAndLaterSaveRecovers() {
        FakeStore delegate = new FakeStore();
        delegate.failWith = data -> new RuntimeException("simulated disk failure");
        AsyncGuideProgressStore store = new AsyncGuideProgressStore(delegate, new LocalPersistenceRuntime());

        assertDoesNotThrow(() -> store.save(dataFor("A")));
        awaitCallCount(delegate.saveCallCount, 1, 2000);
        assertTrue(store.flushBounded(Duration.ofSeconds(2)));
        assertTrue(store.lastSaveFailed(), "a failed delegate save must be reflected in lastSaveFailed()");

        delegate.failWith = data -> null; // recovers
        store.save(dataFor("B"));
        awaitCallCount(delegate.saveCallCount, 2, 2000);
        assertTrue(store.flushBounded(Duration.ofSeconds(2)));
        assertFalse(store.lastSaveFailed(), "a subsequent successful save must clear the failure flag");
        assertEquals(List.of("A", "B"), delegate.savedMarkers);
    }

    @Test
    @DisplayName("flushBounded waits for an in-flight save to actually complete, within the given bound")
    void flushBoundedWaitsForInFlightSave() {
        FakeStore delegate = new FakeStore();
        delegate.blockUntil = new CountDownLatch(1);
        AsyncGuideProgressStore store = new AsyncGuideProgressStore(delegate, new LocalPersistenceRuntime());

        store.save(dataFor("A"));
        new Thread(() -> {
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {
            }
            delegate.blockUntil.countDown();
        }).start();

        boolean flushed = store.flushBounded(Duration.ofSeconds(2));
        assertTrue(flushed, "flush must succeed once the in-flight save completes within the bound");
        assertEquals(List.of("A"), delegate.savedMarkers);
    }

    @Test
    @DisplayName("flushBounded returns false (times out) rather than blocking forever on a stuck save")
    void flushBoundedTimesOutRatherThanBlockingForever() {
        FakeStore delegate = new FakeStore();
        delegate.blockUntil = new CountDownLatch(1); // deliberately never counted down in this test
        AsyncGuideProgressStore store = new AsyncGuideProgressStore(delegate, new LocalPersistenceRuntime());

        store.save(dataFor("A"));
        long start = System.nanoTime();
        boolean flushed = store.flushBounded(Duration.ofMillis(150));
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertFalse(flushed, "flush must report failure when the bound elapses before the save completes");
        assertTrue(elapsedMs < 1000, "flushBounded must not block substantially longer than its own timeout; took " + elapsedMs + "ms");
        delegate.blockUntil.countDown(); // release the stuck save so the test JVM can shut down cleanly
    }

    @Test
    @DisplayName("flushBounded with nothing pending returns true immediately")
    void flushBoundedWithNothingPendingReturnsTrueImmediately() {
        FakeStore delegate = new FakeStore();
        AsyncGuideProgressStore store = new AsyncGuideProgressStore(delegate, new LocalPersistenceRuntime());

        assertTrue(store.flushBounded(Duration.ofMillis(50)));
    }

    @Test
    @DisplayName("resetContext is synchronous and drops a save that was only queued (not yet started) before it")
    void resetContextDropsQueuedButNotYetStartedSave() {
        FakeStore delegate = new FakeStore();
        delegate.enteredSave = new CountDownLatch(1);
        delegate.blockUntil = new CountDownLatch(1);
        AsyncGuideProgressStore store = new AsyncGuideProgressStore(delegate, new LocalPersistenceRuntime());

        store.save(dataFor("A")); // becomes active, blocks inside delegate.save
        try {
            assertTrue(delegate.enteredSave.await(2, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            fail(e);
        }
        store.save(dataFor("B")); // queued as pending - NOT yet dispatched to the delegate

        store.resetContext(new GuideContext("uuid", "singleplayer:test")); // must drop the pending B outright

        delegate.blockUntil.countDown(); // let A finish
        assertTrue(store.flushBounded(Duration.ofSeconds(2)));

        assertEquals(1, delegate.resetCallCount.get(), "resetContext must reach the delegate synchronously");
        assertEquals(List.of("A"), delegate.savedMarkers,
                "B was only ever queued (never dispatched) before the reset, so it must never reach the delegate");
    }

    @Test
    @DisplayName("resetContext itself never throws even with nothing pending")
    void resetContextWorksWithNothingPending() {
        FakeStore delegate = new FakeStore();
        AsyncGuideProgressStore store = new AsyncGuideProgressStore(delegate, new LocalPersistenceRuntime());

        assertDoesNotThrow(() -> store.resetContext(new GuideContext("uuid", "singleplayer:test")));
        assertEquals(1, delegate.resetCallCount.get());
    }

    @Test
    @DisplayName("load() delegates synchronously")
    void loadDelegatesSynchronously() {
        FakeStore delegate = new FakeStore();
        AsyncGuideProgressStore store = new AsyncGuideProgressStore(delegate, new LocalPersistenceRuntime());

        assertEquals(GuideProgressData.empty(), store.load());
    }
}
