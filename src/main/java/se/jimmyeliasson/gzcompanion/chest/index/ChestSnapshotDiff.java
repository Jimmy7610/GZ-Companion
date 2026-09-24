package se.jimmyeliasson.gzcompanion.chest.index;

import se.jimmyeliasson.gzcompanion.chest.model.ChestSlotEntry;
import se.jimmyeliasson.gzcompanion.chest.model.StoredContainer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * "Sedan förra öppningen": per-item count differences between a storage location's previous
 * legitimate snapshot and its current one. Only real differences are returned - unchanged items
 * are omitted, items that appeared or disappeared entirely are included - in a deterministic
 * order: largest gain first, largest loss last (i.e. by delta descending), ties by item id.
 */
public final class ChestSnapshotDiff {
    private ChestSnapshotDiff() {}

    public record ItemDelta(String itemId, int delta) {
        public boolean isGain() {
            return delta > 0;
        }
    }

    /** Empty when the container has no previous snapshot, or nothing changed. */
    public static List<ItemDelta> sincePrevious(StoredContainer container) {
        if (container == null || !container.hasPreviousSnapshot()) return List.of();
        return compute(container.previousSnapshot().slots(), container.slots());
    }

    public static List<ItemDelta> compute(List<ChestSlotEntry> previous, List<ChestSlotEntry> current) {
        Map<String, Long> before = totals(previous);
        Map<String, Long> after = totals(current);
        Set<String> ids = new LinkedHashSet<>(before.keySet());
        ids.addAll(after.keySet());

        List<ItemDelta> result = new ArrayList<>();
        for (String id : ids) {
            long delta = after.getOrDefault(id, 0L) - before.getOrDefault(id, 0L);
            if (delta == 0) continue;
            int clamped = (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, delta));
            result.add(new ItemDelta(id, clamped));
        }
        result.sort(Comparator.comparingInt(ItemDelta::delta).reversed().thenComparing(ItemDelta::itemId));
        return result;
    }

    private static Map<String, Long> totals(List<ChestSlotEntry> slots) {
        Map<String, Long> totals = new LinkedHashMap<>();
        if (slots == null) return totals;
        for (ChestSlotEntry slot : slots) {
            if (slot == null || slot.count() <= 0 || slot.itemId() == null || slot.itemId().isBlank()) continue;
            totals.merge(slot.itemId(), (long) slot.count(), Long::sum);
        }
        return totals;
    }
}
