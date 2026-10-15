package se.jimmyeliasson.gzcompanion.chest.bridge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import se.jimmyeliasson.gzcompanion.chest.model.StoragePosition;
import se.jimmyeliasson.gzcompanion.chest.nav.ChestNavigationMath;
import se.jimmyeliasson.gzcompanion.chest.nav.NavigationDestination;
import se.jimmyeliasson.gzcompanion.chest.nav.NavigationPoint;
import se.jimmyeliasson.gzcompanion.chest.nav.PlayerPose;
import se.jimmyeliasson.gzcompanion.ui.GZTheme;

import static org.junit.jupiter.api.Assertions.*;

/** The HUD card's text is derived from the resolved {@link NavigationDestination} alone - storage or saved point alike. */
class KistorNavigationHudTextTest {
    static final String CTX = "p@@server:play.gamezonemc.se";
    static final String OW = "minecraft:overworld";

    private static NavigationDestination point(String label) {
        return NavigationDestination.of(NavigationPoint.of("marketwatch:n1", CTX, OW, new StoragePosition(0, 64, 0), label).orElseThrow());
    }

    private static KistorNavigationHudElement.HudText at(NavigationDestination dest, PlayerPose pose) {
        return KistorNavigationHudElement.describe(dest, ChestNavigationMath.evaluate(pose, dest));
    }

    @Test
    @DisplayName("Title comes from the resolved destination (a MarketWatch label here), upper-cased and never cut")
    void titleFromDestination() {
        String longName = "Raw Beef från Olivres Shop vid torget bakom kyrkan i östra delen av staden";
        KistorNavigationHudElement.HudText text = at(point(longName), new PlayerPose(OW, 0.5, 64, -300, 0f));
        assertEquals(longName.toUpperCase(java.util.Locale.ROOT), text.title(), "Ellipsizing is the renderer's job");
    }

    @Test
    @DisplayName("Directional: arrow, distance, vertical line; no emphasis")
    void directional() {
        KistorNavigationHudElement.HudText text = at(point("Butik"), new PlayerPose(OW, 0.5, 40, -299.5, 0f));
        assertTrue(text.showArrow());
        assertFalse(text.emphasized());
        assertEquals("300 block fågelvägen", text.line2());
        assertEquals("↑ 24", text.line3());
        assertEquals(GZTheme.COLOR_TEXT_PRIMARY, text.line3Color());
    }

    @Test
    @DisplayName("Near: emphasized, arrow kept, 'Nära' uses the shared • separator")
    void near() {
        KistorNavigationHudElement.HudText text = at(point("Butik"), new PlayerPose(OW, 0.5, 64, -9.5, 0f));
        assertTrue(text.showArrow());
        assertTrue(text.emphasized());
        assertEquals("Nära • 10 block fågelvägen", text.line2());
        assertNull(text.line3(), "Negligible height difference is not shown");
    }

    @Test
    @DisplayName("Arrived: no arrow, green 'DU ÄR FRAMME', no third line")
    void arrived() {
        KistorNavigationHudElement.HudText text = at(point("Butik"), new PlayerPose(OW, 0.5, 64, 2.0, 0f));
        assertFalse(text.showArrow());
        assertTrue(text.emphasized());
        assertEquals("DU ÄR FRAMME", text.line2());
        assertEquals(GZTheme.COLOR_STATUS_GREEN, text.line2Color());
        assertNull(text.line3());
    }

    @Test
    @DisplayName("Wrong dimension: no arrow, yellow warning naming both dimensions")
    void wrongDimension() {
        KistorNavigationHudElement.HudText text = at(point("Butik"), new PlayerPose("minecraft:the_nether", 0, 64, 0, 0f));
        assertFalse(text.showArrow());
        assertEquals("⚠ Finns i Overworld", text.line2());
        assertEquals(GZTheme.COLOR_STATUS_YELLOW, text.line2Color());
        assertEquals("Du är i Nether", text.line3());
    }
}
