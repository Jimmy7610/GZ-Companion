package se.jimmyeliasson.gzcompanion.chest.index;

import se.jimmyeliasson.gzcompanion.chest.model.StoragePosition;
import se.jimmyeliasson.gzcompanion.chest.model.StoredContainer;
import se.jimmyeliasson.gzcompanion.chest.model.StoredContainerId;

/**
 * One already-known storage location contributing a last-known amount of one item to the
 * {@link ChestItemIndex}. Everything here comes from a previously, legitimately opened
 * {@link StoredContainer} - never from the world.
 */
public record ItemLocation(StoredContainerId id, String title, int count, long lastOpenedAtMs,
                           boolean favorite, String group) {

    public static ItemLocation of(StoredContainer container, int count) {
        return new ItemLocation(container.id(), container.displayTitle(), count, container.lastOpenedAtMs(),
                container.favorite(), container.group());
    }

    public String dimensionKey() {
        return id.dimensionKey();
    }

    public StoragePosition anchor() {
        return id.anchor();
    }
}
