package se.jimmyeliasson.gzcompanion.guide.progress;

import se.jimmyeliasson.gzcompanion.guide.model.GuideCompletionSource;

/**
 * Record of a completed guide step including why and when it was completed.
 */
public record StepCompletionRecord(
    String stepId,
    GuideCompletionSource source,
    long completedAtMs
) {
    public StepCompletionRecord {
        if (source == null) {
            source = GuideCompletionSource.MANUAL;
        }
    }
}