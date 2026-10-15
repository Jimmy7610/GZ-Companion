package se.jimmyeliasson.gzcompanion.events;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.Objects;

/**
 * One scheduled event, resolved to a real point in time. {@code start} is the API's
 * {@code date + startTime} interpreted in the schedule's own zone (Europe/Stockholm), so DST is
 * handled by the zone rules - never by a hard-coded offset.
 */
public record EventScheduleEntry(String eventName, ZonedDateTime start) {
    public EventScheduleEntry {
        Objects.requireNonNull(eventName, "eventName");
        Objects.requireNonNull(start, "start");
    }

    public Instant instant() {
        return start.toInstant();
    }

    /** The schedule-local date (what the in-game hologram shows). */
    public LocalDate localDate() {
        return start.toLocalDate();
    }

    /** The schedule-local start time (what the in-game hologram shows). */
    public LocalTime localTime() {
        return start.toLocalTime();
    }
}
