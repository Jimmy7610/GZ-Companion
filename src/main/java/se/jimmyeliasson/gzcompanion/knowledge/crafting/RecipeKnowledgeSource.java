package se.jimmyeliasson.gzcompanion.knowledge.crafting;

/**
 * Where a Rule Pack {@link GameZoneCraftingEntry} claims to come from. Deliberately does NOT
 * include a "VANILLA" value — nothing in this enum is ever inferred from the Minecraft client;
 * every value here represents a hand-authored GameZone Rule Pack fact requiring its own
 * {@link se.jimmyeliasson.gzcompanion.knowledge.common.VerificationMetadata}. Client/server-synced
 * recipe information observed at runtime is modeled entirely separately by
 * {@link ClientRecipeSnapshot} and is never merged with this identity - see
 * {@code docs/KNOWLEDGE-BASE.md} for the full rationale.
 */
public enum RecipeKnowledgeSource {
    /** GameZone adds a recipe that does not exist in vanilla Minecraft. */
    GAMEZONE_ADDITION("GameZone-tillägg"),
    /** GameZone replaces/changes a vanilla recipe's ingredients or output. */
    GAMEZONE_REPLACEMENT("GameZone-ersättning"),
    /** GameZone is documented to disable a vanilla recipe entirely. */
    GAMEZONE_DISABLED("Avaktiverat av GameZone");

    private final String displayName;

    RecipeKnowledgeSource(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
