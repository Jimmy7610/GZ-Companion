package se.jimmyeliasson.gzcompanion.chest.material;

import se.jimmyeliasson.gzcompanion.chest.index.ChestItemEntry;
import se.jimmyeliasson.gzcompanion.chest.index.ChestItemIndex;

import java.util.ArrayList;
import java.util.List;

/**
 * "Hitta material i kistor": for each required material, the needed amount, the last-known amount
 * across already-indexed storage, the estimated missing amount, and where it was last known to
 * be. Uses ONLY existing local last-known snapshots (via {@link ChestItemIndex}); callers must
 * only invoke this when the player's "use last-known chest data in planners" setting is on.
 */
public final class ChestMaterialAvailability {
    private ChestMaterialAvailability() {}

    public static List<MaterialAvailability> compute(ChestMaterialRequest request, ChestItemIndex index) {
        if (request == null) return List.of();
        ChestItemIndex safeIndex = index != null ? index : ChestItemIndex.empty();
        List<MaterialAvailability> result = new ArrayList<>(request.needs().size());
        for (MaterialNeed need : request.needs()) {
            if (!need.isTrackable()) {
                // A category requirement can't honestly be matched to one item id - never guessed.
                result.add(new MaterialAvailability(need, 0, need.needed(), List.of()));
                continue;
            }
            ChestItemEntry entry = safeIndex.get(need.itemId()).orElse(null);
            int total = entry != null ? entry.totalCount() : 0;
            int missing = Math.max(0, need.needed() - total);
            result.add(new MaterialAvailability(need, total, missing, entry != null ? entry.locations() : List.of()));
        }
        return result;
    }

    /** Count of trackable materials the last-known estimate fully covers. */
    public static int coveredCount(List<MaterialAvailability> rows) {
        int n = 0;
        for (MaterialAvailability row : rows) {
            if (row.isCoveredByEstimate()) n++;
        }
        return n;
    }
}
