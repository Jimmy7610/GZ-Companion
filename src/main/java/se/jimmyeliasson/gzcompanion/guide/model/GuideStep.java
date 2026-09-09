package se.jimmyeliasson.gzcompanion.guide.model;

import java.util.List;

/**
 * Immutable definition of a single lesson / step in a guide.
 */
public record GuideStep(
    String id,
    String chapterId,
    int order,
    String title,
    String summary,
    String description,
    String why,
    String tip,
    String warning,
    List<String> prerequisites,
    boolean optional,
    boolean manualCompletionAllowed,
    List<GuideCondition> conditions,
    List<String> supersededBy
) {
    public GuideStep {
        if (prerequisites == null) prerequisites = List.of();
        if (conditions == null) conditions = List.of();
        if (supersededBy == null) supersededBy = List.of();
    }
}