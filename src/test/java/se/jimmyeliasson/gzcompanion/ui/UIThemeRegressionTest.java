package se.jimmyeliasson.gzcompanion.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UIThemeRegressionTest {

    @Test
    @DisplayName("Regression: All opaque typography and accent tokens have full 0xFF alpha")
    void testTypographyAlphaTokens() {
        assertFullAlpha(GZTheme.COLOR_EMERALD, "COLOR_EMERALD");
        assertFullAlpha(GZTheme.COLOR_MINT, "COLOR_MINT");
        assertFullAlpha(GZTheme.COLOR_EMERALD_DARK, "COLOR_EMERALD_DARK");
        assertFullAlpha(GZTheme.COLOR_TEXT_PRIMARY, "COLOR_TEXT_PRIMARY");
        assertFullAlpha(GZTheme.COLOR_TEXT_SECONDARY, "COLOR_TEXT_SECONDARY");
        assertFullAlpha(GZTheme.COLOR_TEXT_MUTED, "COLOR_TEXT_MUTED");
        assertFullAlpha(GZTheme.COLOR_TEXT_ACCENT, "COLOR_TEXT_ACCENT");
        assertFullAlpha(GZTheme.COLOR_TEXT_ON_EMERALD, "COLOR_TEXT_ON_EMERALD");
        assertFullAlpha(GZTheme.COLOR_STATUS_GREEN, "COLOR_STATUS_GREEN");
        assertFullAlpha(GZTheme.COLOR_STATUS_YELLOW, "COLOR_STATUS_YELLOW");
        assertFullAlpha(GZTheme.COLOR_STATUS_RED, "COLOR_STATUS_RED");
        assertFullAlpha(GZTheme.COLOR_STATUS_GREY, "COLOR_STATUS_GREY");
    }

    @Test
    @DisplayName("Regression: Translucent surface tokens have non-zero alpha below 0xFF")
    void testTranslucentSurfacesAlpha() {
        assertTranslucent(GZTheme.COLOR_BACKDROP, "COLOR_BACKDROP");
        assertTranslucent(GZTheme.COLOR_PANEL_BG, "COLOR_PANEL_BG");
        assertTranslucent(GZTheme.COLOR_CARD_BG, "COLOR_CARD_BG");
        assertTranslucent(GZTheme.COLOR_CARD_INNER, "COLOR_CARD_INNER");
        assertTranslucent(GZTheme.COLOR_NAV_ACTIVE, "COLOR_NAV_ACTIVE");
        assertTranslucent(GZTheme.COLOR_NAV_HOVER, "COLOR_NAV_HOVER");
    }

    @Test
    @DisplayName("Regression: All TabType values have valid non-blank symbols and labels")
    void testTabTypeSymbols() {
        for (TabType tab : TabType.values()) {
            assertNotNull(tab.getDisplayName());
            assertFalse(tab.getDisplayName().isBlank(), "Tab display name cannot be blank");
            assertNotNull(tab.getIconSymbol());
            assertFalse(tab.getIconSymbol().isBlank(), "Tab icon symbol cannot be blank");
            assertEquals(1, tab.getIconSymbol().length(), "Icon symbol must be single character ASCII/Latin-1 glyph");
        }
    }

    @Test
    @DisplayName("Regression: opaque() helper guarantees 0xFF alpha")
    void testOpaqueHelper() {
        assertEquals(0xFF10B981, GZTheme.opaque(0x10B981));
        assertEquals(0xFFF8FAFC, GZTheme.opaque(0xF8FAFC));
        assertEquals(0xFF10B981, GZTheme.opaque(0xFF10B981));
    }

    private void assertFullAlpha(int color, String name) {
        int alpha = (color >> 24) & 0xFF;
        assertEquals(0xFF, alpha, name + " must have 0xFF alpha (was 0x" + Integer.toHexString(alpha) + ")");
    }

    private void assertTranslucent(int color, String name) {
        int alpha = (color >> 24) & 0xFF;
        assertTrue(alpha > 0 && alpha < 0xFF, name + " must have translucent alpha between 1 and 254 (was " + alpha + ")");
    }
}