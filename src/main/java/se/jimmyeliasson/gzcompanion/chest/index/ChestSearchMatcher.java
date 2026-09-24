package se.jimmyeliasson.gzcompanion.chest.index;

import se.jimmyeliasson.gzcompanion.chest.model.ChestSlotEntry;
import se.jimmyeliasson.gzcompanion.chest.model.DimensionNames;
import se.jimmyeliasson.gzcompanion.chest.model.StoredContainer;

import java.util.Locale;
import java.util.function.Function;

/**
 * The single source of truth for Kistor's local-only text matching, shared by the FÖRVARING
 * (storage) and SAKER (item) searches. Operates only on already-indexed local data.
 *
 * <p>A storage location matches on: coordinates, storage type (display + enum name, plus
 * "dubbel kista"), dimension key and short dimension name, custom label, group, location note,
 * and any contained item's raw id or display name.
 */
public final class ChestSearchMatcher {
    private ChestSearchMatcher() {}

    /** Lower-cased, trimmed query, or {@code null} for "no query". */
    public static String normalizeQuery(String query) {
        if (query == null || query.isBlank()) return null;
        return query.trim().toLowerCase(Locale.ROOT);
    }

    public static boolean containerMatches(StoredContainer container, String normalizedQuery, Function<String, String> displayNameFn) {
        if (normalizedQuery == null) return true;
        return containerMetadataMatches(container, normalizedQuery) || containerContentMatches(container, normalizedQuery, displayNameFn);
    }

    /** Matches everything about a storage location EXCEPT its contents. */
    public static boolean containerMetadataMatches(StoredContainer container, String q) {
        if (q == null) return true;
        if (coordinatesMatch(container, q)) return true;
        if (contains(container.kind().getDisplayName(), q)) return true;
        if (contains(container.kind().name(), q)) return true;
        if (contains(container.storageTypeText(), q)) return true;
        if (contains(container.dimensionKey(), q)) return true;
        if (contains(DimensionNames.shortName(container.dimensionKey()), q)) return true;
        if (contains(container.label(), q)) return true;
        if (contains(container.group(), q)) return true;
        return contains(container.locationNote(), q);
    }

    public static boolean containerContentMatches(StoredContainer container, String q, Function<String, String> displayNameFn) {
        if (q == null) return true;
        for (ChestSlotEntry slot : container.slots()) {
            if (itemMatches(slot.itemId(), displayNameFn != null ? displayNameFn.apply(slot.itemId()) : null, q)) return true;
        }
        return false;
    }

    public static boolean itemMatches(String itemId, String displayName, String q) {
        if (q == null) return true;
        return contains(itemId, q) || contains(displayName, q);
    }

    /**
     * Coordinate text matching tolerant of the separators players naturally type: "120 64 -32",
     * "120, 64, -32" and "120,64,-32" all match the same position.
     */
    static boolean coordinatesMatch(StoredContainer container, String q) {
        String coords = container.anchor().toCoordinateText();
        if (coords.contains(q)) return true;
        String normalized = q.replace(',', ' ').replaceAll("\\s+", " ").trim();
        return !normalized.isEmpty() && coords.contains(normalized);
    }

    private static boolean contains(String haystack, String q) {
        return haystack != null && haystack.toLowerCase(Locale.ROOT).contains(q);
    }
}
