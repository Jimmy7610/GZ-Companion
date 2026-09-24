package se.jimmyeliasson.gzcompanion.chest.material;

import se.jimmyeliasson.gzcompanion.chest.model.StoredContainerId;

import java.util.List;

/**
 * A local "Hämtningslista": pickup suggestions grouped by storage location, plus any shortage
 * the last-known snapshots can't cover. Planning assistance only - nothing here withdraws items,
 * clicks slots, moves the player, or runs commands.
 */
public record PickupPlan(List<Group> groups, List<Shortage> shortages, List<MaterialNeed> untrackable) {
    public PickupPlan {
        groups = groups != null ? List.copyOf(groups) : List.of();
        shortages = shortages != null ? List.copyOf(shortages) : List.of();
        untrackable = untrackable != null ? List.copyOf(untrackable) : List.of();
    }

    public record Line(String itemId, String displayName, int amount) {
        /** Stable local checklist key for this line within its storage group. */
        public String checklistKey(StoredContainerId storage) {
            return storage.asStableKey() + "#" + itemId;
        }
    }

    public record Group(StoredContainerId storageId, String title, long lastOpenedAtMs, List<Line> lines) {
        public Group {
            lines = lines != null ? List.copyOf(lines) : List.of();
        }

        public int totalAmount() {
            long sum = 0;
            for (Line line : lines) sum += line.amount();
            return (int) Math.min(Integer.MAX_VALUE, sum);
        }
    }

    public record Shortage(String itemId, String displayName, int missing) {}

    public boolean isEmpty() {
        return groups.isEmpty() && shortages.isEmpty() && untrackable.isEmpty();
    }
}
