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
 */
public record ClientRecipeSnapshot(
    String outputItemId,
    int outputCount,
    RecipeKind kind,
    int width,
    int height,
    List<List<String>> slotAlternativeItemIds
) {
    public ClientRecipeSnapshot {
        Objects.requireNonNull(outputItemId, "outputItemId");
        Objects.requireNonNull(kind, "kind");
        outputCount = Math.max(1, outputCount);
        slotAlternativeItemIds = slotAlternativeItemIds != null
                ? slotAlternativeItemIds.stream().map(List::copyOf).toList()
                : List.of();
    }
}
