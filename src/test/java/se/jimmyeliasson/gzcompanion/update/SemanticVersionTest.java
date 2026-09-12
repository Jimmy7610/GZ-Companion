package se.jimmyeliasson.gzcompanion.update;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Pure SemVer parsing/comparison logic - no I/O, no live client, no live network. */
class SemanticVersionTest {

    private static SemanticVersion v(String raw) {
        return SemanticVersion.parse(raw).orElseThrow(() -> new AssertionError("expected to parse: " + raw));
    }

    @Test
    @DisplayName("alpha.1 < alpha.2")
    void alphaOneLessThanAlphaTwo() {
        assertTrue(v("0.1.0-alpha.1").compareTo(v("0.1.0-alpha.2")) < 0);
    }

    @Test
    @DisplayName("alpha.9 < alpha.10 - numeric identifiers compare numerically, never as strings")
    void alphaNineLessThanAlphaTen() {
        assertTrue(v("0.1.0-alpha.9").compareTo(v("0.1.0-alpha.10")) < 0);
        assertTrue(v("0.1.0-alpha.2").compareTo(v("0.1.0-alpha.10")) < 0);
    }

    @Test
    @DisplayName("alpha < beta")
    void alphaLessThanBeta() {
        assertTrue(v("0.1.0-alpha.10").compareTo(v("0.1.0-beta.1")) < 0);
    }

    @Test
    @DisplayName("prerelease < stable release of the same core version")
    void prereleaseLessThanStable() {
        assertTrue(v("0.1.0-beta.1").compareTo(v("0.1.0")) < 0);
    }

    @Test
    @DisplayName("The full required ordering chain holds end to end")
    void fullOrderingChainHolds() {
        SemanticVersion[] chain = {
                v("0.1.0-alpha.1"), v("0.1.0-alpha.2"), v("0.1.0-alpha.10"), v("0.1.0-beta.1"), v("0.1.0")
        };
        for (int i = 0; i < chain.length - 1; i++) {
            assertTrue(chain[i].compareTo(chain[i + 1]) < 0, chain[i] + " should be < " + chain[i + 1]);
        }
    }

    @Test
    @DisplayName("Equal versions compare equal - no update")
    void equalVersionsCompareEqual() {
        assertEquals(0, v("0.1.0-alpha.2").compareTo(v("0.1.0-alpha.2")));
    }

    @Test
    @DisplayName("An older release compares less than a newer current version - no update")
    void olderComparesLess() {
        assertTrue(v("0.1.0-alpha.1").compareTo(v("0.1.0-alpha.2")) < 0);
        assertTrue(v("0.1.0-alpha.2").compareTo(v("0.1.0-alpha.1")) > 0);
    }

    @Test
    @DisplayName("A leading 'v' (GitHub tag format) is tolerated")
    void leadingVIsTolerated() {
        assertEquals(v("0.1.0-alpha.2"), v("v0.1.0-alpha.2"));
    }

    @Test
    @DisplayName("Build metadata never affects precedence")
    void buildMetadataIgnoredForPrecedence() {
        assertEquals(0, v("0.1.0+build1").compareTo(v("0.1.0+build2")));
    }

    @Test
    @DisplayName("Malformed versions are rejected safely - never thrown, never guessed")
    void malformedVersionsRejectedSafely() {
        assertTrue(SemanticVersion.parse("not-a-version").isEmpty());
        assertTrue(SemanticVersion.parse("1.2").isEmpty());
        assertTrue(SemanticVersion.parse("1.2.3.4").isEmpty());
        assertTrue(SemanticVersion.parse("").isEmpty());
        assertTrue(SemanticVersion.parse(null).isEmpty());
        assertTrue(SemanticVersion.parse("01.2.3").isEmpty(), "leading zeros are not valid SemVer");
    }

    @Test
    @DisplayName("toDisplayString round-trips the parsed form")
    void toDisplayStringRoundTrips() {
        assertEquals("0.1.0-alpha.2", v("0.1.0-alpha.2").toDisplayString());
        assertEquals("0.1.0", v("0.1.0").toDisplayString());
    }

    @Test
    @DisplayName("isPrerelease reflects presence of prerelease identifiers")
    void isPrereleaseReflectsIdentifiers() {
        assertTrue(v("0.1.0-alpha.2").isPrerelease());
        assertFalse(v("0.1.0").isPrerelease());
    }

    @Test
    @DisplayName("Major/minor/patch differences dominate prerelease comparison")
    void coreVersionDominatesComparison() {
        assertTrue(v("0.1.0-alpha.99").compareTo(v("0.2.0-alpha.1")) < 0);
        assertTrue(v("0.1.9").compareTo(v("0.2.0")) < 0);
    }

    @Test
    @DisplayName("A longer prerelease identifier list outranks a shared-prefix shorter one")
    void longerPrereleaseListOutranksShorterSharedPrefix() {
        SemanticVersion shorter = new SemanticVersion(0, 1, 0, List.of("alpha"));
        SemanticVersion longer = new SemanticVersion(0, 1, 0, List.of("alpha", "1"));
        assertTrue(shorter.compareTo(longer) < 0);
    }
}
