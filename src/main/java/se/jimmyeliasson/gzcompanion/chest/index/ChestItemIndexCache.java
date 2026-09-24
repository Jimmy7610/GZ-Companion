package se.jimmyeliasson.gzcompanion.chest.index;

import se.jimmyeliasson.gzcompanion.chest.ChestManager;
import se.jimmyeliasson.gzcompanion.chest.model.ChestGroupFilter;
import se.jimmyeliasson.gzcompanion.chest.model.ChestTypeFilter;
import se.jimmyeliasson.gzcompanion.chest.model.StoredContainer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Single-entry memo for {@link ChestItemIndex}: rebuilt only when the requested (context,
 * {@link ChestManager#revision()}, type filter, group filter) key changes. Every Chest Manager
 * mutation bumps the revision, so invalidation is deterministic and a render frame never pays for
 * a rebuild when nothing changed. Also caches the scoped container list the index was built from.
 */
public final class ChestItemIndexCache {
    private record Key(String contextKey, long revision, ChestTypeFilter typeFilter, ChestGroupFilter groupFilter) {}

    private Key key;
    private ChestItemIndex index = ChestItemIndex.empty();
    private List<StoredContainer> scopeContainers = List.of();

    public ChestItemIndex get(ChestManager manager, String contextKey, ChestTypeFilter typeFilter, ChestGroupFilter groupFilter) {
        refresh(manager, contextKey, typeFilter, groupFilter);
        return index;
    }

    /** The containers (context + filters applied) the current cached index was built from. */
    public List<StoredContainer> scopeContainers(ChestManager manager, String contextKey, ChestTypeFilter typeFilter, ChestGroupFilter groupFilter) {
        refresh(manager, contextKey, typeFilter, groupFilter);
        return scopeContainers;
    }

    private void refresh(ChestManager manager, String contextKey, ChestTypeFilter typeFilter, ChestGroupFilter groupFilter) {
        if (manager == null || contextKey == null) {
            key = null;
            index = ChestItemIndex.empty();
            scopeContainers = List.of();
            return;
        }
        ChestTypeFilter safeType = typeFilter != null ? typeFilter : ChestTypeFilter.ALL;
        ChestGroupFilter safeGroup = groupFilter != null ? groupFilter : ChestGroupFilter.ALL;
        Key wanted = new Key(contextKey, manager.revision(), safeType, safeGroup);
        if (Objects.equals(wanted, key)) return;

        List<StoredContainer> scoped = new ArrayList<>();
        for (StoredContainer container : manager.getContainers(contextKey)) {
            if (safeType.matches(container.kind()) && safeGroup.matches(container)) {
                scoped.add(container);
            }
        }
        this.scopeContainers = List.copyOf(scoped);
        this.index = ChestItemIndex.build(contextKey, scoped, manager::itemDisplayName);
        this.key = wanted;
    }

    public void invalidate() {
        key = null;
    }
}
