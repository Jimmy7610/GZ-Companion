package se.jimmyeliasson.gzcompanion.settlement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Structural fair-play guarantees for the LIVE Settlement dashboard, enforced by scanning its own
 * source rather than merely asserting behavior at runtime - mirrors {@code BountyFairPlayTest}/
 * {@code LeaderboardFairPlayTest}'s convention. Every live fact Settlement shows must come from
 * the SAME vanilla-visible TAB data Home/Online already legitimately read (see
 * docs/SETTLEMENT-COMPANION.md) - never entity/world/chunk scanning, never a packet/command sent
 * automatically, and never a new network source.
 *
 * <p>Unlike Bounty Board, Settlement's live "DU"/local-player identification legitimately DOES
 * need {@code getPlayerName()} (exactly like the Online tab already does) - that is intentionally
 * NOT in this test's forbidden list.
 */
class SettlementFairPlayTest {
    private static final Path SETTLEMENT_PACKAGE_DIR = Path.of("src", "main", "java", "se", "jimmyeliasson", "gzcompanion", "settlement");
    private static final Path GAMEZONE_LIVE_CONTEXT_FILE = Path.of("src", "main", "java", "se", "jimmyeliasson", "gzcompanion", "gamezone", "GameZoneLiveContext.java");
    private static final Path GAMEZONE_LIVE_CONTEXT_BUILDER_FILE = Path.of("src", "main", "java", "se", "jimmyeliasson", "gzcompanion", "gamezone", "GameZoneLiveContextBuilder.java");
    private static final Path UI_COMPONENT_FILE = Path.of("src", "main", "java", "se", "jimmyeliasson", "gzcompanion", "ui", "tabs", "SettlementTabComponent.java");

    private static List<Path> sourceFiles() throws IOException {
        assertTrue(Files.isDirectory(SETTLEMENT_PACKAGE_DIR), "expected settlement source directory to exist: " + SETTLEMENT_PACKAGE_DIR.toAbsolutePath());
        assertTrue(Files.isRegularFile(GAMEZONE_LIVE_CONTEXT_FILE), "expected GameZoneLiveContext.java to exist");
        assertTrue(Files.isRegularFile(GAMEZONE_LIVE_CONTEXT_BUILDER_FILE), "expected GameZoneLiveContextBuilder.java to exist");
        assertTrue(Files.isRegularFile(UI_COMPONENT_FILE), "expected SettlementTabComponent.java to exist");
        try (Stream<Path> files = Files.walk(SETTLEMENT_PACKAGE_DIR)) {
            List<Path> all = new java.util.ArrayList<>(files.filter(p -> p.toString().endsWith(".java")).toList());
            all.add(GAMEZONE_LIVE_CONTEXT_FILE);
            all.add(GAMEZONE_LIVE_CONTEXT_BUILDER_FILE);
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
    @DisplayName("No entity/world/chunk scanning or coordinate API is referenced anywhere in the live Settlement source")
    void neverScansEntitiesWorldOrCoordinates() throws IOException {
        // Deliberately does NOT forbid the bare substring ".level()" - unlike Bounty Board, this
        // domain legitimately has its OWN, wholly unrelated level() accessor (a settlement level
        // NUMBER, e.g. SettlementLevel.level()/EffectiveCurrentLevel.level()) used dozens of times
        // - a coincidental name clash with Minecraft's ClientLevel.level(), not evidence of world
        // access. ClientLevel/BlockPos/Vec3 below are specific, non-coincidental Minecraft world/
        // coordinate TYPE references instead.
        List<String> forbidden = List.of(
                "getEntities(", "level.entitiesForRendering", "EntitySelector", "getNearestEntity",
                "ClientLevel", "BlockPos", "Vec3", "getEntityByUuid",
                "ChunkPos", "getChunk("
        );
        for (String source : sourceContents()) {
            for (String pattern : forbidden) {
                assertFalse(source.contains(pattern), "Settlement must never reference \"" + pattern + "\" (entity/world/chunk scanning or coordinates)");
            }
        }
    }

    @Test
    @DisplayName("No Minecraft chat/command-sending API is referenced anywhere - Settlement sends nothing automatically")
    void neverSendsCommandsOrChatMessages() throws IOException {
        for (String source : sourceContents()) {
            assertFalse(source.contains("sendCommand"), "must never send a Minecraft command automatically");
            assertFalse(source.contains("ClientPacketListener"), "must never touch the raw packet connection");
            assertFalse(source.contains(".sendChatMessage"), "must never send chat");
            assertFalse(source.contains("sendUnsignedCommand"), "must never send a command automatically");
        }
    }

    @Test
    @DisplayName("No packet listener/handler class is referenced")
    void neverRegistersPacketListeners() throws IOException {
        for (String source : sourceContents()) {
            assertFalse(source.toLowerCase(Locale.ROOT).contains("packetlistener"),
                    "Settlement must never add a packet listener");
        }
    }

    @Test
    @DisplayName("No new network client/executor/thread is constructed - live data comes ONLY from the existing shared trackers")
    void neverConstructsNewNetworkOrThreadingInfrastructure() throws IOException {
        for (String source : sourceContents()) {
            assertFalse(source.contains("HttpClient"), "must never construct/reference an HttpClient - no new network source");
            assertFalse(source.contains("new Thread("), "must never spawn a raw Thread");
            assertFalse(source.contains("Executors."), "must never create a new executor");
            assertFalse(source.contains("ScheduledExecutorService"), "must never create a new scheduler/timer");
            assertFalse(source.contains("http://") || source.contains("https://"), "must never contain any URL literal - Settlement performs no network I/O of its own");
        }
    }

    @Test
    @DisplayName("No GameZone GUI is opened or clicked automatically")
    void neverOpensOrClicksGameZoneGuiAutomatically() throws IOException {
        for (String source : sourceContents()) {
            assertFalse(source.contains("ContainerScreen"), "must never open/reference a container GUI automatically");
            assertFalse(source.contains("clickMenuButton"), "must never click a menu button automatically");
        }
    }

    @Test
    @DisplayName("Same-settlement online usernames come only from GameZoneSettlementTracker - no separate live-player source is introduced")
    void sameSettlementDataComesOnlyFromTheExistingTracker() throws IOException {
        String builderSource = Files.readString(GAMEZONE_LIVE_CONTEXT_BUILDER_FILE);
        assertTrue(builderSource.contains("sameSettlementOnlineUsernames"),
                "GameZoneLiveContextBuilder must reuse GameZoneSettlementTracker.sameSettlementOnlineUsernames - never re-derive it");
    }
}
