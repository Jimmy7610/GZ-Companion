package se.jimmyeliasson.gzcompanion.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link LocalPersistenceRuntime}'s laziness/singleton/bounded-scheduling guarantees - mirrors
 * {@code GameZoneLiveDataRuntimeTest} for the sibling local-disk-persistence worker. See
 * docs/PERFORMANCE-AUDIT-ALPHA4.md's "2026-09-13 follow-up"/"correctness follow-up" sections.
 */
class LocalPersistenceRuntimeTest {

    @Test
    @DisplayName("Constructing the runtime does not create the executor")
    void constructionIsLazy() {
        LocalPersistenceRuntime runtime = new LocalPersistenceRuntime();
        assertFalse(runtime.isExecutorInitializedForTesting());
    }

    @Test
    @DisplayName("submit() creates the worker only on first call, then reuses the same one for later submissions")
    void executorIsCreatedOnceAndReused() throws InterruptedException {
        LocalPersistenceRuntime runtime = new LocalPersistenceRuntime();
        assertFalse(runtime.isExecutorInitializedForTesting());

        AtomicReference<Thread> firstThread = new AtomicReference<>();
        CountDownLatch firstDone = new CountDownLatch(1);
        assertTrue(runtime.submit(() -> {
            firstThread.set(Thread.currentThread());
            firstDone.countDown();
        }));
        assertTrue(firstDone.await(2, TimeUnit.SECONDS));
        assertTrue(runtime.isExecutorInitializedForTesting());

        AtomicReference<Thread> secondThread = new AtomicReference<>();
        CountDownLatch secondDone = new CountDownLatch(1);
        assertTrue(runtime.submit(() -> {
            secondThread.set(Thread.currentThread());
            secondDone.countDown();
        }));
        assertTrue(secondDone.await(2, TimeUnit.SECONDS));

        assertSame(firstThread.get(), secondThread.get(), "repeated submissions must run on the identical worker thread");
    }

    @Test
    @DisplayName("The worker thread is a daemon named gzcompanion-local-persistence")
    void workerThreadIsANamedDaemon() throws Exception {
        LocalPersistenceRuntime runtime = new LocalPersistenceRuntime();
        AtomicReference<Thread> observed = new AtomicReference<>();
        CountDownLatch ran = new CountDownLatch(1);

        assertTrue(runtime.submit(() -> {
            observed.set(Thread.currentThread());
            ran.countDown();
        }));

        assertTrue(ran.await(2, TimeUnit.SECONDS));
        assertEquals("gzcompanion-local-persistence", observed.get().getName());
        assertTrue(observed.get().isDaemon(), "the local-persistence worker must be a daemon thread");
    }

    @Test
    @DisplayName("Once the worker is busy and the bounded queue is completely full, a further submit() is explicitly rejected")
    void submitIsRejectedOnceQueueIsSaturated() throws InterruptedException {
        LocalPersistenceRuntime runtime = new LocalPersistenceRuntime();
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
            assertTrue(runtime.submit(() -> {}), "job " + i + " should still fit in the bounded queue");
        }

        assertFalse(runtime.submit(() -> {}), "a submit() beyond the bounded capacity must be explicitly rejected");
        blockWorker.countDown();
    }

    @Test
    @DisplayName("A rejected submit() never runs the task on the calling thread")
    void rejectedSubmitNeverRunsOnCallingThread() throws InterruptedException {
        LocalPersistenceRuntime runtime = new LocalPersistenceRuntime();
        CountDownLatch blockWorker = new CountDownLatch(1);
        CountDownLatch workerEntered = new CountDownLatch(1);
        Thread testThread = Thread.currentThread();

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
            runtime.submit(() -> {});
        }

        AtomicInteger ranOnCallingThread = new AtomicInteger(0);
        boolean accepted = runtime.submit(() -> {
            if (Thread.currentThread() == testThread) {
                ranOnCallingThread.incrementAndGet();
            }
        });

        assertFalse(accepted);
        assertEquals(0, ranOnCallingThread.get());
        blockWorker.countDown();
    }
}
