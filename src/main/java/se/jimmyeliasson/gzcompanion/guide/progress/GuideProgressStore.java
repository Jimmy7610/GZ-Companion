package se.jimmyeliasson.gzcompanion.guide.progress;

/**
 * Interface for loading and persisting guide progression data.
 *
 * <p>{@link #save} may throw {@link GuideProgressPersistenceException} (unchecked) if the write
 * genuinely fails - a real implementation must never swallow a write failure and return normally
 * as if it had succeeded (see {@code JsonGuideProgressStore} and
 * docs/PERFORMANCE-AUDIT-ALPHA4.md's "2026-09-13 correctness follow-up" section). {@code
 * AsyncGuideProgressStore} is the one production caller responsible for catching this.
 *
 * <p>{@link #resetContext} returns {@code true} only if the reset was actually, durably performed.
 * {@code false} means the reset did NOT happen at all - the caller ({@code GuideEngine}) must treat
 * existing in-memory/disk progress as still authoritative and must NOT reload from disk in that
 * case (see docs/PERFORMANCE-AUDIT-ALPHA4.md's "2026-09-13 final persistence correctness pass"
 * section for the exact bug this contract change closes: an async decorator could previously give
 * up waiting for outstanding writes to finish and apply the reset anyway, letting a pre-reset write
 * land afterward and resurrect progress the player had just explicitly removed).
 */
public interface GuideProgressStore {
    GuideProgressData load();
    void save(GuideProgressData data);
    boolean resetContext(GuideContext context);
}