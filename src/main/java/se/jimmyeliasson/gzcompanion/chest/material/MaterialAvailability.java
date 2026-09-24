package se.jimmyeliasson.gzcompanion.chest.material;

import se.jimmyeliasson.gzcompanion.chest.index.ItemLocation;

import java.util.List;

/**
 * Last-known availability of one required material across the indexed storage of the current
 * context. {@code estimatedMissing} is an ESTIMATE ("saknas enligt estimat") from last-known
 * snapshots - never GameZone's live server inventory.
 */
public record MaterialAvailability(MaterialNeed need, int lastKnownTotal, int estimatedMissing, List<ItemLocation> locations) {
    public MaterialAvailability {
        locations = locations != null ? List.copyOf(locations) : List.of();
    }

    public boolean isTrackable() {
        return need.isTrackable();
    }

    public boolean isCoveredByEstimate() {
        return isTrackable() && estimatedMissing == 0;
    }
}
