package se.jimmyeliasson.gzcompanion.events;

/**
 * Fetches the GameZone events schedule. Never throws - every failure is an {@link EventFetchResult}.
 */
public interface EventScheduleSource {
    EventFetchResult fetch();
}
