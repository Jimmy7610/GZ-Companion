package se.jimmyeliasson.gzcompanion.chest.index;

import java.util.List;

/**
 * One aggregated item across every indexed storage location in the current context: the total
 * last-known count and every contributing location (ordered largest amount first, then most
 * recently opened, then stable key). Totals are LAST KNOWN, never live server inventory.
 */
public record ChestItemEntry(String itemId, String displayName, int totalCount, List<ItemLocation> locations) {
    public ChestItemEntry {
        locations = locations != null ? List.copyOf(locations) : List.of();
    }

    public int containerCount() {
        return locations.size();
    }
}
