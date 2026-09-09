package se.jimmyeliasson.gzcompanion.guide;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import se.jimmyeliasson.gzcompanion.guide.model.GuideCompletionSource;
import se.jimmyeliasson.gzcompanion.guide.progress.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GuideProgressStoreTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Should save and reload progress with atomic persistence")
    void testSaveAndLoad() {
        Path storePath = tempDir.resolve("guide-progress.json");
        JsonGuideProgressStore store = new JsonGuideProgressStore(storePath);

        GuideContext ctx = new GuideContext("player-uuid-1", "singleplayer:world1");
        StepCompletionRecord rec = new StepCompletionRecord("gather_wood", GuideCompletionSource.INVENTORY_EVIDENCE, 12345L);
        ContextProgress ctxProg = new ContextProgress("gather_wood", Map.of("gather_wood", rec));
        GuideProgressData data = new GuideProgressData(1, Map.of(ctx.getStorageKey(), ctxProg));

        store.save(data);

        assertTrue(Files.exists(storePath));

        GuideProgressData reloaded = store.load();
        ContextProgress loadedCtx = reloaded.getContext(ctx.getStorageKey());
        assertTrue(loadedCtx.isStepCompleted("gather_wood"));
        assertEquals(GuideCompletionSource.INVENTORY_EVIDENCE, loadedCtx.getCompletionRecord("gather_wood").source());
    }

    @Test
    @DisplayName("Should isolate progress between different players and contexts")
    void testContextIsolation() {
        Path storePath = tempDir.resolve("guide-progress.json");
        JsonGuideProgressStore store = new JsonGuideProgressStore(storePath);

        GuideContext ctx1 = new GuideContext("player-1", "singleplayer:world1");
        GuideContext ctx2 = new GuideContext("player-2", "server:play.gamezonemc.se");

        StepCompletionRecord rec1 = new StepCompletionRecord("step_1", GuideCompletionSource.MANUAL, 100L);
        ContextProgress prog1 = new ContextProgress("step_1", Map.of("step_1", rec1));

        GuideProgressData data = new GuideProgressData(1, Map.of(ctx1.getStorageKey(), prog1));
        store.save(data);

        GuideProgressData loaded = store.load();
        assertTrue(loaded.getContext(ctx1.getStorageKey()).isStepCompleted("step_1"));
        assertFalse(loaded.getContext(ctx2.getStorageKey()).isStepCompleted("step_1"));
    }

    @Test
    @DisplayName("Should recover gracefully from malformed or corrupted progress file")
    void testCorruptionRecovery() throws IOException {
        Path storePath = tempDir.resolve("guide-progress.json");
        Files.writeString(storePath, "{ invalid json content !!! @#$");

        JsonGuideProgressStore store = new JsonGuideProgressStore(storePath);
        GuideProgressData loaded = store.load();

        assertNotNull(loaded);
        assertTrue(loaded.contexts().isEmpty());

        // Check that a corrupt backup file was preserved
        boolean backupFound = Files.list(tempDir)
                .anyMatch(p -> p.getFileName().toString().contains(".corrupt."));
        assertTrue(backupFound, "Corrupt file should be backed up with timestamp suffix");
    }
}
