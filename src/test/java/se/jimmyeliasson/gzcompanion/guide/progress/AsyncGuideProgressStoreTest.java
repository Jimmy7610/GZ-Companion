package se.jimmyeliasson.gzcompanion.guide.progress;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import se.jimmyeliasson.gzcompanion.guide.model.GuideCompletionSource;
import se.jimmyeliasson.gzcompanion.storage.LocalPersistenceRuntime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
 * client tick thread" finding, hardened by a follow-up correctness pass (real failure propagation,
 * a full - not best-effort - reset-vs-active-save ordering guarantee). Mirrors {@code
 * LeaderboardManagerTest}'s FakeSource-with-a-latch convention for deterministic concurrency tests.
 */
class AsyncGuideProgressStoreTest {

    private static GuideProgressData dataFor(String marker) {
        StepCompletionRecord record = new StepCompletionRecord(marker, GuideCompletionSource.MANUAL, 1L);
        ContextProgress ctx = new ContextProgress(marker, Map.of(marker, record));
        return new GuideProgressData(1, Map.of("ctx", ctx));
    }

    /** A pure in-memory fake, marker-based - used for the coalescing/scheduling tests where exact
     * JSON semantics don't matter, only call order/count. Throws the same exception type the real
     * production delegate now throws, so the async layer's catch logic is exercised realistically. */
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
            savedMarkers.add(marker);
        }

        @Override
        public void resetContext(GuideContext context) {
            resetCallCount.incrementAndGet();
        }
    }

    /** Wraps a REAL {@link JsonGuideProgressStore} but lets a test pause/observe individual
     * {@code save()} calls deterministically - used for tests that need genuine JSON
     * load/save/resetContext semantics (not just call-order bookkeeping), e.g. the other-context
     * preservation test and the real active-save/reset race test. */
    private static final class ControllableStore implements GuideProgressStore {
        private final JsonGuideProgressStore real;
        volatile CountDownLatch enteredSave;
        volatile CountDownLatch blockUntil;

        ControllableStore(Path path) {
            this.real = new JsonGuideProgressStore(path);
        }

        @Override
        public GuideProgressData load() {
            return real.load();
        }

        @Override
        public void save(GuideProgressData data) {
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
            real.save(data);
        }

        @Override
        public void resetContext(GuideContext context) {
            real.resetContext(context);
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

    // ------------------------------------------------------------------
    // Basic coalescing (unchanged behavior, re-verified against the rewritten implementation)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("save() returns immediately without waiting for the delegate's write to complete")
    void saveDoesNotBlockTheCallingThread() {
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
    void rapidSavesCoalesceToLatestState() throws InterruptedException {
        FakeStore delegate = new FakeStore();
        delegate.enteredSave = new CountDownLatch(1);
        delegate.blockUntil = new CountDownLatch(1);
        AsyncGuideProgressStore store = new AsyncGuideProgressStore(delegate, new LocalPersistenceRuntime());

        store.save(dataFor("A"));
        assertTrue(delegate.enteredSave.await(2, TimeUnit.SECONDS), "A's save must have actually started");

        store.save(dataFor("B"));
        store.save(dataFor("C"));
        store.save(dataFor("D"));

        delegate.blockUntil.countDown();
        awaitCallCount(delegate.saveCallCount, 2, 2000);

        assertEquals(List.of("A", "D"), delegate.savedMarkers,
                "only A (already in flight) then the LATEST state D should ever reach the delegate - B and C must be dropped, not queued");
    }

    @Test
    @DisplayName("No unbounded pending queue regardless of how many rapid state changes occur")
    void noUnboundedPendingQueueRegardlessOfChangeCount() throws InterruptedException {
        FakeStore delegate = new FakeStore();
        delegate.enteredSave = new CountDownLatch(1);
        delegate.blockUntil = new CountDownLatch(1);
        AsyncGuideProgressStore store = new AsyncGuideProgressStore(delegate, new LocalPersistenceRuntime());

        store.save(dataFor("first"));
        assertTrue(delegate.enteredSave.await(2, TimeUnit.SECONDS));

        for (int i = 0; i < 50; i++) {
            store.save(dataFor("state-" + i));
        }

        delegate.blockUntil.countDown();
        awaitCallCount(delegate.saveCallCount, 2, 2000);

        assertEquals(2, delegate.saveCallCount.get(), "50 rapid state changes while one save is active must still only ever produce 2 delegate writes");
    }

    @Test
    @DisplayName("load() delegates synchronously")
    void loadDelegatesSynchronously() {
        FakeStore delegate = new FakeStore();
        AsyncGuideProgressStore store = new AsyncGuideProgressStore(delegate, new LocalPersistenceRuntime());

        assertEquals(GuideProgressData.empty(), store.load());
    }

    // ------------------------------------------------------------------
    // BLOCKER 1 (2026-09-13 correctness follow-up): real write-failure visibility, retry, and
    // flush semantics - proven against the FakeStore's coalescing/state-machine behavior first...
    // ------------------------------------------------------------------

    @Test
    @DisplayName("A failing delegate save does not crash, is observable, and a later save recovers")
    void failedSaveDoesNotCrashAndLaterSaveRecovers() {
        FakeStore delegate = new FakeStore();
        delegate.failWith = data -> new GuideProgressPersistenceException("simulated disk failure", new IOException("disk full"));
        AsyncGuideProgressStore store = new AsyncGuideProgressStore(delegate, new LocalPersistenceRuntime());

        assertDoesNotThrow(() -> store.save(dataFor("A")));
        awaitCallCount(delegate.saveCallCount, 1, 2000);
        assertFalse(store.flushBounded(Duration.ofSeconds(2)), "flushBounded must report false while the only requested state failed to persist");
        assertTrue(store.lastSaveFailed(), "a failed delegate save must be reflected in lastSaveFailed()");

        delegate.failWith = data -> null; // recovers
        store.save(dataFor("B"));
        awaitCallCount(delegate.saveCallCount, 2, 2000);
        assertTrue(store.flushBounded(Duration.ofSeconds(2)));
        assertFalse(store.lastSaveFailed(), "a subsequent successful save must clear the failure flag");
        assertEquals(List.of("B"), delegate.savedMarkers, "A never actually reached durable storage - only B (which supersedes it) did");
    }

    @Test
    @DisplayName("flushBounded gives a dirty failed write exactly one retry opportunity, not a hammering loop")
    void flushGivesDirtyWriteExactlyOneRetry() {
        FakeStore delegate = new FakeStore();
        delegate.failWith = data -> new GuideProgressPersistenceException("fails every time", new IOException("permission denied"));
        AsyncGuideProgressStore store = new AsyncGuideProgressStore(delegate, new LocalPersistenceRuntime());

        store.save(dataFor("A"));
        awaitCallCount(delegate.saveCallCount, 1, 2000);

        boolean flushed = store.flushBounded(Duration.ofSeconds(1));

        assertFalse(flushed, "a write that keeps failing must never report a successful flush");
        assertEquals(2, delegate.saveCallCount.get(), "flushBounded must attempt exactly one retry (1 original + 1 retry), never a repeated hammering loop");
        assertTrue(store.lastSaveFailed());
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
        delegate.blockUntil.countDown();
    }

    @Test
    @DisplayName("flushBounded with nothing pending returns true immediately")
    void flushBoundedWithNothingPendingReturnsTrueImmediately() {
        FakeStore delegate = new FakeStore();
        AsyncGuideProgressStore store = new AsyncGuideProgressStore(delegate, new LocalPersistenceRuntime());

        assertTrue(store.flushBounded(Duration.ofMillis(50)));
    }

    // ------------------------------------------------------------------
    // BLOCKER 1, continued: the REAL production JsonGuideProgressStore, not just FakeStore -
    // proves the actual failure contract (throws instead of swallowing) end to end, using a
    // deterministic, cross-platform, non-admin-permission filesystem obstruction rather than
    // relying on OS-specific ACL/chmod tricks.
    // ------------------------------------------------------------------

    @Test
    @DisplayName("A real JsonGuideProgressStore write failure is signaled to the async layer, leaves previous valid data intact, and recovers once the obstruction is removed")
    void realStoreFailureIsSignaledLeavesPriorDataIntactAndRecovers(@TempDir Path tempDir) throws Exception {
        Path progressPath = tempDir.resolve("guide-progress.json");
        JsonGuideProgressStore realStore = new JsonGuideProgressStore(progressPath);
        AsyncGuideProgressStore store = new AsyncGuideProgressStore(realStore, new LocalPersistenceRuntime());

        // 1. Happy path first: a normal successful save.
        store.save(dataFor("A"));
        assertTrue(store.flushBounded(Duration.ofSeconds(2)));
        assertFalse(store.lastSaveFailed());
        String contentAfterA = Files.readString(progressPath);
        assertTrue(contentAfterA.contains("\"A\""), "the file must contain A's data after a successful save");

        // 2. Sabotage: JsonGuideProgressStore writes to "<file>.tmp" before atomically moving it
        // into place. Pre-creating a DIRECTORY at that exact .tmp path makes the next write's
        // FileWriter construction fail deterministically, on any OS, without needing chmod/ACLs -
        // and crucially, it fails BEFORE the atomic move, so the real file (still holding A) is
        // never touched.
        Path tmpBlocker = tempDir.resolve("guide-progress.json.tmp");
        Files.createDirectory(tmpBlocker);

        store.save(dataFor("B"));
        awaitFlagTrue(store, 2000);
        assertTrue(store.lastSaveFailed(), "a real disk failure must now be observable via lastSaveFailed()");
        assertFalse(store.flushBounded(Duration.ofSeconds(2)), "flushBounded must report false - B was never actually persisted");

        String contentStillA = Files.readString(progressPath);
        assertEquals(contentAfterA, contentStillA, "the previously-valid file must be completely untouched by the failed write");

        // 3. Recovery: remove the obstruction, then flushBounded's own retry (or a fresh save)
        // must succeed and clear the failure.
        Files.delete(tmpBlocker);
        assertTrue(store.flushBounded(Duration.ofSeconds(2)), "once the obstruction is gone, the retained dirty state must persist successfully");
        assertFalse(store.lastSaveFailed());
        assertTrue(Files.readString(progressPath).contains("\"B\""), "B must finally be durably persisted after recovery");
    }

    private static void awaitFlagTrue(AsyncGuideProgressStore store, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (store.lastSaveFailed()) return;
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        fail("Timed out waiting for lastSaveFailed() to become true");
    }

    // ------------------------------------------------------------------
    // BLOCKER 2 (2026-09-13 correctness follow-up, full fix - no best-effort caveat): reset must
    // fully drain outstanding writes before applying itself, so no pre-reset write can ever land
    // afterward.
    // ------------------------------------------------------------------

    @Test
    @DisplayName("resetContext() waits for an already-active save to fully finish, then applies itself - the active save is allowed to complete since it was accepted before reset began")
    void resetWaitsForActiveSaveThenAppliesItself() throws InterruptedException {
        FakeStore delegate = new FakeStore();
        delegate.enteredSave = new CountDownLatch(1);
        delegate.blockUntil = new CountDownLatch(1);
        AsyncGuideProgressStore store = new AsyncGuideProgressStore(delegate, new LocalPersistenceRuntime());

        store.save(dataFor("A"));
        assertTrue(delegate.enteredSave.await(2, TimeUnit.SECONDS), "A's write must have already started (\"accepted as current\") before reset is requested");

        new Thread(() -> {
            try {
                Thread.sleep(150);
            } catch (InterruptedException ignored) {
            }
            delegate.blockUntil.countDown(); // simulates the disk write eventually completing
        }).start();

        GuideContext ctx = new GuideContext("uuid", "singleplayer:test");
        store.resetContext(ctx); // must block until A finishes, then apply the reset

        assertEquals(List.of("A"), delegate.savedMarkers, "A's already-in-flight write is allowed to finish");
        assertEquals(1, delegate.resetCallCount.get(), "the reset itself must run, synchronously, only after A finished");
        assertTrue(store.flushBounded(Duration.ofSeconds(1)), "nothing further must be dirty/pending once reset has returned");
    }

    @Test
    @DisplayName("A save that was only queued (pending) is drained (allowed to run) before reset applies itself - reset still has the final word")
    void resetDrainsQueuedSaveBeforeApplyingItself() throws InterruptedException {
        FakeStore delegate = new FakeStore();
        delegate.enteredSave = new CountDownLatch(1);
        delegate.blockUntil = new CountDownLatch(1);
        AsyncGuideProgressStore store = new AsyncGuideProgressStore(delegate, new LocalPersistenceRuntime());

        store.save(dataFor("A")); // becomes active, blocks
        assertTrue(delegate.enteredSave.await(2, TimeUnit.SECONDS));
        store.save(dataFor("B")); // queued as pending - not yet dispatched

        new Thread(() -> {
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {
            }
            delegate.blockUntil.countDown(); // let A finish, which then promotes+dispatches B
        }).start();

        store.resetContext(new GuideContext("uuid", "singleplayer:test")); // must wait for BOTH A and B to fully drain first

        assertEquals(List.of("A", "B"), delegate.savedMarkers, "both A (already active) and B (queued) must be allowed to actually run before reset proceeds");
        assertEquals(1, delegate.resetCallCount.get());
    }

    @Test
    @DisplayName("Required deterministic race: RESET WINS PERMANENTLY - a worker paused immediately before its durable write, released only after reset returns, can never leave pre-reset state visible afterward")
    void resetWinsPermanentlyOverAPausedActiveSave(@TempDir Path tempDir) throws Exception {
        Path progressPath = tempDir.resolve("guide-progress.json");
        ControllableStore controllable = new ControllableStore(progressPath);
        controllable.enteredSave = new CountDownLatch(1);
        controllable.blockUntil = new CountDownLatch(1);
        AsyncGuideProgressStore store = new AsyncGuideProgressStore(controllable, new LocalPersistenceRuntime());

        GuideContext ctx = new GuideContext("uuid", "singleplayer:test");
        StepCompletionRecord rec = new StepCompletionRecord("step1", GuideCompletionSource.MANUAL, 1L);
        GuideProgressData preResetData = new GuideProgressData(1, Map.of(
                ctx.getStorageKey(), new ContextProgress("step1", Map.of("step1", rec))));

        // Worker A: "accepted as current" (dispatched), then paused immediately before its
        // durable write actually happens.
        store.save(preResetData);
        assertTrue(controllable.enteredSave.await(2, TimeUnit.SECONDS), "the write must be accepted/dispatched and paused right before the durable write");

        // Main thread: resetContext runs concurrently with the paused worker. Release the worker
        // only shortly after reset has had to start waiting for it.
        new Thread(() -> {
            try {
                Thread.sleep(150);
            } catch (InterruptedException ignored) {
            }
            controllable.blockUntil.countDown(); // "then: allow worker A to proceed"
        }).start();

        store.resetContext(ctx);

        // Required final result: RESET WINS PERMANENTLY. No old/pre-reset state may appear.
        GuideProgressData finalState = controllable.load();
        assertFalse(finalState.getContext(ctx.getStorageKey()).isStepCompleted("step1"),
                "no pre-reset state may be visible once resetContext() has returned, even though the paused write was allowed to physically finish first");

        // And nothing further is dirty/in-flight that could later re-introduce it.
        assertTrue(store.flushBounded(Duration.ofSeconds(1)));
        finalState = controllable.load();
        assertFalse(finalState.getContext(ctx.getStorageKey()).isStepCompleted("step1"), "the reset result must remain stable after a flush too");
    }

    @Test
    @DisplayName("A save requested after resetContext returns is completely unaffected by the reset and persists normally")
    void saveAfterResetProceedsNormally() {
        FakeStore delegate = new FakeStore();
        AsyncGuideProgressStore store = new AsyncGuideProgressStore(delegate, new LocalPersistenceRuntime());

        store.save(dataFor("A"));
        awaitCallCount(delegate.saveCallCount, 1, 2000);

        store.resetContext(new GuideContext("uuid", "singleplayer:test"));
        store.save(dataFor("B"));
        awaitCallCount(delegate.saveCallCount, 2, 2000);

        assertEquals(List.of("A", "B"), delegate.savedMarkers);
        assertEquals(1, delegate.resetCallCount.get());
    }

    @Test
    @DisplayName("Resetting one context never loses another context's already-durable or concurrently in-flight progress")
    void resetPreservesOtherContextsProgress(@TempDir Path tempDir) throws Exception {
        Path progressPath = tempDir.resolve("guide-progress.json");
        ControllableStore controllable = new ControllableStore(progressPath);
        AsyncGuideProgressStore store = new AsyncGuideProgressStore(controllable, new LocalPersistenceRuntime());

        GuideContext ctxX = new GuideContext("uuid-x", "singleplayer:worldX");
        GuideContext ctxY = new GuideContext("uuid-y", "singleplayer:worldY");
        StepCompletionRecord recX = new StepCompletionRecord("step1", GuideCompletionSource.MANUAL, 1L);
        StepCompletionRecord recY = new StepCompletionRecord("step1", GuideCompletionSource.MANUAL, 2L);
        GuideProgressData both = new GuideProgressData(1, Map.of(
                ctxX.getStorageKey(), new ContextProgress("step1", Map.of("step1", recX)),
                ctxY.getStorageKey(), new ContextProgress("step1", Map.of("step1", recY))
        ));

        store.save(both);
        assertTrue(store.flushBounded(Duration.ofSeconds(2)));

        store.resetContext(ctxX);

        GuideProgressData afterReset = controllable.load();
        assertFalse(afterReset.getContext(ctxX.getStorageKey()).isStepCompleted("step1"), "ctxX must be reset");
        assertTrue(afterReset.getContext(ctxY.getStorageKey()).isStepCompleted("step1"), "ctxY's progress must survive ctxX's reset");
    }

    @Test
    @DisplayName("If the underlying reset write itself fails, resetContext does not throw and marks the failure observable")
    void resetContextPersistenceFailureIsObservableNotThrown(@TempDir Path tempDir) throws Exception {
        Path progressPath = tempDir.resolve("guide-progress.json");
        JsonGuideProgressStore realStore = new JsonGuideProgressStore(progressPath);
        AsyncGuideProgressStore store = new AsyncGuideProgressStore(realStore, new LocalPersistenceRuntime());

        Path tmpBlocker = tempDir.resolve("guide-progress.json.tmp");
        Files.createDirectory(tmpBlocker);

        GuideContext ctx = new GuideContext("uuid", "singleplayer:test");
        assertDoesNotThrow(() -> store.resetContext(ctx));
        assertTrue(store.lastSaveFailed(), "a reset whose underlying write fails must be observable via lastSaveFailed(), never crash the caller");
    }

    @Test
    @DisplayName("resetContext works cleanly (never throws) with nothing pending")
    void resetContextWorksWithNothingPending() {
        FakeStore delegate = new FakeStore();
        AsyncGuideProgressStore store = new AsyncGuideProgressStore(delegate, new LocalPersistenceRuntime());

        assertDoesNotThrow(() -> store.resetContext(new GuideContext("uuid", "singleplayer:test")));
        assertEquals(1, delegate.resetCallCount.get());
    }

    @Test
    @DisplayName("If an active save cannot finish within the reset wait bound, resetContext proceeds anyway rather than hanging indefinitely")
    void resetProceedsAfterBoundIfActiveSaveNeverFinishes() throws InterruptedException {
        FakeStore delegate = new FakeStore();
        delegate.enteredSave = new CountDownLatch(1);
        delegate.blockUntil = new CountDownLatch(1); // deliberately never released within this test
        AsyncGuideProgressStore store = new AsyncGuideProgressStore(delegate, new LocalPersistenceRuntime(), Duration.ofMillis(150));

        store.save(dataFor("A"));
        assertTrue(delegate.enteredSave.await(2, TimeUnit.SECONDS));

        long start = System.nanoTime();
        store.resetContext(new GuideContext("uuid", "singleplayer:test"));
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertTrue(elapsedMs < 2000, "resetContext must not hang indefinitely waiting for a stuck save; took " + elapsedMs + "ms");
        assertEquals(1, delegate.resetCallCount.get(), "the reset must still be attempted even after the wait bound elapses");

        delegate.blockUntil.countDown(); // release the stuck save so the JVM can shut down cleanly
    }
}
