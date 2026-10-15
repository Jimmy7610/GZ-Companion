package se.jimmyeliasson.gzcompanion.marketwatch.storage;

import java.util.Objects;

/**
 * One purely local "Mina anteckningar" watchlist entry. Never server truth - GameZone's actual
 * live demand/price is only ever seen by manually opening {@code /marketwatch} in-game; this is
 * the player's own local note about a resource they're tracking.
 *
 * <p>Price/demand/quantity notes are deliberately combined into one free-text {@code note}
 * field rather than three separate structured fields, mirroring the same simplification already
 * applied to Settlement Companion's member organizer - the player can write whatever structure
 * they like into it.
 *
 * @param location optional place the player saved themselves (typed X/Y/Z or "Använd min plats"); {@code null} for
 *                 every note that never had one, including all notes written before this field existed.
 */
public record MarketWatchNote(String id, String itemId, String displayName, String categoryId, String note,
                               long lastObservedAtMs, boolean favorite, MarketWatchSavedLocation location) {
    public MarketWatchNote {
        Objects.requireNonNull(id, "id");
        displayName = (displayName != null && !displayName.isBlank()) ? displayName.trim()
                : (itemId != null ? itemId : "Okänt föremål");
        note = note != null ? note.trim() : "";
    }

    /** A note without a saved location. */
    public MarketWatchNote(String id, String itemId, String displayName, String categoryId, String note,
                           long lastObservedAtMs, boolean favorite) {
        this(id, itemId, displayName, categoryId, note, lastObservedAtMs, favorite, null);
    }

    public boolean hasLocation() {
        return location != null;
    }

    public MarketWatchNote withNote(String newNote) {
        return new MarketWatchNote(id, itemId, displayName, categoryId, newNote, lastObservedAtMs, favorite, location);
    }

    public MarketWatchNote withFavorite(boolean newFavorite) {
        return new MarketWatchNote(id, itemId, displayName, categoryId, note, lastObservedAtMs, newFavorite, location);
    }

    public MarketWatchNote withLastObservedAtMs(long nowMs) {
        return new MarketWatchNote(id, itemId, displayName, categoryId, note, nowMs, favorite, location);
    }

    /** {@code null} removes the saved location. */
    public MarketWatchNote withLocation(MarketWatchSavedLocation newLocation) {
        return new MarketWatchNote(id, itemId, displayName, categoryId, note, lastObservedAtMs, favorite, newLocation);
    }
}
