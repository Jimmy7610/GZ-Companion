package se.jimmyeliasson.gzcompanion.chest.nav;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import se.jimmyeliasson.gzcompanion.chest.model.StorageKind;
import se.jimmyeliasson.gzcompanion.chest.model.StoragePosition;
import se.jimmyeliasson.gzcompanion.chest.model.StorageShape;
import se.jimmyeliasson.gzcompanion.chest.model.StoredContainer;
import se.jimmyeliasson.gzcompanion.chest.model.StoredContainerId;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure navigation math, independent of any live Minecraft client. Minecraft yaw convention:
 * 0 = facing +Z (south), 90 = facing -X (west), 180 = -Z (north), -90 = +X (east); the yaw grows
 * when turning right. Arrow convention: 0 = up/ahead, positive = clockwise/right, ±180 = behind.
 */
class ChestNavigationMathTest {
    static final double EPS = 1e-6;
    static final String OW = "minecraft:overworld";

    static StoredContainer target(int x, int y, int z) {
        return new StoredContainer(new StoredContainerId("c", OW, new StoragePosition(x, y, z), StorageKind.CHEST),
                "Materiallager", null, StorageShape.SINGLE, 1L, List.of());
    }

    /** Player standing at the block center of (px, py, pz). */
    static PlayerPose at(double px, double py, double pz, float yaw) {
        return new PlayerPose(OW, px + 0.5, py, pz + 0.5, yaw);
    }

    @Test
    @DisplayName("Target directly ahead -> arrow up (0°) for every facing direction")
    void ahead() {
        // Facing south (+Z), target south.
        assertEquals(0.0, ChestNavigationMath.relativeBearingDegrees(0, 0, 0f, 0, 10), EPS);
        // Facing west (yaw 90, -X), target west.
        assertEquals(0.0, ChestNavigationMath.relativeBearingDegrees(0, 0, 90f, -10, 0), EPS);
        // Facing north (yaw 180, -Z), target north.
        assertEquals(0.0, ChestNavigationMath.relativeBearingDegrees(0, 0, 180f, 0, -10), EPS);
        // Facing east (yaw -90, +X), target east.
        assertEquals(0.0, ChestNavigationMath.relativeBearingDegrees(0, 0, -90f, 10, 0), EPS);
    }

    @Test
    @DisplayName("Target directly to the right -> +90° (clockwise)")
    void right() {
        // Facing south (+Z): the right-hand side is west (-X).
        assertEquals(90.0, ChestNavigationMath.relativeBearingDegrees(0, 0, 0f, -10, 0), EPS);
        // Facing north (-Z): the right-hand side is east (+X).
        assertEquals(90.0, ChestNavigationMath.relativeBearingDegrees(0, 0, 180f, 10, 0), EPS);
    }

    @Test
    @DisplayName("Target directly to the left -> -90° (counter-clockwise)")
    void left() {
        assertEquals(-90.0, ChestNavigationMath.relativeBearingDegrees(0, 0, 0f, 10, 0), EPS);
        assertEquals(-90.0, ChestNavigationMath.relativeBearingDegrees(0, 0, 90f, 0, 10), EPS);
    }

    @Test
    @DisplayName("Target behind -> ±180° (arrow down), normalized into [-180, 180)")
    void behind() {
        double b = ChestNavigationMath.relativeBearingDegrees(0, 0, 0f, 0, -10);
        assertEquals(-180.0, b, EPS);
        assertTrue(b >= -180.0 && b < 180.0);
    }

    @Test
    @DisplayName("Yaw wraparound: unnormalized yaw values (e.g. 720+, negative) give the same bearing")
    void wraparound() {
        assertEquals(0.0, ChestNavigationMath.relativeBearingDegrees(0, 0, 360f, 0, 10), EPS);
        assertEquals(90.0, ChestNavigationMath.relativeBearingDegrees(0, 0, 720f, -10, 0), EPS);
        assertEquals(90.0, ChestNavigationMath.relativeBearingDegrees(0, 0, -360f, -10, 0), EPS);
        // Near the ±180 seam: target yaw 179° vs player -179° is only 2° to the LEFT, not 358° right.
        double targetYaw179X = -Math.sin(Math.toRadians(179)) * 10;
        double targetYaw179Z = Math.cos(Math.toRadians(179)) * 10;
        assertEquals(-2.0, ChestNavigationMath.relativeBearingDegrees(0, 0, -179f, targetYaw179X, targetYaw179Z), 1e-4);
        assertEquals(-180.0, ChestNavigationMath.wrapDegrees(180.0), EPS);
        assertEquals(-179.0, ChestNavigationMath.wrapDegrees(181.0), EPS);
        assertEquals(179.0, ChestNavigationMath.wrapDegrees(-181.0), EPS);
        assertEquals(10.0, ChestNavigationMath.wrapDegrees(370.0), EPS);
    }

    @Test
    @DisplayName("Identical horizontal position -> bearing 0, horizontal distance 0")
    void identicalPosition() {
        assertEquals(0.0, ChestNavigationMath.relativeBearingDegrees(5, 5, 123f, 5, 5), EPS);
        ChestNavigationReading r = ChestNavigationMath.evaluate(at(3, 90, 3, 45f), target(3, 64, 3));
        assertEquals(0.0, r.horizontalDistance(), EPS);
        assertEquals(0.0, r.relativeBearingDegrees(), EPS);
        assertEquals(-26, r.verticalDelta(), "Directly above the target: only the vertical difference remains");
        assertEquals(ChestNavigationStatus.DIRECTIONAL, r.status(), "Far above is not 'arrived' - arrival is 3D proximity");
    }

    @Test
    @DisplayName("Vertical difference: negative = target lower, positive = target higher, negligible hidden")
    void verticalDelta() {
        assertEquals(-23, ChestNavigationMath.verticalDelta(87.0, 64));
        assertEquals(17, ChestNavigationMath.verticalDelta(47.2, 64));
        assertEquals(0, ChestNavigationMath.verticalDelta(64.0, 65), "A one-block difference is negligible");
        assertEquals(0, ChestNavigationMath.verticalDelta(64.9, 64));
        assertEquals(-2, ChestNavigationMath.verticalDelta(66.0, 64));
    }

    @Test
    @DisplayName("Horizontal distance ignores Y, and different Y levels don't change the bearing")
    void horizontalDistance() {
        assertEquals(5.0, ChestNavigationMath.horizontalDistance(0, 0, 3, 4), EPS);
        ChestNavigationReading low = ChestNavigationMath.evaluate(at(0, 10, 0, 0f), target(0, 64, 100));
        ChestNavigationReading high = ChestNavigationMath.evaluate(at(0, 200, 0, 0f), target(0, 64, 100));
        assertEquals(low.horizontalDistance(), high.horizontalDistance(), EPS);
        assertEquals(100.0, low.horizontalDistance(), EPS);
        assertEquals(low.relativeBearingDegrees(), high.relativeBearingDegrees(), EPS);
        assertTrue(low.verticalDelta() > 0);
        assertTrue(high.verticalDelta() < 0);
        assertEquals(100, low.roundedHorizontalBlocks());
    }

    @Test
    @DisplayName("Status thresholds: DIRECTIONAL far away, NEAR within 15 blocks, ARRIVED within ~4.5 blocks (coordinates only)")
    void arrivalThresholds() {
        StoredContainer t = target(0, 64, 0);
        assertEquals(ChestNavigationStatus.DIRECTIONAL, ChestNavigationMath.evaluate(at(0, 63, 40, 0f), t).status());
        assertEquals(ChestNavigationStatus.NEAR, ChestNavigationMath.evaluate(at(0, 63, 11, 0f), t).status());
        assertEquals(ChestNavigationStatus.ARRIVED, ChestNavigationMath.evaluate(at(0, 63, 3, 0f), t).status());
        assertEquals(ChestNavigationStatus.ARRIVED, ChestNavigationMath.evaluate(at(0, 63, 0, 0f), t).status());
    }

    @Test
    @DisplayName("Wrong dimension: no direction at all, never a cross-dimension route")
    void wrongDimension() {
        PlayerPose inNether = new PlayerPose("minecraft:the_nether", 0.5, 64, 10.5, 0f);
        ChestNavigationReading r = ChestNavigationMath.evaluate(inNether, target(0, 64, 0));
        assertEquals(ChestNavigationStatus.WRONG_DIMENSION, r.status());
        assertFalse(r.hasDirection());
        assertEquals(0.0, r.horizontalDistance(), EPS);
        assertEquals(OW, r.targetDimensionKey());
        assertEquals("minecraft:the_nether", r.playerDimensionKey());
        assertEquals("⚠ Finns i Overworld", KistorNavigationText.wrongDimensionTarget(r));
        assertEquals("Du är i Nether", KistorNavigationText.wrongDimensionPlayer(r));
    }

    @Test
    @DisplayName("A proven double chest is navigated to the midpoint of both halves")
    void doubleChestMidpoint() {
        StoredContainer dbl = new StoredContainer(new StoredContainerId("c", OW, new StoragePosition(10, 64, 10), StorageKind.CHEST),
                null, new StoragePosition(11, 64, 10), StorageShape.DOUBLE, 1L, List.of());
        double[] p = ChestNavigationMath.targetPoint(dbl);
        assertEquals(11.0, p[0], EPS);
        assertEquals(10.5, p[2], EPS);
    }

    @Test
    @DisplayName("HUD copy: straight-line distance wording and vertical arrows")
    void text() {
        ChestNavigationReading r = ChestNavigationMath.evaluate(at(0, 76, 0, 0f), target(0, 64, 284));
        assertEquals("284 block fågelvägen", KistorNavigationText.distance(r));
        assertEquals("↓ 12", KistorNavigationText.vertical(r));
        assertEquals("12 block lägre", KistorNavigationText.verticalLong(r));
        ChestNavigationReading level = ChestNavigationMath.evaluate(at(0, 64, 0, 0f), target(0, 64, 50));
        assertNull(KistorNavigationText.vertical(level));
    }
}
