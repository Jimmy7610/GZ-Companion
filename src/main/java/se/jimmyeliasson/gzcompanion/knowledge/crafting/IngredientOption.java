package se.jimmyeliasson.gzcompanion.knowledge.crafting;

import java.util.Objects;

/**
 * One legitimately-resolvable ingredient alternative for a client recipe slot: the raw Minecraft
 * item id plus its resolved, translated display name. Contains no Minecraft API types - the
 * display name is resolved once by {@code MinecraftRecipeDisplayAdapter} when the recipe book
 * snapshot is built, never re-resolved per render frame or per keystroke.
 */
public record IngredientOption(String itemId, String displayName) {
    public IngredientOption {
        Objects.requireNonNull(itemId, "itemId");
        displayName = (displayName != null && !displayName.isBlank()) ? displayName : itemId;
    }
}
