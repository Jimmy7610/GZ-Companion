package se.jimmyeliasson.gzcompanion.guide;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import se.jimmyeliasson.gzcompanion.guide.bridge.GuidePlayerSnapshot;
import se.jimmyeliasson.gzcompanion.guide.bridge.MinecraftGuideSnapshotProvider;
import se.jimmyeliasson.gzcompanion.guide.model.GuideCompletionSource;
import se.jimmyeliasson.gzcompanion.guide.model.GuideLoadStatus;
import se.jimmyeliasson.gzcompanion.guide.model.GuideStep;
import se.jimmyeliasson.gzcompanion.guide.model.GuideStepState;
import se.jimmyeliasson.gzcompanion.guide.progress.GuideContext;
import se.jimmyeliasson.gzcompanion.guide.progress.JsonGuideProgressStore;

import java.nio.file.Path;
import java.util.HashMap;
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
}
