package se.jimmyeliasson.gzcompanion.online;

/**
 * A simple 3-tier read of Minecraft's own already-known {@code PlayerInfo} latency value - never a
 * network probe of our own, and never the focus of the UI. Thresholds are centralized here as the
 * single source of truth for both rendering and tests.
 */
public enum PingQuality {
    GOOD,
    OK,
    POOR;

    /** Latency at or below this is GOOD. */
    public static final int GOOD_MAX_MS = 150;
    /** Latency at or below this (and above {@link #GOOD_MAX_MS}) is OK; anything higher is POOR. */
    public static final int OK_MAX_MS = 400;

    public static PingQuality fromLatencyMs(int latencyMs) {
        if (latencyMs <= GOOD_MAX_MS) return GOOD;
        if (latencyMs <= OK_MAX_MS) return OK;
        return POOR;
    }
}
