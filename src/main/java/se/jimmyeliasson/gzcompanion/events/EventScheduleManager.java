package se.jimmyeliasson.gzcompanion.events;

import se.jimmyeliasson.gzcompanion.gamezone.net.GameZoneLiveDataRuntime;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;

/**
 * Cache and fetch state machine for the Events tab - same proven design as {@code BountyManager}.
 *
 * <ul>
 *   <li>No background polling: the schedule is fetched only while Events is open ({@link
 *       #ensureFresh}), at most once per {@link #REFRESH_TTL} - calling it every render frame is a
 *       cheap no-op in between.</li>
 *   <li>All network work runs on the shared {@link GameZoneLiveDataRuntime} worker; render code
 *       only reads {@link #getSnapshot()} (a volatile read).</li>
 *   <li>At most one fetch at a time: a request while one is in flight is coalesced, never queued.</li>
 *   <li>"Uppdatera"/"Försök igen" ({@link #manualRefresh}) may skip the TTL but not the shorter
 *       {@link #MANUAL_REFRESH_COOLDOWN}, so repeated clicks can never spam the endpoint.</li>
 *   <li>A failed refresh keeps the last good schedule (status {@link EventScheduleStatus#STALE}).</li>
 *   <li>If the shared runtime rejects the job, the snapshot reverts and no attempt is recorded.</li>
 * </ul>
 */
public final class EventScheduleManager {
    /** Automatic refreshes while Events is open never happen more often than this. */
    public static final Duration REFRESH_TTL = Duration.ofMinutes(5);
    /** A manual refresh may bypass the TTL, but not more often than this. */
    public static final Duration MANUAL_REFRESH_COOLDOWN = Duration.ofSeconds(10);

    private final EventScheduleSource source;
    private final GameZoneLiveDataRuntime runtime;
    private final Supplier<Instant> clock;

    private final Object lock = new Object();
    private volatile EventScheduleSnapshot snapshot = EventScheduleSnapshot.idle();
    private boolean activeFetch;
    private volatile Instant lastFetchAttemptAt;

    public EventScheduleManager(EventScheduleSource source, GameZoneLiveDataRuntime runtime) {
        this(source, runtime, Instant::now);
    }

    /** Test-only seam: an injectable clock proves TTL/cooldown behaviour without real waiting. */
    EventScheduleManager(EventScheduleSource source, GameZoneLiveDataRuntime runtime, Supplier<Instant> clock) {
        this.source = source;
        this.runtime = runtime;
        this.clock = clock;
    }

    /** Cheap, non-blocking read - never null. */
    public EventScheduleSnapshot getSnapshot() {
        return snapshot;
    }

    /** Starts a background fetch if none was ever attempted or the last attempt is older than {@link #REFRESH_TTL}. */
    public void ensureFresh() {
        Instant lastAttempt = lastFetchAttemptAt;
        if (lastAttempt != null && Duration.between(lastAttempt, clock.get()).compareTo(REFRESH_TTL) < 0) return;
        requestFetch();
    }

    /** "Uppdatera" / "Försök igen": returns false (a no-op) during the cooldown or while a fetch is running. */
    public boolean manualRefresh() {
        if (!canManualRefresh()) return false;
        return requestFetch();
    }

    public boolean canManualRefresh() {
        Instant lastAttempt = lastFetchAttemptAt;
        if (lastAttempt != null && Duration.between(lastAttempt, clock.get()).compareTo(MANUAL_REFRESH_COOLDOWN) < 0) return false;
        synchronized (lock) {
            return !activeFetch;
        }
    }

    private boolean requestFetch() {
        EventScheduleSnapshot previous;
        synchronized (lock) {
            if (activeFetch) return false; // coalesce
            previous = snapshot;
            activeFetch = true;
            snapshot = new EventScheduleSnapshot(EventScheduleStatus.LOADING, previous.schedule(), previous.fetchedAt(), null);
        }
        boolean accepted = runtime.submit(() -> runFetch(previous));
        if (accepted) {
            lastFetchAttemptAt = clock.get();
        } else {
            synchronized (lock) {
                activeFetch = false;
                snapshot = previous;
            }
        }
        return accepted;
    }

    private void runFetch(EventScheduleSnapshot previous) {
        EventFetchResult result;
        try {
            result = source.fetch();
        } catch (RuntimeException e) {
            result = new EventFetchResult.Unavailable("Oväntat fel: " + e.getMessage());
        }
        EventScheduleSnapshot next = apply(previous, result, clock.get());
        synchronized (lock) {
            snapshot = next;
            activeFetch = false;
        }
    }

    static EventScheduleSnapshot apply(EventScheduleSnapshot previous, EventFetchResult result, Instant now) {
        return switch (result) {
            case EventFetchResult.Success success -> new EventScheduleSnapshot(EventScheduleStatus.LOADED, success.schedule(), now, null);
            case EventFetchResult.Unavailable unavailable -> failed(previous, EventScheduleStatus.UNAVAILABLE, unavailable.reason());
            case EventFetchResult.Incompatible incompatible -> failed(previous, EventScheduleStatus.INCOMPATIBLE, incompatible.reason());
        };
    }

    private static EventScheduleSnapshot failed(EventScheduleSnapshot previous, EventScheduleStatus withoutCache, String reason) {
        return previous.hasSchedule()
                ? new EventScheduleSnapshot(EventScheduleStatus.STALE, previous.schedule(), previous.fetchedAt(), reason)
                : new EventScheduleSnapshot(withoutCache, null, null, reason);
    }
}
