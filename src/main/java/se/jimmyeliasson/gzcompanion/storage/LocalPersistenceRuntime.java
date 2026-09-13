package se.jimmyeliasson.gzcompanion.storage;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The ONE shared background executor for local-disk persistence work that must not block the
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
 * executor thread is created on first actual use, not at construction, so simply starting
 * Companion (or using any feature that never needs background disk persistence) never creates it.
 */
public final class LocalPersistenceRuntime {
    private final Object lock = new Object();
    private ExecutorService executor;

    /** The one shared daemon executor for local persistence work. Created on first call. */
    public ExecutorService executor() {
        synchronized (lock) {
            if (executor == null) {
                executor = Executors.newSingleThreadExecutor(runnable -> {
                    Thread thread = new Thread(runnable, "gzcompanion-local-persistence");
                    thread.setDaemon(true);
                    return thread;
                });
            }
            return executor;
        }
    }

    /** Test-only introspection - never used by production code. */
    boolean isExecutorInitializedForTesting() {
        synchronized (lock) {
            return executor != null;
        }
    }
}
