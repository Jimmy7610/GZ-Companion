package se.jimmyeliasson.gzcompanion.knowledge.crafting;

import java.util.List;
import java.util.Objects;

/**
 * A single recipe as legitimately observed from the local client's own recipe book
 * ({@code LocalPlayer.getRecipeBook()} - see {@code MinecraftRecipeDisplayAdapter}). Contains
 * no Minecraft API types.
 *
 * <p>This is deliberately NOT a {@link RecipeKnowledgeSource#GAMEZONE_ADDITION}-style Rule Pack
 * fact and carries no {@link se.jimmyeliasson.gzcompanion.knowledge.common.VerificationMetadata}
 * - it is runtime-observed, client/server-synced information (recipe book unlocks arrive over
 * the network from whichever server the player is connected to), and must never be labeled
 * "Vanilla" or merged with GameZone verification semantics. See
 * {@code docs/KNOWLEDGE-BASE.md} for the full rationale.
 *
 * <p>Only recipes the player has legitimately discovered (unlocked in their own recipe book) are
 * ever represented here - this is not a dump of every possible recipe, and nothing here is
 * scanned or inferred beyond what the client already legitimately knows.
 *
 * <p>{@code outputDisplayName} and each {@link IngredientOption#displayName()} are resolved once,
 * by the adapter, when this snapshot is built - never re-resolved per render frame.
 */
public record ClientRecipeSnapshot(
    String outputItemId,
    String outputDisplayName,
    int outputCount,
    RecipeKind kind,
    int width,
    int height,
    List<List<IngredientOption>> slotAlternatives,
    int recipeDisplayId
) {
    public ClientRecipeSnapshot {
        Objects.requireNonNull(outputItemId, "outputItemId");
        Objects.requireNonNull(kind, "kind");
        outputDisplayName = (outputDisplayName != null && !outputDisplayName.isBlank()) ? outputDisplayName : outputItemId;
        outputCount = Math.max(1, outputCount);
        slotAlternatives = slotAlternatives != null
                ? slotAlternatives.stream().map(List::copyOf).toList()
                : List.of();
    }

    /**
     * A stable identity for this recipe. The primary, load-bearing component is
     * {@code recipeDisplayId}, backed by {@code RecipeDisplayEntry.id().index()} - a real,
     * legitimately client-visible integer the server itself assigns to distinguish each synced
     * recipe (confirmed by inspecting the Minecraft 26.1.2 {@code RecipeDisplayEntry} record:
     * {@code id()} returns a {@code RecipeDisplayId} wrapping that integer, kept stable for the
     * lifetime of the connection - {@code ClientRecipeBook} never reassigns it to an existing
     * recipe, even though the map backing {@code getCollections()} can otherwise iterate in a
     * different order after a rebuild). This is NOT an invented or guessed identifier - it is data
     * the client already legitimately received - and it correctly distinguishes even the rare case
     * of two different real recipes that happen to render identical output and ingredients, which
     * a purely content-derived key alone could not.
     *
     * <p>The full output (id, count), kind/dimensions, and every slot's sorted ingredient-id
     * alternatives are appended too - sorted within each slot since ingredient alternative order
     * is not guaranteed stable by the API, only the identity of the set is. This is defense in
     * depth on top of {@code recipeDisplayId}, not a replacement for it: two recipes producing the
     * same output via the same ingredients but a different output count are different real
     * recipes and therefore already have different {@code recipeDisplayId}s, but the count is
     * still included explicitly so the key never silently depends on that fact alone. A UI
     * selection keyed on this survives the recipe book being re-read and returned in a different
     * order.
     */
    public String stableKey() {
        StringBuilder sb = new StringBuilder();
        sb.append(recipeDisplayId).append('|').append(outputItemId).append('|').append(outputCount)
                .append('|').append(kind).append('|').append(width).append('x').append(height);
        for (List<IngredientOption> slot : slotAlternatives) {
            List<String> ids = slot.stream().map(IngredientOption::itemId).sorted().toList();
            sb.append('|').append(String.join(",", ids));
        }
        return sb.toString();
    }
}
