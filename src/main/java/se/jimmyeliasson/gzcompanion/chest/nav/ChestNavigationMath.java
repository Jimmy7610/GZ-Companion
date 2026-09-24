package se.jimmyeliasson.gzcompanion.chest.nav;

import se.jimmyeliasson.gzcompanion.chest.model.StoragePosition;
import se.jimmyeliasson.gzcompanion.chest.model.StoredContainer;

import java.util.Objects;

/**
 * Pure navigation math for Kistor "HITTA". Compares ONLY the player's own pose with the saved
 * coordinates of one explicitly selected, previously legitimately opened storage location. No
 * pathfinding, no route, no world access - just a bearing and straight-line distances.
 *
 * <p><b>Yaw convention (verified against Minecraft's own entity rotation):</b> a player with yaw
 * {@code θ} looks along {@code (-sin θ, cos θ)} in (x, z). The yaw that would face a target at
 * offset {@code (dx, dz)} is therefore {@code atan2(-dx, dz)}. Yaw increases when the camera turns
 * right, so {@code wrap(targetYaw - playerYaw)} is positive when the target is to the right.
 */
public final class ChestNavigationMath {
    /** Within this 3D distance the HUD switches to its simplified "near" state. */
    public static final double NEAR_DISTANCE = 15.0;
    /** Within this 3D distance the HUD shows "Du är framme" (roughly vanilla reach distance). */
    public static final double ARRIVAL_DISTANCE = 4.5;
    /** Vertical differences smaller than this many blocks are not shown. */
    public static final int VERTICAL_NEGLIGIBLE_BLOCKS = 2;

    private ChestNavigationMath() {}

    /** Wraps any angle in degrees into [-180, 180). */
    public static double wrapDegrees(double degrees) {
        double wrapped = degrees % 360.0;
        if (wrapped >= 180.0) wrapped -= 360.0;
        if (wrapped < -180.0) wrapped += 360.0;
        return wrapped;
    }

    /** Minecraft yaw (degrees) that would look straight at a target at horizontal offset (dx, dz). */
    public static double targetYawDegrees(double dx, double dz) {
        return Math.toDegrees(Math.atan2(-dx, dz));
    }

    /**
     * Relative bearing of the target versus the camera yaw, in [-180, 180): 0 = ahead, +90 = to
     * the right, -90 = to the left, -180 = behind. If the player stands at the target's exact
     * horizontal position there is no meaningful direction and 0 is returned.
     */
    public static double relativeBearingDegrees(double playerX, double playerZ, float playerYawDegrees, double targetX, double targetZ) {
        double dx = targetX - playerX;
        double dz = targetZ - playerZ;
        if (Math.abs(dx) < 1.0e-9 && Math.abs(dz) < 1.0e-9) return 0.0;
        return wrapDegrees(targetYawDegrees(dx, dz) - playerYawDegrees);
    }

    public static double horizontalDistance(double playerX, double playerZ, double targetX, double targetZ) {
        double dx = targetX - playerX;
        double dz = targetZ - playerZ;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** Target block Y minus the player's block Y (floor of feet Y); 0 when negligible. */
    public static int verticalDelta(double playerY, int targetBlockY) {
        int delta = targetBlockY - (int) Math.floor(playerY);
        return Math.abs(delta) < VERTICAL_NEGLIGIBLE_BLOCKS ? 0 : delta;
    }

    /** The point navigated toward: the block center, or the midpoint of both halves of a proven double chest. */
    public static double[] targetPoint(StoredContainer target) {
        StoragePosition a = target.anchor();
        StoragePosition p = target.isDoubleWide() ? target.partner() : null;
        if (p == null) {
            return new double[]{a.x() + 0.5, a.y() + 0.5, a.z() + 0.5};
        }
        return new double[]{(a.x() + p.x()) / 2.0 + 0.5, (a.y() + p.y()) / 2.0 + 0.5, (a.z() + p.z()) / 2.0 + 0.5};
    }

    /**
     * Evaluates one navigation frame. If the dimensions differ, no direction is produced at all
     * ({@link ChestNavigationStatus#WRONG_DIMENSION}) - never a guessed cross-dimension route.
     */
    public static ChestNavigationReading evaluate(PlayerPose pose, StoredContainer target) {
        Objects.requireNonNull(pose, "pose");
        Objects.requireNonNull(target, "target");
        String targetDim = target.dimensionKey();
        if (!Objects.equals(pose.dimensionKey(), targetDim)) {
            return new ChestNavigationReading(ChestNavigationStatus.WRONG_DIMENSION, 0.0, 0.0, 0.0, 0, targetDim, pose.dimensionKey());
        }

        double[] t = targetPoint(target);
        double horizontal = horizontalDistance(pose.x(), pose.z(), t[0], t[2]);
        // Player reference height ~1 block above the feet (between feet and eyes).
        double dy = t[1] - (pose.y() + 1.0);
        double distance3d = Math.sqrt(horizontal * horizontal + dy * dy);
        double bearing = relativeBearingDegrees(pose.x(), pose.z(), pose.yawDegrees(), t[0], t[2]);
        int vertical = verticalDelta(pose.y(), target.anchor().y());

        ChestNavigationStatus status;
        if (distance3d <= ARRIVAL_DISTANCE) {
            status = ChestNavigationStatus.ARRIVED;
        } else if (distance3d <= NEAR_DISTANCE) {
            status = ChestNavigationStatus.NEAR;
        } else {
            status = ChestNavigationStatus.DIRECTIONAL;
        }
        return new ChestNavigationReading(status, bearing, horizontal, distance3d, vertical, targetDim, pose.dimensionKey());
    }
}
