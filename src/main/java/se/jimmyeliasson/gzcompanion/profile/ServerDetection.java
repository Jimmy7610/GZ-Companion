package se.jimmyeliasson.gzcompanion.profile;

import java.util.Locale;

/**
 * Safe client-side hostname detection for GameZoneMC.
 */
public final class ServerDetection {
    private static final String TARGET_HOST = "play.gamezonemc.se";
    private static final String DOMAIN_SUFFIX = "gamezonemc.se";

    private ServerDetection() {}

    /**
     * Checks if the given address string corresponds to GameZoneMC.
     * Handles case-insensitivity, ports (e.g. play.gamezonemc.se:25565), and subdomains.
     */
    public static boolean isGameZone(String address) {
        if (address == null || address.isBlank()) {
            return false;
        }

        String cleaned = address.trim().toLowerCase(Locale.ROOT);

        // Strip port if present
        int colonIdx = cleaned.indexOf(':');
        if (colonIdx != -1) {
            cleaned = cleaned.substring(0, colonIdx);
        }

        return cleaned.equals(TARGET_HOST) || cleaned.endsWith("." + DOMAIN_SUFFIX) || cleaned.equals(DOMAIN_SUFFIX);
    }
}
