package se.jimmyeliasson.gzcompanion.leaderboard;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Structural fair-play guarantees for the Leaderboards feature, enforced by scanning its own
 * source rather than merely asserting behavior at runtime - this is READ-ONLY public web data, and
 * these tests exist to make it structurally impossible for a future change to quietly add a
 * Minecraft command call, a packet listener, or a network call to a non-GameZone host without an
 * obviously-failing test right here. See docs/LEADERBOARDS.md's fair-play/privacy sections.
 */
class LeaderboardFairPlayTest {
    private static final Path SOURCE_DIR = Path.of("src", "main", "java", "se", "jimmyeliasson", "gzcompanion", "leaderboard");

    private static List<String> javaSources() throws IOException {
        assertTrue(Files.isDirectory(SOURCE_DIR), "expected leaderboard source directory to exist: " + SOURCE_DIR.toAbsolutePath());
        try (Stream<Path> files = Files.walk(SOURCE_DIR)) {
            return files.filter(p -> p.toString().endsWith(".java"))
                    .map(p -> {
                        try {
                            return Files.readString(p);
                        } catch (IOException e) {
                            throw new java.io.UncheckedIOException(e);
                        }
                    })
                    .toList();
        }
    }

    @Test
    @DisplayName("No Minecraft chat/command sending API is referenced anywhere in the leaderboard package")
    void neverSendsCommandsOrChatMessages() throws IOException {
        for (String source : javaSources()) {
            assertFalse(source.contains("sendCommand"), "leaderboard code must never send a Minecraft command");
            assertFalse(source.contains("ClientPacketListener"), "leaderboard code must never touch the raw packet connection");
            assertFalse(source.contains(".sendChatMessage"), "leaderboard code must never send chat");
        }
    }

    @Test
    @DisplayName("No packet listener/handler class is referenced")
    void neverRegistersPacketListeners() throws IOException {
        for (String source : javaSources()) {
            assertFalse(source.toLowerCase(java.util.Locale.ROOT).contains("packetlistener"),
                    "leaderboard code must never add a packet listener");
        }
    }

    @Test
    @DisplayName("The local Minecraft player's UUID is never referenced - only a plain username string comparison is allowed")
    void neverReferencesLocalPlayerUuid() throws IOException {
        for (String source : javaSources()) {
            assertFalse(source.contains("getGameProfile()"), "must never read the local player's UUID/game profile");
            assertFalse(source.contains("player.getUUID"), "must never read the local player's UUID");
        }
    }

    @Test
    @DisplayName("Only the GameZone host is ever mentioned as an HTTP target in the source")
    void onlyGameZoneHostIsContacted() throws IOException {
        for (String source : javaSources()) {
            // Every literal "https://" in this package must point at gamezonemc.se or be part of a
            // User-Agent/comment referencing the project's own GitHub repo, never a third-party host.
            var matcher = java.util.regex.Pattern.compile("https?://([a-zA-Z0-9.-]+)").matcher(source);
            while (matcher.find()) {
                String host = matcher.group(1);
                assertTrue(host.endsWith("gamezonemc.se") || host.equals("github.com"),
                        "unexpected host literal found in leaderboard source: " + host);
            }
        }
    }
}
