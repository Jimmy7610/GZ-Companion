package se.jimmyeliasson.gzcompanion.events;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * JSON-in, {@link EventScheduleDto}-out for {@code GET /api/v1/events/schedule}. Pure (no I/O).
 * The envelope must be exactly right - {@code status == "success"}, a real IANA {@code timeZone},
 * a {@code data} array - otherwise the whole response is rejected as incompatible rather than
 * guessed at. Individual {@code data[]} fields are kept raw; {@link EventSchedule#from} decides
 * which entries are usable.
 */
public final class EventScheduleJsonParser {
    private EventScheduleJsonParser() {}

    public static EventScheduleDto parse(String json) throws EventScheduleIncompatibleException {
        JsonElement root;
        try {
            root = JsonParser.parseString(json == null ? "" : json);
        } catch (RuntimeException e) {
            throw new EventScheduleIncompatibleException("Ogiltig JSON från eventschemat.");
        }
        if (root == null || !root.isJsonObject()) {
            throw new EventScheduleIncompatibleException("Eventschemat svarade inte med ett JSON-objekt.");
        }
        JsonObject obj = root.getAsJsonObject();

        String status = string(obj, "status");
        if (!"success".equals(status)) {
            throw new EventScheduleIncompatibleException("Eventschemat svarade med status \"" + status + "\".");
        }

        String timeZone = string(obj, "timeZone");
        if (timeZone == null || timeZone.isBlank()) {
            throw new EventScheduleIncompatibleException("Eventschemat saknar tidszon.");
        }
        try {
            ZoneId.of(timeZone.trim());
        } catch (DateTimeException e) {
            throw new EventScheduleIncompatibleException("Okänd tidszon i eventschemat: " + timeZone);
        }

        if (!obj.has("data") || !obj.get("data").isJsonArray()) {
            throw new EventScheduleIncompatibleException("Eventschemat saknar data.");
        }
        JsonArray data = obj.getAsJsonArray("data");
        List<EventScheduleDto.Item> items = new ArrayList<>(data.size());
        for (JsonElement el : data) {
            if (el == null || !el.isJsonObject()) {
                items.add(new EventScheduleDto.Item(null, null, null));
                continue;
            }
            JsonObject item = el.getAsJsonObject();
            items.add(new EventScheduleDto.Item(string(item, "date"), string(item, "eventName"), string(item, "startTime")));
        }
        return new EventScheduleDto(status, timeZone.trim(), items);
    }

    /** The member as a string, or null when absent, null, or not a JSON string. */
    private static String string(JsonObject obj, String key) {
        JsonElement el = obj.get(key);
        if (el == null || !el.isJsonPrimitive() || !el.getAsJsonPrimitive().isString()) return null;
        return el.getAsString();
    }
}
