package se.jimmyeliasson.gzcompanion.events;

import java.util.List;

/**
 * The GameZoneEngine events-schedule API response exactly as delivered - raw strings, nothing
 * interpreted yet. Kept separate from the UI-facing {@link EventSchedule} so a change in the wire
 * format is absorbed in one place ({@link EventScheduleJsonParser} / {@link EventSchedule#from}).
 *
 * <pre>{"status":"success","timeZone":"Europe/Stockholm","data":[{"date":"2026-10-15","eventName":"Team Deathmatch","startTime":"20:00"}]}</pre>
 *
 * @param timeZone the zone {@code date + startTime} are local to (validated by the parser).
 * @param items    every {@code data[]} element, in API order; fields are null when absent or not a string.
 */
public record EventScheduleDto(String status, String timeZone, List<Item> items) {
    public EventScheduleDto {
        items = items == null ? List.of() : List.copyOf(items);
    }

    /** One raw {@code data[]} element. */
    public record Item(String date, String eventName, String startTime) {}
}
