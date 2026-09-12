package se.jimmyeliasson.gzcompanion.leaderboard;

import java.time.Duration;
import java.time.Instant;

/**
 * Small, pure, deterministic freshness-label formatting for {@link LeaderboardSnapshot} - mirrors
 * {@code GameZoneStatusFormatter}'s existing "never throws, locale-independent" convention. A
 * snapshot may NEVER be labeled LIVE unless {@link LeaderboardStatus#isLive()} is true - this class
 * exists specifically so that rule lives in exactly one place rather than being re-derived ad hoc in
 * UI code.
 */
public final class LeaderboardFormatter {
    private LeaderboardFormatter() {}

    /** {@code "LIVE"} / {@code "Uppdaterad nyss"} / {@code "SENAST HÄMTADE"} / {@code "Laddar..."} etc. - the short freshness badge/label. */
    public static String freshnessLabel(LeaderboardSnapshot snapshot) {
        if (snapshot == null) return "";
        return switch (snapshot.status()) {
            case LOADING -> snapshot.hasEntries() ? "UPPDATERAR" : "LADDAR";
            case LOADED -> "LIVE";
            case STALE -> "SENAST HÄMTADE";
            case UNAVAILABLE -> "OTILLGÄNGLIG";
            case ERROR -> "FEL";
            case INCOMPATIBLE -> "OTILLGÄNGLIG";
            case IDLE -> "";
        };
    }

    /** The longer footer line, e.g. {@code "Uppdaterad 12 sek sedan"} or {@code "Senast hämtad 4 min sedan"}. */
    public static String freshnessDetail(LeaderboardSnapshot snapshot, Instant now) {
        if (snapshot == null) return "";
        if (snapshot.fetchedAt() == null) {
            return switch (snapshot.status()) {
                case LOADING -> "Hämtar...";
                case UNAVAILABLE -> "Kunde inte hämta just nu.";
                case ERROR -> "Kunde inte hämta just nu.";
                case INCOMPATIBLE -> "GameZone har ändrat sidan - stöds inte just nu.";
                default -> "";
            };
        }
        String ago = timeAgo(snapshot.fetchedAt(), now);
        return snapshot.status().isLive() ? ("Uppdaterad " + ago) : ("Senast hämtad " + ago);
    }

    /** {@code "just nu"} / {@code "12 sek sedan"} / {@code "4 min sedan"} / {@code "3 h sedan"}. Never negative even if clocks are slightly off. */
    public static String timeAgo(Instant fetchedAt, Instant now) {
        if (fetchedAt == null || now == null) return "okänt";
        long seconds = Math.max(0, Duration.between(fetchedAt, now).getSeconds());
        if (seconds < 5) return "just nu";
        if (seconds < 60) return seconds + " sek sedan";
        if (seconds < 3600) return (seconds / 60) + " min sedan";
        if (seconds < 86400) return (seconds / 3600) + " h sedan";
        return (seconds / 86400) + " dagar sedan";
    }
}
