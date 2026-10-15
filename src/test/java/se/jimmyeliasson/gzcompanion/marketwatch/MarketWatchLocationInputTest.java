package se.jimmyeliasson.gzcompanion.marketwatch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import se.jimmyeliasson.gzcompanion.chest.nav.PlayerPose;
import se.jimmyeliasson.gzcompanion.marketwatch.storage.MarketWatchSavedLocation;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** Manual X/Y/Z entry for a MarketWatch note's saved location: parsing, validation and dimension rules. */
class MarketWatchLocationInputTest {
    static final String OW = "minecraft:overworld";
    static final String NETHER = "minecraft:the_nether";
    static final Optional<PlayerPose> IN_NETHER = Optional.of(new PlayerPose(NETHER, 5, 70, 5, 0f));
    static final long NOW = 9_000L;

    private static MarketWatchLocationInput.Outcome resolve(String x, String y, String z, String knownDim, Optional<PlayerPose> pose) {
        return MarketWatchLocationInput.resolve(x, y, z, knownDim, pose, null, NOW);
    }

    private static MarketWatchSavedLocation ok(MarketWatchLocationInput.Outcome outcome) {
        assertTrue(outcome.isValid(), () -> "Expected valid, got: " + outcome.error());
        assertNotNull(outcome.location());
        return outcome.location();
    }

    private static String error(MarketWatchLocationInput.Outcome outcome) {
        assertFalse(outcome.isValid(), "Expected a validation error");
        assertNull(outcome.location(), "An invalid input must never produce a location");
        assertNotNull(outcome.error());
        return outcome.error();
    }

    @Test
    @DisplayName("Manual positive X/Y/Z become exactly that location")
    void positiveCoordinates() {
        MarketWatchSavedLocation loc = ok(resolve("847", "70", "12", OW, Optional.empty()));
        assertEquals(new MarketWatchSavedLocation(OW, 847, 70, 12, NOW), loc);
    }

    @Test
    @DisplayName("Negative X/Y/Z work, and a leading + is accepted")
    void negativeCoordinates() {
        MarketWatchSavedLocation loc = ok(resolve("-847", "-60", "-12", OW, Optional.empty()));
        assertEquals(-847, loc.x());
        assertEquals(-60, loc.y());
        assertEquals(-12, loc.z());
        assertEquals(5, ok(resolve("+5", "64", "+6", OW, Optional.empty())).x());
    }

    @Test
    @DisplayName("Surrounding whitespace is ignored")
    void whitespaceIgnored() {
        MarketWatchSavedLocation loc = ok(resolve("  -847 ", "\t70", " 12\t", OW, Optional.empty()));
        assertEquals(List.of(-847, 70, 12), List.of(loc.x(), loc.y(), loc.z()));
    }

    @Test
    @DisplayName("All three blank means no location at all (not an error)")
    void allBlankIsNoLocation() {
        MarketWatchLocationInput.Outcome outcome = resolve("", " ", "\t", null, Optional.empty());
        assertTrue(outcome.isValid());
        assertNull(outcome.location());
    }

    @Test
    @DisplayName("Partly entered coordinates are an error - never silently 'no location' and never a 0")
    void partialInputIsAnError() {
        for (String[] c : new String[][]{{"10", "", ""}, {"", "64", ""}, {"", "", "-3"}, {"10", "64", ""}, {"10", " ", "5"}, {"", "64", "5"}}) {
            assertEquals(MarketWatchLocationInput.ERROR_INCOMPLETE, error(resolve(c[0], c[1], c[2], OW, IN_NETHER)), String.join(",", c));
        }
    }

    @Test
    @DisplayName("Fractional values are rejected with the axis named")
    void fractionalRejected() {
        assertEquals("X måste vara ett heltal.", error(resolve("1.5", "64", "2", OW, Optional.empty())));
        assertEquals("Y måste vara ett heltal.", error(resolve("1", "64,5", "2", OW, Optional.empty())));
        assertEquals("Z måste vara ett heltal.", error(resolve("1", "64", "2.0", OW, Optional.empty())));
    }

    @Test
    @DisplayName("Non-numeric values are rejected with the axis named")
    void nonNumericRejected() {
        assertEquals("X måste vara ett heltal.", error(resolve("abc", "64", "2", OW, Optional.empty())));
        assertEquals("Y måste vara ett heltal.", error(resolve("1", "6 4", "2", OW, Optional.empty())));
        assertEquals("Z måste vara ett heltal.", error(resolve("1", "64", "--2", OW, Optional.empty())));
        assertEquals("X måste vara ett heltal.", error(resolve("-", "64", "2", OW, Optional.empty())));
        assertEquals("X måste vara ett heltal.", error(resolve("1e3", "64", "2", OW, Optional.empty())));
    }

    @Test
    @DisplayName("Out-of-range values use the same world limits as saved locations, including int overflow")
    void outOfRangeRejected() {
        assertTrue(resolve("30000000", "2047", "-30000000", OW, Optional.empty()).isValid(), "Limits themselves are allowed");
        assertTrue(resolve("0", "-2048", "0", OW, Optional.empty()).isValid());
        for (String[] c : new String[][]{{"30000001", "64", "0"}, {"0", "64", "-30000001"}, {"0", "2048", "0"}, {"0", "-2049", "0"},
                {"99999999999", "64", "0"}, {"0", "64", "-99999999999999999999"}}) {
            assertEquals(MarketWatchLocationInput.ERROR_OUTSIDE_WORLD, error(resolve(c[0], c[1], c[2], OW, Optional.empty())), String.join(",", c));
        }
    }

    @Test
    @DisplayName("No invalid field ever falls back to zero")
    void neverFallsBackToZero() {
        for (String bad : List.of("", "abc", "1.5", "99999999999", " ")) {
            MarketWatchLocationInput.Outcome outcome = resolve(bad, "64", "0", OW, IN_NETHER);
            assertNull(outcome.location(), "No location for X=" + bad);
            assertFalse(outcome.isValid());
        }
    }

    @Test
    @DisplayName("Editing an existing location's coordinates keeps that location's dimension, even if the player stands elsewhere")
    void editKeepsExistingDimension() {
        MarketWatchSavedLocation existing = MarketWatchSavedLocation.of(OW, 1, 64, 1, 100L).orElseThrow();
        MarketWatchSavedLocation edited = ok(MarketWatchLocationInput.resolve("200", "70", "-5", existing.dimensionKey(), IN_NETHER, existing, NOW));
        assertEquals(OW, edited.dimensionKey(), "Never switched to the player's current (Nether) dimension");
        assertEquals(List.of(200, 70, -5), List.of(edited.x(), edited.y(), edited.z()));
        assertEquals(NOW, edited.savedAtMs(), "A changed location gets a new saved time");
    }

    @Test
    @DisplayName("An unchanged existing location is kept as-is, including its saved time")
    void unchangedExistingKept() {
        MarketWatchSavedLocation existing = MarketWatchSavedLocation.of(OW, 1, 64, -1, 100L).orElseThrow();
        assertSame(existing, ok(MarketWatchLocationInput.resolve(" 1", "64 ", "-1", OW, IN_NETHER, existing, NOW)));
    }

    @Test
    @DisplayName("A new typed location uses the player's current dimension at the moment of saving")
    void newManualUsesCurrentDimension() {
        assertEquals(NETHER, ok(resolve("10", "64", "10", null, IN_NETHER)).dimensionKey());
        assertEquals("gamezone:resource_world",
                ok(resolve("10", "64", "10", null, Optional.of(new PlayerPose("gamezone:resource_world", 0, 0, 0, 0f)))).dimensionKey());
    }

    @Test
    @DisplayName("No known dimension and no live pose: saving a new typed location is refused - never assumed Overworld")
    void noDimensionPreventsSaving() {
        assertEquals(MarketWatchLocationInput.ERROR_NO_DIMENSION, error(resolve("10", "64", "10", null, Optional.empty())));
        assertEquals(MarketWatchLocationInput.ERROR_NO_DIMENSION, error(resolve("10", "64", "10", null, null)));
        assertEquals(MarketWatchLocationInput.ERROR_NO_DIMENSION,
                error(resolve("10", "64", "10", null, Optional.of(new PlayerPose(null, 0, 0, 0, 0f)))));
        assertEquals(MarketWatchLocationInput.ERROR_NO_DIMENSION, error(resolve("10", "64", "10", "not a dimension", Optional.empty())));
    }

    @Test
    @DisplayName("'Använd min plats' fills exactly the pose's floored block position and dimension into the fields")
    void useMyPositionFillsFieldsAndDimension() {
        MarketWatchSavedLocation captured = MarketWatchNavigation.locationFromPose(
                Optional.of(new PlayerPose(NETHER, -12.3, 64.9, 7.8, 0f)), NOW).orElseThrow();
        assertArrayEquals(new String[]{"-13", "64", "7"}, MarketWatchLocationInput.fieldsFor(captured));
        assertEquals(NETHER, captured.dimensionKey());
        // Saving the filled fields with the captured dimension reproduces exactly that location.
        assertSame(captured, ok(MarketWatchLocationInput.resolve("-13", "64", "7", captured.dimensionKey(), Optional.empty(), captured, NOW + 1)));
        assertArrayEquals(new String[]{"", "", ""}, MarketWatchLocationInput.fieldsFor(null));
    }
}
