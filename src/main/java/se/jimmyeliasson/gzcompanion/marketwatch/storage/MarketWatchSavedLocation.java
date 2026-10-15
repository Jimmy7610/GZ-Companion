package se.jimmyeliasson.gzcompanion.marketwatch.storage;

import se.jimmyeliasson.gzcompanion.chest.model.StoragePosition;
import se.jimmyeliasson.gzcompanion.chest.nav.NavigationPoint;

import java.util.Optional;

/**
 * A place the player saved on a MarketWatch note THEMSELVES - typed as X/Y/Z, or filled from their
 * own position with "Använd min plats" - never a shop location discovered from MarketWatch or
 * {@code /market}, which the
 * Companion does not read. The world/server context is implied by the context bucket the note is
 * stored in, so only the dimension and block position are kept here.
 *
 * <p>Construct through {@link #of} so an incomplete or out-of-world position is rejected; a note
 * either has a complete, valid location or none at all.
 */
public record MarketWatchSavedLocation(String dimensionKey, int x, int y, int z, long savedAtMs) {

    public MarketWatchSavedLocation {
        if (!NavigationPoint.isValidDimensionKey(dimensionKey)) throw new IllegalArgumentException("Invalid dimension key: " + dimensionKey);
        if (!NavigationPoint.isValidBlockPosition(x, y, z)) throw new IllegalArgumentException("Position outside the world");
    }

    public static Optional<MarketWatchSavedLocation> of(String dimensionKey, int x, int y, int z, long savedAtMs) {
        if (!NavigationPoint.isValidDimensionKey(dimensionKey) || !NavigationPoint.isValidBlockPosition(x, y, z)) {
            return Optional.empty();
        }
        return Optional.of(new MarketWatchSavedLocation(dimensionKey, x, y, z, savedAtMs));
    }

    public StoragePosition position() {
        return new StoragePosition(x, y, z);
    }
}
