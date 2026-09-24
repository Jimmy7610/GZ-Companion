package se.jimmyeliasson.gzcompanion.chest.model;

/**
 * Deterministic, presentation-only classification of how long ago a storage snapshot was
 * legitimately captured. An old snapshot is NOT implied to be wrong - only older. Cached chest
 * contents are never labeled "live", whatever their age.
 */
public enum ChestFreshness {
    /** Opened within {@link #FRESH_WINDOW_MS}. */
    FRESH("FÄRSK"),
    /** Opened within the last 24 hours. */
    RECENT("SENASTE DYGNET"),
    /** Opened within the last {@link #OLDER_THRESHOLD_MS}. */
    EARLIER("TIDIGARE"),
    /** Opened several days ago or more. */
    OLDER("ÄLDRE SNAPSHOT"),
    /** No usable open timestamp is known. */
    UNKNOWN("OKÄND TID");

    public static final long FRESH_WINDOW_MS = 30L * 60_000L;
    public static final long RECENT_WINDOW_MS = 24L * 3_600_000L;
    public static final long OLDER_THRESHOLD_MS = 3L * 86_400_000L;

    private final String badge;

    ChestFreshness(String badge) {
        this.badge = badge;
    }

    public String badge() {
        return badge;
    }

    public static ChestFreshness classify(long openedAtMs, long nowMs) {
        if (openedAtMs <= 0) return UNKNOWN;
        long age = Math.max(0L, nowMs - openedAtMs);
        if (age < FRESH_WINDOW_MS) return FRESH;
        if (age < RECENT_WINDOW_MS) return RECENT;
        if (age < OLDER_THRESHOLD_MS) return EARLIER;
        return OLDER;
    }

    /**
     * Swedish relative-time text ("just nu", "18 min sedan", "2 h sedan", "3 dagar sedan").
     * A timestamp in the future (clock skew) is treated as "just nu" rather than negative.
     */
    public static String relativeTime(long thenMs, long nowMs) {
        if (thenMs <= 0) return "okänt";
        long diff = Math.max(0L, nowMs - thenMs);
        if (diff < 60_000L) return "just nu";
        if (diff < 3_600_000L) return (diff / 60_000L) + " min sedan";
        if (diff < 86_400_000L) return (diff / 3_600_000L) + " h sedan";
        long days = diff / 86_400_000L;
        return days + (days == 1 ? " dag sedan" : " dagar sedan");
    }
}
