package se.jimmyeliasson.gzcompanion.chest.nav;

/**
 * One frame's navigation result. {@code relativeBearingDegrees} is in [-180, 180): 0 = the target
 * is straight ahead of the camera (arrow up), positive = to the right (arrow rotates clockwise),
 * negative = to the left, -180 = directly behind (arrow down). Distances are straight-line
 * coordinate distances ("fågelvägen"), never a walkable route distance.
 *
 * @param verticalDelta target block Y minus the player's block Y: negative = target is lower.
 *                      {@code 0} when the difference is negligible (see
 *                      {@link ChestNavigationMath#VERTICAL_NEGLIGIBLE_BLOCKS}).
 */
public record ChestNavigationReading(
    ChestNavigationStatus status,
    double relativeBearingDegrees,
    double horizontalDistance,
    double distance3d,
    int verticalDelta,
    String targetDimensionKey,
    String playerDimensionKey
) {
    public boolean hasDirection() {
        return status != ChestNavigationStatus.WRONG_DIMENSION;
    }

    /** Horizontal distance rounded to whole blocks for display. */
    public long roundedHorizontalBlocks() {
        return Math.round(horizontalDistance);
    }
}
