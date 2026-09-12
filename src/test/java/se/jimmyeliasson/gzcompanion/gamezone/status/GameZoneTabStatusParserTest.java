package se.jimmyeliasson.gzcompanion.gamezone.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure parsing logic - no live Minecraft client needed, matching this codebase's established
 * pattern of unit-testing extracted pure logic. Every header example used here is synthetic test
 * data (or, where marked REAL DATA, an exact captured real-server string) - never a hardcoded
 * assumption baked into production code.
 */
class GameZoneTabStatusParserTest {

    private static final String REAL_HEADER =
            "\n"
            + "          GAMEZONE MC\n"
            + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
            + "23/100 • TPS 20,0 • 10 - Småstad\n"
            + "Coins 65 068 • Stadskassa 11 487 272\n"
            + "Trälskärsbukten • MEMBER • +44.3%\n"
            + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n";

    @Test
    @DisplayName("REAL DATA: the exact captured real header parses every field correctly")
    void realCapturedHeaderParsesAllFields() {
        GameZoneLiveStatus status = GameZoneTabStatusParser.parse(REAL_HEADER);

        assertEquals(23, status.onlinePlayers());
        assertEquals(100, status.maxPlayers());
        assertEquals(20.0, status.tps());
        assertEquals(10, status.cityLevel());
        assertEquals("Småstad", status.cityName());
        assertEquals(65068L, status.coins());
        assertEquals(11487272L, status.treasury());
        assertEquals("Trälskärsbukten", status.settlementName());
        assertEquals("MEMBER", status.settlementRole());
        assertEquals(44.3, status.settlementBonusPercent(), 0.0001);
    }

    @Test
    @DisplayName("Null/blank/empty header yields UNKNOWN rather than a guess")
    void nullOrBlankHeaderIsUnknown() {
        assertEquals(GameZoneLiveStatus.UNKNOWN, GameZoneTabStatusParser.parse(null));
        assertEquals(GameZoneLiveStatus.UNKNOWN, GameZoneTabStatusParser.parse(""));
        assertEquals(GameZoneLiveStatus.UNKNOWN, GameZoneTabStatusParser.parse("   "));
    }

    @Test
    @DisplayName("A header with no recognizable lines at all yields UNKNOWN")
    void unrecognizableHeaderIsUnknown() {
        GameZoneLiveStatus status = GameZoneTabStatusParser.parse("Welcome to the server!\nType /help for commands");
        assertEquals(GameZoneLiveStatus.UNKNOWN, status);
        assertFalse(status.hasAnyData());
    }

    // ------------------------------------------------------------------
    // 1-2: TPS decimal comma AND decimal point
    // ------------------------------------------------------------------

    @Test
    @DisplayName("TPS with a decimal COMMA parses correctly")
    void tpsDecimalCommaParses() {
        GameZoneLiveStatus status = GameZoneTabStatusParser.parse("23/100 • TPS 20,0 • 10 - Småstad");
        assertEquals(20.0, status.tps());
    }

    @Test
    @DisplayName("TPS with a decimal POINT parses correctly")
    void tpsDecimalPointParses() {
        GameZoneLiveStatus status = GameZoneTabStatusParser.parse("9/100 • TPS 19.8 • 3 - By");
        assertEquals(19.8, status.tps());
        assertEquals(9, status.onlinePlayers());
        assertEquals(100, status.maxPlayers());
        assertEquals(3, status.cityLevel());
        assertEquals("By", status.cityName());
    }

    // ------------------------------------------------------------------
    // 3-4: bullet AND middle-dot separators
    // ------------------------------------------------------------------

    @Test
    @DisplayName("The real U+2022 BULLET separator parses the player/TPS/city line")
    void bulletSeparatorPlayerLineParses() {
        GameZoneLiveStatus status = GameZoneTabStatusParser.parse("23/100 • TPS 20,0 • 10 - Småstad");
        assertEquals(23, status.onlinePlayers());
        assertEquals(100, status.maxPlayers());
        assertEquals(10, status.cityLevel());
        assertEquals("Småstad", status.cityName());
    }

    @Test
    @DisplayName("The U+00B7 MIDDLE DOT separator also parses the player/TPS/city line")
    void middleDotSeparatorPlayerLineParses() {
        GameZoneLiveStatus status = GameZoneTabStatusParser.parse("100/100 · TPS 20,0 · 15 - Storstad");
        assertEquals(100, status.onlinePlayers());
        assertEquals(100, status.maxPlayers());
        assertEquals(20.0, status.tps());
        assertEquals(15, status.cityLevel());
        assertEquals("Storstad", status.cityName());
    }

    @Test
    @DisplayName("The middle-dot separator also parses the money line")
    void middleDotSeparatorMoneyLineParses() {
        GameZoneLiveStatus status = GameZoneTabStatusParser.parse("Coins 1000 · Stadskassa 2000");
        assertEquals(1000L, status.coins());
        assertEquals(2000L, status.treasury());
    }

    // ------------------------------------------------------------------
    // 5-8: money parsing
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Ordinary ASCII spaces as thousands grouping in money parse correctly")
    void ordinarySpacesInMoneyParse() {
        GameZoneLiveStatus status = GameZoneTabStatusParser.parse("Coins 65 068 • Stadskassa 11 487 272");
        assertEquals(65068L, status.coins());
        assertEquals(11487272L, status.treasury());
    }

    @Test
    @DisplayName("REAL DATA: U+00A0 NBSP as thousands grouping in money parses correctly")
    void nbspInMoneyParses() {
        GameZoneLiveStatus status = GameZoneTabStatusParser.parse("Coins 65 068 • Stadskassa 11 487 272");
        assertEquals(65068L, status.coins());
        assertEquals(11487272L, status.treasury());
    }

    @Test
    @DisplayName("No thousands separator at all in money still parses correctly")
    void noThousandsSeparatorInMoneyParses() {
        GameZoneLiveStatus status = GameZoneTabStatusParser.parse("Coins 65068 • Stadskassa 11487272");
        assertEquals(65068L, status.coins());
        assertEquals(11487272L, status.treasury());
    }

    @Test
    @DisplayName("Large money values (beyond int range) parse correctly using long")
    void largeMoneyValuesParse() {
        GameZoneLiveStatus status = GameZoneTabStatusParser.parse("Coins 9 999 999 999 • Stadskassa 123 456 789 012");
        assertEquals(9999999999L, status.coins());
        assertEquals(123456789012L, status.treasury());
    }

    // ------------------------------------------------------------------
    // 9-10: malformed lines fail safely
    // ------------------------------------------------------------------

    @Test
    @DisplayName("A malformed player count line fails safely - no guessed player/TPS/city fields")
    void malformedPlayerCountFailsSafely() {
        GameZoneLiveStatus status = GameZoneTabStatusParser.parse("abc/100 • TPS 20,0 • 10 - Småstad");
        assertNull(status.onlinePlayers());
        assertNull(status.maxPlayers());
        assertNull(status.tps());
        assertNull(status.cityLevel());
        assertNull(status.cityName());
    }

    @Test
    @DisplayName("A malformed TPS value fails the whole structural line safely - never a guessed TPS")
    void malformedTpsFailsSafely() {
        GameZoneLiveStatus status = GameZoneTabStatusParser.parse("23/100 • TPS abc • 10 - Småstad");
        assertNull(status.tps());
        assertNull(status.onlinePlayers(), "The whole line is one structural unit - a malformed TPS token means the line isn't confidently recognized at all, never a partial guess within it.");
    }

    // ------------------------------------------------------------------
    // 11-13: missing lines/fields
    // ------------------------------------------------------------------

    @Test
    @DisplayName("A missing coins/Stadskassa line leaves coins+treasury null, but other fields still parse")
    void missingCoinsLineOtherFieldsStillParse() {
        String header = "23/100 • TPS 20,0 • 10 - Småstad\nTrälskärsbukten • MEMBER • +44.3%";
        GameZoneLiveStatus status = GameZoneTabStatusParser.parse(header);

        assertNull(status.coins());
        assertNull(status.treasury());
        assertEquals(23, status.onlinePlayers());
        assertEquals("Trälskärsbukten", status.settlementName());
    }

    @Test
    @DisplayName("A missing settlement line leaves settlement fields null, but other fields still parse")
    void missingSettlementLineOtherFieldsStillParse() {
        String header = "23/100 • TPS 20,0 • 10 - Småstad\nCoins 65 068 • Stadskassa 11 487 272";
        GameZoneLiveStatus status = GameZoneTabStatusParser.parse(header);

        assertNull(status.settlementName());
        assertNull(status.settlementRole());
        assertNull(status.settlementBonusPercent());
        assertEquals(23, status.onlinePlayers());
        assertEquals(65068L, status.coins());
    }

    @Test
    @DisplayName("A settlement line with no bonus segment leaves the bonus null - never inferred")
    void missingBonusIsNull() {
        GameZoneLiveStatus status = GameZoneTabStatusParser.parse("Fjordheim • LORD");
        assertEquals("Fjordheim", status.settlementName());
        assertEquals("LORD", status.settlementRole());
        assertNull(status.settlementBonusPercent());
    }

    // ------------------------------------------------------------------
    // 14-15: bonus sign/decimal handling
    // ------------------------------------------------------------------

    @Test
    @DisplayName("A negative bonus parses correctly")
    void negativeBonusParses() {
        GameZoneLiveStatus status = GameZoneTabStatusParser.parse("Fjordheim • LORD • -2.5%");
        assertEquals(-2.5, status.settlementBonusPercent(), 0.0001);
    }

    @Test
    @DisplayName("A comma-decimal bonus parses correctly")
    void commaDecimalBonusParses() {
        GameZoneLiveStatus status = GameZoneTabStatusParser.parse("Fjordheim • LORD • +44,3%");
        assertEquals(44.3, status.settlementBonusPercent(), 0.0001);
    }

    @Test
    @DisplayName("A zero bonus is syntactically valid and parses to exactly zero")
    void zeroBonusParses() {
        GameZoneLiveStatus status = GameZoneTabStatusParser.parse("Fjordheim • LORD • 0%");
        assertEquals(0.0, status.settlementBonusPercent(), 0.0001);
    }

    // ------------------------------------------------------------------
    // 16-17: unrelated/reordered lines
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Unrelated extra header lines (MOTD, box-drawing, tips) are ignored")
    void unrelatedExtraLinesAreIgnored() {
        String header = "          GAMEZONE MC\n"
                + "━━━━━━━━━━━━━━━━━━━━\n"
                + "23/100 • TPS 20,0 • 10 - Småstad\n"
                + "Coins 65 068 • Stadskassa 11 487 272\n"
                + "Trälskärsbukten • MEMBER • +44.3%\n"
                + "━━━━━━━━━━━━━━━━━━━━\n";
        GameZoneLiveStatus status = GameZoneTabStatusParser.parse(header);

        assertEquals(23, status.onlinePlayers());
        assertEquals(65068L, status.coins());
        assertEquals("Trälskärsbukten", status.settlementName());
    }

    @Test
    @DisplayName("Reordered lines (settlement first, money last) still parse correctly - structural, not positional")
    void reorderedLinesStillParse() {
        String header = "Trälskärsbukten • MEMBER • +44.3%\n"
                + "━━━━━━━━━━━━━━━━━━━━\n"
                + "Coins 65 068 • Stadskassa 11 487 272\n"
                + "23/100 • TPS 20,0 • 10 - Småstad\n";
        GameZoneLiveStatus status = GameZoneTabStatusParser.parse(header);

        assertEquals(23, status.onlinePlayers());
        assertEquals(65068L, status.coins());
        assertEquals("Trälskärsbukten", status.settlementName());
        assertEquals(44.3, status.settlementBonusPercent(), 0.0001);
    }

    // ------------------------------------------------------------------
    // 18, 23: partial status usability / UNKNOWN-not-guessed
    // ------------------------------------------------------------------

    @Test
    @DisplayName("A status with only some fields known still reports hasAnyData() and exposes only the known ones")
    void partialStatusRemainsUsable() {
        String header = "23/100 • TPS 20,0 • 10 - Småstad";
        GameZoneLiveStatus status = GameZoneTabStatusParser.parse(header);

        assertTrue(status.hasAnyData());
        assertTrue(status.hasServerInfo());
        assertTrue(status.hasCity());
        assertFalse(status.hasEconomy());
        assertFalse(status.hasSettlement());
        assertNull(status.coins());
        assertNull(status.settlementName());
    }

    @Test
    @DisplayName("Every field independently defaults to null/UNKNOWN rather than a guessed value")
    void everyFieldDefaultsToUnknownIndependently() {
        GameZoneLiveStatus status = GameZoneLiveStatus.UNKNOWN;
        assertNull(status.onlinePlayers());
        assertNull(status.maxPlayers());
        assertNull(status.tps());
        assertNull(status.cityLevel());
        assertNull(status.cityName());
        assertNull(status.coins());
        assertNull(status.treasury());
        assertNull(status.settlementName());
        assertNull(status.settlementRole());
        assertNull(status.settlementBonusPercent());
        assertFalse(status.hasAnyData());
    }

    // ------------------------------------------------------------------
    // 24: consistency with the shared settlement parser (single source of truth)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Settlement name/role always match what GameZoneTabIdentityParser itself would produce - no second definition")
    void settlementFieldsMatchSharedParser() {
        String header = "Uddevalla • KING • +8.0%";
        GameZoneLiveStatus status = GameZoneTabStatusParser.parse(header);
        se.jimmyeliasson.gzcompanion.gamezone.settlement.GameZoneTabIdentityParser.ParsedHeader shared =
                se.jimmyeliasson.gzcompanion.gamezone.settlement.GameZoneTabIdentityParser.parseHeader(header);

        assertNotNull(shared);
        assertEquals(shared.name(), status.settlementName());
        assertEquals(shared.role(), status.settlementRole());
    }
}
