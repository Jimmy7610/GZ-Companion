package se.jimmyeliasson.gzcompanion.chest.nav;

/**
 * The local player's OWN current position, dimension and camera/look yaw, as plain values. This
 * is the only live input navigation ever uses - it is compared against the saved coordinates of
 * one explicitly selected, previously legitimately opened storage location. Nothing else in the
 * world is ever read.
 *
 * @param yawDegrees Minecraft's own yaw convention: 0 = facing +Z (south), 90 = facing -X
 *                   (west), 180/-180 = facing -Z (north), -90 = facing +X (east). Increases as
 *                   the camera turns right (clockwise seen from above).
 */
public record PlayerPose(String dimensionKey, double x, double y, double z, float yawDegrees) {}
