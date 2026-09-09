package se.jimmyeliasson.gzcompanion.guide.model;

import java.util.List;

/**
 * Result of evaluating a GuideCondition against a player snapshot.
 */
public record GuideConditionResult(
    boolean satisfied,
    int currentCount,
    int requiredCount,
    String description,
    List<GuideConditionResult> children
) {
    public GuideConditionResult {
        if (children == null) {
            children = List.of();
        }
    }
}