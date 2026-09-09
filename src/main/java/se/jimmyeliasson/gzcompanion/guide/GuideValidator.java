package se.jimmyeliasson.gzcompanion.guide;

import se.jimmyeliasson.gzcompanion.guide.model.GuideChapter;
import se.jimmyeliasson.gzcompanion.guide.model.GuideCondition;
import se.jimmyeliasson.gzcompanion.guide.model.GuideConditionType;
import se.jimmyeliasson.gzcompanion.guide.model.GuideDefinition;
import se.jimmyeliasson.gzcompanion.guide.model.GuideStep;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Validates GuideDefinition schemas, step ID uniqueness, prerequisite graph acyclicity,
 * supersededBy references, and condition structure.
 */
public final class GuideValidator {

    public record ValidationResult(boolean isValid, List<String> errors, List<String> warnings) {
        public static ValidationResult success(List<String> warnings) {
            return new ValidationResult(true, List.of(), List.copyOf(warnings));
        }

        public static ValidationResult failure(List<String> errors, List<String> warnings) {
            return new ValidationResult(false, List.copyOf(errors), List.copyOf(warnings));
        }
    }

    private GuideValidator() {
    }

    public static ValidationResult validate(GuideDefinition guide) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (guide == null) {
            errors.add("Guide definition is null");
            return ValidationResult.failure(errors, warnings);
        }

        if (guide.id() == null || guide.id().isBlank()) {
            errors.add("Guide ID is missing or blank");
        }

        if (guide.title() == null || guide.title().isBlank()) {
            errors.add("Guide title is missing or blank");
        }

        if (guide.chapters() == null || guide.chapters().isEmpty()) {
            errors.add("Guide must contain at least one chapter");
        }

        if (guide.steps() == null || guide.steps().isEmpty()) {
            errors.add("Guide must contain at least one step");
            return ValidationResult.failure(errors, warnings);
        }

        Set<String> chapterIds = new HashSet<>();
        for (GuideChapter chapter : guide.chapters()) {
            if (chapter.id() == null || chapter.id().isBlank()) {
                errors.add("Chapter has missing or blank ID");
                continue;
            }
            if (!chapterIds.add(chapter.id())) {
                errors.add("Duplicate chapter ID: '" + chapter.id() + "'");
            }
            if (chapter.title() == null || chapter.title().isBlank()) {
                errors.add("Chapter '" + chapter.id() + "' has missing or blank title");
            }
        }

        Set<String> stepIds = new HashSet<>();
        Map<String, GuideStep> stepMap = new HashMap<>();

        for (GuideStep step : guide.steps()) {
            if (step.id() == null || step.id().isBlank()) {
                errors.add("Step has missing or blank ID");
                continue;
            }
            if (!stepIds.add(step.id())) {
                errors.add("Duplicate step ID: '" + step.id() + "'");
            }
            stepMap.put(step.id(), step);

            if (step.chapterId() == null || !chapterIds.contains(step.chapterId())) {
                errors.add("Step '" + step.id() + "' references non-existent chapter '" + step.chapterId() + "'");
            }

            if (step.title() == null || step.title().isBlank()) {
                errors.add("Step '" + step.id() + "' has missing or blank title");
            }

            if (step.conditions() != null) {
                for (GuideCondition cond : step.conditions()) {
                    validateCondition(step.id(), cond, errors, warnings);
                }
            }
        }

        // Validate prerequisites & supersededBy references
        for (GuideStep step : stepMap.values()) {
            if (step.prerequisites() != null) {
                for (String prereqId : step.prerequisites()) {
                    if (!stepMap.containsKey(prereqId)) {
                        errors.add("Step '" + step.id() + "' has non-existent prerequisite: '" + prereqId + "'");
                    } else if (prereqId.equals(step.id())) {
                        errors.add("Step '" + step.id() + "' cannot depend on itself");
                    }
                }
            }

            if (step.supersededBy() != null) {
                for (String supersededId : step.supersededBy()) {
                    if (!stepMap.containsKey(supersededId)) {
                        errors.add("Step '" + step.id() + "' has non-existent supersededBy reference: '" + supersededId + "'");
                    } else if (supersededId.equals(step.id())) {
                        errors.add("Step '" + step.id() + "' cannot be superseded by itself");
                    }
                }
            }
        }

        // Check for circular dependencies in prerequisites
        detectCycles(stepMap, errors);

        if (errors.isEmpty()) {
            return ValidationResult.success(warnings);
        } else {
            return ValidationResult.failure(errors, warnings);
        }
    }

    private static void validateCondition(String stepId, GuideCondition condition, List<String> errors, List<String> warnings) {
        if (condition == null) {
            errors.add("Step '" + stepId + "' has null condition");
            return;
        }

        GuideConditionType type = condition.type();
        if (type == null) {
            errors.add("Step '" + stepId + "' condition has null type");
            return;
        }

        switch (type) {
            case MANUAL -> {
                // No requirements
            }
            case HAS_ITEM -> {
                if (condition.itemId() == null || condition.itemId().isBlank()) {
                    errors.add("Step '" + stepId + "' HAS_ITEM condition missing itemId");
                }
                if (condition.count() <= 0) {
                    errors.add("Step '" + stepId + "' HAS_ITEM condition count must be > 0 (found " + condition.count() + ")");
                }
            }
            case HAS_ANY_ITEM -> {
                if (condition.itemIds() == null || condition.itemIds().isEmpty()) {
                    errors.add("Step '" + stepId + "' HAS_ANY_ITEM condition missing itemIds");
                }
            }
            case HAS_ITEM_TAG -> {
                if (condition.tag() == null || condition.tag().isBlank()) {
                    errors.add("Step '" + stepId + "' HAS_ITEM_TAG condition missing tag");
                }
                if (condition.count() <= 0) {
                    errors.add("Step '" + stepId + "' HAS_ITEM_TAG condition count must be > 0 (found " + condition.count() + ")");
                }
            }
            case HAS_EDIBLE_ITEM -> {
                if (condition.count() <= 0) {
                    errors.add("Step '" + stepId + "' HAS_EDIBLE_ITEM condition count must be > 0 (found " + condition.count() + ")");
                }
            }
            case ALL_OF, ANY_OF -> {
                if (condition.subConditions() == null || condition.subConditions().isEmpty()) {
                    errors.add("Step '" + stepId + "' " + type + " condition has empty subConditions");
                } else {
                    for (GuideCondition sub : condition.subConditions()) {
                        validateCondition(stepId + " [nested]", sub, errors, warnings);
                    }
                }
            }
        }
    }

    private static void detectCycles(Map<String, GuideStep> stepMap, List<String> errors) {
        Map<String, Integer> state = new HashMap<>();
        for (String stepId : stepMap.keySet()) {
            state.put(stepId, 0);
        }

        for (String stepId : stepMap.keySet()) {
            if (state.get(stepId) == 0) {
                dfsCycleCheck(stepId, stepMap, state, new ArrayList<>(), errors);
            }
        }
    }

    private static void dfsCycleCheck(String current, Map<String, GuideStep> stepMap, Map<String, Integer> state,
                                      List<String> path, List<String> errors) {
        state.put(current, 1);
        path.add(current);

        GuideStep step = stepMap.get(current);
        if (step != null && step.prerequisites() != null) {
            for (String prereq : step.prerequisites()) {
                Integer s = state.get(prereq);
                if (s != null && s == 1) {
                    int cycleStart = path.indexOf(prereq);
                    List<String> cyclePath = new ArrayList<>(path.subList(cycleStart, path.size()));
                    cyclePath.add(prereq);
                    errors.add("Circular prerequisite dependency detected: " + String.join(" -> ", cyclePath));
                } else if (s != null && s == 0) {
                    dfsCycleCheck(prereq, stepMap, state, path, errors);
                }
            }
        }

        path.remove(path.size() - 1);
        state.put(current, 2);
    }
}
