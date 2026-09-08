package se.jimmyeliasson.gzcompanion.profile;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ServerDetectionTest {

    @ParameterizedTest
    @ValueSource(strings = {
        "play.gamezonemc.se",
        "PLAY.GAMEZONEMC.SE",
        "play.gamezonemc.se:25565",
        "gamezonemc.se",
        "gamezonemc.se:1234",
        "survival.gamezonemc.se",
        "  play.gamezonemc.se  "
    })
    @DisplayName("Should correctly identify valid GameZone host variations")
    void testValidGameZoneHosts(String host) {
        assertTrue(ServerDetection.isGameZone(host), "Expected " + host + " to be recognized as GameZone");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "hypixel.net",
        "play.othermc.com",
        "localhost",
        "127.0.0.1",
        "gamezonemc.se.other.com",
        "",
        "   "
    })
    @DisplayName("Should reject non-GameZone hostnames")
    void testNonGameZoneHosts(String host) {
        assertFalse(ServerDetection.isGameZone(host), "Expected " + host + " to NOT be recognized as GameZone");
    }

    @Test
    @DisplayName("Should safely reject null host")
    void testNullHost() {
        assertFalse(ServerDetection.isGameZone(null));
    }
}
