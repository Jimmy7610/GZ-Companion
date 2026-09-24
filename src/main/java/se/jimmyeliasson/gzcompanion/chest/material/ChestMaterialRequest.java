package se.jimmyeliasson.gzcompanion.chest.material;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A material requirement set handed from a planner (Settlement / Byggplaner) to Kistor's
 * "Hitta material i kistor" view. Plain data only; holds no chest state itself.
 *
 * @param title       what the materials are for, e.g. "Settlement nivå 4 → 6" or a building name.
 * @param sourceLabel which planner produced it, e.g. "Settlement" or "Byggplaner".
 */
public record ChestMaterialRequest(String title, String sourceLabel, List<MaterialNeed> needs) {
    public ChestMaterialRequest {
        title = title != null ? title : "";
        sourceLabel = sourceLabel != null ? sourceLabel : "";
        needs = mergeDuplicates(needs);
    }

    /**
     * Identical concrete item ids are merged into one need (amounts summed, first order and name
     * kept); category needs are merged by display name. Zero-amount needs are dropped.
     */
    static List<MaterialNeed> mergeDuplicates(List<MaterialNeed> raw) {
        if (raw == null) return List.of();
        Map<String, MaterialNeed> merged = new LinkedHashMap<>();
        for (MaterialNeed need : raw) {
            if (need == null || need.needed() <= 0) continue;
            String key = need.isTrackable() ? need.itemId() : "category:" + need.displayName();
            MaterialNeed existing = merged.get(key);
            if (existing == null) {
                merged.put(key, need);
            } else {
                long sum = (long) existing.needed() + need.needed();
                merged.put(key, new MaterialNeed(existing.itemId(), existing.displayName(), (int) Math.min(Integer.MAX_VALUE, sum)));
            }
        }
        return List.copyOf(new ArrayList<>(merged.values()));
    }
}
