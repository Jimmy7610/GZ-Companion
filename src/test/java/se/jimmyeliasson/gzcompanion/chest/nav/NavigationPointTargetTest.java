package se.jimmyeliasson.gzcompanion.chest.nav;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import se.jimmyeliasson.gzcompanion.chest.ChestManager;
import se.jimmyeliasson.gzcompanion.chest.KistorRuntime;
import se.jimmyeliasson.gzcompanion.chest.model.ChestCaptureEvent;
import se.jimmyeliasson.gzcompanion.chest.model.ChestSlotEntry;
import se.jimmyeliasson.gzcompanion.chest.model.StorageKind;
import se.jimmyeliasson.gzcompanion.chest.model.StoragePosition;
import se.jimmyeliasson.gzcompanion.chest.model.StorageShape;
import se.jimmyeliasson.gzcompanion.chest.model.StoredContainer;
import se.jimmyeliasson.gzcompanion.chest.model.StoredContainerId;
import se.jimmyeliasson.gzcompanion.chest.storage.JsonChestIndexStore;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The shared HITTA navigation generalised to non-storage coordinates ({@link NavigationPoint}):
 * one target slot for both kinds, the same context/disconnect safety, and storage navigation
 * unchanged.
 */
class NavigationPointTargetTest {
    @TempDir
    Path tempDir;

    static final String CTX = "p@@server:play.gamezonemc.se";
    static final String OTHER = "p@@singleplayer:world";
    static final String OW = "minecraft:overworld";
    static final StoragePosition CHEST_POS = new StoragePosition(100, 64, 100);

    private ChestManager manager;
    private KistorRuntime runtime;

    private void setUp() {
        manager = new ChestManager(new JsonChestIndexStore(tempDir.resolve("chest-index.json")));
        manager.initialize();
        runtime = new KistorRuntime(manager);
        open(CHEST_POS, 1000L);
        manager.setLabel(CTX, chestId(), "Materiallager");
    }

    private Optional<ChestCaptureEvent> open(StoragePosition pos, long at) {
        manager.recordPendingInteraction(CTX, OW, StorageKind.CHEST, pos, null, StorageShape.SINGLE, at);
        manager.tryBeginCapture(CTX, OW, EnumSet.of(StorageKind.CHEST), at + 10);
        manager.updateCaptureSlots(List.of(new ChestSlotEntry(0, "minecraft:stone", 3)), at + 20);
        return manager.endCapture(at + 30);
    }

    private static StoredContainerId chestId() {
        return new StoredContainerId(CTX, OW, CHEST_POS, StorageKind.CHEST);
    }

    private static NavigationPoint shop(String context) {
        return NavigationPoint.of("marketwatch:n1", context, OW, new StoragePosition(-847, 70, 12), "Olivres Shop").orElseThrow();
    }

    @Test
    @DisplayName("A point in the current context can be navigated to and resolves to its block center")
    void startPointResolvesDestination() {
        setUp();
        ChestNavigationManager nav = runtime.navigation();
        assertTrue(nav.startPoint(CTX, shop(CTX), 10L));
        assertTrue(nav.isActive());
        assertTrue(nav.isPointTarget("marketwatch:n1"));
        assertFalse(nav.isPointTarget("marketwatch:other"));
        assertTrue(nav.target().isEmpty(), "A point is not a storage target");

        NavigationDestination dest = nav.resolveDestination(manager).orElseThrow();
        assertEquals("Olivres Shop", dest.title());
        assertEquals(OW, dest.dimensionKey());
        assertEquals(-846.5, dest.x());
        assertEquals(70.5, dest.y());
        assertEquals(12.5, dest.z());
        assertEquals(70, dest.blockY());
        assertTrue(dest.container().isEmpty());
    }

    @Test
    @DisplayName("A point from another world/server context is refused")
    void pointFromOtherContextRefused() {
        setUp();
        ChestNavigationManager nav = runtime.navigation();
        assertFalse(nav.startPoint(CTX, shop(OTHER), 10L));
        assertFalse(nav.startPoint(CTX, null, 10L));
        assertFalse(nav.startPoint(null, shop(CTX), 10L));
        assertFalse(nav.isActive());
    }

    @Test
    @DisplayName("A point replaces an active chest target, and a chest replaces an active point (REPLACED)")
    void pointAndChestReplaceEachOther() {
        setUp();
        ChestNavigationManager nav = runtime.navigation();
        assertTrue(nav.start(manager, CTX, chestId(), 1L));

        assertTrue(nav.startPoint(CTX, shop(CTX), 2L));
        assertEquals(ChestNavigationManager.StopReason.REPLACED, nav.lastStopReason().orElseThrow());
        assertFalse(nav.isTarget(chestId()));
        assertTrue(nav.isPointTarget("marketwatch:n1"));

        assertTrue(nav.start(manager, CTX, chestId(), 3L));
        assertTrue(nav.isTarget(chestId()));
        assertTrue(nav.pointTarget().isEmpty());
        assertEquals("Materiallager", nav.resolveDestination(manager).orElseThrow().title());
        assertEquals(chestId(), nav.resolveDestination(manager).orElseThrow().container().orElseThrow());
    }

    @Test
    @DisplayName("Stop, context change and disconnect end a point target exactly like a chest target")
    void pointObeysLifecycleRules() {
        setUp();
        ChestNavigationManager nav = runtime.navigation();

        nav.startPoint(CTX, shop(CTX), 1L);
        nav.stop(ChestNavigationManager.StopReason.USER);
        assertFalse(nav.isActive());
        assertEquals(ChestNavigationManager.StopReason.USER, nav.lastStopReason().orElseThrow());

        nav.startPoint(CTX, shop(CTX), 2L);
        nav.onContextObserved(CTX);
        assertTrue(nav.isActive(), "Same context keeps navigating");
        nav.onContextObserved(OTHER);
        assertFalse(nav.isActive());
        assertEquals(ChestNavigationManager.StopReason.CONTEXT_CHANGED, nav.lastStopReason().orElseThrow());

        nav.startPoint(CTX, shop(CTX), 3L);
        runtime.onDisconnect();
        assertFalse(nav.isActive());
        assertEquals(ChestNavigationManager.StopReason.DISCONNECTED, nav.lastStopReason().orElseThrow());
    }

    @Test
    @DisplayName("A point never depends on Kistor: index reset, an unavailable manager or opening a chest don't stop it")
    void pointIndependentOfKistor() {
        setUp();
        ChestNavigationManager nav = runtime.navigation();
        nav.startPoint(CTX, shop(CTX), 1L);

        runtime.onChestIndexCleared(CTX);
        assertTrue(nav.isActive());
        assertTrue(nav.resolveTarget(manager).isEmpty(), "No storage target while a point is active");
        assertTrue(nav.isActive(), "resolveTarget must not stop a point target");
        assertTrue(nav.resolveDestination(null).isPresent(), "Resolves without a Chest Manager");
        assertFalse(nav.onStorageLegitimatelyOpened(chestId()));
        assertTrue(nav.isActive());
    }

    @Test
    @DisplayName("Chest destinations evaluate exactly as before the generalisation")
    void chestEvaluationUnchanged() {
        setUp();
        StoredContainer chest = manager.getContainer(CTX, chestId()).orElseThrow();
        PlayerPose pose = new PlayerPose(OW, 80.2, 60.0, 130.7, 37f);
        ChestNavigationReading viaContainer = ChestNavigationMath.evaluate(pose, chest);
        ChestNavigationReading viaDestination = ChestNavigationMath.evaluate(pose, NavigationDestination.of(chest));
        assertEquals(viaContainer, viaDestination);
        assertEquals(Math.sqrt(20.3 * 20.3 + 30.2 * 30.2), viaContainer.horizontalDistance(), 1e-9);
        assertEquals(4, viaContainer.verticalDelta());
    }

    @Test
    @DisplayName("Point math: distance, bearing, wrong dimension and arrival")
    void pointMath() {
        NavigationDestination dest = NavigationDestination.of(shop(CTX));
        // Player 100 blocks north of the point (-Z side), facing south (yaw 0): straight ahead.
        ChestNavigationReading ahead = ChestNavigationMath.evaluate(new PlayerPose(OW, -846.5, 70, -87.5, 0f), dest);
        assertEquals(ChestNavigationStatus.DIRECTIONAL, ahead.status());
        assertEquals(100.0, ahead.horizontalDistance(), 1e-9);
        assertEquals(0.0, ahead.relativeBearingDegrees(), 1e-9);

        ChestNavigationReading nether = ChestNavigationMath.evaluate(new PlayerPose("minecraft:the_nether", 0, 70, 0, 0f), dest);
        assertEquals(ChestNavigationStatus.WRONG_DIMENSION, nether.status());

        ChestNavigationReading arrived = ChestNavigationMath.evaluate(new PlayerPose(OW, -846.5, 70, 11.0, 0f), dest);
        assertEquals(ChestNavigationStatus.ARRIVED, arrived.status());
    }

    @Test
    @DisplayName("NavigationPoint.of rejects incomplete or out-of-world coordinates - no default position exists")
    void pointValidation() {
        StoragePosition ok = new StoragePosition(1, 64, 1);
        assertTrue(NavigationPoint.of("s", CTX, OW, ok, "x").isPresent());
        assertTrue(NavigationPoint.of(null, CTX, OW, ok, "x").isEmpty());
        assertTrue(NavigationPoint.of("s", " ", OW, ok, "x").isEmpty());
        assertTrue(NavigationPoint.of("s", CTX, null, ok, "x").isEmpty());
        assertTrue(NavigationPoint.of("s", CTX, "overworld", ok, "x").isEmpty(), "Dimension must be a namespaced id");
        assertTrue(NavigationPoint.of("s", CTX, OW, null, "x").isEmpty());
        assertTrue(NavigationPoint.of("s", CTX, OW, new StoragePosition(30_000_001, 64, 0), "x").isEmpty());
        assertTrue(NavigationPoint.of("s", CTX, OW, new StoragePosition(0, 5000, 0), "x").isEmpty());
        assertEquals("Sparad plats", NavigationPoint.of("s", CTX, OW, ok, "  ").orElseThrow().label());
        assertThrows(IllegalArgumentException.class, () -> new NavigationPoint("s", CTX, "bad", ok, "x"));
    }

    @Test
    @DisplayName("Every dimension Minecraft can name is supported - vanilla, datapack and modded - not just the overworld")
    void anyMinecraftDimensionIsSupported() {
        StoragePosition pos = new StoragePosition(3, 70, 4);
        for (String dim : List.of("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end",
                "gamezone:resource_world", "my_mod:deep/caves", "a.b-c_1:x/y.z-w_2")) {
            NavigationPoint point = NavigationPoint.of("s", CTX, dim, pos, "x").orElseThrow(() -> new AssertionError(dim));
            NavigationDestination dest = NavigationDestination.of(point);
            assertEquals(ChestNavigationStatus.ARRIVED,
                    ChestNavigationMath.evaluate(new PlayerPose(dim, 3.5, 70, 4.5, 0f), dest).status(), dim);
            assertEquals(ChestNavigationStatus.WRONG_DIMENSION,
                    ChestNavigationMath.evaluate(new PlayerPose("other:dim", 3.5, 70, 4.5, 0f), dest).status(), dim);
        }
        // Strings Minecraft's Identifier can never produce could never match the player's dimension.
        for (String bad : List.of("", " ", "overworld", "Minecraft:Overworld", "minecraft:", ":overworld", "minecraft:the nether", "mod:dim:extra")) {
            assertTrue(NavigationPoint.of("s", CTX, bad, pos, "x").isEmpty(), bad);
        }
    }

    @Test
    @DisplayName("Disconnect then reconnect to the SAME context never resurrects a point or chest target")
    void reconnectDoesNotResurrectTarget() {
        setUp();
        ChestNavigationManager nav = runtime.navigation();
        nav.startPoint(CTX, shop(CTX), 1L);
        runtime.onDisconnect();
        nav.onContextObserved(CTX); // what the JOIN handler does on reconnect
        assertFalse(nav.isActive());
        assertTrue(nav.resolveDestination(manager).isEmpty());

        nav.start(manager, CTX, chestId(), 2L);
        runtime.onDisconnect();
        nav.onContextObserved(CTX);
        assertFalse(nav.isActive());
        assertTrue(nav.resolveDestination(manager).isEmpty());
    }

    @Test
    @DisplayName("A refused start never disturbs the current target, of either kind")
    void refusedStartKeepsCurrentTarget() {
        setUp();
        ChestNavigationManager nav = runtime.navigation();
        nav.startPoint(CTX, shop(CTX), 1L);
        assertFalse(nav.start(manager, CTX, new StoredContainerId(CTX, OW, new StoragePosition(9, 9, 9), StorageKind.CHEST), 2L));
        assertTrue(nav.isPointTarget("marketwatch:n1"));

        nav.start(manager, CTX, chestId(), 3L);
        assertFalse(nav.startPoint(CTX, shop(OTHER), 4L));
        assertTrue(nav.isTarget(chestId()));
        assertTrue(nav.lastStopReason().isPresent());
        assertEquals(ChestNavigationManager.StopReason.REPLACED, nav.lastStopReason().orElseThrow(), "From the earlier point -> chest switch only");
    }

    @Test
    @DisplayName("Restarting the same point is not a replacement")
    void restartingSamePointIsNotReplaced() {
        setUp();
        ChestNavigationManager nav = runtime.navigation();
        nav.startPoint(CTX, shop(CTX), 1L);
        nav.startPoint(CTX, shop(CTX), 2L);
        assertTrue(nav.lastStopReason().isEmpty());
        assertEquals(2L, nav.startedAtMs());
    }
}
