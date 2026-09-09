package se.jimmyeliasson.gzcompanion.chest.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Local cached "last known" snapshot of one physically opened storage location.
 * Represents ONLY what the client legitimately observed when the player opened it.
 * Never implies live/current state.
 */
public record StoredContainer(
    StoredContainerId id,
    String label,
    StoragePosition partner,
    boolean partnerUnknown,
    long lastOpenedAtMs,
    List<ChestSlotEntry> slots
) {
    public StoredContainer {
        Objects.requireNonNull(id, "id");
        slots = slots != null ? List.copyOf(slots) : List.of();
    }

    public StorageKind kind() {
        return id.kind();
    }

    public StoragePosition anchor() {
        return id.anchor();
    }

    public String dimensionKey() {
        return id.dimensionKey();
    }

    public boolean isDoubleWide() {
        return partner != null;
    }

    public StoredContainer withLabel(String newLabel) {
        return new StoredContainer(id, newLabel, partner, partnerUnknown, lastOpenedAtMs, slots);
    }

    public StoredContainer withSnapshot(StoragePosition newPartner, boolean newPartnerUnknown, long newLastOpenedAtMs, List<ChestSlotEntry> newSlots) {
        return new StoredContainer(id, label, newPartner, newPartnerUnknown, newLastOpenedAtMs, newSlots);
    }

    /**
     * Aggregates identical item IDs across raw slots into a compact display list, ordered by
     * first appearance. Internal raw slot data is preserved separately in {@link #slots()}.
     */
    public List<AggregatedItem> aggregatedItems() {
        List<AggregatedItem> result = new ArrayList<>();
        for (ChestSlotEntry slot : slots) {
            if (slot.count() <= 0) continue;
            boolean merged = false;
            for (int i = 0; i < result.size(); i++) {
                AggregatedItem existing = result.get(i);
                if (existing.itemId().equals(slot.itemId())) {
                    result.set(i, new AggregatedItem(existing.itemId(), existing.count() + slot.count()));
                    merged = true;
                    break;
                }
            }
            if (!merged) {
                result.add(new AggregatedItem(slot.itemId(), slot.count()));
            }
        }
        return Collections.unmodifiableList(result);
    }

    public record AggregatedItem(String itemId, int count) {}
}
