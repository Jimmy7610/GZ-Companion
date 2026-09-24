package se.jimmyeliasson.gzcompanion.chest.index;

import java.util.List;

/**
 * Result of a SAKER-mode search. {@code matchedStorageCount} is only meaningful for
 * {@link Scope#STORAGE_MATCH}.
 */
public record ChestItemSearchResult(List<ChestItemEntry> entries, Scope scope, int matchedStorageCount) {
    public enum Scope {
        /** No query: every aggregated item. */
        ALL,
        /** The query matched item names/ids (or nothing matched at all). */
        ITEM_MATCH,
        /** The query matched storage metadata; entries are restricted to those storage locations. */
        STORAGE_MATCH
    }

    public ChestItemSearchResult {
        entries = entries != null ? List.copyOf(entries) : List.of();
    }
}
