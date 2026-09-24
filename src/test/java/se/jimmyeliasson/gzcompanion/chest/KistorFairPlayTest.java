package se.jimmyeliasson.gzcompanion.chest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Structural fair-play guarantees for Kistor 2.0, enforced by scanning the new source (mirrors
 * {@code SettlementFairPlayTest}/{@code BountyFairPlayTest}). Kistor 2.0 is a domain/UI/navigation
 * layer ABOVE the existing capture system: none of its code may scan the world, read block
 * entities or chunks, raytrace, draw through walls, move/turn the player, click GUIs, send chat or
 * commands, or open a network connection.
 *
 * <p>The pre-existing capture classes ({@code ChestCaptureController}/{@code
 * MinecraftChestCaptureAdapter}) legitimately read the ONE block the player clicked and are
 * covered by their own documented rules; they are deliberately not part of this scan.
 */
class KistorFairPlayTest {
    private static final Path MAIN = Path.of("src", "main", "java", "se", "jimmyeliasson", "gzcompanion");

    private static List<Path> kistor2Sources() throws IOException {
        List<Path> files = new ArrayList<>();
        for (String dir : List.of("chest/index", "chest/nav", "chest/material", "ui/tabs/kistor")) {
            Path p = MAIN.resolve(dir);
            assertTrue(Files.isDirectory(p), "expected source dir " + p.toAbsolutePath());
            try (Stream<Path> s = Files.walk(p)) {
                s.filter(f -> f.toString().endsWith(".java")).forEach(files::add);
            }
        }
        for (String file : List.of("chest/KistorRuntime.java", "chest/ChestCaptureFeedback.java",
                "chest/bridge/KistorNavigationHudElement.java", "chest/bridge/MinecraftPlayerPoseReader.java",
                "chest/bridge/ChestItemIcons.java", "ui/tabs/KistorTabComponent.java", "ui/layout/KistorNavigationHudLayout.java",
                "ui/layout/KistorLayout.java", "mixin/BossHealthOverlayAccessor.java")) {
            Path p = MAIN.resolve(file);
            assertTrue(Files.isRegularFile(p), "expected source file " + p.toAbsolutePath());
            files.add(p);
        }
        return files;
    }

    private static void assertNoneContain(List<String> forbidden, String why) throws IOException {
        for (Path file : kistor2Sources()) {
            String src = Files.readString(file);
            for (String pattern : forbidden) {
                assertFalse(src.contains(pattern), file.getFileName() + " must never reference \"" + pattern + "\" (" + why + ")");
            }
        }
    }

    @Test
    @DisplayName("No world, chunk, block or block-entity scanning (no chest radar / X-ray / hidden storage detection)")
    void neverScansTheWorld() throws IOException {
        assertNoneContain(List.of("getBlockState", "getBlockEntity", "BlockEntity", "getChunk", "ChunkPos", "LevelChunk",
                "getEntities", "entitiesForRendering", "BlockPos.betweenClosed", "blockEntityRenderDispatcher"), "world scanning");
    }

    @Test
    @DisplayName("No raytracing and no world-space rendering (no ESP boxes or outlines through walls)")
    void neverRaytracesOrDrawsInTheWorld() throws IOException {
        assertNoneContain(List.of(".clip(", ".pick(", "HitResult", "LevelRenderer", "WorldRenderEvents", "renderLineBox",
                "ShapeRenderer", "setGlowing", "Outline"), "raytrace / ESP");
    }

    @Test
    @DisplayName("No player automation: never moves, turns, paths, presses keys, clicks GUIs or transfers items")
    void neverAutomatesThePlayer() throws IOException {
        assertNoneContain(List.of("setYRot", "setXRot", ".turn(", "setDeltaMovement", "moveTo", ".setPos(", "setDown(",
                "handleInventoryMouseClick", "clickMenuButton", "gameMode.", "Pathfinder", "PathNavigation", "AStar"), "automation");
    }

    @Test
    @DisplayName("No chat, commands, packets or network access")
    void neverSendsAnything() throws IOException {
        assertNoneContain(List.of("sendCommand", "sendUnsignedCommand", "sendChat", "ClientPacketListener", ".send(",
                "HttpClient", "http://", "https://", "Executors.", "new Thread("), "chat/commands/network");
    }
}
