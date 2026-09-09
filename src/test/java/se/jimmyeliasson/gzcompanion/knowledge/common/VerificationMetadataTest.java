package se.jimmyeliasson.gzcompanion.knowledge.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers the hard trust rule enforced by {@link VerificationMetadata}'s canonical constructor:
 * VERIFIED must never survive without a real source trail, and a missing/unparseable status
 * must never default to VERIFIED. This is the single most safety-critical invariant in the M4
 * knowledge base - every downstream loader (commands, crafting, items) depends on it.
 */
class VerificationMetadataTest {

    @Test
    @DisplayName("VERIFIED without a source is downgraded to UNVERIFIED")
    void testVerifiedWithoutSourceIsDowngraded() {
        VerificationMetadata meta = new VerificationMetadata(VerificationStatus.VERIFIED, null, null, "2026-09-10");
        assertEquals(VerificationStatus.UNVERIFIED, meta.status());
    }

    @Test
    @DisplayName("VERIFIED without a lastVerified date is downgraded to UNVERIFIED")
    void testVerifiedWithoutLastVerifiedIsDowngraded() {
        VerificationMetadata meta = new VerificationMetadata(VerificationStatus.VERIFIED, "GameZone Wiki", null, null);
        assertEquals(VerificationStatus.UNVERIFIED, meta.status());
    }

    @Test
    @DisplayName("VERIFIED with a blank (whitespace-only) source is still downgraded")
    void testVerifiedWithBlankSourceIsDowngraded() {
        VerificationMetadata meta = new VerificationMetadata(VerificationStatus.VERIFIED, "   ", "https://example.com", "2026-09-10");
        assertEquals(VerificationStatus.UNVERIFIED, meta.status());
    }

    @Test
    @DisplayName("VERIFIED with sourceName and lastVerified but no sourceReference is downgraded - M4 requires the exact canonical page")
    void testVerifiedWithoutSourceReferenceIsDowngraded() {
        VerificationMetadata meta = new VerificationMetadata(VerificationStatus.VERIFIED, "GameZone Wiki", null, "2026-09-10");
        assertEquals(VerificationStatus.UNVERIFIED, meta.status());
    }

    @Test
    @DisplayName("VERIFIED with a blank (whitespace-only) sourceReference is also downgraded")
    void testVerifiedWithBlankSourceReferenceIsDowngraded() {
        VerificationMetadata meta = new VerificationMetadata(VerificationStatus.VERIFIED, "GameZone Wiki", "   ", "2026-09-10");
        assertEquals(VerificationStatus.UNVERIFIED, meta.status());
    }

    @Test
    @DisplayName("VERIFIED with all three of sourceName, sourceReference, and lastVerified is honored")
    void testVerifiedWithFullSourceIsHonored() {
        VerificationMetadata meta = new VerificationMetadata(VerificationStatus.VERIFIED, "GameZone Wiki",
                "https://www.gamezonemc.se/wiki/commands/kommandon", "2026-09-10");
        assertEquals(VerificationStatus.VERIFIED, meta.status());
        assertTrue(meta.hasSource());
    }

    @Test
    @DisplayName("A null status defaults to UNVERIFIED, never VERIFIED")
    void testNullStatusDefaultsToUnverified() {
        VerificationMetadata meta = new VerificationMetadata(null, "Source", "ref", "2026-09-10");
        assertEquals(VerificationStatus.UNVERIFIED, meta.status());
    }

    @Test
    @DisplayName("of() with a null/blank raw status string defaults to UNVERIFIED")
    void testOfWithMissingRawStatusDefaultsToUnverified() {
        assertEquals(VerificationStatus.UNVERIFIED, VerificationMetadata.of(null, "Source", "ref", "2026-09-10").status());
        assertEquals(VerificationStatus.UNVERIFIED, VerificationMetadata.of("  ", "Source", "ref", "2026-09-10").status());
    }

    @Test
    @DisplayName("of() with an unparseable raw status string becomes UNKNOWN, not VERIFIED")
    void testOfWithUnparseableRawStatusBecomesUnknown() {
        VerificationMetadata meta = VerificationMetadata.of("TOTALLY_MADE_UP", "Source", "ref", "2026-09-10");
        assertEquals(VerificationStatus.UNKNOWN, meta.status());
    }

    @Test
    @DisplayName("of() with an explicit VERIFIED raw status and full source is honored")
    void testOfWithExplicitVerifiedAndSourceIsHonored() {
        VerificationMetadata meta = VerificationMetadata.of("VERIFIED", "GameZone Wiki", "https://www.gamezonemc.se/wiki", "2026-09-10");
        assertEquals(VerificationStatus.VERIFIED, meta.status());
    }

    @Test
    @DisplayName("of() with an explicit VERIFIED raw status but no source is downgraded")
    void testOfWithExplicitVerifiedButNoSourceIsDowngraded() {
        VerificationMetadata meta = VerificationMetadata.of("VERIFIED", null, null, null);
        assertEquals(VerificationStatus.UNVERIFIED, meta.status());
    }

    @Test
    @DisplayName("UNVERIFIED_DEFAULT constant is itself UNVERIFIED with no source")
    void testUnverifiedDefaultConstant() {
        assertEquals(VerificationStatus.UNVERIFIED, VerificationMetadata.UNVERIFIED_DEFAULT.status());
        assertFalse(VerificationMetadata.UNVERIFIED_DEFAULT.hasSource());
    }

    @Test
    @DisplayName("Blank sourceReference/sourceName/lastVerified strings are normalized to null")
    void testBlankStringsNormalizedToNull() {
        VerificationMetadata meta = new VerificationMetadata(VerificationStatus.UNVERIFIED, "  ", "  ", "  ");
        assertNull(meta.sourceName());
        assertNull(meta.sourceReference());
        assertNull(meta.lastVerified());
        assertFalse(meta.hasSource());
    }

    @Test
    @DisplayName("STALE and UNKNOWN statuses pass through unchanged regardless of source presence")
    void testStaleAndUnknownPassThroughUnchanged() {
        assertEquals(VerificationStatus.STALE, new VerificationMetadata(VerificationStatus.STALE, null, null, null).status());
        assertEquals(VerificationStatus.UNKNOWN, new VerificationMetadata(VerificationStatus.UNKNOWN, null, null, null).status());
    }
}
