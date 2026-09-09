package se.jimmyeliasson.gzcompanion.knowledge.crafting;

import java.util.List;

/**
 * One ingredient slot's acceptable alternatives: either an explicit list of Minecraft item IDs,
 * or a vanilla tag reference (e.g. {@code #minecraft:planks}). Never both empty.
 */
public record IngredientRef(List<String> itemIds, String tag) {
    public IngredientRef {
        itemIds = itemIds != null ? List.copyOf(itemIds) : List.of();
    }

    public static IngredientRef ofItems(String... itemIds) {
        return new IngredientRef(List.of(itemIds), null);
    }

    public static IngredientRef ofTag(String tag) {
        return new IngredientRef(List.of(), tag);
    }

    public boolean isEmpty() {
        return itemIds.isEmpty() && (tag == null || tag.isBlank());
    }

    public boolean hasAlternatives() {
        return itemIds.size() > 1 || tag != null;
    }
}
