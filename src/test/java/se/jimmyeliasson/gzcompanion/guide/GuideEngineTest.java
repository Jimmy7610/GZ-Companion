package se.jimmyeliasson.gzcompanion.guide;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import se.jimmyeliasson.gzcompanion.guide.bridge.GuidePlayerSnapshot;
import se.jimmyeliasson.gzcompanion.guide.model.GuideCompletionSource;
import se.jimmyeliasson.gzcompanion.guide.model.GuideStep;
import se.jimmyeliasson.gzcompanion.guide.model.GuideStepState;
import se.jimmyeliasson.gzcompanion.guide.progress.GuideContext;
import se.jimmyeliasson.gzcompanion.guide.progress.JsonGuideProgressStore;

import java.nio.file.Path;
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
        engine.markStepCompleted(testContext, "movement_controls", true);

        assertEquals(GuideStepState.COMPLETED_MANUAL, engine.getStepState(testContext, "movement_controls"));
        assertEquals(GuideStepState.ACTIVE, engine.getStepState(testContext, "open_inventory"));

        GuideStep nextStep = engine.getActiveOrNextStep(testContext);
        assertNotNull(nextStep);
        assertEquals("open_inventory", nextStep.id());
    }

    @Test
    @DisplayName("Should automatically complete HAS_ITEM_TAG steps when inventory evidence is present")
    void testAutoCompletionWithInventoryEvidence() {
        // Complete prerequisites manually
        engine.markStepCompleted(testContext, "movement_controls", true);
        engine.markStepCompleted(testContext, "open_inventory", true);

        // Provide snapshot with 4 logs
        GuidePlayerSnapshot snapshotWithLogs = new GuidePlayerSnapshot(
                Map.of(), Map.of("minecraft:logs", 4), false, "fingerprint1", Map.of()
        );
        engine.setSnapshotProvider(() -> snapshotWithLogs);
        engine.evaluate(testContext);

        assertEquals(GuideStepState.COMPLETED_AUTO, engine.getStepState(testContext, "gather_wood"));
        assertEquals(GuideStepState.ACTIVE, engine.getStepState(testContext, "craft_planks"));
    }

    @Test
    @DisplayName("Should backfill superseded steps via progression inference (e.g. iron pickaxe satisfies stone pickaxe)")
    void testProgressionInferenceSupersededBy() {
        // Player somehow acquires an iron pickaxe directly (or completes craft_iron_pickaxe)
        engine.markStepCompleted(testContext, "craft_iron_pickaxe", true);
        engine.evaluate(testContext);

        // Steps that were supersededBy craft_iron_pickaxe should be marked SATISFIED_BY_LATER_PROGRESS
        GuideStepState stoneState = engine.getStepState(testContext, "craft_stone_pickaxe");
        assertEquals(GuideStepState.SATISFIED_BY_LATER_PROGRESS, stoneState);

        GuideStepState woodState = engine.getStepState(testContext, "craft_wooden_pickaxe");
        assertEquals(GuideStepState.SATISFIED_BY_LATER_PROGRESS, woodState);
    }

    @Test
    @DisplayName("Should calculate overall progress and chapter progress accurately")
    void testProgressCalculation() {
        GuideEngine.ProgressSummary initial = engine.getOverallProgress(testContext);
        assertEquals(0, initial.completedCount());
        assertEquals(22, initial.totalCount());
        assertEquals(0, initial.percent());

        engine.markStepCompleted(testContext, "movement_controls", true);
        engine.markStepCompleted(testContext, "open_inventory", true);

        GuideEngine.ProgressSummary afterTwo = engine.getOverallProgress(testContext);
        assertEquals(2, afterTwo.completedCount());
        assertEquals(22, afterTwo.totalCount());
        assertEquals((2 * 100) / 22, afterTwo.percent());

        GuideEngine.ProgressSummary ch1 = engine.getChapterProgress(testContext, "ch1_kom_igang");
        assertEquals(2, ch1.completedCount());
        assertEquals(3, ch1.totalCount());
    }

    @Test
    @DisplayName("Should resolve dynamic keybinding tokens to player keys or defaults")
    void testTokenResolution() {
        GuidePlayerSnapshot snapshot = new GuidePlayerSnapshot(
                Map.of(), Map.of(), false, "fp",
                Map.of("key.inventory", "I", "key.forward", "Up Arrow")
        );
        engine.setSnapshotProvider(() -> snapshot);
        engine.evaluate(testContext);

        String resolved = engine.resolveTokens("Tryck {key.inventory} för inventory och {key.forward} för att gå.");
        assertEquals("Tryck [I] för inventory och [Up Arrow] för att gå.", resolved);

        // Fallback default test
        String defaultResolved = engine.resolveTokens("Tryck {key.jump} för att hoppa.");
        assertEquals("Tryck [Mellanslag] för att hoppa.", defaultResolved);
    }
}
