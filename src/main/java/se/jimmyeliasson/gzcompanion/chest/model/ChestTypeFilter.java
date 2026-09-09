package se.jimmyeliasson.gzcompanion.chest.model;

import java.util.EnumSet;
import java.util.Set;

/**
 * Compact local-only storage-type filter operating purely on the already-indexed cache. Groups
 * a few closely related {@link StorageKind} values (Chest + Trapped Chest, Dispenser + Dropper)
 * to keep the filter control small.
 */
public enum ChestTypeFilter {
    ALL("Alla", null),
    CHEST("Kista", EnumSet.of(StorageKind.CHEST, StorageKind.TRAPPED_CHEST)),
    BARREL("Tunna", EnumSet.of(StorageKind.BARREL)),
    SHULKER_BOX("Shulker", EnumSet.of(StorageKind.SHULKER_BOX)),
    HOPPER("Hopper", EnumSet.of(StorageKind.HOPPER)),
    DISPENSER_DROPPER("Dispenser/Dropper", EnumSet.of(StorageKind.DISPENSER, StorageKind.DROPPER));

    private final String displayName;
    private final Set<StorageKind> kinds;

    ChestTypeFilter(String displayName, Set<StorageKind> kinds) {
        this.displayName = displayName;
        this.kinds = kinds;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean matches(StorageKind kind) {
        return kinds == null || (kind != null && kinds.contains(kind));
    }

    public ChestTypeFilter next() {
        ChestTypeFilter[] values = values();
        return values[(ordinal() + 1) % values.length];
    }
}
