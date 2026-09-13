package se.jimmyeliasson.gzcompanion.storage;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * The ONE shared background worker for local-disk persistence work that must not block the
 * Minecraft client/tick/render thread - currently used by {@code AsyncGuideProgressStore} to move
 * Guide's automatic (tick-triggered) progress-file writes off the tick thread (see
 * docs/PERFORMANCE-AUDIT-ALPHA4.md's "2026-09-13 follow-up" section).
 *
 * <p>Deliberately separate from {@link se.jimmyeliasson.gzcompanion.gamezone.net.GameZoneLiveDataRuntime}
 * - that one is for network I/O to GameZone's public web pages, this one is for local filesystem
 * I/O. Sharing a single worker across both would be a false economy: a slow/stalled disk write
 * would then also delay GameZone network requests (and vice versa) for no real reason. Correct
 * separation of responsibility matters more than minimizing the raw thread count - see the class
 * doc comment on {@code GameZoneLiveDataRuntime} for the sibling rationale.
 *
 * <p>Lazy, daemon, and a session-wide singleton exactly like {@code GameZoneLiveDataRuntime}: the
 * worker thread is created on first actual use, not at construction, so simply starting Companion
 * (or using any feature that never needs background disk persistence) never creates it.
 *
 * <p><b>Bounded scheduling, no raw executor exposure (2026-09-13 correctness follow-up).</b> Like
 * its GameZone sibling, this class exposes only {@link #submit}, never a raw {@code
 * ExecutorService} - the underlying worker uses a bounded queue ({@link #MAX_QUEUED_JOBS}) and an
 * explicit {@link ThreadPoolExecutor.AbortPolicy} (never {@code CallerRunsPolicy}, which could
 * otherwise run a disk write on whatever thread called {@link #submit}). {@code
 * AsyncGuideProgressStore} already bounds its OWN requests to at most one active + one pending, but
 * this shared foundation is meant for future local-persistence consumers too, so the underlying
 * queue itself must not be able to grow without bound regardless of how a future caller behaves.
 */
public final class LocalPersistenceRuntime {
    /** Bounded backlog for the shared local-persistence worker - see {@code
     * GameZoneLiveDataRuntime#MAX_QUEUED_JOBS} for the identical rationale. */
    public static final int MAX_QUEUED_JOBS = 8;

    private final Object lock = new Object();
    private ThreadPoolExecutor executor;

    /**
     * Submits {@code task} to the one shared local-persistence worker. Returns {@code true} if
     * accepted, {@code false} if the shared queue is saturated - callers must handle {@code false}
     * explicitly (e.g. {@code AsyncGuideProgressStore} treats it exactly like a failed write,
     * retryable later) rather than assuming the task will ever run. Never runs {@code task} on the
     * calling thread.
     */
    public boolean submit(Runnable task) {
        try {
            ensureExecutor().execute(task);
            return true;
        } catch (RejectedExecutionException e) {
            return false;
        }
    }

    private ThreadPoolExecutor ensureExecutor() {
        synchronized (lock) {
            if (executor == null) {
                executor = new ThreadPoolExecutor(
                        1, 1,
                        0L, TimeUnit.MILLISECONDS,
                        new ArrayBlockingQueue<>(MAX_QUEUED_JOBS),
                        runnable -> {
                            Thread thread = new Thread(runnable, "gzcompanion-local-persistence");
                            thread.setDaemon(true);
                            return thread;
                        },
                        new ThreadPoolExecutor.AbortPolicy());
            }
            return executor;
        }
    }

    /** Test-only introspection - never used by production code. */
    public boolean isExecutorInitializedForTesting() {
        synchronized (lock) {
            return executor != null;
        }
    }
}
