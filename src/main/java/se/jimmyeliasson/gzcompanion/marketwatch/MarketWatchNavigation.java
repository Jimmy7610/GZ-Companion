package se.jimmyeliasson.gzcompanion.marketwatch;

import se.jimmyeliasson.gzcompanion.chest.nav.ChestNavigationManager;
import se.jimmyeliasson.gzcompanion.chest.nav.NavigationPoint;
import se.jimmyeliasson.gzcompanion.chest.nav.PlayerPose;
import se.jimmyeliasson.gzcompanion.marketwatch.storage.MarketWatchNote;
import se.jimmyeliasson.gzcompanion.marketwatch.storage.MarketWatchSavedLocation;

import java.util.Optional;

/**
 * The only link between MarketWatch notes and the shared "HITTA" navigation: turns a note's
 * player-saved location into a {@link NavigationPoint} and hands it to
 * {@link ChestNavigationManager#startPoint}. Holds no state and does no navigation math itself.
 *
 * <p>A future verified {@code /market} shop source would build its own {@link NavigationPoint}
 * the same way and call the same {@code startPoint} - nothing here would need to change.
 */
public final class MarketWatchNavigation {
    static final String SOURCE_PREFIX = "marketwatch:";

    private MarketWatchNavigation() {}

    public static String sourceKey(MarketWatchNote note) {
        return SOURCE_PREFIX + note.id();
    }

    /**
     * Records the player's OWN current block position. Empty when there is no live pose or it is
     * outside the world - never a substituted position.
     */
    public static Optional<MarketWatchSavedLocation> locationFromPose(Optional<PlayerPose> pose, long nowMs) {
        if (pose == null || pose.isEmpty()) return Optional.empty();
        PlayerPose p = pose.get();
        if (!Double.isFinite(p.x()) || !Double.isFinite(p.y()) || !Double.isFinite(p.z())) return Optional.empty();
        // A huge value saturates to Integer.MIN/MAX_VALUE here and is then rejected by the world bounds.
        return MarketWatchSavedLocation.of(p.dimensionKey(), (int) Math.floor(p.x()), (int) Math.floor(p.y()), (int) Math.floor(p.z()), nowMs);
    }

    /** The navigation target for a note, or empty when the note has no usable saved location. */
    public static Optional<NavigationPoint> toNavigationPoint(String contextKey, MarketWatchNote note) {
        if (note == null || !note.hasLocation()) return Optional.empty();
        MarketWatchSavedLocation loc = note.location();
        return NavigationPoint.of(sourceKey(note), contextKey, loc.dimensionKey(), loc.position(), note.displayName());
    }

    public static boolean canNavigate(String contextKey, MarketWatchNote note) {
        return toNavigationPoint(contextKey, note).isPresent();
    }

    /** Starts (or replaces the current target with) navigation to the note's saved location. */
    public static boolean startNavigation(ChestNavigationManager navigation, String contextKey, MarketWatchNote note, long nowMs) {
        if (navigation == null) return false;
        return toNavigationPoint(contextKey, note).map(point -> navigation.startPoint(contextKey, point, nowMs)).orElse(false);
    }

    public static boolean isNavigatingTo(ChestNavigationManager navigation, MarketWatchNote note) {
        return navigation != null && note != null && navigation.isPointTarget(sourceKey(note));
    }

    /** The note (or its saved place) is gone: stop if it is the active target, otherwise do nothing. */
    public static void stopIfNavigatingTo(ChestNavigationManager navigation, MarketWatchNote note) {
        if (isNavigatingTo(navigation, note)) {
            navigation.stop(ChestNavigationManager.StopReason.TARGET_MISSING);
        }
    }
}
