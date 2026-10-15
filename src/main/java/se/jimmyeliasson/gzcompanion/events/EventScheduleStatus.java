package se.jimmyeliasson.gzcompanion.events;

/** Freshness/health of the cached {@link EventScheduleSnapshot}. */
public enum EventScheduleStatus {
    /** Never fetched this session. */
    IDLE,
    /** A fetch is in flight; any previously fetched schedule stays visible underneath. */
    LOADING,
    /** The latest fetch succeeded. */
    LOADED,
    /** A previously fetched schedule exists, but the latest refresh failed - shown as "senast hämtade". */
    STALE,
    /** Nothing fetched yet and the latest fetch failed at the network level. */
    UNAVAILABLE,
    /** Nothing fetched yet and the endpoint answered with something this version cannot read. */
    INCOMPATIBLE
}
