package se.jimmyeliasson.gzcompanion.chest.nav;

import se.jimmyeliasson.gzcompanion.chest.model.StoragePosition;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * A known block coordinate that is NOT a Kistor storage location, handed to the shared "HITTA"
 * navigation ({@link ChestNavigationManager#startPoint}) by another feature - today a location
 * the player saved on a MarketWatch note, later any other verified source of coordinates.
 *
 * <p>Callers never see the HUD or the math: they only describe WHERE (context + dimension + block
 * position) and WHAT to call it. Construct through {@link #of} so an incomplete or out-of-world
 * coordinate is rejected up front - there is no default position, so a missing coordinate can
 * never silently become {@code 0,0}.
 *
 * @param sourceKey         stable identity of whatever asked for navigation (e.g.
 *                          {@code "marketwatch:<noteId>"}), so the caller can tell whether
 *                          its own entry is the active target.
 * @param contextStorageKey the world/server context the coordinate belongs to - navigation
 *                          stops as soon as the player is in another context.
 * @param label             what the HUD shows as the target title.
 */
public record NavigationPoint(
    String sourceKey,
    String contextStorageKey,
    String dimensionKey,
    StoragePosition position,
    String label
) {
    /** Minecraft's world border limit for X/Z. */
    public static final int MAX_HORIZONTAL = 30_000_000;
    /** Widest build height a datapack can configure (vanilla is -64..319). */
    public static final int MIN_Y = -2048;
    public static final int MAX_Y = 2047;

    /**
     * Exactly Minecraft's own {@code Identifier} grammar ({@code validNamespaceChar} /
     * {@code validPathChar}), i.e. every string {@code level.dimension().identifier().toString()}
     * can produce - vanilla, datapack and modded dimensions alike. Navigation itself only compares
     * dimension keys for equality, so a key outside this grammar could never match the player's
     * dimension and is rejected rather than shown as a target that can never be reached.
     */
    private static final Pattern DIMENSION_KEY = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    public NavigationPoint {
        Objects.requireNonNull(sourceKey, "sourceKey");
        Objects.requireNonNull(contextStorageKey, "contextStorageKey");
        Objects.requireNonNull(dimensionKey, "dimensionKey");
        Objects.requireNonNull(position, "position");
        if (!isValidDimensionKey(dimensionKey)) throw new IllegalArgumentException("Invalid dimension key: " + dimensionKey);
        if (!isValidBlockPosition(position.x(), position.y(), position.z())) throw new IllegalArgumentException("Position outside the world: " + position);
        label = (label != null && !label.isBlank()) ? label.trim() : "Sparad plats";
    }

    /** Validating factory: empty unless every part is present and inside the world. */
    public static Optional<NavigationPoint> of(String sourceKey, String contextStorageKey, String dimensionKey,
                                               StoragePosition position, String label) {
        if (sourceKey == null || sourceKey.isBlank()) return Optional.empty();
        if (contextStorageKey == null || contextStorageKey.isBlank()) return Optional.empty();
        if (!isValidDimensionKey(dimensionKey) || position == null) return Optional.empty();
        if (!isValidBlockPosition(position.x(), position.y(), position.z())) return Optional.empty();
        return Optional.of(new NavigationPoint(sourceKey, contextStorageKey, dimensionKey, position, label));
    }

    public static boolean isValidDimensionKey(String dimensionKey) {
        return dimensionKey != null && DIMENSION_KEY.matcher(dimensionKey).matches();
    }

    public static boolean isValidBlockPosition(int x, int y, int z) {
        return Math.abs(x) <= MAX_HORIZONTAL && Math.abs(z) <= MAX_HORIZONTAL && y >= MIN_Y && y <= MAX_Y;
    }
}
