package se.jimmyeliasson.gzcompanion.guide.progress;

/**
 * Interface for loading and persisting guide progression data.
 */
public interface GuideProgressStore {
    GuideProgressData load();
    void save(GuideProgressData data);
    void resetContext(GuideContext context);
}