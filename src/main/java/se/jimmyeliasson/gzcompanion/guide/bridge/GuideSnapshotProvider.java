package se.jimmyeliasson.gzcompanion.guide.bridge;

/**
 * Provider interface to extract client-visible player state for guide evaluation.
 */
@FunctionalInterface
public interface GuideSnapshotProvider {
    GuidePlayerSnapshot createSnapshot();
}