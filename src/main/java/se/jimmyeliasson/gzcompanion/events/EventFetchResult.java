package se.jimmyeliasson.gzcompanion.events;

/**
 * The outcome of one {@link EventScheduleSource#fetch()} - a sealed result rather than an
 * exception, like {@code BountyFetchResult}, so {@link EventScheduleManager} handles every case.
 */
public sealed interface EventFetchResult {
    /** A usable schedule. Its entries may legitimately be empty (nothing scheduled). */
    record Success(EventSchedule schedule) implements EventFetchResult {}

    /** Network-level failure: offline, timeout, non-2xx, redirect, refused host, oversized response. */
    record Unavailable(String reason) implements EventFetchResult {}

    /** Reachable, but the response is not a schedule this version understands. */
    record Incompatible(String reason) implements EventFetchResult {}
}
