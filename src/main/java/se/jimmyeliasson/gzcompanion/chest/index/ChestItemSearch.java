package se.jimmyeliasson.gzcompanion.chest.index;

import se.jimmyeliasson.gzcompanion.chest.model.ChestItemSortMode;
import se.jimmyeliasson.gzcompanion.chest.model.StoredContainer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * SAKER-mode search over an already built {@link ChestItemIndex}. Deterministic two-step rule:
 * <ol>
 *   <li>If any aggregated item's display name or raw id matches the query, those items are the
 *       result ({@link ChestItemSearchResult.Scope#ITEM_MATCH}).</li>
 *   <li>Otherwise, if the query matches storage metadata (label, group, location note, type,
 *       dimension or coordinates), the result is every item stored in those matching storage
 *       locations, with totals restricted to them
 *       ({@link ChestItemSearchResult.Scope#STORAGE_MATCH}) - e.g. searching "Min bas" lists the
 *       things in the "Min bas" group.</li>
 * </ol>
 */
public final class ChestItemSearch {
    private ChestItemSearch() {}

    public static ChestItemSearchResult search(String contextKey, ChestItemIndex index, List<StoredContainer> scopeContainers,
                                               String query, ChestItemSortMode sortMode, Function<String, String> displayNameFn) {
        ChestItemIndex safeIndex = index != null ? index : ChestItemIndex.empty();
        String q = ChestSearchMatcher.normalizeQuery(query);
        if (q == null) {
            return new ChestItemSearchResult(safeIndex.entries(sortMode), ChestItemSearchResult.Scope.ALL, 0);
        }

        List<ChestItemEntry> itemMatches = new ArrayList<>();
        for (ChestItemEntry entry : safeIndex.entries(sortMode)) {
            if (ChestSearchMatcher.itemMatches(entry.itemId(), entry.displayName(), q)) {
                itemMatches.add(entry);
            }
        }
        if (!itemMatches.isEmpty()) {
            return new ChestItemSearchResult(itemMatches, ChestItemSearchResult.Scope.ITEM_MATCH, 0);
        }

        List<StoredContainer> matchingStorage = new ArrayList<>();
        if (scopeContainers != null) {
            for (StoredContainer container : scopeContainers) {
                if (ChestSearchMatcher.containerMetadataMatches(container, q)) {
                    matchingStorage.add(container);
                }
            }
        }
        if (matchingStorage.isEmpty()) {
            return new ChestItemSearchResult(List.of(), ChestItemSearchResult.Scope.ITEM_MATCH, 0);
        }
        ChestItemIndex scoped = ChestItemIndex.build(contextKey, matchingStorage, displayNameFn);
        return new ChestItemSearchResult(scoped.entries(sortMode), ChestItemSearchResult.Scope.STORAGE_MATCH, matchingStorage.size());
    }
}
