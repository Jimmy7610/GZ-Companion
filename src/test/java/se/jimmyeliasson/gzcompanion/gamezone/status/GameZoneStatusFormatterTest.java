package se.jimmyeliasson.gzcompanion.gamezone.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Pure, deterministic formatting - no Minecraft dependency, no locale dependency. */
class GameZoneStatusFormatterTest {

    @Test
    @DisplayName("Money is grouped with spaces every 3 digits")
    void moneyIsGrouped() {
        assertEquals("65 068", GameZoneStatusFormatter.formatMoney(65068L));
        assertEquals("11 487 272", GameZoneStatusFormatter.formatMoney(11487272L));
    }

    @Test
    @DisplayName("Small money values (under 1000) are not grouped at all")
    void smallMoneyValuesAreNotGrouped() {
        assertEquals("42", GameZoneStatusFormatter.formatMoney(42L));
        assertEquals("999", GameZoneStatusFormatter.formatMoney(999L));
    }

    @Test
    @DisplayName("Zero formats as a plain 0")
    void zeroMoneyFormatsPlain() {
        assertEquals("0", GameZoneStatusFormatter.formatMoney(0L));
    }

    @Test
    @DisplayName("A null money value formats as the unknown placeholder, never throws")
    void nullMoneyIsUnknownPlaceholder() {
        assertDoesNotThrow(() -> GameZoneStatusFormatter.formatMoney(null));
        assertEquals("—", GameZoneStatusFormatter.formatMoney(null));
    }

    @Test
    @DisplayName("TPS always shows exactly one decimal place")
    void tpsShowsOneDecimal() {
        assertEquals("20.0", GameZoneStatusFormatter.formatTps(20.0));
        assertEquals("19.8", GameZoneStatusFormatter.formatTps(19.8));
    }

    @Test
    @DisplayName("A null TPS value formats as the unknown placeholder")
    void nullTpsIsUnknownPlaceholder() {
        assertEquals("—", GameZoneStatusFormatter.formatTps(null));
    }

    @Test
    @DisplayName("A positive bonus is prefixed with an explicit plus sign")
    void positiveBonusHasPlusSign() {
        assertEquals("+44.3%", GameZoneStatusFormatter.formatBonusPercent(44.3));
    }

    @Test
    @DisplayName("A negative bonus keeps its own minus sign, never a double sign")
    void negativeBonusKeepsMinusSign() {
        assertEquals("-2.5%", GameZoneStatusFormatter.formatBonusPercent(-2.5));
    }

    @Test
    @DisplayName("A zero bonus has no sign prefix")
    void zeroBonusHasNoSign() {
        assertEquals("0.0%", GameZoneStatusFormatter.formatBonusPercent(0.0));
    }

    @Test
    @DisplayName("A null bonus formats as the unknown placeholder")
    void nullBonusIsUnknownPlaceholder() {
        assertEquals("—", GameZoneStatusFormatter.formatBonusPercent(null));
    }

    @Test
    @DisplayName("Formatting output is deterministic regardless of the JVM default locale")
    void formattingIsLocaleIndependent() {
        java.util.Locale original = java.util.Locale.getDefault();
        try {
            java.util.Locale.setDefault(java.util.Locale.GERMANY); // uses comma decimals by default
            assertEquals("20.0", GameZoneStatusFormatter.formatTps(20.0));
            assertEquals("+44.3%", GameZoneStatusFormatter.formatBonusPercent(44.3));
            assertEquals("11 487 272", GameZoneStatusFormatter.formatMoney(11487272L));
        } finally {
            java.util.Locale.setDefault(original);
        }
    }
}
