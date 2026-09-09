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
    List<List<IngredientOption>> slotAlternatives
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
     * A stable, content-derived identity for this recipe, built purely from properties already
     * legitimately visible in this snapshot (output, kind, dimensions, and every slot's item ids
     * in slot order) - never a guessed or invented server-side recipe identifier. Two snapshots
     * with identical content always produce the same key regardless of their position in a list,
     * so a UI selection keyed on this survives the recipe book being re-read and reordered.
     * Two recipes that legitimately differ only by output count or by which of several equally
     * valid alternative items is listed first still produce distinct keys, since every
     * alternative's item id is included.
     */
    public String stableKey() {
        StringBuilder sb = new StringBuilder();
        sb.append(outputItemId).append('|').append(kind).append('|').append(width).append('x').append(height);
        for (List<IngredientOption> slot : slotAlternatives) {
            sb.append('|');
            for (IngredientOption option : slot) {
                sb.append(option.itemId()).append(',');
            }
        }
        return sb.toString();
    }
}
