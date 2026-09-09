package se.jimmyeliasson.gzcompanion.chest.model;

import java.util.Objects;

/**
 * Stable identity for one physically opened storage location.
 * Identity intentionally includes context (player/world/server isolation, reusing
 * {@code GuideContext} storage-key semantics), dimension, the canonical anchor position,
 * and storage kind, so that:
 * - re-opening the SAME physical storage updates the existing record rather than duplicating it,
 * - the same X/Y/Z in a different world/server context is a different entry,
 * - the same X/Y/Z in a different dimension is a different entry.
 */
public record StoredContainerId(
    String contextStorageKey,
    String dimensionKey,
    StoragePosition anchor,
    StorageKind kind
) {
    public StoredContainerId {
        contextStorageKey = Objects.requireNonNullElse(contextStorageKey, "unknown_context");
        dimensionKey = Objects.requireNonNullElse(dimensionKey, "minecraft:overworld");
        anchor = Objects.requireNonNullElse(anchor, new StoragePosition(0, 0, 0));
        kind = Objects.requireNonNullElse(kind, StorageKind.CHEST);
    }

    /**
     * Stable string form suitable for use as a persistence map key. Not intended for display.
     */
    public String asStableKey() {
        return contextStorageKey + "|" + dimensionKey + "|" + anchor.x() + "," + anchor.y() + "," + anchor.z() + "|" + kind.name();
    }
}
