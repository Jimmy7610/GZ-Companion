package se.jimmyeliasson.gzcompanion.chest.bridge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import se.jimmyeliasson.gzcompanion.ui.GZTheme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Visual-token guardrails for the lightweight Kistor navigation HUD. */
class KistorNavigationHudStyleTest {

    @Test
    @DisplayName("Navigation HUD keeps the panel navy but uses a readable translucent background")
    void navigationBackgroundIsTranslucent() {
        int bg = KistorNavigationHudElement.NAVIGATION_CARD_BG;
        int panel = GZTheme.COLOR_PANEL_BG;

        int alpha = (bg >>> 24) & 0xFF;
        int panelAlpha = (panel >>> 24) & 0xFF;

        assertEquals(panel & 0x00FFFFFF, bg & 0x00FFFFFF, "Keep the same dark navy RGB");
        assertTrue(alpha <= panelAlpha / 2, "HUD should be at most half as opaque as the main panel");
        assertTrue(alpha >= 0x40, "HUD must retain at least ~25% opacity for readable text");
    }
}
