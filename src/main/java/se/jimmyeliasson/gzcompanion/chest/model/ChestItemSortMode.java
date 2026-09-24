package se.jimmyeliasson.gzcompanion.chest.model;

/**
 * Local-only sort control for the Kistor SAKER (aggregated items) list. Every mode is total and
 * deterministic: ties always fall back to display name, then raw item id.
 */
public enum ChestItemSortMode {
    /** Largest last-known total first. Default. */
    COUNT("Antal"),
    /** Display name, case-insensitive. */
    NAME("Namn"),
    /** Spread across the most storage locations first. */
    SPREAD("Förvaringar");

    private final String displayName;

    ChestItemSortMode(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public ChestItemSortMode next() {
        ChestItemSortMode[] values = values();
        return values[(ordinal() + 1) % values.length];
    }
}
