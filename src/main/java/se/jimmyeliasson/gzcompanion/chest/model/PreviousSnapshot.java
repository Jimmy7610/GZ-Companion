package se.jimmyeliasson.gzcompanion.chest.model;

import java.util.List;

/**
 * The one previous legitimate snapshot kept per storage location, so Kistor can show what
 * changed "sedan förra öppningen" (since the previous opening). Deliberately bounded: exactly one
 * previous snapshot is ever retained - never an unbounded history.
 */
public record PreviousSnapshot(long openedAtMs, List<ChestSlotEntry> slots) {
    public PreviousSnapshot {
        slots = slots != null ? List.copyOf(slots) : List.of();
    }
}
