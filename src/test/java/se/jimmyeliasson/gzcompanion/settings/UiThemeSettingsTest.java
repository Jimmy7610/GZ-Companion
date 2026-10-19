package se.jimmyeliasson.gzcompanion.settings;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import se.jimmyeliasson.gzcompanion.ui.theme.UiTheme;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** "Utseende": persistence, backward compatibility, safe fallback, and the live-switch listener. */
class UiThemeSettingsTest {
    @TempDir
    Path tempDir;

    private Path file() {
        return tempDir.resolve("settings.json");
    }

    private CompanionSettings load() {
        SettingsLoadResult r = new JsonSettingsStore(file()).load();
        assertTrue(r.isUsable(), "load outcome " + r.outcome());
        return r.settings();
    }

    @Test
    @DisplayName("A settings.json from before themes existed loads as Minecraft, with every other setting intact")
    void legacyFileDefaultsToMinecraft() throws Exception {
        Files.writeString(file(), "{\"schemaVersion\":1,\"companionNotificationsEnabled\":false,\"gameZoneToastsEnabled\":true,"
                + "\"showTechnicalIds\":false,\"showUnverifiedKnowledge\":true,\"useLastKnownChestDataInPlanners\":false,"
                + "\"favoritePlayers\":[\"Alfa\"],\"hideUnverifiedChatWarning\":true}");
        CompanionSettings s = load();
        assertEquals(UiTheme.MINECRAFT, s.uiTheme());
        assertFalse(s.companionNotificationsEnabled());
        assertFalse(s.showTechnicalIds());
        assertEquals(List.of("Alfa"), s.favoritePlayers());
        assertTrue(s.hideUnverifiedChatWarning());
    }

    @Test
    @DisplayName("Tydlig round-trips; the file stores the stable id")
    void tydligRoundTrips() throws Exception {
        new JsonSettingsStore(file()).save(CompanionSettings.defaults().withUiTheme(UiTheme.TYDLIG));
        assertTrue(Files.readString(file()).contains("\"uiTheme\": \"tydlig\""));
        assertEquals(UiTheme.TYDLIG, load().uiTheme());
        new JsonSettingsStore(file()).save(CompanionSettings.defaults());
        assertTrue(Files.readString(file()).contains("\"uiTheme\": \"minecraft\""));
    }

    @Test
    @DisplayName("Unknown, future, wrong-typed or null theme values load as Minecraft without losing other settings or crashing")
    void unknownValuesFailSafe() throws Exception {
        for (String value : new String[]{"\"neon\"", "\"tydlig-v2\"", "\"\"", "123", "true", "null", "{}", "[\"tydlig\"]"}) {
            Files.writeString(file(), "{\"schemaVersion\":1,\"showTechnicalIds\":false,\"uiTheme\":" + value + "}");
            CompanionSettings s = load();
            assertEquals(UiTheme.MINECRAFT, s.uiTheme(), value);
            assertFalse(s.showTechnicalIds(), "Other settings still load for " + value);
        }
    }

    @Test
    @DisplayName("The old constructors and defaults() all mean Minecraft; a null theme is normalised to Minecraft")
    void constructorsDefaultToMinecraft() {
        assertEquals(UiTheme.MINECRAFT, CompanionSettings.defaults().uiTheme());
        assertEquals(UiTheme.MINECRAFT, new CompanionSettings(true, true, true, true, true, List.of()).uiTheme());
        assertEquals(UiTheme.MINECRAFT, new CompanionSettings(true, true, true, true, true, List.of(), true).uiTheme());
        assertEquals(UiTheme.MINECRAFT, new CompanionSettings(true, true, true, true, true, List.of(), true, null).uiTheme());
    }

    @Test
    @DisplayName("setUiTheme persists, notifies the listener immediately, and survives a restart")
    void managerSwitchesAndPersists() {
        JsonSettingsStore store = new JsonSettingsStore(file());
        SettingsManager manager = new SettingsManager(store);
        manager.initialize();
        List<UiTheme> applied = new ArrayList<>();
        manager.setThemeListener(applied::add);
        assertEquals(List.of(UiTheme.MINECRAFT), applied, "Listener gets the loaded theme on registration");

        assertTrue(manager.setUiTheme(UiTheme.TYDLIG));
        assertEquals(UiTheme.TYDLIG, applied.get(applied.size() - 1), "Applied immediately - no restart");
        SettingsManager restarted = new SettingsManager(new JsonSettingsStore(file()));
        restarted.initialize();
        assertEquals(UiTheme.TYDLIG, restarted.getSettings().uiTheme());
    }

    @Test
    @DisplayName("Changing any other setting keeps the chosen theme (no setter silently resets it)")
    void otherMutationsKeepTheme() {
        SettingsManager manager = new SettingsManager(new JsonSettingsStore(file()));
        manager.initialize();
        manager.setUiTheme(UiTheme.TYDLIG);
        manager.setCompanionNotificationsEnabled(false);
        manager.setGameZoneToastsEnabled(false);
        manager.setShowTechnicalIds(false);
        manager.setShowUnverifiedKnowledge(false);
        manager.setUseLastKnownChestDataInPlanners(false);
        manager.setHideUnverifiedChatWarning(true);
        manager.addFavoritePlayer("Alfa");
        manager.toggleFavoritePlayer("Beta");
        manager.removeFavoritePlayer("Alfa");
        assertEquals(UiTheme.TYDLIG, manager.getSettings().uiTheme());
        assertEquals(UiTheme.TYDLIG, load().uiTheme());
    }

    @Test
    @DisplayName("Återställ inställningar returns to Minecraft and re-applies it")
    void resetReturnsToMinecraft() {
        SettingsManager manager = new SettingsManager(new JsonSettingsStore(file()));
        manager.initialize();
        List<UiTheme> applied = new ArrayList<>();
        manager.setThemeListener(applied::add);
        manager.setUiTheme(UiTheme.TYDLIG);
        assertTrue(manager.resetToDefaults());
        assertEquals(UiTheme.MINECRAFT, manager.getSettings().uiTheme());
        assertEquals(UiTheme.MINECRAFT, applied.get(applied.size() - 1));
    }

    @Test
    @DisplayName("With an incompatible (future-schema) settings file the theme stays Minecraft and cannot be changed")
    void incompatibleFileKeepsDefault() throws Exception {
        Files.writeString(file(), "{\"schemaVersion\":99,\"uiTheme\":\"tydlig\"}");
        SettingsManager manager = new SettingsManager(new JsonSettingsStore(file()));
        manager.initialize();
        List<UiTheme> applied = new ArrayList<>();
        manager.setThemeListener(applied::add);
        assertEquals(List.of(UiTheme.MINECRAFT), applied);
        assertFalse(manager.setUiTheme(UiTheme.TYDLIG));
        assertTrue(Files.readString(file()).contains("\"schemaVersion\":99"), "Future file left untouched");
    }

    @Test
    @DisplayName("A corrupt settings file recovers to defaults, including the Minecraft theme")
    void corruptFileRecoversToMinecraft() throws Exception {
        Files.writeString(file(), "{not json");
        SettingsManager manager = new SettingsManager(new JsonSettingsStore(file()));
        manager.initialize();
        assertEquals(UiTheme.MINECRAFT, manager.getSettings().uiTheme());
        assertTrue(manager.setUiTheme(UiTheme.TYDLIG), "Usable again after recovery");
    }
}
