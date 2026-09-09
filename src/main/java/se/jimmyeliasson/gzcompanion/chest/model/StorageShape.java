package se.jimmyeliasson.gzcompanion.chest.model;

/**
 * Explicit physical shape of a storage location, derived ONLY from the already client-visible
 * {@code BlockState} of the block the player clicked. Replaces the previous ambiguous
 * {@code partnerUnknown} boolean, which could not distinguish "Minecraft proved this is a single
 * chest" from "this is a double chest half whose partner could not be resolved."
 */
public enum StorageShape {
    /** A single chest/trapped chest, confirmed by {@code ChestType.SINGLE}. No partner exists. */
    SINGLE,
    /** A double chest half whose partner position was deterministically resolved. */
    DOUBLE,
    /** A chest-family block whose double/single state could not be safely resolved. */
    UNKNOWN,
    /** Shape does not apply to this storage kind (Barrel, Shulker Box, Hopper, Dispenser, Dropper). */
    NOT_APPLICABLE
}
