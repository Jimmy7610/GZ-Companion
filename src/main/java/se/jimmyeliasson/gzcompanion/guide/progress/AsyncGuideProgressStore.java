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
 * the shared {@link LocalPersistenceRuntime} executor; a request while a save is already active
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
 * <p><b>Failure handling.</b> {@link GuideProgressStore#save}'s own atomic temp-file-then-move
 * write means a failed/partial write can never corrupt the previously-valid file - the old file is
 * simply left in place. A save failure here is caught, logged, and never rethrown (it must never
 * crash the background thread or the client); the NEXT real state change naturally retries, since
 * {@link #activeSave} is always cleared after an attempt regardless of outcome. {@link
 * #lastSaveFailed()} exposes the most recent outcome for future diagnostics surfacing.
 *
 * <p><b>{@link #resetContext} stays synchronous.</b> {@code GuideEngine.resetGuideProgress()}
 * immediately reloads from disk right after calling this - making the reset itself asynchronous
 * would let that immediate reload race ahead of the write and read stale data. Reset is a rare,
 * explicit, low-frequency user action (the same category as the Settings/Chest/MarketWatch/
 * Building/Settlement stores' own synchronous on-click saves, already accepted as fine in the
 * alpha.4 audit), so a brief synchronous write here is acceptable. To stop a save that was queued
 * BEFORE the reset from later silently undoing it, {@code resetContext} bumps {@link #epoch} and
 * drops {@link #pendingSave} outright; the background writer re-checks its captured epoch
 * immediately before actually writing and skips (logs, does not write) a save that a reset has
 * since superseded. This closes the realistic case (a save still sitting in the pending slot,
 * not yet dispatched). A save that is already physically inside {@code delegate.save(...)} at the
 * exact instant a reset is requested is not retroactively cancelled - an accepted, narrow,
 * best-effort limitation given how rare and inexpensive this file's writes are; see
 * docs/PERFORMANCE-AUDIT-ALPHA4.md for the full reasoning.
 *
 * <p><b>Shutdown / final flush.</b> {@link #flushBounded} performs a bounded best-effort wait for
 * any in-flight/pending save to actually reach disk - see {@code GZCompanionClient}'s {@code
 * ClientLifecycleEvents.CLIENT_STOPPING} registration, which calls this with a short timeout so a
 * normal game exit does not silently drop the player's latest guide progress, without ever risking
 * blocking shutdown indefinitely.
 */
public final class AsyncGuideProgressStore implements GuideProgressStore {
    private static final Logger LOGGER = LoggerFactory.getLogger(AsyncGuideProgressStore.class);

    private final GuideProgressStore delegate;
    private final LocalPersistenceRuntime runtime;

    private final Object lock = new Object();
    private record PendingWrite(GuideProgressData data, long epoch) {}
    private PendingWrite activeSave;
    private PendingWrite pendingSave;
    private long epoch = 0;
    private volatile boolean lastSaveFailed;

    public AsyncGuideProgressStore(GuideProgressStore delegate, LocalPersistenceRuntime runtime) {
        this.delegate = delegate;
        this.runtime = runtime;
    }

    @Override
    public GuideProgressData load() {
        return delegate.load();
    }

    /**
     * Requests that {@code data} eventually be persisted - returns immediately, never touches the
     * disk on the calling thread. See class doc comment for the coalescing "latest state wins"
     * policy.
     */
    @Override
    public void save(GuideProgressData data) {
        if (data == null) return;
        synchronized (lock) {
            PendingWrite write = new PendingWrite(data, epoch);
            if (activeSave == null) {
                activeSave = write;
                runtime.executor().execute(this::runSaveThenAdvance);
            } else {
                pendingSave = write; // replaces whatever was pending - latest wins, never queued
            }
        }
    }

    /**
     * A rare, explicit, whole-context reset - kept synchronous on purpose (the caller immediately
     * reloads afterward). Supersedes anything queued-but-not-yet-written from before this call; see
     * class doc comment for the narrow limitation on an already-in-flight write.
     */
    @Override
    public void resetContext(GuideContext context) {
        synchronized (lock) {
            epoch++;
            pendingSave = null;
        }
        delegate.resetContext(context);
    }

    private void runSaveThenAdvance() {
        PendingWrite toWrite;
        boolean stillCurrent;
        synchronized (lock) {
            toWrite = activeSave;
            stillCurrent = toWrite.epoch() == epoch;
        }
        if (stillCurrent) {
            try {
                delegate.save(toWrite.data());
                lastSaveFailed = false;
            } catch (Exception e) {
                lastSaveFailed = true;
                LOGGER.error("Background guide progress save failed - will retry automatically on the next state change", e);
            }
        } else {
            LOGGER.debug("Dropping a background guide-progress save superseded by a reset before it could run");
        }
        synchronized (lock) {
            activeSave = null;
            if (pendingSave != null) {
                activeSave = pendingSave;
                pendingSave = null;
                runtime.executor().execute(this::runSaveThenAdvance);
            }
            lock.notifyAll();
        }
    }

    /**
     * Bounded best-effort wait for any in-flight/pending save to actually reach disk - never blocks
     * longer than {@code timeout}. Returns {@code true} if everything requested so far was written
     * (or there was nothing to write), {@code false} if the timeout elapsed first or the waiting
     * thread was interrupted. Safe to call from the main/render thread during shutdown only - it is
     * a blocking wait, not a fire-and-forget call like {@link #save}.
     */
    public boolean flushBounded(Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        synchronized (lock) {
            try {
                while (activeSave != null || pendingSave != null) {
                    long remainingMs = (deadline - System.nanoTime()) / 1_000_000;
                    if (remainingMs <= 0) {
                        return false;
                    }
                    lock.wait(remainingMs);
                }
                return true;
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

    /** Test-only introspection - never used by production code. */
    boolean hasPendingWorkForTesting() {
        synchronized (lock) {
            return activeSave != null || pendingSave != null;
        }
    }
}
