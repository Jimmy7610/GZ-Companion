package se.jimmyeliasson.gzcompanion.ui.theme;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The theme choice and both token sets: Minecraft is locked to the pre-theme look, Tydlig is checked for readability. */
class ThemeTokensTest {

    @Test
    @DisplayName("Persisted ids and Swedish names are stable; unknown, blank, future and null ids fall back to Minecraft")
    void themeIdsAndFallback() {
        assertEquals("minecraft", UiTheme.MINECRAFT.id());
        assertEquals("tydlig", UiTheme.TYDLIG.id());
        assertEquals("Minecraft (Standard)", UiTheme.MINECRAFT.displayName());
        assertEquals("Tydlig", UiTheme.TYDLIG.displayName());
        assertEquals(UiTheme.MINECRAFT, UiTheme.DEFAULT);
        assertEquals(UiTheme.TYDLIG, UiTheme.fromId("tydlig"));
        assertEquals(UiTheme.TYDLIG, UiTheme.fromId(" TYDLIG "));
        assertEquals(UiTheme.MINECRAFT, UiTheme.fromId("minecraft"));
        for (String bad : new String[]{null, "", "   ", "neon", "minecraft-v2", "dark", "Tydlig 2", "1"}) {
            assertEquals(UiTheme.MINECRAFT, UiTheme.fromId(bad), String.valueOf(bad));
        }
    }

    @Test
    @DisplayName("Minecraft theme = exactly the values GZ Companion shipped with before themes (no upgrade regression)")
    void minecraftIsTheLegacyLook() {
        ThemeTokens t = ThemeTokens.MINECRAFT;
        assertEquals(List.of(0xB3070A0E, 0xF20D141C, 0xCC131F2B, 0xE61E2E3D, 0x800A1017, 0x4D10B981, 0x331E293B),
                List.of(t.backdrop(), t.panelBg(), t.cardBg(), t.cardHover(), t.cardInner(), t.navActive(), t.navHover()));
        assertEquals(List.of(0xFF10B981, 0xFF34D399, 0xFF059669, 0x33475569, 0x66475569, 0x9910B981),
                List.of(t.emerald(), t.mint(), t.emeraldDark(), t.borderSubtle(), t.borderModal(), t.borderEmerald()));
        assertEquals(List.of(0xFFF8FAFC, 0xFF94A3B8, 0xFF64748B, 0xFF34D399, 0xFF04170E),
                List.of(t.textPrimary(), t.textSecondary(), t.textMuted(), t.textAccent(), t.textOnEmerald()));
        assertEquals(List.of(0xFF22C55E, 0xFFF59E0B, 0xFFEF4444, 0xFF64748B),
                List.of(t.statusGreen(), t.statusYellow(), t.statusRed(), t.statusGrey()));
        assertEquals(List.of(0x991E293B, 0xE61E2E3D, 0x8064748B, 0x800A1017, 0x40475569),
                List.of(t.buttonBg(), t.buttonHoverBg(), t.buttonHoverBorder(), t.badgeBg(), t.badgeBorder()));
        assertTrue(t.textShadows());
        assertTrue(t.chamferedCorners());
        assertNull(t.fontId(), "Minecraft's own font");
    }

    @Test
    @DisplayName("Tydlig: own sans-serif font, no drop shadows, square corners, fully opaque surfaces and text")
    void tydligIdentity() {
        ThemeTokens t = ThemeTokens.TYDLIG;
        assertEquals("gzcompanion:tydlig", t.fontId());
        assertFalse(t.textShadows());
        assertFalse(t.chamferedCorners());
        for (int surface : new int[]{t.panelBg(), t.cardBg(), t.cardHover(), t.cardInner(), t.badgeBg(), t.buttonBg(), t.buttonHoverBg()}) {
            assertEquals(0xFF, surface >>> 24, String.format("Opaque surface %08X - no world showing through behind text", surface));
        }
        for (int text : new int[]{t.textPrimary(), t.textSecondary(), t.textMuted(), t.textAccent(), t.textOnEmerald(), t.mint()}) {
            assertEquals(0xFF, text >>> 24);
        }
    }

    @Test
    @DisplayName("Tydlig text meets WCAG AA (4.5:1) on every surface it is drawn on")
    void tydligContrast() {
        ThemeTokens t = ThemeTokens.TYDLIG;
        for (int surface : new int[]{t.panelBg(), t.cardBg(), t.cardHover(), t.cardInner()}) {
            for (int text : new int[]{t.textPrimary(), t.textSecondary(), t.textMuted(), t.textAccent(), t.mint()}) {
                double ratio = contrast(text, surface);
                assertTrue(ratio >= 4.5, String.format("%08X on %08X = %.2f", text, surface, ratio));
            }
        }
        assertTrue(contrast(t.textOnEmerald(), t.emerald()) >= 4.5, "Primary button text");
        for (int status : new int[]{t.statusGreen(), t.statusYellow(), t.statusRed(), t.statusGrey()}) {
            assertTrue(contrast(status, t.cardBg()) >= 4.5, String.format("Status %08X", status));
        }
    }

    @Test
    @DisplayName("Tydlig makes secondary and muted text clearly more readable than Minecraft does")
    void tydligMoreReadableThanMinecraft() {
        int mcPanel = composite(ThemeTokens.MINECRAFT.cardBg(), composite(ThemeTokens.MINECRAFT.panelBg(), 0xFF000000));
        assertTrue(contrast(ThemeTokens.TYDLIG.textSecondary(), ThemeTokens.TYDLIG.cardBg()) > contrast(ThemeTokens.MINECRAFT.textSecondary(), mcPanel));
        assertTrue(contrast(ThemeTokens.TYDLIG.textMuted(), ThemeTokens.TYDLIG.cardBg()) > contrast(ThemeTokens.MINECRAFT.textMuted(), mcPanel) + 2);
    }

    // --- WCAG 2.x contrast helpers (alpha composited over the surface below) ---

    static int composite(int argb, int belowOpaque) {
        double a = (argb >>> 24) / 255.0;
        int r = (int) Math.round(((argb >> 16) & 0xFF) * a + ((belowOpaque >> 16) & 0xFF) * (1 - a));
        int g = (int) Math.round(((argb >> 8) & 0xFF) * a + ((belowOpaque >> 8) & 0xFF) * (1 - a));
        int b = (int) Math.round((argb & 0xFF) * a + (belowOpaque & 0xFF) * (1 - a));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    static double luminance(int rgb) {
        double[] c = {((rgb >> 16) & 0xFF) / 255.0, ((rgb >> 8) & 0xFF) / 255.0, (rgb & 0xFF) / 255.0};
        for (int i = 0; i < 3; i++) c[i] = c[i] <= 0.03928 ? c[i] / 12.92 : Math.pow((c[i] + 0.055) / 1.055, 2.4);
        return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2];
    }

    static double contrast(int fg, int bgOpaque) {
        double l1 = luminance(composite(fg, bgOpaque));
        double l2 = luminance(bgOpaque);
        return (Math.max(l1, l2) + 0.05) / (Math.min(l1, l2) + 0.05);
    }
}
