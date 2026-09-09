package se.jimmyeliasson.gzcompanion.chest.model;

/**
 * Compact local-only sort control for the indexed storage list. No server data is involved.
 */
public enum ChestSortMode {
    /** Most recently opened first. Default. */
    RECENT("Senast öppnad"),
    /** Local label if present, otherwise storage display name, case-insensitive. */
    NAME("Namn"),
    /** Storage kind, then label/coordinates. */
    TYPE("Typ");

    private final String displayName;

    ChestSortMode(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public ChestSortMode next() {
        ChestSortMode[] values = values();
        return values[(ordinal() + 1) % values.length];
    }
}
