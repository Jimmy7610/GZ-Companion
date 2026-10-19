package se.jimmyeliasson.gzcompanion.ui.tabs;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** The Settings tab's theme and privacy wording. */
class SettingsTabTextTest {

    @Test
    @DisplayName("Utseende explains that only the look changes")
    void themeHint() {
        assertEquals("Detta påverkar bara utseendet – inga funktioner ändras.", SettingsTabComponent.THEME_HINT);
    }

    @Test
    @DisplayName("The privacy block states exactly what the anonymous counter sends")
    void anonymousUsageInfo() {
        assertEquals("GZ Companion använder ett slumpmässigt installations-ID och versionsnummer för att räkna anonyma aktiva "
                + "installationer. Ingen Minecraft-identitet eller speldata skickas.", SettingsTabComponent.ANONYMOUS_USAGE_INFO);
    }

    @Test
    @DisplayName("The absolute 'no telemetry' claim is gone and there is no on/off switch for the counter")
    void noAbsoluteTelemetryClaim() throws IOException {
        String src = Files.readString(Path.of("src", "main", "java", "se", "jimmyeliasson", "gzcompanion", "ui", "tabs", "SettingsTabComponent.java"));
        assertFalse(src.contains("Ingen telemetri skickas någonsin"));
        assertFalse(src.contains("UsageHeartbeat"), "Settings only describes the counter - no toggle");
    }
}
