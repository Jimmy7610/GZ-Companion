package se.jimmyeliasson.gzcompanion.ui.tabs;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import se.jimmyeliasson.gzcompanion.events.EventSchedule;
import se.jimmyeliasson.gzcompanion.events.EventScheduleJsonParser;
import se.jimmyeliasson.gzcompanion.events.EventScheduleSnapshot;
import se.jimmyeliasson.gzcompanion.events.EventScheduleStatus;
import se.jimmyeliasson.gzcompanion.events.EventScheduleView;

import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

/** The Events tab's text and state rules, without a Font. */
class EventsTabComponentTest {
    private static final ZoneId STOCKHOLM = ZoneId.of("Europe/Stockholm");
    private static final Instant FETCHED = Instant.parse("2026-10-15T18:00:00Z");

    private static EventSchedule schedule(String items) throws Exception {
        return EventSchedule.from(EventScheduleJsonParser.parse("{\"status\":\"success\",\"timeZone\":\"Europe/Stockholm\",\"data\":[" + items + "]}"));
    }

    private static String item(String date, String name, String time) {
        return "{\"date\":\"" + date + "\",\"eventName\":\"" + name + "\",\"startTime\":\"" + time + "\"}";
    }

    @Test
    @DisplayName("Header badge follows the shared Bounties/Leaderboards vocabulary")
    void badge() throws Exception {
        EventSchedule s = schedule(item("2026-10-16", "A", "20:00"));
        assertEquals("", EventsTabComponent.badgeLabel(EventScheduleSnapshot.idle()));
        assertEquals("LADDAR", EventsTabComponent.badgeLabel(new EventScheduleSnapshot(EventScheduleStatus.LOADING, null, null, null)));
        assertEquals("UPPDATERAR", EventsTabComponent.badgeLabel(new EventScheduleSnapshot(EventScheduleStatus.LOADING, s, FETCHED, null)));
        assertEquals("LIVE", EventsTabComponent.badgeLabel(new EventScheduleSnapshot(EventScheduleStatus.LOADED, s, FETCHED, null)));
        assertEquals("CACHAD", EventsTabComponent.badgeLabel(new EventScheduleSnapshot(EventScheduleStatus.STALE, s, FETCHED, "x")));
        assertEquals("OTILLGÄNGLIG", EventsTabComponent.badgeLabel(new EventScheduleSnapshot(EventScheduleStatus.UNAVAILABLE, null, null, "x")));
    }

    @Test
    @DisplayName("Footer: fresh data age, 'Visar senast hämtade schema' after a failed refresh, plus the zone note when needed")
    void footer() throws Exception {
        EventSchedule s = schedule(item("2026-10-16", "A", "20:00"));
        assertEquals("Uppdaterad nyss", EventsTabComponent.footerText(new EventScheduleSnapshot(EventScheduleStatus.LOADED, s, FETCHED, null), FETCHED.plusSeconds(20), STOCKHOLM));
        assertEquals("Uppdaterad för 4 min sedan", EventsTabComponent.footerText(new EventScheduleSnapshot(EventScheduleStatus.LOADED, s, FETCHED, null), FETCHED.plusSeconds(250), STOCKHOLM));
        assertEquals("Visar senast hämtade schema", EventsTabComponent.footerText(new EventScheduleSnapshot(EventScheduleStatus.STALE, s, FETCHED, "timeout"), FETCHED, STOCKHOLM));
        assertEquals("Visar senast hämtade schema • Tider i svensk tid",
                EventsTabComponent.footerText(new EventScheduleSnapshot(EventScheduleStatus.STALE, s, FETCHED, "timeout"), FETCHED, ZoneId.of("UTC")));
        assertEquals("Hämtar schema...", EventsTabComponent.footerText(new EventScheduleSnapshot(EventScheduleStatus.LOADING, null, null, null), FETCHED, STOCKHOLM));
        assertEquals("Kunde inte hämta eventschemat", EventsTabComponent.footerText(new EventScheduleSnapshot(EventScheduleStatus.UNAVAILABLE, null, null, "x"), FETCHED, STOCKHOLM));
    }

    @Test
    @DisplayName("No cache + failure: a clear Swedish error with 'Försök igen'; loading shows no retry")
    void errorStateWithRetry() {
        EventScheduleSnapshot unavailable = new EventScheduleSnapshot(EventScheduleStatus.UNAVAILABLE, null, null, "HTTP 500");
        assertEquals("KUNDE INTE HÄMTA EVENTSCHEMAT", EventsTabComponent.stateHeadline(unavailable));
        assertFalse(EventsTabComponent.stateBody(unavailable).isBlank());
        assertTrue(EventsTabComponent.offersRetry(unavailable));

        EventScheduleSnapshot incompatible = new EventScheduleSnapshot(EventScheduleStatus.INCOMPATIBLE, null, null, "status error");
        assertEquals("EVENTSCHEMAT KUNDE INTE LÄSAS", EventsTabComponent.stateHeadline(incompatible));
        assertTrue(EventsTabComponent.offersRetry(incompatible));

        EventScheduleSnapshot loading = new EventScheduleSnapshot(EventScheduleStatus.LOADING, null, null, null);
        assertEquals("HÄMTAR EVENTSCHEMA...", EventsTabComponent.stateHeadline(loading));
        assertFalse(EventsTabComponent.offersRetry(loading));
    }

    @Test
    @DisplayName("A schedule with nothing upcoming says so - and a cached one does not claim it is current")
    void emptyStates() throws Exception {
        EventSchedule empty = schedule("");
        EventScheduleSnapshot loaded = new EventScheduleSnapshot(EventScheduleStatus.LOADED, empty, FETCHED, null);
        assertEquals("INGA KOMMANDE EVENTS", EventsTabComponent.stateHeadline(loaded));
        assertEquals("Det finns inga schemalagda events de närmaste dagarna.", EventsTabComponent.stateBody(loaded));
        assertFalse(EventsTabComponent.offersRetry(loaded));
        EventScheduleSnapshot stale = new EventScheduleSnapshot(EventScheduleStatus.STALE, empty, FETCHED, "x");
        assertTrue(EventsTabComponent.stateBody(stale).startsWith("Det senast hämtade schemat"));
    }

    @Test
    @DisplayName("List height counts day headings, rows and gaps; scrolling is clamped to the content")
    void listHeightAndScroll() throws Exception {
        EventSchedule s = schedule(item("2026-10-16", "A", "20:00") + "," + item("2026-10-16", "B", "21:00") + "," + item("2026-10-17", "C", "20:00"));
        EventScheduleView view = EventScheduleView.of(s, Instant.parse("2026-10-16T10:00:00Z"));
        int expected = 3 + (EventsTabComponent.DAY_HEADING_H + 2 * EventsTabComponent.EVENT_ROW_H)
                + EventsTabComponent.GROUP_GAP + (EventsTabComponent.DAY_HEADING_H + EventsTabComponent.EVENT_ROW_H) + 3;
        assertEquals(expected, EventsTabComponent.listContentHeight(view));
        assertEquals(0, EventsTabComponent.clampScroll(-50, 200, 80));
        assertEquals(120, EventsTabComponent.clampScroll(500, 200, 80));
        assertEquals(0, EventsTabComponent.clampScroll(30, 50, 80), "Content that fits never scrolls");
    }
}
