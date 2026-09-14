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
 * {@link AsyncGuideProgressStore}'s coalescing/failure/reset/flush guarantees.
 *
 * <p>History: the alpha.4 audit found guide progress persistence blocking the Minecraft tick
 * thread; the first follow-up moved it to a background writer; independent review then found the
 * failure contract was invisible in production and the reset-vs-active-save ordering was only
 * best-effort; this file's "final persistence correctness pass" section proves the CURRENT
 * contract - a reset either fully succeeds and is durably persisted, or does not happen at all,
 * never a partial/uncertain outcome - including the exact dangerous orderings a reviewer would
 * look for (an active save hung while holding a shared monitor, a saturated runtime, a dirty write
 * that keeps failing, and the false "superset" assumption {@code undoStepCompletion} disproves).
 */
class AsyncGuideProgressStoreTest {

    private static GuideProgressData dataFor(String marker) {
        StepCompletionRecord record = new StepCompletionRecord(marker, GuideCompletionSource.MANUAL, 1L);
        ContextProgress ctx = new ContextProgress(marker, Map.of(marker, record));
        return new GuideProgressData(1, Map.of("ctx", ctx));
    }

    /** A snapshot with strictly FEWER completions than {@link #dataFor} produces (zero, in fact) -
     * stands in for what {@code undoStepCompletion} produces: a legitimate newer snapshot that is
     * NOT a superset of an older one. Still carries {@code marker} as the (now unlinked)
     * {@code selectedStepId} purely so {@link FakeStore#save} can extract a distinguishing marker
     * without needing special-casing for "no completions at all". */
    private static GuideProgressData dataWithNoCompletions(String marker) {
        ContextProgress ctx = new ContextProgress(marker, Map.of());
        return new GuideProgressData(1, Map.of("ctx", ctx));
    }

    /** A pure in-memory fake, marker-based - used where exact JSON semantics don't matter, only
     * call order/count. Throws the same exception type the real production delegate now throws. */
    private static final class FakeStore implements GuideProgressStore {
        final List<String> savedMarkers = Collections.synchronizedList(new ArrayList<>());
        final AtomicInteger saveCallCount = new AtomicInteger();
        final AtomicInteger resetCallCount = new AtomicInteger();
        volatile CountDownLatch enteredSave; // counted down the instant save() is invoked
        volatile CountDownLatch blockUntil;  // save() blocks here before returning, if set
        volatile Function<GuideProgressData, RuntimeException> failWith = data -> null;
        volatile boolean resetSucceeds = true;

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
        public boolean resetContext(GuideContext context) {
            resetCallCount.incrementAndGet();
            return resetSucceeds;
        }
    }

    /** Wraps a REAL {@link JsonGuideProgressStore} but lets a test pause/observe individual
     * {@code save()} calls deterministically - used for tests needing genuine JSON load/save/
     * resetContext semantics, e.g. other-context preservation. */
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
        public boolean resetContext(GuideContext context) {
            return real.resetContext(context);
        }
    }

    /** A test double whose save/load/resetContext all share the SAME intrinsic monitor -
     * reproducing {@code JsonGuideProgressStore}'s actual synchronization, unlike {@link FakeStore}
     * (which only coordinates via test latches, never a real shared lock). Used specifically to
     * prove that an active save genuinely holding this monitor cannot cause {@code resetContext}
     * to block behind it - the fix must abort on an incomplete drain BEFORE ever trying to call
     * into the delegate, not merely time out its own internal wait. */
    private static final class MonitorSharingStore implements GuideProgressStore {
        volatile CountDownLatch enteredSave;
        volatile CountDownLatch releaseSave;
        final AtomicInteger resetCallCount = new AtomicInteger();
        final AtomicInteger loadCallCount = new AtomicInteger();

        @Override
        public synchronized GuideProgressData load() {
            loadCallCount.incrementAndGet();
            return GuideProgressData.empty();
        }

        @Override
        public synchronized void save(GuideProgressData data) {
            CountDownLatch entered = enteredSave;
            if (entered != null) entered.countDown();
            CountDownLatch release = releaseSave;
            if (release != null) {
                try {
                    release.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        @Override
        public synchronized boolean resetContext(GuideContext context) {
            resetCallCount.incrementAndGet();
            return true;
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
    // Basic coalescing (unchanged behavior, re-verified)
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

        assertTrue(elapsedMs < 500, "save() must return almost immediately; took " + elapsedMs + "ms");
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
        assertTrue(delegate.enteredSave.await(2, TimeUnit.SECONDS));

        store.save(dataFor("B"));
        store.save(dataFor("C"));
        store.save(dataFor("D"));

        delegate.blockUntil.countDown();
        awaitCallCount(delegate.saveCallCount, 2, 2000);

        assertEquals(List.of("A", "D"), delegate.savedMarkers,
                "only A (already in flight) then the LATEST state D should ever reach the delegate");
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

        assertEquals(2, delegate.saveCallCount.get());
    }

    @Test
    @DisplayName("load() delegates synchronously")
    void loadDelegatesSynchronously() {
        FakeStore delegate = new FakeStore();
        AsyncGuideProgressStore store = new AsyncGuideProgressStore(delegate, new LocalPersistenceRuntime());

        assertEquals(GuideProgressData.empty(), store.load());
    }

    // ------------------------------------------------------------------
    // Real write-failure visibility, retry, and flush semantics
    // ------------------------------------------------------------------

    @Test
    @DisplayName("A failing delegate save does not crash, is observable, and a later save recovers")
    void failedSaveDoesNotCrashAndLaterSaveRecovers() {
        FakeStore delegate = new FakeStore();
        delegate.failWith = data -> new GuideProgressPersistenceException("simulated disk failure", new IOException("disk full"));
        AsyncGuideProgressStore store = new AsyncGuideProgressStore(delegate, new LocalPersistenceRuntime());

        assertDoesNotThrow(() -> store.save(dataFor("A")));
        awaitCallCount(delegate.saveCallCount, 1, 2000);
        assertFalse(store.flushBounded(Duration.ofSeconds(2)));
        assertTrue(store.lastSaveFailed());

        delegate.failWith = data -> null;
        store.save(dataFor("B"));
        awaitCallCount(delegate.saveCallCount, 2, 2000);
        assertTrue(store.flushBounded(Duration.ofSeconds(2)));
        assertFalse(store.lastSaveFailed());
        assertEquals(List.of("B"), delegate.savedMarkers);
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

        assertFalse(flushed);
        assertEquals(2, delegate.saveCallCount.get(), "exactly one retry - the original attempt plus one, never a hammering loop");
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

        assertTrue(store.flushBounded(Duration.ofSeconds(2)));
        assertEquals(List.of("A"), delegate.savedMarkers);
    }

    @Test
    @DisplayName("flushBounded returns false (times out) rather than blocking forever on a stuck save")
    void flushBoundedTimesOutRatherThanBlockingForever() {
        FakeStore delegate = new FakeStore();
        delegate.blockUntil = new CountDownLatch(1);
        AsyncGuideProgressStore store = new AsyncGuideProgressStore(delegate, new LocalPersistenceRuntime());

        store.save(dataFor("A"));
        long start = System.nanoTime();
        boolean flushed = store.flushBounded(Duration.ofMillis(150));
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertFalse(flushed);
        assertTrue(elapsedMs < 1000, "took " + elapsedMs + "ms");
        delegate.blockUntil.countDown();
    }

    @Test
    @DisplayName("flushBounded with nothing pending returns true immediately")
    void flushBoundedWithNothingPendingReturnsTrueImmediately() {
        FakeStore delegate = new FakeStore();
        AsyncGuideProgressStore store = new AsyncGuideProgressStore(delegate, new LocalPersistenceRuntime());

        assertTrue(store.flushBounded(Duration.ofMillis(50)));
    }

    @Test
    @DisplayName("Real JsonGuideProgressStore failure is signaled, leaves previous valid data intact, and recovers once the obstruction is removed")
    void realStoreFailureIsSignaledLeavesPriorDataIntactAndRecovers(@TempDir Path tempDir) throws Exception {
        Path progressPath = tempDir.resolve("guide-progress.json");
        JsonGuideProgressStore realStore = new JsonGuideProgressStore(progressPath);
        AsyncGuideProgressStore store = new AsyncGuideProgressStore(realStore, new LocalPersistenceRuntime());

        store.save(dataFor("A"));
        assertTrue(store.flushBounded(Duration.ofSeconds(2)));
        String contentAfterA = Files.readString(progressPath);
        assertTrue(contentAfterA.contains("\"A\""));

        // Pre-creating a DIRECTORY at the exact ".tmp" path the atomic write needs makes the next
        // write's FileWriter construction fail deterministically, on any OS, with no chmod/ACLs -
        // and crucially fails BEFORE the atomic move, so the real file (still holding A) is untouched.
        Path tmpBlocker = tempDir.resolve("guide-progress.json.tmp");
        Files.createDirectory(tmpBlocker);

        store.save(dataFor("B"));
        awaitFlagTrue(store, 2000);
        assertFalse(store.flushBounded(Duration.ofSeconds(2)));
        assertEquals(contentAfterA, Files.readString(progressPath), "the previously-valid file must be completely untouched by the failed write");

        Files.delete(tmpBlocker);
        assertTrue(store.flushBounded(Duration.ofSeconds(2)));
        assertFalse(store.lastSaveFailed());
        assertTrue(Files.readString(progressPath).contains("\"B\""));
    }

    // ------------------------------------------------------------------
    // Non-monotonic ("latest wins", NOT superset) snapshot semantics - undoStepCompletion means a
    // newer authoritative snapshot can legitimately contain LESS than an older one.
    // ------------------------------------------------------------------

    @Test
    @DisplayName("A newer snapshot that REMOVES progress (as undoStepCompletion produces) still supersedes a dirty failed older one - the failed one is never retried/resurrected")
    void newerSnapshotThatRemovesProgressStillSupersedesDirtyOlderOne() {
        FakeStore delegate = new FakeStore();
        delegate.failWith = data -> new GuideProgressPersistenceException("A fails", new IOException("boom"));
        AsyncGuideProgressStore store = new AsyncGuideProgressStore(delegate, new LocalPersistenceRuntime());

        store.save(dataFor("A")); // fails, becomes dirty
        awaitFlagTrue(store, 2000);

        delegate.failWith = data -> null; // the undo (fewer completions than A) will succeed
        store.save(dataWithNoCompletions("undone"));
        awaitCallCount(delegate.saveCallCount, 2, 2000);

        assertTrue(store.flushBounded(Duration.ofSeconds(2)));
        assertEquals(List.of("undone"), delegate.savedMarkers, "A must never be retried/resurrected once a newer request - even one with strictly less data - has been made");
        assertFalse(store.lastSaveFailed());
    }

    // ------------------------------------------------------------------
    // FINAL PERSISTENCE CORRECTNESS PASS: reset must have exactly two outcomes - fully applied and
    // durably persisted, or not performed at all. An incomplete drain must ABORT, never proceed.
    // ------------------------------------------------------------------

    @Test
    @DisplayName("REQUIRED: active-save timeout - delegate.resetContext is NEVER called, reset reports failure, a later reset can still succeed")
    void activeSaveTimeoutAbortsResetWithoutCallingDelegate() throws InterruptedException {
        FakeStore delegate = new FakeStore();
        delegate.enteredSave = new CountDownLatch(1);
        delegate.blockUntil = new CountDownLatch(1); // deliberately never released during the reset attempt
        AsyncGuideProgressStore store = new AsyncGuideProgressStore(delegate, new LocalPersistenceRuntime(), Duration.ofMillis(150));

        store.save(dataFor("A"));
        assertTrue(delegate.enteredSave.await(2, TimeUnit.SECONDS), "A's write must be genuinely active/in-flight");

        long start = System.nanoTime();
        boolean result = store.resetContext(new GuideContext("uuid", "singleplayer:test"));
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertFalse(result, "reset must report failure when the pre-reset drain cannot be confirmed within the bound");
        assertTrue(elapsedMs < 2000, "resetContext must return promptly, not hang; took " + elapsedMs + "ms");
        assertEquals(0, delegate.resetCallCount.get(), "delegate.resetContext() must NEVER be called after a failed drain");

        // Now let the old save finish - prove no reset was falsely claimed to have happened.
        delegate.blockUntil.countDown();
        awaitCallCount(delegate.saveCallCount, 1, 2000);
        assertEquals(0, delegate.resetCallCount.get(), "the old save finishing afterward must not retroactively trigger any reset");

        // A later reset, with nothing outstanding anymore, must succeed normally.
        boolean laterResult = store.resetContext(new GuideContext("uuid", "singleplayer:test"));
        assertTrue(laterResult);
        assertEquals(1, delegate.resetCallCount.get());
    }

    @Test
    @DisplayName("REQUIRED: saturated local-persistence runtime - reset reports failure, delegate.resetContext is NEVER called, so no old dirty state can be written under a falsely-successful reset")
    void saturatedRuntimeAbortsResetWithoutCallingDelegate() throws InterruptedException {
        FakeStore delegate = new FakeStore();
        LocalPersistenceRuntime runtime = new LocalPersistenceRuntime();
        AsyncGuideProgressStore store = new AsyncGuideProgressStore(delegate, runtime);

        // Saturate the shared runtime from outside this store entirely (simulating other
        // concurrent local-persistence consumers), so the store's OWN attempt to dispatch a
        // reset-drain retry is itself rejected.
        CountDownLatch blockWorker = new CountDownLatch(1);
        CountDownLatch workerEntered = new CountDownLatch(1);
        assertTrue(runtime.submit(() -> {
            workerEntered.countDown();
            try {
                blockWorker.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));
        assertTrue(workerEntered.await(2, TimeUnit.SECONDS));
        for (int i = 0; i < LocalPersistenceRuntime.MAX_QUEUED_JOBS; i++) {
            assertTrue(runtime.submit(() -> {}));
        }

        // save()'s own dispatch is rejected too (queue full) - it becomes dirty immediately.
        store.save(dataFor("A"));
        assertTrue(store.lastSaveFailed(), "a save that cannot even be scheduled must be treated as failed");

        boolean result = store.resetContext(new GuideContext("uuid", "singleplayer:test"));

        assertFalse(result, "reset must fail when the runtime cannot accept even the one bounded retry attempt");
        assertEquals(0, delegate.resetCallCount.get(), "delegate.resetContext() must NEVER be called - no reset occurred, so no old dirty state could ever be written under a falsely-successful reset");

        blockWorker.countDown();
    }

    @Test
    @DisplayName("REQUIRED: dirty failure then reset - a write that fails again during the reset's one retry aborts the reset; later recovery lets a retried reset succeed; a subsequent flush never resurrects the original failure")
    void dirtyFailureDuringResetAbortsThenLaterRecoverySucceeds() {
        FakeStore delegate = new FakeStore();
        delegate.failWith = data -> new GuideProgressPersistenceException("keeps failing", new IOException("boom"));
        AsyncGuideProgressStore store = new AsyncGuideProgressStore(delegate, new LocalPersistenceRuntime());

        store.save(dataFor("A"));
        awaitFlagTrue(store, 2000);

        GuideContext ctx = new GuideContext("uuid", "singleplayer:test");
        boolean firstAttempt = store.resetContext(ctx); // drainBounded gives A one retry - it fails again

        assertFalse(firstAttempt, "reset must not be applied while the dirty write's one retry also fails");
        assertEquals(0, delegate.resetCallCount.get());
        assertEquals(2, delegate.saveCallCount.get(), "the original attempt plus exactly one retry from the reset's drain");

        delegate.failWith = data -> null; // persistence recovers
        boolean secondAttempt = store.resetContext(ctx); // this retry succeeds, so the drain is now clean

        assertTrue(secondAttempt, "reset must succeed once the dirty write's retry finally persists");
        assertEquals(1, delegate.resetCallCount.get());
        assertEquals(List.of("A"), delegate.savedMarkers, "A finally persisted via the successful retry - a legitimate, not stale, write, ordered strictly before the reset that immediately followed it");

        int saveCallCountAfterReset = delegate.saveCallCount.get();
        assertTrue(store.flushBounded(Duration.ofSeconds(1)));
        assertEquals(saveCallCountAfterReset, delegate.saveCallCount.get(), "a later flush must not trigger any further save - nothing is dirty/pending after a successful reset");
        assertEquals(1, delegate.resetCallCount.get(), "a later flush must never resurrect or redo the already-applied reset");
    }

    @Test
    @DisplayName("REQUIRED: successful active-save drain - reset waits for the old save, then succeeds; final disk state contains the reset; a subsequent flush cannot reintroduce old progress")
    void successfulActiveSaveDrainThenResetSucceeds(@TempDir Path tempDir) throws Exception {
        Path progressPath = tempDir.resolve("guide-progress.json");
        ControllableStore controllable = new ControllableStore(progressPath);
        controllable.enteredSave = new CountDownLatch(1);
        controllable.blockUntil = new CountDownLatch(1);
        AsyncGuideProgressStore store = new AsyncGuideProgressStore(controllable, new LocalPersistenceRuntime());

        GuideContext ctx = new GuideContext("uuid", "singleplayer:test");
        StepCompletionRecord rec = new StepCompletionRecord("step1", GuideCompletionSource.MANUAL, 1L);
        GuideProgressData preResetData = new GuideProgressData(1, Map.of(
                ctx.getStorageKey(), new ContextProgress("step1", Map.of("step1", rec))));

        store.save(preResetData);
        assertTrue(controllable.enteredSave.await(2, TimeUnit.SECONDS));

        new Thread(() -> {
            try {
                Thread.sleep(150);
            } catch (InterruptedException ignored) {
            }
            controllable.blockUntil.countDown();
        }).start();

        boolean result = store.resetContext(ctx);

        assertTrue(result, "reset must succeed once the old save finishes within the bound");
        GuideProgressData onDisk = controllable.load();
        assertFalse(onDisk.getContext(ctx.getStorageKey()).isStepCompleted("step1"));

        assertTrue(store.flushBounded(Duration.ofSeconds(1)));
        onDisk = controllable.load();
        assertFalse(onDisk.getContext(ctx.getStorageKey()).isStepCompleted("step1"), "a subsequent flush must not reintroduce the pre-reset progress");
    }

    @Test
    @DisplayName("REQUIRED (deterministic race): RESET WINS PERMANENTLY - a worker paused immediately before its durable write, released only after reset returns, can never leave pre-reset state visible afterward")
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

        store.save(preResetData);
        assertTrue(controllable.enteredSave.await(2, TimeUnit.SECONDS), "the write must be accepted and paused right before its durable write");

        new Thread(() -> {
            try {
                Thread.sleep(150);
            } catch (InterruptedException ignored) {
            }
            controllable.blockUntil.countDown(); // "then: allow worker A to proceed"
        }).start();

        boolean result = store.resetContext(ctx);
        assertTrue(result);

        GuideProgressData finalState = controllable.load();
        assertFalse(finalState.getContext(ctx.getStorageKey()).isStepCompleted("step1"),
                "no pre-reset state may be visible once resetContext() has returned");

        assertTrue(store.flushBounded(Duration.ofSeconds(1)));
        finalState = controllable.load();
        assertFalse(finalState.getContext(ctx.getStorageKey()).isStepCompleted("step1"), "must remain stable after a flush too");
    }

    @Test
    @DisplayName("A save requested after a successful resetContext is completely unaffected and persists normally")
    void saveAfterResetProceedsNormally() {
        FakeStore delegate = new FakeStore();
        AsyncGuideProgressStore store = new AsyncGuideProgressStore(delegate, new LocalPersistenceRuntime());

        store.save(dataFor("A"));
        awaitCallCount(delegate.saveCallCount, 1, 2000);

        assertTrue(store.resetContext(new GuideContext("uuid", "singleplayer:test")));
        store.save(dataFor("B"));
        awaitCallCount(delegate.saveCallCount, 2, 2000);

        assertEquals(List.of("A", "B"), delegate.savedMarkers);
        assertEquals(1, delegate.resetCallCount.get());
    }

    @Test
    @DisplayName("REQUIRED: resetting one context never loses another context's already-durable progress")
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

        assertTrue(store.resetContext(ctxX));

        GuideProgressData afterReset = controllable.load();
        assertFalse(afterReset.getContext(ctxX.getStorageKey()).isStepCompleted("step1"));
        assertTrue(afterReset.getContext(ctxY.getStorageKey()).isStepCompleted("step1"), "ctxY's progress must survive ctxX's reset");
    }

    @Test
    @DisplayName("If the underlying reset write itself fails, resetContext does not throw, reports false, and marks the failure observable")
    void resetContextPersistenceFailureIsObservableNotThrown(@TempDir Path tempDir) throws Exception {
        Path progressPath = tempDir.resolve("guide-progress.json");
        JsonGuideProgressStore realStore = new JsonGuideProgressStore(progressPath);
        AsyncGuideProgressStore store = new AsyncGuideProgressStore(realStore, new LocalPersistenceRuntime());

        Path tmpBlocker = tempDir.resolve("guide-progress.json.tmp");
        Files.createDirectory(tmpBlocker);

        GuideContext ctx = new GuideContext("uuid", "singleplayer:test");
        boolean[] result = new boolean[1];
        assertDoesNotThrow(() -> result[0] = store.resetContext(ctx));
        assertFalse(result[0]);
        assertTrue(store.lastSaveFailed());
    }

    @Test
    @DisplayName("resetContext works cleanly with nothing pending")
    void resetContextWorksWithNothingPending() {
        FakeStore delegate = new FakeStore();
        AsyncGuideProgressStore store = new AsyncGuideProgressStore(delegate, new LocalPersistenceRuntime());

        assertTrue(store.resetContext(new GuideContext("uuid", "singleplayer:test")));
        assertEquals(1, delegate.resetCallCount.get());
    }

    @Test
    @DisplayName("REQUIRED: real synchronized-delegate blocking case - a save genuinely holding the delegate's own monitor must not make reset block behind it; reset aborts within its bound and delegate.resetContext is never called")
    void resetDoesNotBlockBehindADelegateHeldMonitor() throws InterruptedException {
        MonitorSharingStore delegate = new MonitorSharingStore();
        delegate.enteredSave = new CountDownLatch(1);
        delegate.releaseSave = new CountDownLatch(1); // never released within this test's timeout window
        AsyncGuideProgressStore store = new AsyncGuideProgressStore(delegate, new LocalPersistenceRuntime(), Duration.ofMillis(200));

        store.save(dataFor("A"));
        assertTrue(delegate.enteredSave.await(2, TimeUnit.SECONDS), "the background thread must now be INSIDE the synchronized save(), holding the delegate's monitor");

        long start = System.nanoTime();
        boolean result = store.resetContext(new GuideContext("uuid", "singleplayer:test"));
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertFalse(result, "reset must report failure rather than blocking behind the held monitor");
        assertTrue(elapsedMs < 1500, "resetContext must return close to its own bound, never block waiting to enter the delegate's still-held monitor; took " + elapsedMs + "ms");
        assertEquals(0, delegate.resetCallCount.get(), "delegate.resetContext() must NEVER even be attempted when the drain could not be confirmed safe - attempting it would block on the held monitor");

        delegate.releaseSave.countDown(); // let the stuck save finish so the JVM can shut down cleanly
    }
}
