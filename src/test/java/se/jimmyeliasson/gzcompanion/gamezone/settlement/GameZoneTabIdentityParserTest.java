package se.jimmyeliasson.gzcompanion.gamezone.settlement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure parsing logic - no live Minecraft client needed, matching this codebase's established
 * pattern of unit-testing extracted pure logic. Every settlement name/role/prefix example used
 * here is synthetic test data, never a hardcoded assumption baked into production code - see
 * {@link GameZoneTabIdentityParser}'s own javadoc for the fair-play reasoning.
 */
class GameZoneTabIdentityParserTest {

    // ------------------------------------------------------------------
    // Settlement name / role parsing from the TAB header
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Settlement name is parsed from a realistic header line")
    void parsesSettlementNameFromRealisticHeader() {
        GameZoneTabIdentityParser.ParsedHeader header = GameZoneTabIdentityParser.parseHeader("Trälskärsbukten · MEMBER · +44.3%");
        assertNotNull(header);
        assertEquals("Trälskärsbukten", header.name());
    }

    @Test
    @DisplayName("Role MEMBER is parsed")
    void parsesMemberRole() {
        GameZoneTabIdentityParser.ParsedHeader header = GameZoneTabIdentityParser.parseHeader("Trälskärsbukten · MEMBER · +44.3%");
        assertNotNull(header);
        assertEquals("MEMBER", header.role());
    }

    @Test
    @DisplayName("Role KING is parsed")
    void parsesKingRole() {
        GameZoneTabIdentityParser.ParsedHeader header = GameZoneTabIdentityParser.parseHeader("Uddevalla · KING · +10.0%");
        assertNotNull(header);
        assertEquals("KING", header.role());
        assertEquals("Uddevalla", header.name());
    }

    @Test
    @DisplayName("Role LORD is parsed, even without a trailing bonus segment")
    void parsesLordRoleWithoutBonus() {
        GameZoneTabIdentityParser.ParsedHeader header = GameZoneTabIdentityParser.parseHeader("Fjordheim · LORD");
        assertNotNull(header);
        assertEquals("LORD", header.role());
        assertEquals("Fjordheim", header.name());
    }

    @Test
    @DisplayName("A malformed/unrecognized header yields UNKNOWN (null) rather than a guess")
    void malformedHeaderIsUnknown() {
        assertNull(GameZoneTabIdentityParser.parseHeader("Welcome to the server!"));
        assertNull(GameZoneTabIdentityParser.parseHeader("SomeName · CITIZEN · +1%"));
        assertNull(GameZoneTabIdentityParser.parseHeader(""));
        assertNull(GameZoneTabIdentityParser.parseHeader(null));
    }

    @Test
    @DisplayName("Unrelated TAB header lines are ignored - the matching line is found structurally, not by fixed position")
    void unrelatedLinesAreIgnored() {
        String header = "Welcome to GameZoneMC!\nTrälskärsbukten · MEMBER · +44.3%\nType /help for commands";
        GameZoneTabIdentityParser.ParsedHeader parsed = GameZoneTabIdentityParser.parseHeader(header);
        assertNotNull(parsed);
        assertEquals("Trälskärsbukten", parsed.name());
        assertEquals("MEMBER", parsed.role());
    }

    // ------------------------------------------------------------------
    // Settlement prefix extraction from a player's own TAB display text
    // ------------------------------------------------------------------

    @Test
    @DisplayName("The local player's settlement prefix is extracted from their own TAB display")
    void extractsLocalPrefix() {
        assertEquals("BUS", GameZoneTabIdentityParser.extractPrefix("[BUS] jbl76", "jbl76"));
    }

    @Test
    @DisplayName("A candidate with the same prefix as the local player is classified as same-settlement")
    void samePrefixIsSameSettlement() {
        assertTrue(GameZoneTabIdentityParser.isSameSettlement("[BUS] Olivre", "Olivre", "BUS"));
    }

    @Test
    @DisplayName("A candidate with a different prefix is NOT classified as same-settlement")
    void differentPrefixIsNotSameSettlement() {
        assertFalse(GameZoneTabIdentityParser.isSameSettlement("[DDD] SomeoneElse", "SomeoneElse", "BUS"));
    }

    @Test
    @DisplayName("With no known local prefix, no same-settlement guesses are ever made")
    void noLocalPrefixMeansNoGuesses() {
        assertFalse(GameZoneTabIdentityParser.isSameSettlement("[BUS] Olivre", "Olivre", null));
        assertFalse(GameZoneTabIdentityParser.isSameSettlement("[BUS] Olivre", "Olivre", ""));
    }

    @Test
    @DisplayName("A culture sigil (a bare symbol, not a bracketed code) is never mistaken for a settlement prefix")
    void cultureSigilIsNotMistakenForPrefix() {
        assertNull(GameZoneTabIdentityParser.extractPrefix("✦ jbl76", "jbl76"));
    }

    @Test
    @DisplayName("A role symbol (a bare glyph, not a bracketed code) is never mistaken for a settlement prefix")
    void roleSymbolIsNotMistakenForPrefix() {
        assertNull(GameZoneTabIdentityParser.extractPrefix("♚ jbl76", "jbl76"));
    }

    @Test
    @DisplayName("A level indicator (not bracketed alphanumeric) is never mistaken for a settlement prefix")
    void levelIndicatorIsNotMistakenForPrefix() {
        assertNull(GameZoneTabIdentityParser.extractPrefix("Lv.42 jbl76", "jbl76"));
    }

    @Test
    @DisplayName("Prefix matching is format-invariant (case) as long as the semantic bracketed code is the same")
    void prefixMatchingIsCaseInvariant() {
        assertEquals("BUS", GameZoneTabIdentityParser.extractPrefix("[bus] jbl76", "jbl76"));
        assertTrue(GameZoneTabIdentityParser.isSameSettlement("[bus] Olivre", "Olivre", "BUS"));
    }

    @Test
    @DisplayName("Malformed or missing display text/username never throws - always fails safely to UNKNOWN")
    void malformedPrefixInputFailsSafely() {
        assertDoesNotThrow(() -> GameZoneTabIdentityParser.extractPrefix(null, "jbl76"));
        assertDoesNotThrow(() -> GameZoneTabIdentityParser.extractPrefix("[BUS] jbl76", null));
        assertDoesNotThrow(() -> GameZoneTabIdentityParser.extractPrefix("", ""));
        assertNull(GameZoneTabIdentityParser.extractPrefix(null, "jbl76"));
        assertNull(GameZoneTabIdentityParser.extractPrefix("[BUS] jbl76", null));
        assertNull(GameZoneTabIdentityParser.extractPrefix("garbled text with no name in it", "jbl76"));
    }

    // ------------------------------------------------------------------
    // Combined identity
    // ------------------------------------------------------------------

    @Test
    @DisplayName("identityFor combines header + local prefix into one known identity")
    void identityForCombinesBothHalves() {
        GameZoneSettlementIdentity identity = GameZoneTabIdentityParser.identityFor(
                "Trälskärsbukten · MEMBER · +44.3%", "[BUS] jbl76", "jbl76");
        assertTrue(identity.known());
        assertEquals("Trälskärsbukten", identity.settlementName());
        assertEquals("MEMBER", identity.role());
        assertEquals("BUS", identity.settlementPrefix());
    }

    @Test
    @DisplayName("identityFor is UNKNOWN when neither the header nor the local prefix parse")
    void identityForUnknownWhenNothingParses() {
        GameZoneSettlementIdentity identity = GameZoneTabIdentityParser.identityFor("nothing useful here", "jbl76", "jbl76");
        assertEquals(GameZoneSettlementIdentity.UNKNOWN, identity);
        assertFalse(identity.known());
    }
}
