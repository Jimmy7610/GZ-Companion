package se.jimmyeliasson.gzcompanion.gamezone.toast;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Local, in-memory notification queue. Never persists anything to disk - toasts are purely
 * ephemeral UI state. Deduplicates repeated identical events within a short window and respects
 * two independent settings toggles (all Companion notifications, and GameZone event toasts
 * specifically) so either can turn this off without touching the other.
 */
public final class GameZoneToastManager {
    public static final long DEFAULT_DEDUPE_WINDOW_MS = 8_000L;
    public static final long DEFAULT_TOAST_DURATION_MS = 5_000L;
    private static final int MAX_QUEUED_TOASTS = 3;

    private final Deque<ToastEntry> queue = new ArrayDeque<>();
    private final Map<String, Long> lastShownAtByDedupeKey = new HashMap<>();
    private Supplier<Boolean> notificationsEnabledSupplier = () -> true;
    private Supplier<Boolean> gameZoneToastsEnabledSupplier = () -> true;

    public void setNotificationsEnabledSupplier(Supplier<Boolean> supplier) {
        if (supplier != null) this.notificationsEnabledSupplier = supplier;
    }

    public void setGameZoneToastsEnabledSupplier(Supplier<Boolean> supplier) {
        if (supplier != null) this.gameZoneToastsEnabledSupplier = supplier;
    }

    /**
     * Offers a new toast for display. Returns {@code false} (and shows nothing) if either
     * notifications setting is off, or if the same {@code dedupeKey} was already shown within
     * {@link #DEFAULT_DEDUPE_WINDOW_MS}.
     */
    public boolean offer(String dedupeKey, String title, String body, long nowMs) {
        if (!notificationsEnabledSupplier.get() || !gameZoneToastsEnabledSupplier.get()) {
            return false;
        }
        pruneExpiredDedupeEntries(nowMs);

        Long lastShown = lastShownAtByDedupeKey.get(dedupeKey);
        if (lastShown != null && (nowMs - lastShown) < DEFAULT_DEDUPE_WINDOW_MS) {
            return false;
        }
        lastShownAtByDedupeKey.put(dedupeKey, nowMs);

        if (queue.size() >= MAX_QUEUED_TOASTS) {
            queue.pollFirst();
        }
        queue.addLast(new ToastEntry(title, body, nowMs, nowMs + DEFAULT_TOAST_DURATION_MS));
        return true;
    }

    /**
     * Opportunistically removes dedupe entries older than {@link #DEFAULT_DEDUPE_WINDOW_MS} - once
     * an entry is that old it can never again suppress a future {@link #offer}, so keeping it
     * around forever would let this map grow without bound over a long session as distinct event
     * keys (e.g. different players, different amounts) accumulate. No background thread: this
     * runs inline, only when a new event is actually offered, keeping the map's size proportional
     * to recent activity rather than lifetime activity.
     */
    private void pruneExpiredDedupeEntries(long nowMs) {
        lastShownAtByDedupeKey.entrySet().removeIf(entry -> (nowMs - entry.getValue()) >= DEFAULT_DEDUPE_WINDOW_MS);
    }

    /** The toast that should currently be visible, if any - expired entries are dropped first. */
    public Optional<ToastEntry> currentToast(long nowMs) {
        while (!queue.isEmpty() && queue.peekFirst().isExpired(nowMs)) {
            queue.pollFirst();
        }
        return queue.isEmpty() ? Optional.empty() : Optional.of(queue.peekFirst());
    }

    public int queuedCount() {
        return queue.size();
    }

    public void clear() {
        queue.clear();
        lastShownAtByDedupeKey.clear();
    }

    /** Test-only introspection into the dedupe map's size - never used by production code. */
    int dedupeMapSizeForTesting() {
        return lastShownAtByDedupeKey.size();
    }
}
