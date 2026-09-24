package se.jimmyeliasson.gzcompanion.chest.model;

import java.util.Objects;

/**
 * Emitted exactly once when a legitimate capture session has been finalized AND persisted (see
 * {@code ChestManager.endCapture}). Never emitted while a storage screen is still open, and never
 * for a session that failed closed without a legitimate snapshot.
 *
 * @param contentsChanged whether the persisted contents differ from the previously stored
 *                        snapshot (always true for {@link Kind#NEW}).
 */
public record ChestCaptureEvent(Kind kind, StoredContainer container, boolean contentsChanged) {
    public enum Kind {
        /** The storage location was not in the index before this finalization. */
        NEW,
        /** A known storage location was legitimately reopened and its snapshot replaced. */
        UPDATED
    }

    public ChestCaptureEvent {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(container, "container");
    }

    public int distinctItemCount() {
        return container.aggregatedItems().size();
    }
}
