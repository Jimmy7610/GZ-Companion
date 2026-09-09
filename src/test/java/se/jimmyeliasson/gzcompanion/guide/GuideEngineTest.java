package se.jimmyeliasson.gzcompanion.guide;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import se.jimmyeliasson.gzcompanion.guide.bridge.GuidePlayerSnapshot;
import se.jimmyeliasson.gzcompanion.guide.bridge.MinecraftGuideSnapshotProvider;
import se.jimmyeliasson.gzcompanion.guide.model.GuideCompletionSource;
import se.jimmyeliasson.gzcompanion.guide.model.GuideLoadStatus;
import se.jimmyeliasson.gzcompanion.guide.model.GuideManifest;
import se.jimmyeliasson.gzcompanion.guide.model.GuideStep;
import se.jimmyeliasson.gzcompanion.guide.model.GuideStepState;
import se.jimmyeliasson.gzcompanion.guide.progress.GuideContext;
import se.jimmyeliasson.gzcompanion.guide.progress.JsonGuideProgressStore;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GuideEngineTest {

    @TempDir
    Path tempDir;

    private GuideEngine engine;
    private final GuideContext testContext = new GuideContext("test-uuid", "singleplayer:test");

    @BeforeEach
    void setUp() {
        Path storePath = tempDir.resolve("guide-progress.json");
        JsonGuideProgressStore store = new JsonGuideProgressStore(storePath);
        this.engine = new GuideEngine(new GuideLoader(), store, () -> GuidePlayerSnapshot.EMPTY);
        this.engine.initialize();
    }

    @Test
    @DisplayName("Should evaluate initial state as movement_controls ACTIVE and subsequent steps LOCKED")
    void testInitialProgressionState() {
        assertEquals(GuideLoadStatus.LOADED, engine.getLoadStatus());

        GuideStep activeStep = engine.getActiveOrNextStep(testContext);
        assertNotNull(activeStep);
        assertEquals("movement_controls", activeStep.id());

        assertEquals(GuideStepState.ACTIVE, engine.getStepState(testContext, "movement_controls"));
        assertEquals(GuideStepState.LOCKED, engine.getStepState(testContext, "open_inventory"));
        assertEquals(GuideStepState.LOCKED, engine.getStepState(testContext, "gather_wood"));
    }

    @Test
    @DisplayName("Should unlock and transition states when manual step is completed")
    void testManualCompletionAndUnlock() {
        boolean success = engine.markStepCompleted(testContext, "movement_controls", true);
        assertTrue(success);

        assertEquals(GuideStepState.COMPLETED_MANUAL, engine.getStepState(testContext, "movement_controls"));
        assertEquals(GuideStepState.ACTIVE, engine.getStepState(testContext, "open_inventory"));

        GuideStep nextStep = engine.getActiveOrNextStep(testContext);
        assertNotNull(nextStep);
        assertEquals("open_inventory", nextStep.id());
    }

    @Test
    @DisplayName("Should enforce manualCompletionAllowed, locked state, and reject unknown IDs")
    void testManualCompletionEnforcement() {
        // Unknown step ID
        assertFalse(engine.markStepCompleted(testContext, "non_existent_step_123", true));

        // Locked step (open_inventory has prerequisite movement_controls)
        assertFalse(engine.markStepCompleted(testContext, "open_inventory", true));

        // Complete movement_controls
        assertTrue(engine.markStepCompleted(testContext, "movement_controls", true));

        // Now open_inventory is unlocked and allows manual completion
        assertTrue(engine.markStepCompleted(testContext, "open_inventory", true));
    }

    @Test
    @DisplayName("Should automatically complete HAS_ITEM_TAG steps when inventory evidence is present")
    void testAutoCompletionWithInventoryEvidence() {
        // Complete prerequisites manually
        engine.markStepCompleted(testContext, "movement_controls", true);
        engine.markStepCompleted(testContext, "open_inventory", true);

        // Provide snapshot with 4 logs
        GuidePlayerSnapshot snapshotWithLogs = new GuidePlayerSnapshot(
                Map.of(), Map.of("minecraft:logs", 4), false, "fingerprint_logs_4", Map.of()
        );
        engine.setSnapshotProvider(() -> snapshotWithLogs);
        boolean changed = engine.evaluate(testContext);
        assertTrue(changed);

        assertEquals(GuideStepState.COMPLETED_AUTO, engine.getStepState(testContext, "gather_wood"));
        assertEquals(GuideStepState.ACTIVE, engine.getStepState(testContext, "craft_planks"));
    }

    @Test
    @DisplayName("Should backfill superseded steps via progression inference (e.g. iron pickaxe satisfies stone pickaxe)")
    void testProgressionInferenceSupersededBy() {
        // Player obtains an iron pickaxe (inventory evidence)
        GuidePlayerSnapshot snapshotWithIronPick = new GuidePlayerSnapshot(
                Map.of("minecraft:iron_pickaxe", 1), Map.of(), false, "fingerprint_iron_pick", Map.of()
        );
        engine.setSnapshotProvider(() -> snapshotWithIronPick);
        boolean changed = engine.evaluate(testContext, true);
        assertTrue(changed);

        // Steps that were supersededBy craft_iron_pickaxe should be marked SATISFIED_BY_LATER_PROGRESS
        GuideStepState stoneState = engine.getStepState(testContext, "craft_stone_pickaxe");
        assertEquals(GuideStepState.SATISFIED_BY_LATER_PROGRESS, stoneState);

        GuideStepState woodState = engine.getStepState(testContext, "craft_wooden_pickaxe");
        assertEquals(GuideStepState.SATISFIED_BY_LATER_PROGRESS, woodState);
    }

    @Test
    @DisplayName("Should calculate overall progress using required steps only, excluding optional steps from blocking 100%")
    void testOptionalStepExclusionFromRequiredPercent() {
        GuideEngine.ProgressSummary initial = engine.getOverallProgress(testContext);
        assertEquals(0, initial.completedCount());
        assertEquals(21, initial.totalCount(), "Total required steps should be 21 (22 total - 1 optional craft_bed)");
        assertEquals(0, initial.percent());

        // Complete all 21 required steps, leaving craft_bed incomplete
        for (GuideStep step : engine.getActiveGuide().steps()) {
            if (!step.optional()) {
                engine.markStepCompleted(testContext, step.id(), true);
            }
        }

        // Even though craft_bed is not completed, required progress should reach 100%
        assertFalse(engine.isStepCompleted(testContext, "craft_bed"));
        GuideEngine.ProgressSummary completedSummary = engine.getOverallProgress(testContext);
        assertEquals(21, completedSummary.completedCount());
        assertEquals(21, completedSummary.totalCount());
        assertEquals(100, completedSummary.percent());
    }

    @Test
    @DisplayName("Should skip redundant evaluation when snapshot fingerprint and context are unchanged")
    void testSnapshotFingerprintOptimization() {
        GuidePlayerSnapshot snapshot1 = new GuidePlayerSnapshot(
                Map.of("minecraft:stick", 2), Map.of(), false, "fingerprint_static_1", Map.of()
        );
        engine.setSnapshotProvider(() -> snapshot1);

        // First evaluation: triggers evaluation
        boolean firstEval = engine.evaluate(testContext);
        // Second evaluation with exact same snapshot & context: skips evaluation
        boolean secondEval = engine.evaluate(testContext);
        assertFalse(secondEval, "Second evaluation with identical fingerprint and context must be skipped");

        // Third evaluation with changed fingerprint: triggers evaluation
        GuidePlayerSnapshot snapshot2 = new GuidePlayerSnapshot(
                Map.of("minecraft:stick", 4), Map.of(), false, "fingerprint_static_2", Map.of()
        );
        engine.setSnapshotProvider(() -> snapshot2);
        boolean thirdEval = engine.evaluate(testContext);
        // Fourth evaluation with different context: triggers evaluation even with same fingerprint
        GuideContext otherContext = new GuideContext("test-uuid-2", "server:play.gamezonemc.se");
        boolean fourthEval = engine.evaluate(otherContext);
        // Must evaluate for new context
        assertNotNull(engine.getStepState(otherContext, "movement_controls"));
    }

    @Test
    @DisplayName("Should resolve dynamic keybinding tokens using production unbraced token format")
    void testProductionShapedKeyTokensResolution() {
        // Exact format produced by MinecraftGuideSnapshotProvider.extractKeyTokens
        Map<String, String> prodKeyTokens = new HashMap<>();
        prodKeyTokens.put("key.forward", "W");
        prodKeyTokens.put("key.back", "S");
        prodKeyTokens.put("key.left", "A");
        prodKeyTokens.put("key.right", "D");
        prodKeyTokens.put("key.jump", "Mellanslag");
        prodKeyTokens.put("key.inventory", "I"); // Custom rebound inventory key
        prodKeyTokens.put("key.attack", "Vänsterklick");
        prodKeyTokens.put("key.use", "Högerklick");
        prodKeyTokens.put("key.sneak", "Skift");
        prodKeyTokens.put("key.sprint", "Ctrl");
        prodKeyTokens.put("key.drop", "Q");
        prodKeyTokens.put("key.swapOffhand", "G"); // Custom rebound offhand key

        GuidePlayerSnapshot snapshot = new GuidePlayerSnapshot(
                Map.of(), Map.of(), false, "fp_keys", prodKeyTokens
        );
        engine.setSnapshotProvider(() -> snapshot);
        engine.evaluate(testContext);

        String inventoryText = engine.resolveTokens("Tryck {key.inventory} för att öppna ryggsäcken.");
        assertEquals("Tryck [I] för att öppna ryggsäcken.", inventoryText);

        String offhandText = engine.resolveTokens("Tryck {key.swapOffhand} för att byta hand.");
        assertEquals("Tryck [G] för att byta hand.", offhandText);

        // Fallback default resolution test when keyTokens map is empty
        GuidePlayerSnapshot emptyKeysSnapshot = new GuidePlayerSnapshot(
                Map.of(), Map.of(), false, "fp_empty_keys", Map.of()
        );
        engine.setSnapshotProvider(() -> emptyKeysSnapshot);
        engine.evaluate(testContext);

        String defaultInventoryText = engine.resolveTokens("Tryck {key.inventory} för att öppna.");
        assertEquals("Tryck [E] för att öppna.", defaultInventoryText);

        String defaultOffhandText = engine.resolveTokens("Tryck {key.swapOffhand} för att byta.");
        assertEquals("Tryck [F] för att byta.", defaultOffhandText);
    }

    @Test
    @DisplayName("Should resolve transitive supersession chains (A <- B <- C) in a single evaluate() call")
    void testTransitiveFixedPointSupersession() {
        // craft_iron_pickaxe supersedes craft_stone_pickaxe, which in turn supersedes craft_planks/craft_crafting_table
        // Providing iron_pickaxe snapshot must transitively satisfy the entire chain in ONE evaluate() call
        GuidePlayerSnapshot snapshotWithIronPick = new GuidePlayerSnapshot(
                Map.of("minecraft:iron_pickaxe", 1), Map.of(), false, "fp_iron_pick_transitive", Map.of()
        );
        engine.setSnapshotProvider(() -> snapshotWithIronPick);
        boolean changed = engine.evaluate(testContext, true);
        assertTrue(changed);

        assertEquals(GuideStepState.COMPLETED_AUTO, engine.getStepState(testContext, "craft_iron_pickaxe"));
        assertEquals(GuideStepState.SATISFIED_BY_LATER_PROGRESS, engine.getStepState(testContext, "craft_stone_pickaxe"));
        assertEquals(GuideStepState.SATISFIED_BY_LATER_PROGRESS, engine.getStepState(testContext, "craft_wooden_pickaxe"));
        assertEquals(GuideStepState.SATISFIED_BY_LATER_PROGRESS, engine.getStepState(testContext, "craft_crafting_table"));
        assertEquals(GuideStepState.SATISFIED_BY_LATER_PROGRESS, engine.getStepState(testContext, "craft_planks"));
        assertEquals(GuideStepState.SATISFIED_BY_LATER_PROGRESS, engine.getStepState(testContext, "gather_wood"));
    }

    @Test
    @DisplayName("Should correctly distinguish required completion from optional recommendations")
    void testRequiredVsOptionalSemantics() {
        assertFalse(engine.isRequiredGuideComplete(testContext));
        assertEquals("movement_controls", engine.getActiveOrNextStep(testContext).id());
        assertEquals("movement_controls", engine.getNextRequiredStep(testContext).id());

        // Complete all 21 required steps
        for (GuideStep step : engine.getActiveGuide().steps()) {
            if (!step.optional()) {
                engine.markStepCompleted(testContext, step.id(), true);
            }
        }

        assertTrue(engine.isRequiredGuideComplete(testContext), "Required guide must be complete");
        assertNull(engine.getNextRequiredStep(testContext), "Next required step must be null when all required steps are complete");
        assertNull(engine.getActiveOrNextStep(testContext), "Active/Next step must be null when all required steps are complete");

        // Optional step craft_bed remains available and separate
        GuideStep optStep = engine.getNextOptionalStep(testContext);
        assertNotNull(optStep, "Optional step craft_bed must be available");
        assertEquals("craft_bed", optStep.id());
        assertEquals(1, engine.getIncompleteOptionalStepsCount(testContext));

        // Player can still complete the optional step after required completion
        boolean markedOpt = engine.markStepCompleted(testContext, "craft_bed", true);
        assertTrue(markedOpt);
        assertTrue(engine.isStepCompleted(testContext, "craft_bed"));
        assertEquals(0, engine.getIncompleteOptionalStepsCount(testContext));
        assertNull(engine.getNextOptionalStep(testContext));
    }

    @Test
    @DisplayName("Should enter INCOMPATIBLE state when manifest does not support current Minecraft version")
    void testIncompatibleMinecraftVersion() {
        GuideLoader incompatibleLoader = new GuideLoader() {
            @Override
            public LoadResult loadBundled() {
                GuideLoader realLoader = new GuideLoader();
                LoadResult real = realLoader.loadBundled();
                GuideManifest incompatibleManifest = new GuideManifest(
                        real.manifest().schemaVersion(),
                        real.manifest().contentVersion(),
                        real.manifest().locale(),
                        List.of("1.19.2", "1.20.1"), // Missing 26.1.2
                        real.manifest().guides()
                );
                return new LoadResult(incompatibleManifest, real.guides(), real.warnings(), real.errors());
            }
        };

        JsonGuideProgressStore store = new JsonGuideProgressStore(tempDir.resolve("incompatible-progress.json"));
        GuideEngine incompEngine = new GuideEngine(incompatibleLoader, store, () -> GuidePlayerSnapshot.EMPTY);
        incompEngine.initialize();

        assertEquals(GuideLoadStatus.INCOMPATIBLE, incompEngine.getLoadStatus());
        assertFalse(incompEngine.isGuideLoaded());
    }

    @Test
    @DisplayName("Should strictly reject evaluation and mutations when loadStatus is INCOMPATIBLE")
    void testEvaluateRejectedWhenIncompatible() {
        GuideLoader incompatibleLoader = new GuideLoader() {
            @Override
            public LoadResult loadBundled() {
                GuideLoader realLoader = new GuideLoader();
                LoadResult real = realLoader.loadBundled();
                GuideManifest incompatibleManifest = new GuideManifest(
                        real.manifest().schemaVersion(),
                        real.manifest().contentVersion(),
                        real.manifest().locale(),
                        List.of("1.20.1"),
                        real.manifest().guides()
                );
                return new LoadResult(incompatibleManifest, real.guides(), real.warnings(), real.errors());
            }
        };

        Path progressPath = tempDir.resolve("incompatible-store.json");
        JsonGuideProgressStore store = new JsonGuideProgressStore(progressPath);
        boolean[] snapshotCalled = new boolean[]{false};
        GuideEngine incompEngine = new GuideEngine(incompatibleLoader, store, () -> {
            snapshotCalled[0] = true;
            return GuidePlayerSnapshot.EMPTY;
        });
        incompEngine.initialize();
        assertEquals(GuideLoadStatus.INCOMPATIBLE, incompEngine.getLoadStatus());

        // Evaluation must be rejected immediately without acquiring snapshot
        boolean evalResult = incompEngine.evaluate(testContext, true);
        assertFalse(evalResult, "Evaluate must return false when INCOMPATIBLE");
        assertFalse(snapshotCalled[0], "Snapshot acquisition must not happen when INCOMPATIBLE");

        // Mutations must be rejected
        assertFalse(incompEngine.markStepCompleted(testContext, "movement_controls", true));
        assertFalse(incompEngine.undoStepCompletion(testContext, "movement_controls"));
        assertFalse(java.nio.file.Files.exists(progressPath), "No progress file should be created or mutated");
    }

    @Test
    @DisplayName("Should strictly reject evaluation and mutations when loadStatus is ERROR")
    void testEvaluateRejectedWhenError() {
        GuideLoader errorLoader = new GuideLoader() {
            @Override
            public LoadResult loadBundled() {
                return new LoadResult(null, List.of(), List.of(), List.of("Critical loader failure"));
            }
        };

        Path progressPath = tempDir.resolve("error-store.json");
        JsonGuideProgressStore store = new JsonGuideProgressStore(progressPath);
        boolean[] snapshotCalled = new boolean[]{false};
        GuideEngine errorEngine = new GuideEngine(errorLoader, store, () -> {
            snapshotCalled[0] = true;
            return GuidePlayerSnapshot.EMPTY;
        });
        errorEngine.initialize();
        assertEquals(GuideLoadStatus.ERROR, errorEngine.getLoadStatus());
        assertFalse(errorEngine.isGuideLoaded());

        boolean evalResult = errorEngine.evaluate(testContext, true);
        assertFalse(evalResult, "Evaluate must return false when ERROR");
        assertFalse(snapshotCalled[0], "Snapshot acquisition must not happen when ERROR");
        assertFalse(errorEngine.markStepCompleted(testContext, "movement_controls", true));
        assertFalse(java.nio.file.Files.exists(progressPath));
    }

    @Test
    @DisplayName("Should strictly reject evaluation and mutations when loadStatus is UNAVAILABLE")
    void testEvaluateRejectedWhenUnavailable() {
        Path progressPath = tempDir.resolve("unavail-store.json");
        JsonGuideProgressStore store = new JsonGuideProgressStore(progressPath);
        // Do not call initialize() -> status remains UNAVAILABLE
        GuideEngine unavailEngine = new GuideEngine(new GuideLoader(), store, () -> GuidePlayerSnapshot.EMPTY);
        assertEquals(GuideLoadStatus.UNAVAILABLE, unavailEngine.getLoadStatus());
        assertFalse(unavailEngine.isGuideLoaded());

        assertFalse(unavailEngine.evaluate(testContext, true));
        assertFalse(unavailEngine.markStepCompleted(testContext, "movement_controls", true));
        assertFalse(java.nio.file.Files.exists(progressPath));
    }
}
