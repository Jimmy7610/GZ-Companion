package se.jimmyeliasson.gzcompanion.ui.tabs;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import se.jimmyeliasson.gzcompanion.guide.GuideEngine;
import se.jimmyeliasson.gzcompanion.guide.GuideLoader;
import se.jimmyeliasson.gzcompanion.guide.bridge.GuidePlayerSnapshot;
import se.jimmyeliasson.gzcompanion.guide.model.GuideStep;
import se.jimmyeliasson.gzcompanion.guide.progress.JsonGuideProgressStore;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression coverage for the M2 Home fallback fix: the Home dashboard's objective checklist
 * must never substitute obsolete generic onboarding placeholder copy (e.g. "[ ] Lär dig
 * grunderna") for a real, specific active guide step.
 */
class HomeTabComponentTest {

    @TempDir
    Path tempDir;

    private GuideEngine newEngine() {
        return new GuideEngine(new GuideLoader(), new JsonGuideProgressStore(tempDir.resolve("guide-progress.json")), () -> GuidePlayerSnapshot.EMPTY);
    }

    private GuideStep stepWith(String description, String tip) {
        return new GuideStep("step1", "ch1", 1, "Test Step", "summary", description, "why", tip, null,
                List.of(), false, true, List.of(), List.of());
    }

    @Test
    @DisplayName("Uses the real tip when present")
    void testUsesRealTipWhenPresent() {
        GuideStep step = stepWith("A real description", "A real tip");
        String result = HomeTabComponent.selectObjectiveSecondaryText(step, newEngine());
        assertEquals("Tips: A real tip", result);
    }

    @Test
    @DisplayName("Falls back to the step's real description when the tip is missing, never to generic placeholder copy")
    void testFallsBackToDescriptionWhenTipMissing() {
        GuideStep step = stepWith("A real description", null);
        String result = HomeTabComponent.selectObjectiveSecondaryText(step, newEngine());
        assertEquals("A real description", result);
        assertNotEquals("[ ] Lär dig grunderna", result);
        assertFalse(result.contains("Lär dig grunderna"), "Must never contain the obsolete M1 placeholder checklist text");
    }

    @Test
    @DisplayName("Falls back to the step's real description when the tip is blank")
    void testFallsBackToDescriptionWhenTipBlank() {
        GuideStep step = stepWith("A real description", "   ");
        String result = HomeTabComponent.selectObjectiveSecondaryText(step, newEngine());
        assertEquals("A real description", result);
    }

    @Test
    @DisplayName("Shows nothing rather than inventing copy when neither tip nor description exist")
    void testShowsNothingWhenNoUsefulTextExists() {
        GuideStep step = stepWith(null, null);
        String result = HomeTabComponent.selectObjectiveSecondaryText(step, newEngine());
        assertEquals("", result);
    }
}
