package se.jimmyeliasson.gzcompanion.chest.nav;

/** Presentation state of an active Kistor navigation reading. */
public enum ChestNavigationStatus {
    /** Same dimension, not yet close: rotating arrow + straight-line distance. */
    DIRECTIONAL,
    /** Same dimension, within {@link ChestNavigationMath#NEAR_DISTANCE} blocks. */
    NEAR,
    /** Same dimension, within {@link ChestNavigationMath#ARRIVAL_DISTANCE} blocks: "Du är framme". */
    ARRIVED,
    /** The target is in another dimension - no direction is shown and no cross-dimension route is ever invented. */
    WRONG_DIMENSION
}
