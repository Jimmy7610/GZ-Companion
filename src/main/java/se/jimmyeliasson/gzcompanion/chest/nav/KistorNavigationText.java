package se.jimmyeliasson.gzcompanion.chest.nav;

import se.jimmyeliasson.gzcompanion.chest.model.DimensionNames;

/**
 * Swedish HUD/Companion copy for Kistor navigation. Distances are always worded as straight-line
 * ("fågelvägen") so they can never be mistaken for a walkable route distance.
 */
public final class KistorNavigationText {
    private KistorNavigationText() {}

    public static String distance(ChestNavigationReading reading) {
        long blocks = reading.roundedHorizontalBlocks();
        return blocks + " block fågelvägen";
    }

    /** "↓ 12" (target lower), "↑ 17" (target higher), or {@code null} when negligible. */
    public static String vertical(ChestNavigationReading reading) {
        int dy = reading.verticalDelta();
        if (dy == 0) return null;
        return (dy < 0 ? "↓ " : "↑ ") + Math.abs(dy);
    }

    /** Longer form for the Companion banner, e.g. "12 block lägre". */
    public static String verticalLong(ChestNavigationReading reading) {
        int dy = reading.verticalDelta();
        if (dy == 0) return null;
        return Math.abs(dy) + (dy < 0 ? " block lägre" : " block högre");
    }

    public static String wrongDimensionTarget(ChestNavigationReading reading) {
        return "⚠ Finns i " + DimensionNames.shortName(reading.targetDimensionKey());
    }

    public static String wrongDimensionPlayer(ChestNavigationReading reading) {
        return "Du är i " + DimensionNames.shortName(reading.playerDimensionKey());
    }

    /** Shown right after navigation starts from any tab - the HUD arrow only appears once the Companion is closed. */
    public static final String NAVIGATION_STARTED = "Navigering startad • stäng med G för att se pilen";

    public static String arrived() {
        return "DU ÄR FRAMME";
    }

    /**
     * Distance + vertical difference (or the dimension/arrival state): line 2 of the Kistor
     * "NAVIGERAR" banner, and the distance part of a MarketWatch saved-location line.
     */
    public static String bannerSummary(ChestNavigationReading reading) {
        if (reading == null) return "";
        return switch (reading.status()) {
            case WRONG_DIMENSION -> "Finns i " + DimensionNames.shortName(reading.targetDimensionKey());
            case ARRIVED -> "Du är framme";
            case NEAR, DIRECTIONAL -> {
                String v = verticalLong(reading);
                yield distance(reading) + (v != null ? " • " + v : "");
            }
        };
    }
}
