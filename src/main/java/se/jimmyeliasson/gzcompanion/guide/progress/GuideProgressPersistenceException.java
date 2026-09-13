package se.jimmyeliasson.gzcompanion.guide.progress;

/**
 * Thrown by a {@link GuideProgressStore} implementation when a write genuinely fails (e.g. a real
 * disk I/O error) - see {@code JsonGuideProgressStore#save}. An unchecked exception on purpose: it
 * must propagate cleanly through {@link GuideProgressStore}'s existing {@code void} method
 * signatures without a breaking interface change, and {@link AsyncGuideProgressStore} (the only
 * production caller of a raw {@code JsonGuideProgressStore}) always catches this specifically so it
 * never crashes the client - see docs/PERFORMANCE-AUDIT-ALPHA4.md's "2026-09-13 correctness
 * follow-up" section for why this replaced silently swallowing the failure.
 */
public final class GuideProgressPersistenceException extends RuntimeException {
    public GuideProgressPersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
