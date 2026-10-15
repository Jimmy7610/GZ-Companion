package se.jimmyeliasson.gzcompanion.events;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The parsed, UI-facing schedule: every usable event as a zoned start time, sorted ascending.
 *
 * <p>Built from the raw {@link EventScheduleDto} by {@link #from}: an entry missing its name, date
 * or time - or with an unparseable date/time - is skipped (counted in {@code skippedCount}), never
 * guessed. If the API sent entries but NONE are usable, the response is treated as incompatible
 * rather than shown as an empty week.
 *
 * <p>Times are local to {@code zone}. {@link ZonedDateTime#of} resolves DST edges by the zone's
 * own rules: a time inside the spring-forward gap moves forward by the gap length, and an
 * ambiguous autumn time takes the earlier (summer-time) offset.
 */
public record EventSchedule(ZoneId zone, List<EventScheduleEntry> entries, int skippedCount) {
    /** Sort by real start instant; the stable sort keeps API order for events at the same time. */
    private static final Comparator<EventScheduleEntry> BY_START = Comparator.comparing(EventScheduleEntry::instant);

    public EventSchedule {
        entries = entries == null ? List.of() : entries.stream().sorted(BY_START).toList();
    }

    public static EventSchedule from(EventScheduleDto dto) throws EventScheduleIncompatibleException {
        ZoneId zone;
        try {
            zone = ZoneId.of(dto.timeZone());
        } catch (DateTimeException | NullPointerException e) {
            throw new EventScheduleIncompatibleException("Okänd tidszon i eventschemat: " + dto.timeZone());
        }
        List<EventScheduleEntry> entries = new ArrayList<>();
        int skipped = 0;
        for (EventScheduleDto.Item item : dto.items()) {
            EventScheduleEntry entry = toEntry(item, zone);
            if (entry == null) {
                skipped++;
            } else {
                entries.add(entry);
            }
        }
        if (entries.isEmpty() && skipped > 0) {
            throw new EventScheduleIncompatibleException("Eventschemat innehöll inga giltiga events.");
        }
        return new EventSchedule(zone, entries, skipped);
    }

    private static EventScheduleEntry toEntry(EventScheduleDto.Item item, ZoneId zone) {
        if (item.eventName() == null || item.eventName().isBlank() || item.date() == null || item.startTime() == null) return null;
        try {
            LocalDate date = LocalDate.parse(item.date().trim());
            LocalTime time = LocalTime.parse(item.startTime().trim());
            return new EventScheduleEntry(item.eventName().trim(), ZonedDateTime.of(date, time, zone));
        } catch (DateTimeException e) {
            return null;
        }
    }
}
