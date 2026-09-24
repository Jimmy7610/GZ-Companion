package se.jimmyeliasson.gzcompanion.chest.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Local cached "last known" snapshot of one physically opened storage location.
 * Represents ONLY what the client legitimately observed when the player opened it.
 * Never implies live/current state.
 *
 * <p>Kistor 2.0 adds local-only {@link StorageMetadata} (favorite, group, location note) and at
 * most ONE {@link PreviousSnapshot} (the legitimate snapshot from the opening before the current
 * one), used only to show "sedan förra öppningen" differences.
 */
public record StoredContainer(
    StoredContainerId id,
    String label,
    StoragePosition partner,
    StorageShape shape,
    long lastOpenedAtMs,
    List<ChestSlotEntry> slots,
    StorageMetadata metadata,
    PreviousSnapshot previousSnapshot
) {
    public StoredContainer {
        Objects.requireNonNull(id, "id");
        shape = shape != null ? shape : StorageShape.UNKNOWN;
        slots = slots != null ? List.copyOf(slots) : List.of();
        metadata = metadata != null ? metadata : StorageMetadata.EMPTY;
    }

    /** Pre-Kistor-2.0 shape: no metadata, no previous snapshot. */
    public StoredContainer(StoredContainerId id, String label, StoragePosition partner, StorageShape shape,
                           long lastOpenedAtMs, List<ChestSlotEntry> slots) {
        this(id, label, partner, shape, lastOpenedAtMs, slots, StorageMetadata.EMPTY, null);
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
        return shape == StorageShape.DOUBLE;
    }

    public boolean hasLabel() {
        return label != null && !label.isBlank();
    }

    public boolean favorite() {
        return metadata.favorite();
    }

    public String group() {
        return metadata.group();
    }

    public String locationNote() {
        return metadata.locationNote();
    }

    public boolean hasPreviousSnapshot() {
        return previousSnapshot != null;
    }

    /** The primary display title: the local label if set, otherwise the storage type text. */
    public String displayTitle() {
        return hasLabel() ? label : storageTypeText();
    }

    /** "Dubbel kista" for a proven double chest, otherwise the storage kind's display name. */
    public String storageTypeText() {
        if (shape == StorageShape.DOUBLE && kind() == StorageKind.CHEST) return "Dubbel kista";
        if (shape == StorageShape.DOUBLE && kind() == StorageKind.TRAPPED_CHEST) return "Dubbel fällkista";
        return kind().getDisplayName();
    }

    public StoredContainer withLabel(String newLabel) {
        return new StoredContainer(id, newLabel, partner, shape, lastOpenedAtMs, slots, metadata, previousSnapshot);
    }

    public StoredContainer withMetadata(StorageMetadata newMetadata) {
        return new StoredContainer(id, label, partner, shape, lastOpenedAtMs, slots, newMetadata, previousSnapshot);
    }

    /** Replaces the snapshot in place, keeping label, metadata and the existing previous snapshot. */
    public StoredContainer withSnapshot(StoragePosition newPartner, StorageShape newShape, long newLastOpenedAtMs, List<ChestSlotEntry> newSlots) {
        return new StoredContainer(id, label, newPartner, newShape, newLastOpenedAtMs, newSlots, metadata, previousSnapshot);
    }

    /**
     * A new legitimate finalized snapshot: the current snapshot becomes the single retained
     * previous snapshot (the older previous one is dropped - history is bounded to one step).
     * Label and local metadata are preserved.
     */
    public StoredContainer withNewSnapshot(StoragePosition newPartner, StorageShape newShape, long newLastOpenedAtMs, List<ChestSlotEntry> newSlots) {
        PreviousSnapshot rolled = new PreviousSnapshot(lastOpenedAtMs, slots);
        return new StoredContainer(id, label, newPartner, newShape, newLastOpenedAtMs, newSlots, metadata, rolled);
    }

    /**
     * Aggregates identical item IDs across raw slots into a compact display list, ordered by
     * first appearance. Internal raw slot data is preserved separately in {@link #slots()}.
     */
    public List<AggregatedItem> aggregatedItems() {
        return aggregate(slots);
    }

    public static List<AggregatedItem> aggregate(List<ChestSlotEntry> slots) {
        List<AggregatedItem> result = new ArrayList<>();
        if (slots == null) return result;
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
