package se.jimmyeliasson.gzcompanion.guide.model;

/**
 * Logical chapter grouping of guide steps.
 */
public record GuideChapter(
    String id,
    String title,
    int order
) {}