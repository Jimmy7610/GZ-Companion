package se.jimmyeliasson.gzcompanion.events;

/**
 * The events endpoint answered, but not with a schedule this Companion version understands
 * ({@code status != "success"}, missing/invalid {@code timeZone} or {@code data}, malformed JSON).
 */
public class EventScheduleIncompatibleException extends Exception {
    public EventScheduleIncompatibleException(String message) {
        super(message);
    }
}
