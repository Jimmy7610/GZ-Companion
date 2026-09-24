package se.jimmyeliasson.gzcompanion.chest.index;

import se.jimmyeliasson.gzcompanion.chest.model.ChestItemSortMode;
import se.jimmyeliasson.gzcompanion.chest.model.ChestSlotEntry;
import se.jimmyeliasson.gzcompanion.chest.model.StoredContainer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Kistor 2.0 "SAKER" aggregated item index: for every item id seen in the already-stored,
 * legitimately captured snapshots of ONE storage context, the total last-known count and every
 * contributing storage location.
 *
 * <p>Built purely from {@link StoredContainer} data already held by {@code ChestManager} - it
 * never scans the world, never queries an unopened container, and contains no Minecraft types.
 * Immutable once built; callers cache it per {@code ChestManager.revision()} (see
 * {@link ChestItemIndexCache}) instead of rebuilding it every frame.
 */
public final class ChestItemIndex {
    private static final ChestItemIndex EMPTY = new ChestItemIndex(List.of(), Map.of());

    private final List<ChestItemEntry> entries;
    private final Map<String, ChestItemEntry> byItemId;

    private ChestItemIndex(List<ChestItemEntry> entries, Map<String, ChestItemEntry> byItemId) {
        this.entries = entries;
        this.byItemId = byItemId;
    }

    public static ChestItemIndex empty() {
        return EMPTY;
    }

    /**
     * @param contextKey    only containers whose identity belongs to this context are counted -
     *                      a defensive guarantee that one world/server's storage can never leak
     *                      into another's totals, even if a caller passes a mixed list.
     * @param containers    already-indexed containers.
     * @param displayNameFn resolves an item id to a display name (resolved once per item here).
     */
    public static ChestItemIndex build(String contextKey, List<StoredContainer> containers, Function<String, String> displayNameFn) {
        if (containers == null || containers.isEmpty()) return EMPTY;
        Function<String, String> names = displayNameFn != null ? displayNameFn : id -> id;

        // Keyed by the container's stable key (never a list index, never the record itself).
        Map<String, StoredContainer> containersByKey = new HashMap<>();
        Map<String, Map<String, Integer>> perItem = new LinkedHashMap<>();
        for (StoredContainer container : containers) {
            if (container == null) continue;
            if (contextKey != null && !Objects.equals(container.id().contextStorageKey(), contextKey)) continue;
            String key = container.id().asStableKey();
            containersByKey.put(key, container);
            for (ChestSlotEntry slot : container.slots()) {
                if (!isCountable(slot)) continue;
                perItem.computeIfAbsent(slot.itemId(), k -> new LinkedHashMap<>())
                        .merge(key, slot.count(), ChestItemIndex::saturatingAdd);
            }
        }

        List<ChestItemEntry> built = new ArrayList<>(perItem.size());
        Map<String, ChestItemEntry> lookup = new HashMap<>();
        for (Map.Entry<String, Map<String, Integer>> e : perItem.entrySet()) {
            List<ItemLocation> locations = new ArrayList<>();
            int total = 0;
            for (Map.Entry<String, Integer> c : e.getValue().entrySet()) {
                locations.add(ItemLocation.of(containersByKey.get(c.getKey()), c.getValue()));
                total = saturatingAdd(total, c.getValue());
            }
            locations.sort(LOCATION_ORDER);
            String name = names.apply(e.getKey());
            ChestItemEntry entry = new ChestItemEntry(e.getKey(), name != null && !name.isBlank() ? name : e.getKey(), total, locations);
            built.add(entry);
            lookup.put(entry.itemId(), entry);
        }
        built.sort(comparator(ChestItemSortMode.COUNT));
        return new ChestItemIndex(Collections.unmodifiableList(built), Collections.unmodifiableMap(lookup));
    }

    private static boolean isCountable(ChestSlotEntry slot) {
        if (slot == null || slot.count() <= 0) return false;
        String id = slot.itemId();
        return id != null && !id.isBlank() && !id.equals("minecraft:air");
    }

    private static int saturatingAdd(int a, int b) {
        long sum = (long) a + (long) b;
        return sum > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) sum;
    }

    /** Largest amount first, then most recently opened, then stable key - fully deterministic. */
    static final Comparator<ItemLocation> LOCATION_ORDER = Comparator
            .comparingInt(ItemLocation::count).reversed()
            .thenComparing(Comparator.comparingLong(ItemLocation::lastOpenedAtMs).reversed())
            .thenComparing(l -> l.id().asStableKey());

    public static Comparator<ChestItemEntry> comparator(ChestItemSortMode mode) {
        Comparator<ChestItemEntry> byName = Comparator.comparing(ChestItemEntry::displayName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(ChestItemEntry::itemId);
        ChestItemSortMode safe = mode != null ? mode : ChestItemSortMode.COUNT;
        return switch (safe) {
            case COUNT -> Comparator.comparingInt(ChestItemEntry::totalCount).reversed().thenComparing(byName);
            case NAME -> byName;
            case SPREAD -> Comparator.comparingInt(ChestItemEntry::containerCount).reversed()
                    .thenComparing(Comparator.comparingInt(ChestItemEntry::totalCount).reversed())
                    .thenComparing(byName);
        };
    }

    /**
     * "Hitta närmaste": the location with the smallest horizontal distance to the player's own
     * position, considering ONLY locations in the player's current dimension (distance across
     * dimensions is never meaningful). Ties keep {@link #LOCATION_ORDER}. Never discovers anything
     * - it only chooses among already-known locations.
     */
    public static Optional<ItemLocation> nearestSameDimension(List<ItemLocation> locations, se.jimmyeliasson.gzcompanion.chest.nav.PlayerPose pose) {
        if (locations == null || pose == null) return Optional.empty();
        ItemLocation best = null;
        double bestDistance = Double.MAX_VALUE;
        for (ItemLocation location : locations) {
            if (!Objects.equals(location.dimensionKey(), pose.dimensionKey())) continue;
            double d = se.jimmyeliasson.gzcompanion.chest.nav.ChestNavigationMath.horizontalDistance(
                    pose.x(), pose.z(), location.anchor().x() + 0.5, location.anchor().z() + 0.5);
            if (d < bestDistance) {
                bestDistance = d;
                best = location;
            }
        }
        return Optional.ofNullable(best);
    }

    /** Every entry, default order (largest total first). */
    public List<ChestItemEntry> entries() {
        return entries;
    }

    public List<ChestItemEntry> entries(ChestItemSortMode mode) {
        if (mode == null || mode == ChestItemSortMode.COUNT) return entries;
        List<ChestItemEntry> copy = new ArrayList<>(entries);
        copy.sort(comparator(mode));
        return copy;
    }

    public Optional<ChestItemEntry> get(String itemId) {
        return Optional.ofNullable(itemId != null ? byItemId.get(itemId) : null);
    }

    /** Last-known total of one item across the index, 0 if never seen. */
    public int totalOf(String itemId) {
        return get(itemId).map(ChestItemEntry::totalCount).orElse(0);
    }

    public int size() {
        return entries.size();
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }
}
