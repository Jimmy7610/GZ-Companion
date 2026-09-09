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

    public static GuideConditionResult satisfied(int current, int required, String desc) {
        return new GuideConditionResult(true, current, required, desc, List.of());
    }

    public static GuideConditionResult unsatisfied(int current, int required, String desc) {
        return new GuideConditionResult(false, current, required, desc, List.of());
    }

    public static GuideConditionResult notMet() {
        return new GuideConditionResult(false, 0, 0, null, List.of());
    }
}