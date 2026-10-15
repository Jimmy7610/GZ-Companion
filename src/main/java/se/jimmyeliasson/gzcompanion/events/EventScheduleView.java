package se.jimmyeliasson.gzcompanion.events;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * What the Events tab shows at one instant: the next event and the remaining upcoming events
 * grouped by day, with Swedish headings and relative times. Pure - {@code now} is always passed in,
 * so every rule is testable without the real clock.
 *
 * <p>Dates, headings ("Idag", "Imorgon") and clock times are in the SCHEDULE's zone
 * (Europe/Stockholm, as on the in-game hologram), whatever the player's own system zone is.
 * Countdowns are computed between real instants, so they are correct across DST changes.
 *
 * <p>Swedish weekday/month names are fixed tables rather than {@code Locale("sv")}: Minecraft's
 * bundled Java runtime is not guaranteed to ship locale data, and a missing locale would silently
 * fall back to English names.
 */
public record EventScheduleView(Optional<EventScheduleEntry> next, List<DayGroup> days, int upcomingCount) {
    private static final String[] WEEKDAYS = {"Måndag", "Tisdag", "Onsdag", "Torsdag", "Fredag", "Lördag", "Söndag"};
    private static final String[] MONTHS = {"januari", "februari", "mars", "april", "maj", "juni", "juli",
            "augusti", "september", "oktober", "november", "december"};
    private static final String[] MONTHS_SHORT = {"jan", "feb", "mar", "apr", "maj", "jun", "jul", "aug", "sep", "okt", "nov", "dec"};

    /** One day's upcoming events, already in start order. */
    public record DayGroup(LocalDate date, String heading, List<EventScheduleEntry> entries) {
        public DayGroup {
            entries = List.copyOf(entries);
        }
    }

    public static EventScheduleView of(EventSchedule schedule, Instant now) {
        if (schedule == null) return new EventScheduleView(Optional.empty(), List.of(), 0);
        LocalDate today = now.atZone(schedule.zone()).toLocalDate();
        List<DayGroup> days = new ArrayList<>();
        List<EventScheduleEntry> current = new ArrayList<>();
        LocalDate currentDate = null;
        int count = 0;
        EventScheduleEntry next = null;
        for (EventScheduleEntry entry : schedule.entries()) {
            if (!isUpcoming(entry, now)) continue;
            if (next == null) next = entry;
            count++;
            if (!entry.localDate().equals(currentDate)) {
                if (currentDate != null) days.add(new DayGroup(currentDate, dayHeading(currentDate, today), current));
                currentDate = entry.localDate();
                current = new ArrayList<>();
            }
            current.add(entry);
        }
        if (currentDate != null) days.add(new DayGroup(currentDate, dayHeading(currentDate, today), current));
        return new EventScheduleView(Optional.ofNullable(next), List.copyOf(days), count);
    }

    /** An event is upcoming until its start time; once it has started it is never shown as upcoming. */
    public static boolean isUpcoming(EventScheduleEntry entry, Instant now) {
        return !entry.instant().isBefore(now);
    }

    /** "Idag", "Imorgon", otherwise e.g. "Lördag 17 oktober". */
    public static String dayHeading(LocalDate date, LocalDate today) {
        if (date.equals(today)) return "Idag";
        if (date.equals(today.plusDays(1))) return "Imorgon";
        return longDate(date);
    }

    /** e.g. "Lördag 17 oktober". */
    public static String longDate(LocalDate date) {
        return WEEKDAYS[date.getDayOfWeek().getValue() - 1] + " " + date.getDayOfMonth() + " " + MONTHS[date.getMonthValue() - 1];
    }

    /** The schedule-local start time, "20:00". */
    public static String time(EventScheduleEntry entry) {
        return String.format(Locale.ROOT, "%02d:%02d", entry.localTime().getHour(), entry.localTime().getMinute());
    }

    /**
     * Relative start text: "Börjar nu", "Om 42 min", "Om 2 h 15 min" (today, or within 3 hours),
     * "Imorgon 20:00", a weekday within the coming week ("Lördag 20:00"), else "24 okt 20:00".
     * Minutes are rounded UP, so an event that has not started never reads "Om 0 min".
     */
    public static String relative(EventScheduleEntry entry, Instant now, ZoneId zone) {
        Duration until = Duration.between(now, entry.instant());
        if (until.isNegative() || until.isZero()) return "Börjar nu";
        long minutes = (until.toSeconds() + 59) / 60;
        if (minutes < 60) return "Om " + minutes + " min";

        LocalDate today = now.atZone(zone).toLocalDate();
        LocalDate date = entry.localDate();
        if (date.equals(today) || minutes < 180) {
            long h = minutes / 60;
            long m = minutes % 60;
            return "Om " + h + " h" + (m > 0 ? " " + m + " min" : "");
        }
        long daysAhead = ChronoUnit.DAYS.between(today, date);
        if (daysAhead == 1) return "Imorgon " + time(entry);
        if (daysAhead < 7) return WEEKDAYS[date.getDayOfWeek().getValue() - 1] + " " + time(entry);
        return date.getDayOfMonth() + " " + MONTHS_SHORT[date.getMonthValue() - 1] + " " + time(entry);
    }

    /**
     * A short note when the player's own zone currently differs from the schedule's, so clock times
     * are not mistaken for local time; null when they agree right now.
     */
    public static String zoneNote(ZoneId scheduleZone, ZoneId viewerZone, Instant now) {
        if (viewerZone == null || scheduleZone.getRules().getOffset(now).equals(viewerZone.getRules().getOffset(now))) return null;
        return "Europe/Stockholm".equals(scheduleZone.getId()) ? "Tider i svensk tid" : "Tider i " + scheduleZone.getId();
    }
}
