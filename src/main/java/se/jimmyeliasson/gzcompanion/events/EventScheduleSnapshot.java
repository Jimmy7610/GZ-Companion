package se.jimmyeliasson.gzcompanion.events;

import java.time.Instant;

/**
 * The single cached events state held by {@link EventScheduleManager}. A failed refresh never
 * clears a previously fetched schedule: {@code schedule} stays set (status {@link
 * EventScheduleStatus#STALE}) so the tab can keep showing it.
 *
 * @param schedule     the most recently fetched schedule, or null if none has ever been fetched.
 * @param fetchedAt    when {@code schedule} was fetched; null when it is null.
 * @param errorMessage why the latest fetch failed; null after a success or before any fetch.
 */
public record EventScheduleSnapshot(EventScheduleStatus status, EventSchedule schedule, Instant fetchedAt, String errorMessage) {
    public static EventScheduleSnapshot idle() {
        return new EventScheduleSnapshot(EventScheduleStatus.IDLE, null, null, null);
    }

    public boolean hasSchedule() {
        return schedule != null;
    }
}
