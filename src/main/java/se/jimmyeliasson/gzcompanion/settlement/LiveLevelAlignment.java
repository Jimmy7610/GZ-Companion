package se.jimmyeliasson.gzcompanion.settlement;

/**
 * Whether a live GameZone settlement level (and its level name) agrees with the bundled {@link
 * se.jimmyeliasson.gzcompanion.knowledge.settlement.SettlementCatalog} - see {@link
 * LiveSettlementLevel#evaluate}.
 */
public enum LiveLevelAlignment {
    /** The live level number exists in the catalog AND the catalog's name for it matches the
     * live level name (case/whitespace-normalized only - never fuzzy). Safe to drive automatic
     * planning. */
    ALIGNED,
    /** Both a live level number and a live level name were present, but they do not agree with
     * the bundled catalog (an unknown level number, or a known level number whose catalog name
     * differs from what GameZone reported). The raw live values must still be shown honestly;
     * automatic planning must fail closed to the manual planner. */
    MISMATCH,
    /** Not enough information to confirm OR deny alignment (no live level number, or a level
     * number with no accompanying level name to compare) - never guessed either way. */
    UNKNOWN
}
