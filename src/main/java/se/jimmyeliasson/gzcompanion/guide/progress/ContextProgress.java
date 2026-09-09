package se.jimmyeliasson.gzcompanion.guide.progress;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Saved guide progress for a specific context.
 */
public record ContextProgress(
    String selectedStepId,
    Map<String, StepCompletionRecord> completedSteps
) {
    public ContextProgress {
        completedSteps = completedSteps != null ? Collections.unmodifiableMap(new HashMap<>(completedSteps)) : Map.of();
    }

    public boolean isStepCompleted(String stepId) {
        return completedSteps.containsKey(stepId);
    }

    public StepCompletionRecord getCompletionRecord(String stepId) {
        return completedSteps.get(stepId);
    }
}