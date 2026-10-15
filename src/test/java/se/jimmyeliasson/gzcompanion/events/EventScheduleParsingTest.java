package se.jimmyeliasson.gzcompanion.events;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static se.jimmyeliasson.gzcompanion.events.EventsTestData.*;

/** JSON -> DTO -> parsed schedule: the real shape, envelope validation, entry validation, sorting and zone handling. */
class EventScheduleParsingTest {

    @Test
    @DisplayName("The real API response parses into 14 events in Europe/Stockholm, in order")
    void parsesRealShape() throws Exception {
        EventScheduleDto dto = EventScheduleJsonParser.parse(REAL_JSON);
        assertEquals("success", dto.status());
        assertEquals("Europe/Stockholm", dto.timeZone());
        assertEquals(14, dto.items().size());
        assertEquals(new EventScheduleDto.Item("2026-10-15", "Team Deathmatch", "20:00"), dto.items().get(0));

        EventSchedule schedule = EventSchedule.from(dto);
        assertEquals(STOCKHOLM, schedule.zone());
        assertEquals(14, schedule.entries().size());
        assertEquals(0, schedule.skippedCount());
        EventScheduleEntry first = schedule.entries().get(0);
        assertEquals("Team Deathmatch", first.eventName());
        assertEquals(LocalDate.of(2026, 10, 15), first.localDate());
        assertEquals(LocalTime.of(20, 0), first.localTime());
        assertEquals("Archery Masters", schedule.entries().get(12).eventName());
    }

    @Test
    @DisplayName("Multiple events on the same date are all kept")
    void multipleEventsSameDate() throws Exception {
        EventSchedule s = schedule(json(item("2026-10-16", "A", "18:00") + "," + item("2026-10-16", "B", "20:00") + "," + item("2026-10-16", "C", "21:30")));
        assertEquals(List.of("A", "B", "C"), s.entries().stream().map(EventScheduleEntry::eventName).toList());
        assertTrue(s.entries().stream().allMatch(e -> e.localDate().equals(LocalDate.of(2026, 10, 16))));
    }

    @Test
    @DisplayName("Out-of-order API data is sorted by real start time; equal times keep API order")
    void sortsAscending() throws Exception {
        EventSchedule s = schedule(json(item("2026-10-18", "Late", "21:00") + "," + item("2026-10-16", "Mid", "20:00") + ","
                + item("2026-10-16", "Early", "09:05") + "," + item("2026-10-16", "TieA", "20:00")));
        assertEquals(List.of("Early", "Mid", "TieA", "Late"), s.entries().stream().map(EventScheduleEntry::eventName).toList());
    }

    @Test
    @DisplayName("Times are Europe/Stockholm local time, converted with the zone's own DST rules (no fixed offset)")
    void stockholmTimeZone() throws Exception {
        EventSchedule s = schedule(json(item("2026-10-15", "Summer", "20:00") + "," + item("2026-12-01", "Winter", "20:00")));
        assertEquals(Instant.parse("2026-10-15T18:00:00Z"), s.entries().get(0).instant(), "CEST is UTC+2");
        assertEquals(Instant.parse("2026-12-01T19:00:00Z"), s.entries().get(1).instant(), "CET is UTC+1");
    }

    @Test
    @DisplayName("DST edges: a spring-forward gap time moves forward, an autumn overlap time takes summer time")
    void dstEdges() throws Exception {
        EventSchedule s = schedule(json(item("2026-03-29", "Gap", "02:30") + "," + item("2026-10-25", "Overlap", "02:30")));
        EventScheduleEntry gap = s.entries().get(0);
        assertEquals(LocalTime.of(3, 30), gap.localTime(), "02:30 does not exist on 2026-03-29 in Stockholm");
        assertEquals(Instant.parse("2026-03-29T01:30:00Z"), gap.instant());
        EventScheduleEntry overlap = s.entries().get(1);
        assertEquals(ZoneOffset.ofHours(2), overlap.start().getOffset(), "02:30 happens twice on 2026-10-25; the earlier one is used");
        assertEquals(Instant.parse("2026-10-25T00:30:00Z"), overlap.instant());
    }

    @Test
    @DisplayName("An empty data array is a valid, empty schedule")
    void emptySchedule() throws Exception {
        EventSchedule s = schedule(json(""));
        assertTrue(s.entries().isEmpty());
        assertEquals(0, s.skippedCount());
    }

    @Test
    @DisplayName("Entries with missing or invalid fields are skipped, never guessed; the rest still load")
    void invalidEntriesSkipped() throws Exception {
        String items = String.join(",",
                item("2026-10-16", "Good", "20:00"),
                "{\"date\":\"2026-10-16\",\"startTime\":\"20:00\"}",
                "{\"date\":\"2026-10-16\",\"eventName\":\"NoTime\"}",
                "{\"eventName\":\"NoDate\",\"startTime\":\"20:00\"}",
                item("2026-10-16", " ", "20:00"),
                item("2026-13-01", "BadDate", "20:00"),
                item("2026-10-16", "BadTime", "8pm"),
                item("2026-10-16", "BadTime2", "25:00"),
                "{\"date\":20261016,\"eventName\":\"NumberDate\",\"startTime\":\"20:00\"}",
                "\"not an object\"",
                "null");
        EventSchedule s = schedule(json(items));
        assertEquals(List.of("Good"), s.entries().stream().map(EventScheduleEntry::eventName).toList());
        assertEquals(10, s.skippedCount());
    }

    @Test
    @DisplayName("If entries exist but none is usable, the schedule is rejected rather than shown as empty")
    void allInvalidIsIncompatible() {
        assertThrows(EventScheduleIncompatibleException.class,
                () -> schedule(json("{\"name\":\"x\",\"when\":\"20:00\"},{\"name\":\"y\",\"when\":\"21:00\"}")));
    }

    @Test
    @DisplayName("status != \"success\" is rejected")
    void statusNotSuccess() {
        for (String status : List.of("\"error\"", "\"SUCCESS\"", "null", "1")) {
            String body = "{\"status\":" + status + ",\"timeZone\":\"Europe/Stockholm\",\"data\":[]}";
            assertThrows(EventScheduleIncompatibleException.class, () -> EventScheduleJsonParser.parse(body), status);
        }
        assertThrows(EventScheduleIncompatibleException.class, () -> EventScheduleJsonParser.parse("{\"timeZone\":\"Europe/Stockholm\",\"data\":[]}"));
    }

    @Test
    @DisplayName("Malformed JSON, a non-object root and an empty body are rejected without exceptions escaping")
    void malformedJson() {
        for (String body : new String[]{"not json {{{", "[1,2,3]", "\"text\"", "", "   ", null, "{\"status\":\"success\",", "<html>502</html>"}) {
            assertThrows(EventScheduleIncompatibleException.class, () -> EventScheduleJsonParser.parse(body), String.valueOf(body));
        }
    }

    @Test
    @DisplayName("Missing or non-array data is rejected")
    void missingData() {
        assertThrows(EventScheduleIncompatibleException.class, () -> EventScheduleJsonParser.parse("{\"status\":\"success\",\"timeZone\":\"Europe/Stockholm\"}"));
        assertThrows(EventScheduleIncompatibleException.class, () -> EventScheduleJsonParser.parse("{\"status\":\"success\",\"timeZone\":\"Europe/Stockholm\",\"data\":{}}"));
        assertThrows(EventScheduleIncompatibleException.class, () -> EventScheduleJsonParser.parse("{\"status\":\"success\",\"timeZone\":\"Europe/Stockholm\",\"data\":null}"));
    }

    @Test
    @DisplayName("A missing or unsupported time zone is rejected - never replaced by the system zone")
    void unsupportedTimeZone() {
        for (String tz : List.of("\"Mars/Olympus\"", "\"\"", "null", "\"UTC+99\"")) {
            String body = "{\"status\":\"success\",\"timeZone\":" + tz + ",\"data\":[]}";
            assertThrows(EventScheduleIncompatibleException.class, () -> EventScheduleJsonParser.parse(body), tz);
        }
        assertThrows(EventScheduleIncompatibleException.class, () -> EventScheduleJsonParser.parse("{\"status\":\"success\",\"data\":[]}"));
    }

    @Test
    @DisplayName("Unknown extra fields are ignored")
    void unknownFieldsIgnored() throws Exception {
        String body = "{\"status\":\"success\",\"timeZone\":\"Europe/Stockholm\",\"version\":2,\"data\":["
                + "{\"date\":\"2026-10-16\",\"eventName\":\"X\",\"startTime\":\"20:00\",\"arena\":\"Nord\"}]}";
        assertEquals(1, schedule(body).entries().size());
    }
}
