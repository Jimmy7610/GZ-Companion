package se.jimmyeliasson.gzcompanion.knowledge.crafting;

import se.jimmyeliasson.gzcompanion.knowledge.common.VerificationMetadata;

import java.util.List;
import java.util.Objects;

/**
 * One verified-or-not GameZone crafting fact: an addition, a replacement, or a documented
 * disabling of a vanilla recipe. Pure Rule Pack knowledge - never registers, intercepts, or
 * modifies any actual Minecraft recipe.
 *
 * <p>For {@link RecipeKind#SHAPED}, {@code grid} holds exactly {@code width * height} entries in
 * row-major order (an empty {@link IngredientRef} represents an empty slot). For
 * {@link RecipeKind#SHAPELESS}, {@code width}/{@code height} are {@code 0} and
 * {@code ingredients} holds the unordered ingredient list instead.
 */
public record GameZoneCraftingEntry(
    String id,
    String outputItemId,
    int outputCount,
    RecipeKind kind,
    int width,
    int height,
    List<IngredientRef> grid,
    List<IngredientRef> ingredients,
    RecipeKnowledgeSource source,
    String notes,
    VerificationMetadata verification
) {
    public GameZoneCraftingEntry {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(outputItemId, "outputItemId");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(source, "source");
        outputCount = Math.max(1, outputCount);
        grid = grid != null ? List.copyOf(grid) : List.of();
        ingredients = ingredients != null ? List.copyOf(ingredients) : List.of();
        verification = verification != null ? verification : VerificationMetadata.UNVERIFIED_DEFAULT;
    }
}
