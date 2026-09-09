package se.jimmyeliasson.gzcompanion.guide.model;

import java.util.List;

/**
 * Complete immutable definition of a guide.
 */
public record GuideDefinition(
    int schemaVersion,
    String id,
    String title,
    String description,
    List<GuideChapter> chapters,
    List<GuideStep> steps
) {
    public GuideDefinition {
        if (chapters == null) chapters = List.of();
        if (steps == null) steps = List.of();
    }
}