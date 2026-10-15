package se.jimmyeliasson.gzcompanion.chest.nav;

import se.jimmyeliasson.gzcompanion.chest.model.StoragePosition;
import se.jimmyeliasson.gzcompanion.chest.model.StoredContainer;
import se.jimmyeliasson.gzcompanion.chest.model.StoredContainerId;

import java.util.Optional;

/**
 * The resolved thing the HUD/banner currently guides toward, whatever kind of target it came
 * from: a Kistor storage location or a {@link NavigationPoint}. Holds exactly what
 * {@link ChestNavigationMath#evaluate(PlayerPose, NavigationDestination)} and the HUD title need.
 *
 * @param x,y,z         the exact point navigated toward (block center, or a double chest's midpoint).
 * @param blockY        block Y used for the "↑/↓" vertical difference.
 * @param containerId   the Kistor storage identity, or {@code null} for a non-storage point.
 */
public record NavigationDestination(
    String title,
    String dimensionKey,
    double x,
    double y,
    double z,
    int blockY,
    StoredContainerId containerId
) {
    public static NavigationDestination of(StoredContainer container) {
        double[] t = ChestNavigationMath.targetPoint(container);
        return new NavigationDestination(container.displayTitle(), container.dimensionKey(), t[0], t[1], t[2],
                container.anchor().y(), container.id());
    }

    public static NavigationDestination of(NavigationPoint point) {
        StoragePosition p = point.position();
        return new NavigationDestination(point.label(), point.dimensionKey(), p.x() + 0.5, p.y() + 0.5, p.z() + 0.5, p.y(), null);
    }

    public Optional<StoredContainerId> container() {
        return Optional.ofNullable(containerId);
    }
}
