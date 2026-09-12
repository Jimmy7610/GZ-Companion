package se.jimmyeliasson.gzcompanion.update;

import java.util.Locale;

/**
 * GZ Companion's current channel is ALPHA (matches the current {@code 0.1.0-alpha.N} product
 * version). Explicit rather than inferred purely from "has a prerelease tag" so a future stable
 * release's behavior (accept only STABLE, never silently pick up a prerelease) is deliberate and
 * testable rather than an accident of string matching.
 */
public enum UpdateChannel {
    ALPHA,
    BETA,
    STABLE;

    /** Classifies a version's channel from its first prerelease identifier, or STABLE if it has none. */
    public static UpdateChannel classify(SemanticVersion version) {
        if (!version.isPrerelease()) return STABLE;
        String first = version.prereleaseIdentifiers().get(0).toLowerCase(Locale.ROOT);
        if (first.startsWith("alpha")) return ALPHA;
        if (first.startsWith("beta")) return BETA;
        return ALPHA; // an unrecognized prerelease tag is treated as conservatively as ALPHA, never as STABLE
    }

    /**
     * True if a release on {@code releaseChannel} is acceptable to a client currently on
     * {@code currentChannel}. ALPHA accepts ALPHA/BETA/STABLE (an alpha tester rides the front of
     * the train); BETA accepts BETA/STABLE; STABLE accepts ONLY STABLE - a stable client must never
     * silently receive a prerelease build.
     */
    public boolean accepts(UpdateChannel releaseChannel) {
        return switch (this) {
            case ALPHA -> true;
            case BETA -> releaseChannel != ALPHA;
            case STABLE -> releaseChannel == STABLE;
        };
    }
}
