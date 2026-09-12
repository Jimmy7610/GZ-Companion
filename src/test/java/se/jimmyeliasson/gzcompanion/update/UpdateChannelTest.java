package se.jimmyeliasson.gzcompanion.update;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UpdateChannelTest {

    private static SemanticVersion v(String raw) {
        return SemanticVersion.parse(raw).orElseThrow();
    }

    @Test
    @DisplayName("A version with no prerelease tag classifies as STABLE")
    void noPrereleaseIsStable() {
        assertEquals(UpdateChannel.STABLE, UpdateChannel.classify(v("0.1.0")));
    }

    @Test
    @DisplayName("An alpha prerelease tag classifies as ALPHA")
    void alphaPrereleaseClassifiesAlpha() {
        assertEquals(UpdateChannel.ALPHA, UpdateChannel.classify(v("0.1.0-alpha.2")));
    }

    @Test
    @DisplayName("A beta prerelease tag classifies as BETA")
    void betaPrereleaseClassifiesBeta() {
        assertEquals(UpdateChannel.BETA, UpdateChannel.classify(v("0.1.0-beta.1")));
    }

    @Test
    @DisplayName("ALPHA accepts alpha, beta, and stable releases")
    void alphaChannelAcceptsEverything() {
        assertTrue(UpdateChannel.ALPHA.accepts(UpdateChannel.ALPHA));
        assertTrue(UpdateChannel.ALPHA.accepts(UpdateChannel.BETA));
        assertTrue(UpdateChannel.ALPHA.accepts(UpdateChannel.STABLE));
    }

    @Test
    @DisplayName("BETA accepts beta and stable, never alpha")
    void betaChannelDoesNotAcceptAlpha() {
        assertFalse(UpdateChannel.BETA.accepts(UpdateChannel.ALPHA));
        assertTrue(UpdateChannel.BETA.accepts(UpdateChannel.BETA));
        assertTrue(UpdateChannel.BETA.accepts(UpdateChannel.STABLE));
    }

    @Test
    @DisplayName("STABLE accepts ONLY stable releases - never silently receives a prerelease")
    void stableChannelAcceptsOnlyStable() {
        assertFalse(UpdateChannel.STABLE.accepts(UpdateChannel.ALPHA));
        assertFalse(UpdateChannel.STABLE.accepts(UpdateChannel.BETA));
        assertTrue(UpdateChannel.STABLE.accepts(UpdateChannel.STABLE));
    }
}
