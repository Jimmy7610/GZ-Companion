package se.jimmyeliasson.gzcompanion.chest.model;

/**
 * Conservative explicit allow-list of physical block storage types indexed by the Chest Manager.
 * Only vanilla block-backed storage the player can physically right-click is represented here.
 * Ender chests, minecart/entity containers, and virtual menus are intentionally excluded.
 */
public enum StorageKind {
    CHEST("Kista"),
    TRAPPED_CHEST("Fällkista"),
    BARREL("Tunna"),
    SHULKER_BOX("Shulker Box"),
    HOPPER("Tratt"),
    DISPENSER("Dispenser"),
    DROPPER("Dropper");

    private final String displayName;

    StorageKind(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isChestFamily() {
        return this == CHEST || this == TRAPPED_CHEST;
    }
}
