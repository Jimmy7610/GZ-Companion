package se.jimmyeliasson.gzcompanion.guide.model;

import java.util.List;

/**
 * Typed model representing a condition for guide step completion.
 */
public record GuideCondition(
    GuideConditionType type,
    String itemId,
    List<String> itemIds,
    String tag,
    int count,
    String description,
    List<GuideCondition> subConditions
) {
    public GuideCondition {
        if (type == null) {
            type = GuideConditionType.MANUAL;
        }
        if (itemIds == null) {
            itemIds = List.of();
        }
        if (subConditions == null) {
            subConditions = List.of();
        }
        if (count <= 0) {
            count = 1;
        }
    }

    public static GuideCondition manual() {
        return new GuideCondition(GuideConditionType.MANUAL, null, List.of(), null, 1, null, List.of());
    }

    public static GuideCondition hasItem(String itemId, int count, String description) {
        return new GuideCondition(GuideConditionType.HAS_ITEM, itemId, List.of(), null, count, description, List.of());
    }

    public static GuideCondition hasTag(String tag, int count, String description) {
        return new GuideCondition(GuideConditionType.HAS_ITEM_TAG, null, List.of(), tag, count, description, List.of());
    }

    public static GuideCondition hasAnyItem(List<String> itemIds, int count, String description) {
        return new GuideCondition(GuideConditionType.HAS_ANY_ITEM, null, itemIds, null, count, description, List.of());
    }

    public static GuideCondition hasEdible(int count, String description) {
        return new GuideCondition(GuideConditionType.HAS_EDIBLE_ITEM, null, List.of(), null, count, description, List.of());
    }

    public static GuideCondition allOf(List<GuideCondition> conditions, String description) {
        return new GuideCondition(GuideConditionType.ALL_OF, null, List.of(), null, 1, description, conditions);
    }

    public static GuideCondition anyOf(List<GuideCondition> conditions, String description) {
        return new GuideCondition(GuideConditionType.ANY_OF, null, List.of(), null, 1, description, conditions);
    }
}