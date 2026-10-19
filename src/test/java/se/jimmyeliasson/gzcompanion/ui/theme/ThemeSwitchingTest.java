package se.jimmyeliasson.gzcompanion.ui.theme;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import se.jimmyeliasson.gzcompanion.ui.GZTheme;
import se.jimmyeliasson.gzcompanion.ui.TypographyScale;
import se.jimmyeliasson.gzcompanion.ui.layout.MainScreenLayout;
import se.jimmyeliasson.gzcompanion.ui.layout.UiRect;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Live theme switching through GZTheme, the bundled Tydlig font, and the "no theme branching in tabs" rule. */
class ThemeSwitchingTest {
    @AfterEach
    void restoreDefault() {
        GZTheme.applyTheme(UiTheme.MINECRAFT);
    }

    /** Every themed GZTheme color field and the token it must mirror. */
    private static final Map<String, java.util.function.ToIntFunction<ThemeTokens>> FIELDS = Map.ofEntries(
            Map.entry("COLOR_BACKDROP", ThemeTokens::backdrop), Map.entry("COLOR_PANEL_BG", ThemeTokens::panelBg),
            Map.entry("COLOR_CARD_BG", ThemeTokens::cardBg), Map.entry("COLOR_CARD_HOVER", ThemeTokens::cardHover),
            Map.entry("COLOR_CARD_INNER", ThemeTokens::cardInner), Map.entry("COLOR_NAV_ACTIVE", ThemeTokens::navActive),
            Map.entry("COLOR_NAV_HOVER", ThemeTokens::navHover), Map.entry("COLOR_EMERALD", ThemeTokens::emerald),
            Map.entry("COLOR_MINT", ThemeTokens::mint), Map.entry("COLOR_EMERALD_DARK", ThemeTokens::emeraldDark),
            Map.entry("COLOR_BORDER_SUBTLE", ThemeTokens::borderSubtle), Map.entry("COLOR_BORDER_MODAL", ThemeTokens::borderModal),
            Map.entry("COLOR_BORDER_EMERALD", ThemeTokens::borderEmerald), Map.entry("COLOR_TEXT_PRIMARY", ThemeTokens::textPrimary),
            Map.entry("COLOR_TEXT_SECONDARY", ThemeTokens::textSecondary), Map.entry("COLOR_TEXT_MUTED", ThemeTokens::textMuted),
            Map.entry("COLOR_TEXT_ACCENT", ThemeTokens::textAccent), Map.entry("COLOR_TEXT_ON_EMERALD", ThemeTokens::textOnEmerald),
            Map.entry("COLOR_STATUS_GREEN", ThemeTokens::statusGreen), Map.entry("COLOR_STATUS_YELLOW", ThemeTokens::statusYellow),
            Map.entry("COLOR_STATUS_RED", ThemeTokens::statusRed), Map.entry("COLOR_STATUS_GREY", ThemeTokens::statusGrey));

    private static void assertMirrors(ThemeTokens tokens) throws Exception {
        for (Field f : GZTheme.class.getDeclaredFields()) {
            if (!f.getName().startsWith("COLOR_")) continue;
            assertTrue(FIELDS.containsKey(f.getName()), "Every GZTheme color must be a theme token: " + f.getName());
            assertFalse(Modifier.isFinal(f.getModifiers()), f.getName() + " must not be a compile-time constant, or tabs would not follow the theme");
            assertEquals(FIELDS.get(f.getName()).applyAsInt(tokens), f.getInt(null), f.getName());
        }
    }

    @Test
    @DisplayName("Default is Minecraft; switching to Tydlig and back updates every GZTheme color immediately")
    void switchingUpdatesEveryToken() throws Exception {
        assertEquals(UiTheme.MINECRAFT, GZTheme.activeTheme());
        assertMirrors(ThemeTokens.MINECRAFT);
        assertTrue(GZTheme.textShadows());

        GZTheme.applyTheme(UiTheme.TYDLIG);
        assertEquals(UiTheme.TYDLIG, GZTheme.activeTheme());
        assertSame(ThemeTokens.TYDLIG, GZTheme.tokens());
        assertMirrors(ThemeTokens.TYDLIG);
        assertFalse(GZTheme.textShadows());

        GZTheme.applyTheme(UiTheme.MINECRAFT);
        assertMirrors(ThemeTokens.MINECRAFT);
        assertTrue(GZTheme.textShadows());
    }

    @Test
    @DisplayName("applyTheme(null) falls back to Minecraft instead of failing")
    void nullFallsBack() throws Exception {
        GZTheme.applyTheme(UiTheme.TYDLIG);
        GZTheme.applyTheme(null);
        assertEquals(UiTheme.MINECRAFT, GZTheme.activeTheme());
        assertMirrors(ThemeTokens.MINECRAFT);
    }

    @Test
    @DisplayName("Themes never change layout: typography scales and screen geometry are identical under both themes")
    void geometryIsThemeIndependent() {
        float[] scales = new float[TypographyScale.values().length];
        for (int i = 0; i < scales.length; i++) scales[i] = TypographyScale.values()[i].getScale();
        List<UiRect> before = new ArrayList<>();
        for (int[] s : new int[][]{{320, 240}, {640, 360}, {1280, 720}}) before.add(MainScreenLayout.calculate(s[0], s[1]).contentRect());

        GZTheme.applyTheme(UiTheme.TYDLIG);
        for (int i = 0; i < scales.length; i++) assertEquals(scales[i], TypographyScale.values()[i].getScale(), 0.0001f);
        List<UiRect> after = new ArrayList<>();
        for (int[] s : new int[][]{{320, 240}, {640, 360}, {1280, 720}}) after.add(MainScreenLayout.calculate(s[0], s[1]).contentRect());
        assertEquals(before, after, "Same content area at the minimum, NORMAL and LARGE sizes");
    }

    @Test
    @DisplayName("The Tydlig font resource exists: TTF provider + fallback to Minecraft's font, with the DejaVu license shipped")
    void tydligFontResources() throws IOException {
        String json;
        try (InputStream in = getClass().getResourceAsStream("/assets/gzcompanion/font/tydlig.json")) {
            assertNotNull(in, "font definition missing");
            json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertTrue(json.contains("\"type\": \"ttf\""));
        assertTrue(json.contains("\"file\": \"gzcompanion:tydlig/dejavu_sans_subset.ttf\""));
        assertTrue(json.contains("\"id\": \"minecraft:default\""), "Glyphs outside the subset fall back to Minecraft's font");
        try (InputStream ttf = getClass().getResourceAsStream("/assets/gzcompanion/font/tydlig/dejavu_sans_subset.ttf")) {
            assertNotNull(ttf);
            byte[] head = ttf.readNBytes(4);
            assertArrayEquals(new byte[]{0, 1, 0, 0}, head, "TrueType font header");
        }
        try (InputStream lic = getClass().getResourceAsStream("/assets/gzcompanion/font/tydlig/LICENSE_DEJAVU.txt")) {
            assertNotNull(lic, "The DejaVu/Bitstream Vera license must ship with the font");
            assertTrue(new String(lic.readAllBytes(), StandardCharsets.UTF_8).contains("Bitstream Vera"));
        }
    }

    @Test
    @DisplayName("Tabs never branch on the active theme - only GZTheme/TextUtil (and the Settings selector) know themes exist")
    void noThemeBranchingInTabs() throws IOException {
        Path root = Path.of("src", "main", "java", "se", "jimmyeliasson", "gzcompanion");
        try (Stream<Path> files = Files.walk(root)) {
            for (Path p : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                String rel = root.relativize(p).toString().replace('\\', '/');
                if (rel.startsWith("ui/theme/") || rel.equals("ui/GZTheme.java") || rel.equals("ui/layout/TextUtil.java")) continue;
                String src = Files.readString(p);
                assertFalse(src.contains("activeTheme()"), rel + " must not branch on the active theme");
                assertFalse(src.contains("ThemeTokens"), rel + " must read GZTheme, not theme tokens directly");
                if (!rel.equals("ui/tabs/SettingsTabComponent.java") && !rel.startsWith("settings/") && !rel.equals("core/CompanionSession.java")) {
                    assertFalse(src.contains("UiTheme"), rel + " must not know which themes exist");
                }
            }
        }
    }
}
