package se.jimmyeliasson.gzcompanion.knowledge.crafting;

/** Shape of a recipe's ingredient layout. Never fake a shape a recipe doesn't have. */
public enum RecipeKind {
    /** Ingredients occupy specific grid positions. */
    SHAPED,
    /** Ingredients are an unordered collection - never rendered as a fake grid. */
    SHAPELESS
}
