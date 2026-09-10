package se.jimmyeliasson.gzcompanion.gamezone.parsing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import se.jimmyeliasson.gzcompanion.gamezone.events.GameZoneEventType;
import se.jimmyeliasson.gzcompanion.knowledge.common.VerificationMetadata;
import se.jimmyeliasson.gzcompanion.knowledge.common.VerificationStatus;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GameZoneParserDefinitionTest {

    private static GameZoneParserDefinition parser(boolean enabled, VerificationStatus status) {
        VerificationMetadata verification = status == VerificationStatus.VERIFIED
                ? new VerificationMetadata(status, "Test Source", "https://example.com", "2026-09-10")
                : new VerificationMetadata(status, null, null, null);
        return new GameZoneParserDefinition("p1", GameZoneEventType.WHISPER, ParserMatchType.CONTAINS, "test", List.of(), enabled, verification);
    }

    @Test
    @DisplayName("A parser is only active when enabled AND VERIFIED - never on either alone")
    void testIsActiveRequiresBothEnabledAndVerified() {
        assertTrue(parser(true, VerificationStatus.VERIFIED).isActive());
        assertFalse(parser(false, VerificationStatus.VERIFIED).isActive(), "Disabled must never activate, even if verified");
        assertFalse(parser(true, VerificationStatus.UNVERIFIED).isActive(), "Enabled-but-unverified must never activate - better inactive than false-positive");
        assertFalse(parser(false, VerificationStatus.UNVERIFIED).isActive());
    }

    @Test
    @DisplayName("A malformed regex pattern is detected as invalid syntax")
    void testInvalidRegexSyntaxDetected() {
        GameZoneParserDefinition badRegex = new GameZoneParserDefinition("p2", GameZoneEventType.WHISPER, ParserMatchType.REGEX,
                "[unterminated(", List.of(), true, VerificationMetadata.UNVERIFIED_DEFAULT);
        assertFalse(badRegex.hasValidRegexSyntax());
    }

    @Test
    @DisplayName("A valid regex pattern passes syntax validation")
    void testValidRegexSyntaxPasses() {
        GameZoneParserDefinition goodRegex = new GameZoneParserDefinition("p3", GameZoneEventType.WHISPER, ParserMatchType.REGEX,
                "^Test: (.+)$", List.of("value"), true, VerificationMetadata.UNVERIFIED_DEFAULT);
        assertTrue(goodRegex.hasValidRegexSyntax());
    }

    @Test
    @DisplayName("Non-REGEX match types are always considered valid syntax, regardless of pattern content")
    void testNonRegexAlwaysValidSyntax() {
        GameZoneParserDefinition exact = new GameZoneParserDefinition("p4", GameZoneEventType.WHISPER, ParserMatchType.EXACT,
                "[not actually regex", List.of(), true, VerificationMetadata.UNVERIFIED_DEFAULT);
        assertTrue(exact.hasValidRegexSyntax());
    }
}
