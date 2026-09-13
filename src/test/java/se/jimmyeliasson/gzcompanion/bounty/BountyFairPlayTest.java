package se.jimmyeliasson.gzcompanion.bounty;

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
 * Structural fair-play guarantees for Bounty Board, enforced by scanning its own source rather
 * than merely asserting behavior at runtime - mirrors {@code LeaderboardFairPlayTest}'s convention.
 * GameZone's own bounty documentation explicitly places the bounty target's name in red above the
 * mob in-game specifically so players can identify it visually - Companion must never turn that
 * into automated entity scanning, distance/direction/coordinate display, or automatic hunting/
 * command-sending. See docs/BOUNTY-BOARD.md's fair-play section.
 */
class BountyFairPlayTest {
    private static final Path BOUNTY_PACKAGE_DIR = Path.of("src", "main", "java", "se", "jimmyeliasson", "gzcompanion", "bounty");
    private static final Path UI_COMPONENT_FILE = Path.of("src", "main", "java", "se", "jimmyeliasson", "gzcompanion", "ui", "tabs", "BountiesTabComponent.java");

    private static List<Path> sourceFiles() throws IOException {
        assertTrue(Files.isDirectory(BOUNTY_PACKAGE_DIR), "expected bounty source directory to exist: " + BOUNTY_PACKAGE_DIR.toAbsolutePath());
        assertTrue(Files.isRegularFile(UI_COMPONENT_FILE), "expected BountiesTabComponent.java to exist: " + UI_COMPONENT_FILE.toAbsolutePath());
        try (Stream<Path> files = Files.walk(BOUNTY_PACKAGE_DIR)) {
            List<Path> all = new java.util.ArrayList<>(files.filter(p -> p.toString().endsWith(".java")).toList());
            all.add(UI_COMPONENT_FILE);
            return all;
        }
    }

    private static List<String> sourceContents() throws IOException {
        List<String> contents = new java.util.ArrayList<>();
        for (Path p : sourceFiles()) {
            contents.add(Files.readString(p));
        }
        return contents;
    }

    @Test
    @DisplayName("No entity/world scanning API is referenced anywhere in Bounty Board's source")
    void neverScansEntitiesOrWorld() throws IOException {
        List<String> forbidden = List.of(
                "getEntities(", "level.entitiesForRendering", "EntitySelector",
                "getNearestEntity", "ClientLevel", "Level level", ".level()",
                "BlockPos", "Vec3", "getEntityByUuid"
        );
        for (String source : sourceContents()) {
            for (String pattern : forbidden) {
                assertFalse(source.contains(pattern), "Bounty Board must never reference \"" + pattern + "\" (entity/world scanning or coordinates)");
            }
        }
    }

    @Test
    @DisplayName("No Minecraft chat/command-sending API is referenced anywhere - only clipboard copy is allowed")
    void neverSendsCommandsOrChatMessages() throws IOException {
        for (String source : sourceContents()) {
            assertFalse(source.contains("sendCommand"), "must never send a Minecraft command automatically");
            assertFalse(source.contains("ClientPacketListener"), "must never touch the raw packet connection");
            assertFalse(source.contains(".sendChatMessage"), "must never send chat");
            assertFalse(source.contains("sendUnsignedCommand"), "must never send a command automatically");
        }
    }

    @Test
    @DisplayName("The command-copy action only ever uses the clipboard, never the packet/command pipeline")
    void commandCopyOnlyUsesClipboard() throws IOException {
        String uiSource = Files.readString(UI_COMPONENT_FILE);
        assertTrue(uiSource.contains("setClipboard"), "expected the deliberate command-copy button to use setClipboard");
    }

    @Test
    @DisplayName("No packet listener/handler class is referenced")
    void neverRegistersPacketListeners() throws IOException {
        for (String source : sourceContents()) {
            assertFalse(source.toLowerCase(java.util.Locale.ROOT).contains("packetlistener"),
                    "Bounty Board must never add a packet listener");
        }
    }

    @Test
    @DisplayName("Only the GameZone host is ever mentioned as an HTTP target in the source")
    void onlyGameZoneHostIsContacted() throws IOException {
        for (String source : sourceContents()) {
            var matcher = java.util.regex.Pattern.compile("https?://([a-zA-Z0-9.-]+)").matcher(source);
            while (matcher.find()) {
                String host = matcher.group(1);
                assertTrue(host.endsWith("gamezonemc.se") || host.equals("github.com"),
                        "unexpected host literal found in bounty source: " + host);
            }
        }
    }

    @Test
    @DisplayName("No player UUID/game-profile API is referenced - the request carries no player identity")
    void neverReferencesLocalPlayerIdentity() throws IOException {
        for (String source : sourceContents()) {
            assertFalse(source.contains("getGameProfile()"), "must never read the local player's UUID/game profile");
            assertFalse(source.contains("player.getUUID"), "must never read the local player's UUID");
            assertFalse(source.contains("getPlayerName"), "Bounty Board's request never needs the local username (unlike Leaderboards' local-only DU comparison)");
        }
    }
}
