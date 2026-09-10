package se.jimmyeliasson.gzcompanion.gamezone.toast;

/**
 * One local, ephemeral notification card. Carries only what's safe to keep in memory - never the
 * raw chat text, never persisted to disk.
 */
public record ToastEntry(String title, String body, long shownAtMs, long expiresAtMs) {
    public boolean isExpired(long nowMs) {
        return nowMs >= expiresAtMs;
    }
}
