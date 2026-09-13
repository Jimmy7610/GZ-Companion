package se.jimmyeliasson.gzcompanion.bounty;

import java.time.Duration;
import java.time.Instant;

/**
 * Small, pure, deterministic formatting for Bounty Board - mirrors {@code LeaderboardFormatter}'s
 * "never throws, locale-independent" convention. Reward/remaining-time formatting is done here
 * (unlike Leaderboards, GameZone's bounty API returns raw numbers/timestamps, not pre-formatted
 * text) - see docs/BOUNTY-BOARD.md.
 */
public final class BountyFormatter {
    private BountyFormatter() {}

    /** {@code "LIVE"} / {@code "CACHAD"} / {@code "LADDAR"} etc. - the short freshness badge/label. */
    public static String freshnessLabel(BountySnapshot snapshot) {
        if (snapshot == null) return "";
        return switch (snapshot.status()) {
            case LOADING -> snapshot.hasEntries() ? "UPPDATERAR" : "LADDAR";
            case LOADED -> "LIVE";
            case STALE -> "CACHAD";
            case UNAVAILABLE -> "OTILLGÄNGLIG";
            case ERROR -> "FEL";
            case INCOMPATIBLE -> "OTILLGÄNGLIG";
            case IDLE -> "";
        };
    }

    /** The longer footer line, e.g. {@code "Uppdaterad 12 sek sedan"} or {@code "Cachad data - 4 min sedan"}. */
    public static String freshnessDetail(BountySnapshot snapshot, Instant now) {
        if (snapshot == null) return "";
        if (snapshot.fetchedAt() == null) {
            return switch (snapshot.status()) {
                case LOADING -> "Hämtar...";
                case UNAVAILABLE, ERROR -> "Kunde inte hämta just nu.";
                case INCOMPATIBLE -> "GameZone har ändrat gränssnittet - stöds inte just nu.";
                default -> "";
            };
        }
        String ago = timeAgo(snapshot.fetchedAt(), now);
        return snapshot.status().isLive() ? ("Uppdaterad " + ago) : ("Cachad data - " + ago);
    }

    /** {@code "just nu"} / {@code "12 sek sedan"} / {@code "4 min sedan"} / {@code "3 h sedan"}. Never negative. */
    public static String timeAgo(Instant fetchedAt, Instant now) {
        if (fetchedAt == null || now == null) return "okänt";
        long seconds = Math.max(0, Duration.between(fetchedAt, now).getSeconds());
        if (seconds < 5) return "just nu";
        if (seconds < 60) return seconds + " sek sedan";
        if (seconds < 3600) return (seconds / 60) + " min sedan";
        if (seconds < 86400) return (seconds / 3600) + " h sedan";
        return (seconds / 86400) + " dagar sedan";
    }

    /** {@code "7 500"} / {@code "50 000"} - plain-space thousands grouping, matching GameZone's own
     * displayed style. Never negative (callers should never pass a negative reward - {@link
     * BountyEntry}'s own constructor already rejects one). */
    public static String formatReward(long coins) {
        String digits = Long.toString(Math.max(0, coins));
        StringBuilder grouped = new StringBuilder();
        int sinceGroup = 0;
        for (int i = digits.length() - 1; i >= 0; i--) {
            grouped.append(digits.charAt(i));
            sinceGroup++;
            if (sinceGroup == 3 && i != 0) {
                grouped.append(' ');
                sinceGroup = 0;
            }
        }
        return grouped.reverse().toString();
    }

    /**
     * Locally-calculated remaining time from a {@link BountyExpiry} - {@code "2 d 4 h"} /
     * {@code "5 h 18 min"} / {@code "42 min"} / {@code "< 1 min"} for a real timestamp,
     * {@code "Ingen tidsgräns"} only when the source EXPLICITLY said there is no limit
     * ({@link BountyExpiry.NoLimit}), and {@code "Tidsgräns okänd"} when the source simply didn't
     * say ({@link BountyExpiry.Unknown}) - these two are never conflated. Returns a distinct,
     * honest "may have expired" message once a known expiry has passed but no fresher server
     * response has arrived yet - never silently keeps presenting a crossed expiry as though it
     * were still definitely active.
     */
    public static String formatRemainingTime(BountyExpiry expiry, Instant now) {
        if (expiry == null || expiry instanceof BountyExpiry.Unknown) return "Tidsgräns okänd";
        if (expiry instanceof BountyExpiry.NoLimit) return "Ingen tidsgräns";
        Instant expiresAt = ((BountyExpiry.ExpiresAt) expiry).instant();
        Duration remaining = Duration.between(now, expiresAt);
        if (remaining.isNegative() || remaining.isZero()) {
            return "Kan ha löpt ut - uppdatera";
        }
        long totalHours = remaining.toHours();
        long days = remaining.toDays();
        if (days > 0) {
            return days + " d " + (totalHours % 24) + " h";
        }
        if (totalHours > 0) {
            return totalHours + " h " + (remaining.toMinutes() % 60) + " min";
        }
        long totalMinutes = remaining.toMinutes();
        if (totalMinutes >= 1) {
            return totalMinutes + " min";
        }
        return "< 1 min";
    }

    /** Whether {@code name} can safely be embedded as a single unquoted argument in the documented
     * {@code /bounty info <name>} command - never generates a broken/ambiguous command. */
    public static boolean isNameSafeForCommand(String name) {
        if (name == null || name.isBlank()) return false;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isWhitespace(c) || c == '"' || c == '\\' || c == '/') {
                return false;
            }
        }
        return true;
    }

    /** The exact documented command to copy, or {@code null} if {@code name} cannot be safely
     * represented - callers must not offer the copy action at all in that case, per "do not
     * generate a broken command." */
    public static String bountyInfoCommand(String name) {
        if (!isNameSafeForCommand(name)) return null;
        return "/bounty info " + name;
    }

    /** Cosmetic-only underscore-to-space conversion of the source's own {@code entityType} string
     * (e.g. {@code "WITHER_SKELETON"} -&gt; {@code "WITHER SKELETON"}) - never a guessed mapping to
     * a different name; returns the input unchanged if it contains no underscores, and {@code null}
     * straight through if the source didn't publish one at all. */
    public static String formatEntityType(String entityType) {
        if (entityType == null) return null;
        return entityType.replace('_', ' ');
    }
}
