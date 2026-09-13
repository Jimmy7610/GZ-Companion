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
 * discarded, UNLESS a newer request has arrived in the meantime. Note this is a pure "latest wins"
 * policy, NOT a superset assumption - {@code GuideEngine} supports {@code undoStepCompletion}, so a
 * newer snapshot can legitimately contain LESS than an older one. The newest requested snapshot is
 * simply always treated as authoritative, whatever its relationship to the previous one; retrying a
 * superseded failed write would silently reintroduce a state the newer request may have
 * deliberately removed, which {@link #save} therefore never does. The dirty state is retried
 * exactly once whenever {@link #flushBounded} (or {@link #resetContext}) runs (a "sensible
 * opportunity", not a hammering loop) and otherwise sits inertly - no busy retry loop, no unbounded
 * retry queue (it is a single field, not a list). The atomic temp-file-then-move write itself is
 * unchanged, so a failed/partial write still can never corrupt the previously-valid file.
 *
 * <p><b>{@link #resetContext} (2026-09-13 final persistence correctness pass - abort-on-incomplete-
 * drain).</b> An earlier version of this method drained with a bounded wait but then called {@code
 * delegate.resetContext(...)} UNCONDITIONALLY, even when the drain reported failure/timeout -
 * meaning an outstanding pre-reset write (active, pending, or dirty-from-a-prior-failure) could
 * still exist, and could land on disk AFTER the reset, resurrecting progress the player had just
 * explicitly removed. It also did not account for {@code JsonGuideProgressStore}'s {@code save}/
 * {@code load}/{@code resetContext} all sharing ONE intrinsic monitor: if the active write were
 * genuinely hung while holding that monitor, unconditionally calling {@code
 * delegate.resetContext(...)} afterward would simply block trying to enter it, silently defeating
 * the whole point of a bounded wait. This version instead treats an incomplete drain as an outright
 * abort: {@link #drainBounded} is the ONLY thing ever awaited (it only ever waits on THIS class's
 * own {@link #lock}, never touching the delegate's monitor, so its bound is always genuinely
 * respected regardless of the delegate's internal state) - if it does not report a clean drain
 * (nothing active, pending, or dirty), {@code delegate.resetContext} is NEVER called, and this
 * method returns {@code false} immediately. A reset therefore has exactly two possible outcomes:
 * fully applied and durably persisted ({@code true}), or not performed at all ({@code false}) -
 * never a partial/uncertain state, and {@code GuideEngine} relies on exactly this to decide whether
 * it is safe to reload from disk (see {@code GuideEngine#resetGuideProgress}).
 *
 * <p>When a clean drain IS achieved, this is a genuine guarantee, not a best-effort one, in this
 * project's actual concurrency model: Minecraft's client/tick/render/input handling is
 * single-threaded, and this method is always called from that one thread, so no OTHER save can
 * possibly be dispatched while a call to this method is still on the call stack - once the drain
 * observes "nothing active/pending/dirty," that state cannot change underneath it before the
 * reset's own write runs. The drain wait is bounded (see {@link #RESET_WAIT_BOUND}) purely so a
 * truly hung/dead disk cannot hang the reset button forever; hitting that bound now correctly
 * aborts the reset instead of racing ahead of it. See docs/PERFORMANCE-AUDIT-ALPHA4.md for the full
 * writeup, including why the PRIOR "proceed anyway" version was wrong.
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
            // The newest request is always authoritative - NOT necessarily a superset (GuideEngine
            // supports undoStepCompletion, so a newer snapshot can contain less than an older one).
            // It supersedes anything previously left dirty by a failure regardless, since dirty data
            // is by definition already retired - retrying it later instead of the newest request
            // would silently reintroduce a state the player may have deliberately changed away from.
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
     * {@link #drainBounded}); if - and only if - that drain fully succeeds (nothing left active,
     * pending, or dirty) does this proceed to actually perform the reset. See class doc comment for
     * why an incomplete drain means an outright ABORT (never "proceed anyway"), and why a clean
     * drain is a full guarantee, not a best-effort one, in this project's concurrency model.
     *
     * @return {@code true} only if the reset was actually, durably performed; {@code false} if it
     * was not performed at all - either because the pre-reset drain could not be confirmed safe
     * within {@link #resetWaitBound}, or because the reset's own underlying write failed. Either
     * way, {@code false} means existing in-memory/disk progress is untouched and still authoritative
     * - never thrown as an exception, so a caller like {@code GuideEngine} can inspect it safely.
     */
    @Override
    public boolean resetContext(GuideContext context) {
        boolean drained = drainBounded(resetWaitBound);
        if (!drained) {
            LOGGER.warn("Aborting guide-progress reset for context {} - could not confirm within {} "
                    + "that all outstanding pre-reset writes had finished or been resolved. Applying "
                    + "the reset anyway could let a pre-reset write land afterward and resurrect "
                    + "progress the player just removed, so the reset was NOT performed. This would "
                    + "only happen if the underlying disk I/O itself is stuck - try again once "
                    + "persistence recovers.", context, resetWaitBound);
            return false;
        }
        try {
            delegate.resetContext(context);
            lastSaveFailed = false;
            return true;
        } catch (GuideProgressPersistenceException e) {
            lastSaveFailed = true;
            LOGGER.error("Failed to persist guide progress reset for context {} - the reset was NOT applied", context, e);
            return false;
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
