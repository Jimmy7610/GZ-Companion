package se.jimmyeliasson.gzcompanion.guide.progress;

/**
 * Interface for loading and persisting guide progression data.
 *
 * <p>{@link #save} and {@link #resetContext} may throw {@link GuideProgressPersistenceException}
 * (unchecked) if the write genuinely fails - a real implementation must never swallow a write
 * failure and return normally as if it had succeeded (see {@code JsonGuideProgressStore} and
 * docs/PERFORMANCE-AUDIT-ALPHA4.md's "2026-09-13 correctness follow-up" section). {@code
 * AsyncGuideProgressStore} is the one production caller responsible for catching this.
 */
public interface GuideProgressStore {
    GuideProgressData load();
    void save(GuideProgressData data);
    void resetContext(GuideContext context);
}