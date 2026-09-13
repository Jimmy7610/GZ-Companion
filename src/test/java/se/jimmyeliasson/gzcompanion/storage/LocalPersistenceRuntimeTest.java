package se.jimmyeliasson.gzcompanion.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link LocalPersistenceRuntime}'s laziness/singleton guarantees - mirrors {@code
 * GameZoneLiveDataRuntimeTest} for the sibling local-disk-persistence worker. See
 * docs/PERFORMANCE-AUDIT-ALPHA4.md's "2026-09-13 follow-up" section.
 */
class LocalPersistenceRuntimeTest {

    @Test
    @DisplayName("Constructing the runtime does not create the executor")
    void constructionIsLazy() {
        LocalPersistenceRuntime runtime = new LocalPersistenceRuntime();
        assertFalse(runtime.isExecutorInitializedForTesting());
    }

    @Test
    @DisplayName("executor() creates the worker only on first call, then reuses the same instance")
    void executorIsCreatedOnceAndReused() {
        LocalPersistenceRuntime runtime = new LocalPersistenceRuntime();

        ExecutorService first = runtime.executor();
        assertTrue(runtime.isExecutorInitializedForTesting());

        ExecutorService second = runtime.executor();
        assertSame(first, second);
    }

    @Test
    @DisplayName("The worker thread is a daemon named gzcompanion-local-persistence")
    void workerThreadIsANamedDaemon() throws Exception {
        LocalPersistenceRuntime runtime = new LocalPersistenceRuntime();
        java.util.concurrent.atomic.AtomicReference<Thread> observed = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.CountDownLatch ran = new java.util.concurrent.CountDownLatch(1);

        runtime.executor().execute(() -> {
            observed.set(Thread.currentThread());
            ran.countDown();
        });

        assertTrue(ran.await(2, java.util.concurrent.TimeUnit.SECONDS));
        assertEquals("gzcompanion-local-persistence", observed.get().getName());
        assertTrue(observed.get().isDaemon(), "the local-persistence worker must be a daemon thread");
    }
}
