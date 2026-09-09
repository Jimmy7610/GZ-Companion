package se.jimmyeliasson.gzcompanion.chest.storage;

import se.jimmyeliasson.gzcompanion.chest.model.StoredContainer;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * All indexed storage containers for one isolated player/world/server context.
 * Keyed by {@code StoredContainerId#asStableKey()}, never by array index.
 */
public record ContextContainers(Map<String, StoredContainer> containers) {
    public ContextContainers {
        containers = containers != null ? new HashMap<>(containers) : new HashMap<>();
    }

    public static ContextContainers empty() {
        return new ContextContainers(new HashMap<>());
    }

    public ContextContainers withContainer(StoredContainer container) {
        Objects.requireNonNull(container, "container");
        Map<String, StoredContainer> updated = new HashMap<>(containers);
        updated.put(container.id().asStableKey(), container);
        return new ContextContainers(updated);
    }

    public ContextContainers withoutContainer(String stableKey) {
        Map<String, StoredContainer> updated = new HashMap<>(containers);
        updated.remove(stableKey);
        return new ContextContainers(updated);
    }
}
