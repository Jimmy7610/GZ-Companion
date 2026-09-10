package se.jimmyeliasson.gzcompanion.gamezone.events;

import java.util.Map;
import java.util.Objects;

/**
 * One legitimately-observed GameZone event, produced by {@code GameZoneParserEngine} matching a
 * verified {@code GameZoneParserDefinition} against a message the client already received.
 *
 * <p>Deliberately does NOT carry the raw message text - only the parser's declared capture-group
 * values, which are the only pieces of the message a verified parser explicitly said it needed.
 * This is what makes it safe to keep {@code capturedValues} in memory only (never persisted to
 * disk) and to surface in diagnostics without ever leaking private chat content.
 */
public record GameZoneObservedEvent(
    GameZoneEventType eventType,
    String parserId,
    Map<String, String> capturedValues,
    long observedAtMs
) {
    public GameZoneObservedEvent {
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(parserId, "parserId");
        capturedValues = capturedValues != null ? Map.copyOf(capturedValues) : Map.of();
    }

    /** A stable key for deduplicating repeated identical events within a short window. */
    public String dedupeKey() {
        return parserId + "|" + capturedValues;
    }
}
