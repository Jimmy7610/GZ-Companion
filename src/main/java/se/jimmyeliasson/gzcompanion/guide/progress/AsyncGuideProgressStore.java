package se.jimmyeliasson.gzcompanion.guide.progress;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.jimmyeliasson.gzcompanion.storage.LocalPersistenceRuntime;

import java.time.Duration;

/**
 * A {@link GuideProgressStore} decorator that moves the actual disk write off the calling thread -
 * see docs/PERFORMANCE-AUDIT-ALPHA4.md's "2026-09-13 follow-up" section for the audit finding this
 * fixes: {@code GuideScheduler} triggers {@code GuideEngine.evaluate()} from the Minecraft client
 * tick thread, and a completed step used to make that thread synchronously write, temp-file-and-
 * atomically-move, {@code guide-progress.json} - a real (if infrequent) "filesystem write on the
 * tick thread" violation of this project's performance budget.
 *
 * <p><b>Design - mirrors {@code LeaderboardManager}'s already-proven scheduler.</b> At most ONE
 * {@link #activeSave active save} and ONE {@link #pendingSave pending save} are ever tracked, both
 * guarded by {@link #lock}: a {@link #save} request while nothing is active starts immediately on
 * the shared {@link LocalPersistenceRuntime} worker; a request while a save is already active
 * REPLACES whatever was pending (never a growing queue) - so several rapid step completions (state
 * A, then B, then C, all before the disk has caught up) result in exactly two disk writes: A, then
 * the LATEST state (C), never a write for every intermediate state and never an unbounded backlog.
 * When the active write finishes, it atomically takes whatever is pending (if any) and starts THAT
 * as the new active write - a self-perpetuating chain, no poll loop, no extra thread.
 *
 * <p><b>Snapshots are already immutable.</b> {@link GuideProgressData}/{@link ContextProgress} both
 * defensively copy their maps into unmodifiable copies in their own compact constructors, so a
 * {@code GuideProgressData} handed to {@link #save} can never be mutated afterward by {@code
 * GuideEngine} - the background writer only ever reads it. No additional copying is needed here.
 *
 * <p><b>Failure handling (2026-09-13 correctness follow-up).</b> The production delegate,
 * {@code JsonGuideProgressStore}, now THROWS {@link GuideProgressPersistenceException} on a real
 * write failure instead of silently catching and returning - the original version of this class
 * assumed that throw, but the delegate never actually did it, so a real disk failure was invisible
 * (this class believed every write succeeded). A failed write is caught here, sets {@link
 * #lastSaveFailed}, and - crucially - is retained as {@link #dirtyAfterFailure} rather than being
 * discarded, UNLESS something newer has already been requested in the meantime (a newer {@code
 * GuideProgressData} snapshot is always a superset of an older one - {@code GuideEngine} only ever
 * adds completions, never removes them outside an explicit reset - so a fresh save silently
 * "retrying" a stale failed one loses nothing). The dirty state is retried exactly once whenever
 * {@link #flushBounded} is called (a "sensible opportunity", not a hammering loop) and otherwise
 * sits inertly - no busy retry loop, no unbounded retry queue (it is a single field, not a list).
 * The atomic temp-file-then-move write itself is unchanged, so a failed/partial write still can
 * never corrupt the previously-valid file.
 *
 * <p><b>{@link #resetContext} (2026-09-13 correctness follow-up - full fix, no caveat).</b> The
 * original version tried to invalidate an in-flight write with an epoch counter checked
 * immediately before the delegate call - but the check and the call were not atomic, so a
 * concurrently-completing reset could still let a stale write land afterward. This version instead
 * makes {@code resetContext} synchronously DRAIN everything outstanding first (see {@link
 * #drainBounded}) - waiting for any active write to actually finish, and letting any pending write
 * actually run (never dropping it, so another context's concurrently-queued progress is never lost
 * just because a different context was reset) - and only THEN performs the reset. Since Minecraft's
 * client/tick/render/input handling is single-threaded, and this is always called from that single
 * thread, no OTHER save can possibly be dispatched while a call to this method is still on the call
 * stack; the drain-then-reset ordering is therefore a genuine guarantee, not a best-effort one,
 * under any realistic disk-I/O condition - the wait is bounded (see {@link #RESET_WAIT_BOUND}) only
 * so a truly hung/dead disk (already a far bigger problem than this specific ordering) cannot hang
 * the reset button forever; see docs/PERFORMANCE-AUDIT-ALPHA4.md for the full writeup.
 *
 * <p><b>Shutdown / final flush.</b> {@link #flushBounded} performs a bounded wait for any
 * in-flight/pending save to actually reach disk, giving one retry attempt to anything left dirty
 * from a prior failure - see {@code GZCompanionClient}'s {@code
 * ClientLifecycleEvents.CLIENT_STOPPING} registration, which calls this with a short timeout so a
 * normal game exit does not silently drop the player's latest guide progress, without ever risking
 * blocking shutdown indefinitely. Returns {@code true} only if the latest requested state is
 * actually confirmed persisted.
 */
public final class AsyncGuideProgressStore implements GuideProgressStore {
    private static final Logger LOGGER = LoggerFactory.getLogger(AsyncGuideProgressStore.class);

    /** How long {@link #resetContext} will wait for an already-active write to finish before
     * proceeding anyway - generous relative to how long a small local JSON write actually takes
     * (milliseconds), so hitting this bound in practice would mean the underlying disk I/O itself
     * is hung, a far larger problem than this specific ordering guarantee. */
    private static final Duration RESET_WAIT_BOUND = Duration.ofSeconds(5);

    private final GuideProgressStore delegate;
    private final LocalPersistenceRuntime runtime;
    private final Duration resetWaitBound;

    private final Object lock = new Object();
    private GuideProgressData activeSave;
    private GuideProgressData pendingSave;
    /** The most recent write that failed (or couldn't even be scheduled) and has not since been
     * superseded by a newer request - retried exactly once per {@link #flushBounded} call, never
     * automatically/repeatedly. A single field, never a list - inherently bounded. */
    private GuideProgressData dirtyAfterFailure;
    private volatile boolean lastSaveFailed;

    public AsyncGuideProgressStore(GuideProgressStore delegate, LocalPersistenceRuntime runtime) {
        this(delegate, runtime, RESET_WAIT_BOUND);
    }

    /** Test-only seam - a shorter reset-wait bound lets tests prove the "proceeds anyway after the
     * bound elapses" behavior without a real multi-second wait. */
    AsyncGuideProgressStore(GuideProgressStore delegate, LocalPersistenceRuntime runtime, Duration resetWaitBound) {
        this.delegate = delegate;
        this.runtime = runtime;
        this.resetWaitBound = resetWaitBound;
    }

    @Override
    public GuideProgressData load() {
        return delegate.load();
    }

    /**
     * Requests that {@code data} eventually be persisted - returns immediately, never touches the
     * disk on the calling thread. See class doc comment for the coalescing "latest state wins"
     * policy and the failure/retry contract.
     */
    @Override
    public void save(GuideProgressData data) {
        if (data == null) return;
        synchronized (lock) {
            // A fresh request is always a newer, superset snapshot (GuideEngine only ever adds
            // completions) - it supersedes anything previously left dirty by a failure, regardless
            // of which branch below actually handles it. Without this, a successful dispatch here
            // could leave a now-obsolete dirtyAfterFailure lingering, which a later flush/reset
            // could wrongly "retry" and silently regress already-persisted newer progress.
            dirtyAfterFailure = null;
            if (activeSave == null) {
                dispatchLocked(data);
            } else {
                pendingSave = data; // replaces whatever was pending - latest wins, never queued
            }
        }
    }

    /**
     * A rare, explicit, whole-context reset. Synchronously drains any outstanding write first (see
     * {@link #drainBounded}), THEN performs the reset - see class doc comment for why this is a
     * full guarantee, not a best-effort one, in this project's actual (single client-thread caller)
     * concurrency model. Never throws even if the underlying reset write itself fails - the failure
     * is instead reflected in {@link #lastSaveFailed()}, exactly like a regular {@link #save}.
     */
    @Override
    public void resetContext(GuideContext context) {
        boolean drained = drainBounded(resetWaitBound);
        if (!drained) {
            LOGGER.warn("Timed out waiting for an in-flight guide-progress save to finish before "
                    + "reset - proceeding with the reset anyway. This would only happen if the "
                    + "underlying disk write itself is hung, which is a larger problem than this "
                    + "reset.");
        }
        try {
            delegate.resetContext(context);
            lastSaveFailed = false;
        } catch (GuideProgressPersistenceException e) {
            lastSaveFailed = true;
            LOGGER.error("Failed to persist guide progress reset for context {}", context, e);
        }
    }

    /** Must be called while holding {@link #lock}. Marks {@code data} as the active write and
     * submits it to the shared runtime; if the shared runtime rejects it (its bounded queue is
     * saturated), this is treated exactly like a failed write - never silently dropped, never left
     * claiming an active slot with no job that will ever clear it. */
    private void dispatchLocked(GuideProgressData data) {
        activeSave = data;
        boolean accepted = runtime.submit(this::runSaveThenAdvance);
        if (!accepted) {
            LOGGER.warn("Shared local-persistence worker rejected a guide-progress save (queue "
                    + "saturated) - treating as a failed write, retryable on the next state change or flush");
            activeSave = null;
            lastSaveFailed = true;
            if (pendingSave == null) {
                dirtyAfterFailure = data;
            }
        }
    }

    private void runSaveThenAdvance() {
        GuideProgressData toWrite;
        synchronized (lock) {
            toWrite = activeSave;
        }
        boolean success;
        try {
            delegate.save(toWrite);
            success = true;
        } catch (GuideProgressPersistenceException e) {
            success = false;
            LOGGER.error("Background guide progress save failed - will retry on the next state change or flush", e);
        } catch (Exception e) {
            // Defense in depth - an unexpected failure here must never crash the background worker.
            success = false;
            LOGGER.error("Unexpected failure during background guide progress save", e);
        }
        lastSaveFailed = !success;

        synchronized (lock) {
            activeSave = null;
            if (!success && pendingSave == null) {
                dirtyAfterFailure = toWrite; // nothing newer queued - retain this as the retry target
            }
            if (pendingSave != null) {
                GuideProgressData next = pendingSave;
                pendingSave = null;
                dispatchLocked(next);
            }
            lock.notifyAll();
        }
    }

    /**
     * Bounded wait for any in-flight/pending save to actually reach disk, giving a save left dirty
     * by a prior failure exactly one fresh retry attempt (never a repeated hammering loop). Returns
     * {@code true} only if the latest requested state is confirmed persisted when this returns -
     * {@code false} if the timeout elapsed, the wait was interrupted, or the retry attempt (if any)
     * itself failed. Safe to call from the main/render thread during shutdown - it is a blocking
     * wait, not a fire-and-forget call like {@link #save}.
     */
    public boolean flushBounded(Duration timeout) {
        return drainBounded(timeout);
    }

    /** Waits (bounded) until nothing is active or pending, giving one retry attempt to anything
     * left dirty from a prior failure at the start. Returns {@code true} iff, when it returns,
     * nothing remains active, pending, or dirty - i.e. the latest requested state is confirmed
     * persisted. Used by both {@link #flushBounded} and {@link #resetContext}. */
    private boolean drainBounded(Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        synchronized (lock) {
            try {
                if (activeSave == null && pendingSave == null && dirtyAfterFailure != null) {
                    GuideProgressData retry = dirtyAfterFailure;
                    dirtyAfterFailure = null;
                    dispatchLocked(retry);
                }
                while (activeSave != null || pendingSave != null) {
                    long remainingMs = (deadline - System.nanoTime()) / 1_000_000;
                    if (remainingMs <= 0) {
                        return false;
                    }
                    lock.wait(remainingMs);
                }
                return dirtyAfterFailure == null;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
    }

    /** Whether the most recently attempted background save failed - diagnostics only. */
    public boolean lastSaveFailed() {
        return lastSaveFailed;
    }
}
