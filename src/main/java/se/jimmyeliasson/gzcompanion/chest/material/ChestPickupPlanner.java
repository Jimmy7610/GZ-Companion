package se.jimmyeliasson.gzcompanion.chest.material;

import se.jimmyeliasson.gzcompanion.chest.index.ChestItemEntry;
import se.jimmyeliasson.gzcompanion.chest.index.ChestItemIndex;
import se.jimmyeliasson.gzcompanion.chest.index.ItemLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds a local "Hämtningslista" from a material request and the last-known item index.
 *
 * <p><b>Allocation rule (deterministic, explainable, documented in docs/CHEST-MANAGER.md):</b>
 * for each required material, in the request's own order, take from the storage locations that
 * last held it in {@link ChestItemIndex}'s location order - <em>largest last-known amount first</em>
 * (fewest stops), ties broken by <em>most recently opened</em> (freshest data), then by stable
 * storage key - until the need is covered or the known stock runs out. Whatever the last-known
 * stock can't cover becomes a {@link PickupPlan.Shortage}. Player position is deliberately NOT
 * an input, so the list never reshuffles while the player walks around.
 *
 * <p>Groups are ordered by total pickup amount (largest first), then title, then stable key;
 * lines inside a group keep the request's material order.
 */
public final class ChestPickupPlanner {
    private ChestPickupPlanner() {}

    public static PickupPlan plan(ChestMaterialRequest request, ChestItemIndex index) {
        if (request == null) return new PickupPlan(List.of(), List.of(), List.of());
        ChestItemIndex safeIndex = index != null ? index : ChestItemIndex.empty();

        Map<String, GroupBuilder> groups = new LinkedHashMap<>();
        List<PickupPlan.Shortage> shortages = new ArrayList<>();
        List<MaterialNeed> untrackable = new ArrayList<>();

        for (MaterialNeed need : request.needs()) {
            if (!need.isTrackable()) {
                untrackable.add(need);
                continue;
            }
            int remaining = need.needed();
            ChestItemEntry entry = safeIndex.get(need.itemId()).orElse(null);
            if (entry != null) {
                for (ItemLocation location : entry.locations()) { // already in allocation order
                    if (remaining <= 0) break;
                    int take = Math.min(remaining, location.count());
                    if (take <= 0) continue;
                    groups.computeIfAbsent(location.id().asStableKey(), k -> new GroupBuilder(location))
                            .lines.add(new PickupPlan.Line(need.itemId(), displayName(need, entry), take));
                    remaining -= take;
                }
            }
            if (remaining > 0) {
                shortages.add(new PickupPlan.Shortage(need.itemId(), displayName(need, entry), remaining));
            }
        }

        List<PickupPlan.Group> built = new ArrayList<>();
        for (GroupBuilder b : groups.values()) {
            built.add(new PickupPlan.Group(b.location.id(), b.location.title(), b.location.lastOpenedAtMs(), b.lines));
        }
        built.sort(Comparator.comparingInt(PickupPlan.Group::totalAmount).reversed()
                .thenComparing(PickupPlan.Group::title, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(g -> g.storageId().asStableKey()));
        return new PickupPlan(built, shortages, untrackable);
    }

    /** The planner's own name for the material wins; the index's resolved name is only a fallback for a bare id. */
    private static String displayName(MaterialNeed need, ChestItemEntry entry) {
        if (entry != null && need.displayName().equals(need.itemId())) return entry.displayName();
        return need.displayName();
    }

    private static final class GroupBuilder {
        final ItemLocation location;
        final List<PickupPlan.Line> lines = new ArrayList<>();

        GroupBuilder(ItemLocation location) {
            this.location = location;
        }
    }
}
