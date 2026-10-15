package se.jimmyeliasson.gzcompanion.events;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static se.jimmyeliasson.gzcompanion.events.EventsTestData.*;

/** Upcoming filtering, next-event selection, Swedish day headings and countdown text - all at fixed instants. */
class EventScheduleViewTest {

    private static EventScheduleEntry at(String isoLocal, String name) {
        return new EventScheduleEntry(name, java.time.LocalDateTime.parse(isoLocal).atZone(STOCKHOLM));
    }

    @Test
    @DisplayName("Real schedule at 23:28: today's 20:00/21:00 events are past, the next event is tomorrow 20:00 Survival")
    void pastEventsFilteredAndNextSelected() throws Exception {
        EventScheduleView view = EventScheduleView.of(schedule(REAL_JSON), stockholm("2026-10-15T23:28"));
        assertEquals(12, view.upcomingCount(), "Both 2026-10-15 events have already started");
        EventScheduleEntry next = view.next().orElseThrow();
        assertEquals("Survival", next.eventName());
        assertEquals(stockholm("2026-10-16T20:00"), next.instant());
        assertEquals(6, view.days().size());
        assertEquals("Imorgon", view.days().get(0).heading());
        assertEquals(List.of("Survival", "HungerGames Duo"), view.days().get(0).entries().stream().map(EventScheduleEntry::eventName).toList());
        assertEquals("Lördag 17 oktober", view.days().get(1).heading());
        assertEquals("Onsdag 21 oktober", view.days().get(5).heading());
    }

    @Test
    @DisplayName("Earlier the same evening, 'Idag' groups today's remaining events and the next is the first not yet started")
    void idagGrouping() throws Exception {
        EventScheduleView view = EventScheduleView.of(schedule(REAL_JSON), stockholm("2026-10-15T20:30"));
        assertEquals("Survival", view.next().orElseThrow().eventName());
        assertEquals("Idag", view.days().get(0).heading());
        assertEquals(1, view.days().get(0).entries().size(), "20:00 already started; only 21:00 remains today");
        assertEquals("Imorgon", view.days().get(1).heading());
    }

    @Test
    @DisplayName("An event is upcoming exactly at its start instant and past one second later")
    void upcomingBoundary() {
        EventScheduleEntry e = at("2026-10-16T20:00", "X");
        assertTrue(EventScheduleView.isUpcoming(e, stockholm("2026-10-16T20:00")));
        assertFalse(EventScheduleView.isUpcoming(e, stockholm("2026-10-16T20:00").plusSeconds(1)));
    }

    @Test
    @DisplayName("Everything past (or no schedule at all) means no next event and no groups")
    void nothingUpcoming() throws Exception {
        EventScheduleView view = EventScheduleView.of(schedule(REAL_JSON), stockholm("2026-10-22T00:00"));
        assertTrue(view.next().isEmpty());
        assertTrue(view.days().isEmpty());
        assertEquals(0, view.upcomingCount());
        assertTrue(EventScheduleView.of(null, Instant.EPOCH).next().isEmpty());
        assertTrue(EventScheduleView.of(schedule(json("")), Instant.EPOCH).days().isEmpty());
    }

    @Test
    @DisplayName("Day headings: Idag, Imorgon, then Swedish weekday + date for every weekday and month")
    void dayHeadings() {
        LocalDate today = LocalDate.of(2026, 10, 15);
        assertEquals("Idag", EventScheduleView.dayHeading(today, today));
        assertEquals("Imorgon", EventScheduleView.dayHeading(today.plusDays(1), today));
        assertEquals("Lördag 17 oktober", EventScheduleView.dayHeading(today.plusDays(2), today));
        List<String> week = List.of("Måndag 19 oktober", "Tisdag 20 oktober", "Onsdag 21 oktober", "Torsdag 22 oktober",
                "Fredag 23 oktober", "Lördag 24 oktober", "Söndag 25 oktober");
        for (int i = 0; i < 7; i++) assertEquals(week.get(i), EventScheduleView.longDate(LocalDate.of(2026, 10, 19 + i)));
        assertEquals("Fredag 1 januari", EventScheduleView.longDate(LocalDate.of(2027, 1, 1)));
        assertEquals("Lördag 1 maj", EventScheduleView.longDate(LocalDate.of(2027, 5, 1)));
        assertEquals("Torsdag 31 december", EventScheduleView.longDate(LocalDate.of(2026, 12, 31)));
    }

    @Test
    @DisplayName("Countdown: Om 42 min, Om 2 h 15 min, Om 2 h, minutes rounded up, Börjar nu at the start")
    void countdownFormatting() {
        EventScheduleEntry e = at("2026-10-16T20:00", "X");
        assertEquals("Om 42 min", EventScheduleView.relative(e, stockholm("2026-10-16T19:18"), STOCKHOLM));
        assertEquals("Om 42 min", EventScheduleView.relative(e, stockholm("2026-10-16T19:18").plusSeconds(30), STOCKHOLM), "41.5 min rounds up");
        assertEquals("Om 1 min", EventScheduleView.relative(e, stockholm("2026-10-16T20:00").minusSeconds(5), STOCKHOLM), "never 'Om 0 min'");
        assertEquals("Om 2 h 15 min", EventScheduleView.relative(e, stockholm("2026-10-16T17:45"), STOCKHOLM));
        assertEquals("Om 2 h", EventScheduleView.relative(e, stockholm("2026-10-16T18:00"), STOCKHOLM));
        assertEquals("Om 1 h", EventScheduleView.relative(e, stockholm("2026-10-16T19:00"), STOCKHOLM));
        assertEquals("Börjar nu", EventScheduleView.relative(e, stockholm("2026-10-16T20:00"), STOCKHOLM));
    }

    @Test
    @DisplayName("Further ahead: Imorgon 20:00, weekday within the week, short date beyond; a late-night event soon after midnight still counts down")
    void laterRelativeText() {
        Instant now = stockholm("2026-10-15T10:00");
        assertEquals("Imorgon 20:00", EventScheduleView.relative(at("2026-10-16T20:00", "X"), now, STOCKHOLM));
        assertEquals("Lördag 21:00", EventScheduleView.relative(at("2026-10-17T21:00", "X"), now, STOCKHOLM));
        assertEquals("Onsdag 20:00", EventScheduleView.relative(at("2026-10-21T20:00", "X"), now, STOCKHOLM));
        assertEquals("22 okt 20:00", EventScheduleView.relative(at("2026-10-22T20:00", "X"), now, STOCKHOLM));
        assertEquals("Om 1 h 30 min", EventScheduleView.relative(at("2026-10-16T00:30", "X"), stockholm("2026-10-15T23:00"), STOCKHOLM),
                "Within 3 hours a countdown is clearer than 'Imorgon 00:30'");
    }

    @Test
    @DisplayName("DST autumn: from 00:30 summer time to 03:30 winter time on 2026-10-25 is really 4 hours, not 3")
    void dstAutumnCountdown() {
        EventScheduleEntry e = at("2026-10-25T03:30", "X");
        assertEquals("Om 4 h", EventScheduleView.relative(e, stockholm("2026-10-25T00:30"), STOCKHOLM));
    }

    @Test
    @DisplayName("DST spring: from 01:00 winter time to 04:00 summer time on 2026-03-29 is really 2 hours, not 3")
    void dstSpringCountdown() {
        EventScheduleEntry e = at("2026-03-29T04:00", "X");
        assertEquals("Om 2 h", EventScheduleView.relative(e, stockholm("2026-03-29T01:00"), STOCKHOLM));
    }

    @Test
    @DisplayName("'Idag'/'Imorgon' follow Stockholm's date, not UTC or the player's zone")
    void headingsUseScheduleZone() throws Exception {
        // 22:30 UTC on the 15th is already 00:30 on the 16th in Stockholm (CEST).
        Instant now = Instant.parse("2026-10-15T22:30:00Z");
        EventScheduleView view = EventScheduleView.of(schedule(REAL_JSON), now);
        assertEquals(LocalDate.of(2026, 10, 16), view.days().get(0).date());
        assertEquals("Idag", view.days().get(0).heading());
        assertEquals("Imorgon", view.days().get(1).heading());
    }

    @Test
    @DisplayName("Time column is the schedule-local start time, zero-padded")
    void timeText() {
        assertEquals("20:00", EventScheduleView.time(at("2026-10-16T20:00", "X")));
        assertEquals("09:05", EventScheduleView.time(at("2026-10-16T09:05", "X")));
    }

    @Test
    @DisplayName("Zone note only when the player's current offset differs from Stockholm's")
    void zoneNote() {
        Instant summer = stockholm("2026-07-01T12:00");
        assertNull(EventScheduleView.zoneNote(STOCKHOLM, STOCKHOLM, summer));
        assertNull(EventScheduleView.zoneNote(STOCKHOLM, ZoneId.of("Europe/Berlin"), summer), "Same offset - no note needed");
        assertEquals("Tider i svensk tid", EventScheduleView.zoneNote(STOCKHOLM, ZoneId.of("Europe/London"), summer));
        assertEquals("Tider i svensk tid", EventScheduleView.zoneNote(STOCKHOLM, ZoneId.of("UTC"), summer));
        assertEquals("Tider i Europe/Helsinki", EventScheduleView.zoneNote(ZoneId.of("Europe/Helsinki"), ZoneId.of("UTC"), summer));
        assertNull(EventScheduleView.zoneNote(STOCKHOLM, null, summer));
    }
}
