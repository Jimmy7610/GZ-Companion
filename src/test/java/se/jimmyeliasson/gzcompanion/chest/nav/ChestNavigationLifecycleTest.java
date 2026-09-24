package se.jimmyeliasson.gzcompanion.chest.nav;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import se.jimmyeliasson.gzcompanion.chest.ChestCaptureFeedback;
import se.jimmyeliasson.gzcompanion.chest.ChestManager;
import se.jimmyeliasson.gzcompanion.chest.KistorRuntime;
import se.jimmyeliasson.gzcompanion.chest.model.ChestCaptureEvent;
import se.jimmyeliasson.gzcompanion.chest.model.ChestManagerStatus;
import se.jimmyeliasson.gzcompanion.chest.model.ChestSlotEntry;
import se.jimmyeliasson.gzcompanion.chest.model.StorageKind;
import se.jimmyeliasson.gzcompanion.chest.model.StoragePosition;
import se.jimmyeliasson.gzcompanion.chest.model.StorageShape;
import se.jimmyeliasson.gzcompanion.chest.model.StoredContainerId;
import se.jimmyeliasson.gzcompanion.chest.storage.JsonChestIndexStore;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ChestNavigationLifecycleTest {
    @TempDir
    Path tempDir;

    static final String CTX = "p@@server:play.gamezonemc.se";
    static final String OTHER = "p@@singleplayer:world";
    static final String OW = "minecraft:overworld";
    static final StoragePosition TARGET_POS = new StoragePosition(100, 64, 100);
    static final StoragePosition OTHER_POS = new StoragePosition(50, 64, 50);

    private ChestManager manager;
    private KistorRuntime runtime;

    private void setUp() {
        manager = new ChestManager(new JsonChestIndexStore(tempDir.resolve("chest-index.json")));
        manager.initialize();
        runtime = new KistorRuntime(manager);
        open(TARGET_POS, 1000L);
        open(OTHER_POS, 2000L);
        manager.setLabel(CTX, id(TARGET_POS), "Materiallager");
    }

    private Optional<ChestCaptureEvent> open(StoragePosition pos, long at) {
        manager.recordPendingInteraction(CTX, OW, StorageKind.CHEST, pos, null, StorageShape.SINGLE, at);
        manager.tryBeginCapture(CTX, OW, EnumSet.of(StorageKind.CHEST), at + 10);
        manager.updateCaptureSlots(List.of(new ChestSlotEntry(0, "minecraft:stone", (int) (at % 60) + 1)), at + 20);
        return manager.endCapture(at + 30);
    }

    private static StoredContainerId id(StoragePosition pos) {
        return new StoredContainerId(CTX, OW, pos, StorageKind.CHEST);
    }

    @Test
    @DisplayName("Start targets one known storage by stable id; stop ends it")
    void startAndStop() {
        setUp();
        ChestNavigationManager nav = runtime.navigation();
        assertFalse(nav.isActive());
        assertTrue(nav.start(manager, CTX, id(TARGET_POS), 5000L));
        assertTrue(nav.isActive());
        assertTrue(nav.isTarget(id(TARGET_POS)));
        assertEquals("Materiallager", nav.resolveTarget(manager).orElseThrow().displayTitle());

        nav.stop(ChestNavigationManager.StopReason.USER);
        assertFalse(nav.isActive());
        assertEquals(ChestNavigationManager.StopReason.USER, nav.lastStopReason().orElseThrow());
    }

    @Test
    @DisplayName("Only an already-indexed storage in the CURRENT context can become a target")
    void cannotTargetUnknownStorage() {
        setUp();
        ChestNavigationManager nav = runtime.navigation();
        assertFalse(nav.start(manager, CTX, id(new StoragePosition(1, 2, 3)), 1L), "Never navigate to a storage that was never opened");
        assertFalse(nav.start(manager, OTHER, id(TARGET_POS), 1L), "A target from another context is refused");
        assertFalse(nav.isActive());
    }

    @Test
    @DisplayName("Forgetting the target stops navigation safely")
    void forgottenTarget() {
        setUp();
        ChestNavigationManager nav = runtime.navigation();
        nav.start(manager, CTX, id(TARGET_POS), 1L);
        manager.forgetContainer(CTX, id(TARGET_POS));
        assertTrue(nav.resolveTarget(manager).isEmpty());
        assertFalse(nav.isActive());
        assertEquals(ChestNavigationManager.StopReason.TARGET_MISSING, nav.lastStopReason().orElseThrow());
    }

    @Test
    @DisplayName("A world/server context change stops navigation - never shown in another context")
    void contextChange() {
        setUp();
        ChestNavigationManager nav = runtime.navigation();
        nav.start(manager, CTX, id(TARGET_POS), 1L);
        nav.onContextObserved(CTX);
        assertTrue(nav.isActive(), "Same context keeps navigating");
        nav.onContextObserved(OTHER);
        assertFalse(nav.isActive());
        assertEquals(ChestNavigationManager.StopReason.CONTEXT_CHANGED, nav.lastStopReason().orElseThrow());
    }

    @Test
    @DisplayName("Disconnect stops navigation and drops the session-only material request")
    void disconnect() {
        setUp();
        runtime.navigation().start(manager, CTX, id(TARGET_POS), 1L);
        runtime.openMaterialRequest(CTX, new se.jimmyeliasson.gzcompanion.chest.material.ChestMaterialRequest("x", "y", List.of()));
        runtime.onDisconnect();
        assertFalse(runtime.navigation().isActive());
        assertTrue(runtime.materialRequest(CTX).isEmpty());
    }

    @Test
    @DisplayName("Resetting the Kistor index stops navigation toward storage in that context")
    void reset() {
        setUp();
        runtime.navigation().start(manager, CTX, id(TARGET_POS), 1L);
        manager.clearContext(CTX);
        runtime.onChestIndexCleared(CTX);
        assertFalse(runtime.navigation().isActive());
    }

    @Test
    @DisplayName("Reset detected lazily too: resolveTarget stops navigation even without the explicit hook")
    void resetDetectedLazily() {
        setUp();
        runtime.navigation().start(manager, CTX, id(TARGET_POS), 1L);
        manager.clearContext(CTX);
        assertTrue(runtime.navigation().resolveTarget(manager).isEmpty());
        assertFalse(runtime.navigation().isActive());
    }

    @Test
    @DisplayName("Opening the exact target (legitimate finalized capture) ends navigation with a 'hittad' notification")
    void openingExactTargetEndsNavigation() {
        setUp();
        runtime.navigation().start(manager, CTX, id(TARGET_POS), 1L);
        ChestCaptureEvent event = open(TARGET_POS, 9000L).orElseThrow();
        Optional<ChestCaptureFeedback.Message> message = runtime.onCaptureFinalized(event);
        assertFalse(runtime.navigation().isActive());
        assertEquals(ChestNavigationManager.StopReason.FOUND, runtime.navigation().lastStopReason().orElseThrow());
        assertEquals("✓ Materiallager hittad", message.orElseThrow().title());
    }

    @Test
    @DisplayName("Opening a DIFFERENT chest does not end navigation")
    void openingDifferentChestKeepsNavigating() {
        setUp();
        runtime.navigation().start(manager, CTX, id(TARGET_POS), 1L);
        ChestCaptureEvent event = open(OTHER_POS, 9000L).orElseThrow();
        runtime.onCaptureFinalized(event);
        assertTrue(runtime.navigation().isActive());
        assertTrue(runtime.navigation().isTarget(id(TARGET_POS)));
    }

    @Test
    @DisplayName("The same coordinates in another dimension are a different storage and don't end navigation")
    void sameCoordsOtherDimensionIsDifferent() {
        setUp();
        runtime.navigation().start(manager, CTX, id(TARGET_POS), 1L);
        StoredContainerId netherTwin = new StoredContainerId(CTX, "minecraft:the_nether", TARGET_POS, StorageKind.CHEST);
        assertFalse(runtime.navigation().onStorageLegitimatelyOpened(netherTwin));
        assertTrue(runtime.navigation().isActive());
    }

    @Test
    @DisplayName("An incompatible/unavailable Chest Manager stops navigation")
    void managerUnavailable() throws Exception {
        setUp();
        runtime.navigation().start(manager, CTX, id(TARGET_POS), 1L);
        Path future = tempDir.resolve("future.json");
        Files.writeString(future, "{ \"schemaVersion\": 99, \"contexts\": {} }");
        ChestManager incompatible = new ChestManager(new JsonChestIndexStore(future));
        incompatible.initialize();
        assertEquals(ChestManagerStatus.INCOMPATIBLE, incompatible.getStatus());
        assertTrue(runtime.navigation().resolveTarget(incompatible).isEmpty());
        assertFalse(runtime.navigation().isActive());
        assertEquals(ChestNavigationManager.StopReason.MANAGER_UNAVAILABLE, runtime.navigation().lastStopReason().orElseThrow());
    }

    @Test
    @DisplayName("Diagnostics only say whether navigation is active - never the target or its position")
    void diagnosticsAreRedacted() {
        setUp();
        assertEquals("Kistor-navigering: inaktiv", runtime.diagnosticsNavigationLine());
        runtime.navigation().start(manager, CTX, id(TARGET_POS), 1L);
        String line = runtime.diagnosticsNavigationLine();
        assertEquals("Kistor-navigering: aktiv", line);
        assertFalse(line.contains("100"));
        assertFalse(line.contains("Materiallager"));
    }
}
