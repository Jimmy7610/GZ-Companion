package se.jimmyeliasson.gzcompanion.marketwatch;

import se.jimmyeliasson.gzcompanion.chest.nav.NavigationPoint;
import se.jimmyeliasson.gzcompanion.chest.nav.PlayerPose;
import se.jimmyeliasson.gzcompanion.marketwatch.storage.MarketWatchSavedLocation;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.regex.Pattern;

/**
 * Turns the MarketWatch edit form's X/Y/Z text fields into the note's saved location - typed by
 * the player or filled by "Använd min plats". Pure and Minecraft-free so every rule is testable:
 *
 * <ul>
 *   <li>All three fields blank = no location. Some but not all filled is an error, never a
 *       silent "no location".</li>
 *   <li>Each field must be a whole number (surrounding whitespace ignored, negatives allowed),
 *       inside the same world limits {@link MarketWatchSavedLocation} / {@link NavigationPoint}
 *       enforce. There is no default coordinate - an invalid field never becomes {@code 0}.</li>
 *   <li>The dimension comes from the location being edited or from "Använd min plats"
 *       ({@code knownDimension}); only a brand-new typed location falls back to the player's
 *       dimension at the moment of saving. Without either, saving is refused - never a guessed
 *       Overworld.</li>
 * </ul>
 */
public final class MarketWatchLocationInput {
    private static final Pattern INTEGER = Pattern.compile("[+-]?\\d+");

    static final String ERROR_INCOMPLETE = "Fyll i X, Y och Z (eller töm alla tre).";
    static final String ERROR_OUTSIDE_WORLD = "Platsen ligger utanför världens gränser.";
    static final String ERROR_NO_DIMENSION = "Okänd dimension - stå i världen och spara igen.";

    private MarketWatchLocationInput() {}

    /** What the form should do with its location fields: save none, save one, or show an error. */
    public record Outcome(MarketWatchSavedLocation location, String error) {
        static Outcome none() {
            return new Outcome(null, null);
        }

        static Outcome of(MarketWatchSavedLocation location) {
            return new Outcome(location, null);
        }

        static Outcome invalid(String error) {
            return new Outcome(null, error);
        }

        public boolean isValid() {
            return error == null;
        }
    }

    /**
     * @param knownDimension the dimension to save with (the edited location's, or the one
     *                       "Använd min plats" captured); {@code null} for a new typed location.
     * @param poseAtSave     the player's live pose right now, used only when no dimension is known.
     * @param existing       the note's current saved location, if any - kept as-is (including its
     *                       saved time) when nothing about it changed.
     */
    public static Outcome resolve(String x, String y, String z, String knownDimension, Optional<PlayerPose> poseAtSave,
                                  MarketWatchSavedLocation existing, long nowMs) {
        boolean xBlank = isBlank(x), yBlank = isBlank(y), zBlank = isBlank(z);
        if (xBlank && yBlank && zBlank) return Outcome.none();
        if (xBlank || yBlank || zBlank) return Outcome.invalid(ERROR_INCOMPLETE);

        String[] names = {"X", "Y", "Z"};
        String[] values = {x, y, z};
        int[] parsed = new int[3];
        for (int i = 0; i < 3; i++) {
            String v = values[i].strip();
            if (!INTEGER.matcher(v).matches()) return Outcome.invalid(names[i] + " måste vara ett heltal.");
            OptionalInt n = parseInt(v);
            if (n.isEmpty()) return Outcome.invalid(ERROR_OUTSIDE_WORLD);
            parsed[i] = n.getAsInt();
        }
        if (!NavigationPoint.isValidBlockPosition(parsed[0], parsed[1], parsed[2])) return Outcome.invalid(ERROR_OUTSIDE_WORLD);

        String dimension = knownDimension != null ? knownDimension
                : (poseAtSave != null ? poseAtSave.map(PlayerPose::dimensionKey).orElse(null) : null);
        if (!NavigationPoint.isValidDimensionKey(dimension)) return Outcome.invalid(ERROR_NO_DIMENSION);

        if (existing != null && existing.dimensionKey().equals(dimension)
                && existing.x() == parsed[0] && existing.y() == parsed[1] && existing.z() == parsed[2]) {
            return Outcome.of(existing);
        }
        return MarketWatchSavedLocation.of(dimension, parsed[0], parsed[1], parsed[2], nowMs)
                .map(Outcome::of).orElseGet(() -> Outcome.invalid(ERROR_OUTSIDE_WORLD));
    }

    /** The three field texts for a saved location (empty strings for none). */
    public static String[] fieldsFor(MarketWatchSavedLocation location) {
        if (location == null) return new String[]{"", "", ""};
        return new String[]{Integer.toString(location.x()), Integer.toString(location.y()), Integer.toString(location.z())};
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /** Empty when the digits do not fit an int - such a value is outside the world anyway. */
    private static OptionalInt parseInt(String digits) {
        try {
            return OptionalInt.of(Integer.parseInt(digits));
        } catch (NumberFormatException e) {
            return OptionalInt.empty();
        }
    }
}
