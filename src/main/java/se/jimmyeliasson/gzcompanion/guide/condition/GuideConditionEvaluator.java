package se.jimmyeliasson.gzcompanion.guide.condition;

import se.jimmyeliasson.gzcompanion.guide.bridge.GuidePlayerSnapshot;
import se.jimmyeliasson.gzcompanion.guide.model.GuideCondition;
import se.jimmyeliasson.gzcompanion.guide.model.GuideConditionResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure evaluator for Guide conditions against a player state snapshot.
 */
public final class GuideConditionEvaluator {
    private GuideConditionEvaluator() {}

    public static GuideConditionResult evaluate(GuideCondition condition, GuidePlayerSnapshot snapshot) {
        if (condition == null) {
            return new GuideConditionResult(true, 1, 1, "", List.of());
        }
        if (snapshot == null) {
            snapshot = GuidePlayerSnapshot.EMPTY;
        }

        return switch (condition.type()) {
            case MANUAL -> new GuideConditionResult(true, 1, 1, condition.description(), List.of());

            case HAS_ITEM -> {
                int count = snapshot.getItemCount(condition.itemId());
                boolean satisfied = count >= condition.count();
                yield new GuideConditionResult(satisfied, count, condition.count(), condition.description(), List.of());
            }

            case HAS_ANY_ITEM -> {
                int totalCount = 0;
                for (String id : condition.itemIds()) {
                    totalCount += snapshot.getItemCount(id);
                }
                boolean satisfied = totalCount >= condition.count();
                yield new GuideConditionResult(satisfied, totalCount, condition.count(), condition.description(), List.of());
            }

            case HAS_ITEM_TAG -> {
                int count = snapshot.getTagCount(condition.tag());
                boolean satisfied = count >= condition.count();
                yield new GuideConditionResult(satisfied, count, condition.count(), condition.description(), List.of());
            }

            case HAS_EDIBLE_ITEM -> {
                int count = snapshot.hasEdibleItem() ? 1 : 0;
                boolean satisfied = snapshot.hasEdibleItem();
                yield new GuideConditionResult(satisfied, count, condition.count(), condition.description(), List.of());
            }

            case ALL_OF -> {
                List<GuideConditionResult> children = new ArrayList<>();
                boolean allSatisfied = true;
                for (GuideCondition sub : condition.subConditions()) {
                    GuideConditionResult subRes = evaluate(sub, snapshot);
                    children.add(subRes);
                    if (!subRes.satisfied()) {
                        allSatisfied = false;
                    }
                }
                yield new GuideConditionResult(allSatisfied, allSatisfied ? 1 : 0, 1, condition.description(), children);
            }

            case ANY_OF -> {
                List<GuideConditionResult> children = new ArrayList<>();
                boolean anySatisfied = false;
                for (GuideCondition sub : condition.subConditions()) {
                    GuideConditionResult subRes = evaluate(sub, snapshot);
                    children.add(subRes);
                    if (subRes.satisfied()) {
                        anySatisfied = true;
                    }
                }
                yield new GuideConditionResult(anySatisfied, anySatisfied ? 1 : 0, 1, condition.description(), children);
            }
        };
    }
}