package se.jimmyeliasson.gzcompanion.events;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** Shared fixtures for the Events tests. */
final class EventsTestData {
    private EventsTestData() {}

    static final ZoneId STOCKHOLM = ZoneId.of("Europe/Stockholm");

    /** The exact body GET /api/v1/events/schedule returned on 2026-10-15 at 23:28 Stockholm time. */
    static final String REAL_JSON = "{\"status\":\"success\",\"timeZone\":\"Europe/Stockholm\",\"data\":["
            + "{\"date\":\"2026-10-15\",\"eventName\":\"Team Deathmatch\",\"startTime\":\"20:00\"},"
            + "{\"date\":\"2026-10-15\",\"eventName\":\"Survival\",\"startTime\":\"21:00\"},"
            + "{\"date\":\"2026-10-16\",\"eventName\":\"Survival\",\"startTime\":\"20:00\"},"
            + "{\"date\":\"2026-10-16\",\"eventName\":\"HungerGames Duo\",\"startTime\":\"21:00\"},"
            + "{\"date\":\"2026-10-17\",\"eventName\":\"HungerGames\",\"startTime\":\"20:00\"},"
            + "{\"date\":\"2026-10-17\",\"eventName\":\"Survival\",\"startTime\":\"21:00\"},"
            + "{\"date\":\"2026-10-18\",\"eventName\":\"Survival\",\"startTime\":\"20:00\"},"
            + "{\"date\":\"2026-10-18\",\"eventName\":\"Team Deathmatch\",\"startTime\":\"21:00\"},"
            + "{\"date\":\"2026-10-19\",\"eventName\":\"HungerGames Duo\",\"startTime\":\"20:00\"},"
            + "{\"date\":\"2026-10-19\",\"eventName\":\"Survival\",\"startTime\":\"21:00\"},"
            + "{\"date\":\"2026-10-20\",\"eventName\":\"Survival\",\"startTime\":\"20:00\"},"
            + "{\"date\":\"2026-10-20\",\"eventName\":\"HungerGames\",\"startTime\":\"21:00\"},"
            + "{\"date\":\"2026-10-21\",\"eventName\":\"Archery Masters\",\"startTime\":\"20:00\"},"
            + "{\"date\":\"2026-10-21\",\"eventName\":\"Survival\",\"startTime\":\"21:00\"}]}";

    /** A Stockholm wall-clock time as an instant (DST resolved by the zone rules). */
    static Instant stockholm(String isoLocalDateTime) {
        return LocalDateTime.parse(isoLocalDateTime).atZone(STOCKHOLM).toInstant();
    }

    static String json(String items) {
        return "{\"status\":\"success\",\"timeZone\":\"Europe/Stockholm\",\"data\":[" + items + "]}";
    }

    static String item(String date, String name, String time) {
        return "{\"date\":\"" + date + "\",\"eventName\":\"" + name + "\",\"startTime\":\"" + time + "\"}";
    }

    static EventSchedule schedule(String json) throws EventScheduleIncompatibleException {
        return EventSchedule.from(EventScheduleJsonParser.parse(json));
    }
}
