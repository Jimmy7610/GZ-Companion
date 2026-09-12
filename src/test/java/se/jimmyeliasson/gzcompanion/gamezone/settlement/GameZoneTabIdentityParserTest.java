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
    // Realistic real-server TAB row structure: [PREFIX] <glyph(s)> USERNAME [LEVEL]
    // The prefix is not necessarily immediately adjacent to the username - one or more
    // non-bracketed culture/role glyphs can sit between them, and a bracketed level always
    // follows the username. See this class's own javadoc for the fair-play reasoning.
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Prefix is extracted even with intervening culture/role glyphs between it and the name")
    void extractsPrefixWithInterveningGlyphs() {
        assertEquals("BUS", GameZoneTabIdentityParser.extractPrefix("[BUS] ✦ ♚ Dollymorris07 [1]", "Dollymorris07"));
    }

    @Test
    @DisplayName("Prefix is extracted with a single intervening glyph")
    void extractsPrefixWithSingleGlyph() {
        assertEquals("DDD", GameZoneTabIdentityParser.extractPrefix("[DDD] ✦ DoodleLukas [79]", "DoodleLukas"));
    }

    @Test
    @DisplayName("Prefix is extracted with a different intervening glyph shape - glyphs are never hardcoded")
    void extractsPrefixWithDifferentGlyph() {
        assertEquals("GRH", GameZoneTabIdentityParser.extractPrefix("[GRH] ♚ JaikenSnake [43]", "JaikenSnake"));
    }

    @Test
    @DisplayName("Arbitrary intervening Unicode text (not just single symbols) does not break parsing")
    void arbitraryInterveningTextDoesNotBreakParsing() {
        assertEquals("MJO", GameZoneTabIdentityParser.extractPrefix("[MJO] R ♚ LaudonIS [38]", "LaudonIS"));
    }

    @Test
    @DisplayName("A bracketed level AFTER the username is never mistaken for the settlement prefix")
    void trailingLevelAfterUsernameIsIgnored() {
        assertEquals("BUS", GameZoneTabIdentityParser.extractPrefix("[BUS] Jimmy [38]", "Jimmy"));
        assertNull(GameZoneTabIdentityParser.extractPrefix("Jimmy [38]", "Jimmy"), "With no bracket at all before the name, there is no prefix to find.");
    }

    @Test
    @DisplayName("No bracketed candidate before the username yields UNKNOWN, even with glyphs and a trailing level present")
    void noBracketedCandidateBeforeUsernameIsUnknown() {
        assertNull(GameZoneTabIdentityParser.extractPrefix("✦ Jimmy [38]", "Jimmy"));
        assertNull(GameZoneTabIdentityParser.extractPrefix("✦ ♚ Jimmy [38]", "Jimmy"));
    }

    @Test
    @DisplayName("Two bracketed candidates before the username is ambiguous - never guess which one is the prefix")
    void twoBracketedCandidatesBeforeUsernameIsAmbiguous() {
        assertNull(GameZoneTabIdentityParser.extractPrefix("[BUS] [XYZ] Jimmy [38]", "Jimmy"));
        assertNull(GameZoneTabIdentityParser.extractPrefix("[BUS] ♚ [XYZ] Jimmy", "Jimmy"));
    }

    @Test
    @DisplayName("Bare culture/role glyphs alone (no brackets at all) never become a settlement prefix")
    void bareGlyphsAloneNeverBecomePrefix() {
        assertNull(GameZoneTabIdentityParser.extractPrefix("✦ ♚ Jimmy", "Jimmy"));
        assertNull(GameZoneTabIdentityParser.extractPrefix("R ♚ Jimmy", "Jimmy"));
    }

    @Test
    @DisplayName("Username matching stays case-insensitive even with the realistic glyph+level row shape")
    void usernameMatchStaysCaseInsensitiveWithRealisticRow() {
        assertEquals("BUS", GameZoneTabIdentityParser.extractPrefix("[BUS] ✦ JIMMY [38]", "Jimmy"));
    }

    @Test
    @DisplayName("Same-settlement classification works end-to-end with the realistic row shape")
    void sameSettlementClassificationWorksWithRealisticRows() {
        assertTrue(GameZoneTabIdentityParser.isSameSettlement("[BUS] ✦ ♚ Dollymorris07 [1]", "Dollymorris07", "BUS"));
        assertFalse(GameZoneTabIdentityParser.isSameSettlement("[DDD] ✦ DoodleLukas [79]", "DoodleLukas", "BUS"));
    }

    // ------------------------------------------------------------------
    // REAL captured runtime data from the real GameZoneMC server (human QA diagnostics panel).
    // These exact strings proved two bugs: the header separator is U+2022 BULLET, not only
    // U+00B7 MIDDLE DOT, and settlement prefixes can contain non-ASCII letters (e.g. Swedish Ä).
    // ------------------------------------------------------------------

    @Test
    @DisplayName("REAL DATA: header with the real U+2022 BULLET separator parses correctly")
    void realBulletSeparatorHeaderParses() {
        GameZoneTabIdentityParser.ParsedHeader header = GameZoneTabIdentityParser.parseHeader("Trälskärsbukten • MEMBER • +44.3%");
        assertNotNull(header);
        assertEquals("Trälskärsbukten", header.name());
        assertEquals("MEMBER", header.role());
    }

    @Test
    @DisplayName("REAL DATA: the previously-assumed U+00B7 MIDDLE DOT separator still works (backward compatibility)")
    void middleDotSeparatorHeaderStillWorks() {
        GameZoneTabIdentityParser.ParsedHeader header = GameZoneTabIdentityParser.parseHeader("Trälskärsbukten · MEMBER · +44.3%");
        assertNotNull(header);
        assertEquals("Trälskärsbukten", header.name());
        assertEquals("MEMBER", header.role());
    }

    @Test
    @DisplayName("REAL DATA: KING role parses with the bullet separator")
    void realBulletSeparatorKingRoleParses() {
        GameZoneTabIdentityParser.ParsedHeader header = GameZoneTabIdentityParser.parseHeader("Uddevalla • KING • +8.0%");
        assertNotNull(header);
        assertEquals("Uddevalla", header.name());
        assertEquals("KING", header.role());
    }

    @Test
    @DisplayName("REAL DATA: LORD role parses with the bullet separator and no bonus segment")
    void realBulletSeparatorLordRoleParses() {
        GameZoneTabIdentityParser.ParsedHeader header = GameZoneTabIdentityParser.parseHeader("Fjordheim • LORD");
        assertNotNull(header);
        assertEquals("Fjordheim", header.name());
        assertEquals("LORD", header.role());
    }

    @Test
    @DisplayName("REAL DATA: the full captured multi-line TAB header finds the settlement line, ignoring NBSPs elsewhere")
    void realFullCapturedHeaderFindsSettlementLine() {
        String header = "\n          GAMEZONE MC\n"
                + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
                + "20/100 • TPS 20,0 • 10 - Småstad\n"
                + "Coins 65 068 • Stadskassa 11 487 272\n"
                + "Trälskärsbukten • MEMBER • +44.3%\n"
                + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n";

        GameZoneTabIdentityParser.ParsedHeader parsed = GameZoneTabIdentityParser.parseHeader(header);
        assertNotNull(parsed);
        assertEquals("Trälskärsbukten", parsed.name());
        assertEquals("MEMBER", parsed.role());
    }

    @Test
    @DisplayName("REAL DATA: the real [TRÄ] Unicode prefix is extracted from the real local TAB row")
    void realUnicodePrefixExtractedFromLocalRow() {
        assertEquals("TRÄ", GameZoneTabIdentityParser.extractPrefix("[TRÄ] ⚜ jbl76 [2]", "jbl76"));
    }

    @Test
    @DisplayName("REAL DATA: the real [TRÄ] Unicode prefix is extracted from another player's real TAB row")
    void realUnicodePrefixExtractedFromOtherPlayerRow() {
        assertEquals("TRÄ", GameZoneTabIdentityParser.extractPrefix("[TRÄ] ⚜ Olivre [62]", "Olivre"));
    }

    @Test
    @DisplayName("Other Swedish-letter Unicode prefixes also parse - not just TRÄ")
    void otherSwedishUnicodePrefixesParse() {
        assertEquals("ÅBY", GameZoneTabIdentityParser.extractPrefix("[ÅBY] jbl76", "jbl76"));
        assertEquals("ÖRN", GameZoneTabIdentityParser.extractPrefix("[ÖRN] jbl76", "jbl76"));
        assertEquals("ÄLV", GameZoneTabIdentityParser.extractPrefix("[ÄLV] jbl76", "jbl76"));
    }

    @Test
    @DisplayName("ASCII-only prefixes like [BUS] keep working alongside Unicode support")
    void asciiPrefixesStillWorkAlongsideUnicodeSupport() {
        assertEquals("BUS", GameZoneTabIdentityParser.extractPrefix("[BUS] jbl76", "jbl76"));
    }

    @Test
    @DisplayName("REAL DATA: the trailing [2] level in a real row is never mistaken for the prefix")
    void realTrailingLevelNeverBecomesPrefix() {
        String prefix = GameZoneTabIdentityParser.extractPrefix("[TRÄ] ⚜ jbl76 [2]", "jbl76");
        assertEquals("TRÄ", prefix);
        assertNotEquals("2", prefix);
    }

    @Test
    @DisplayName("REAL DATA shape: two bracketed candidates before the username (one Unicode) is still ambiguous -> UNKNOWN")
    void realShapeTwoBracketedCandidatesIsAmbiguous() {
        assertNull(GameZoneTabIdentityParser.extractPrefix("[TRÄ] [ABC] ⚜ jbl76 [2]", "jbl76"));
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
