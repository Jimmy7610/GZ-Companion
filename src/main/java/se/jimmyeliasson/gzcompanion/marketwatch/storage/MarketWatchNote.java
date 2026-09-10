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
 */
public record MarketWatchNote(String id, String itemId, String displayName, String categoryId, String note,
                               long lastObservedAtMs, boolean favorite) {
    public MarketWatchNote {
        Objects.requireNonNull(id, "id");
        displayName = (displayName != null && !displayName.isBlank()) ? displayName.trim()
                : (itemId != null ? itemId : "Okänt föremål");
        note = note != null ? note.trim() : "";
    }

    public MarketWatchNote withNote(String newNote) {
        return new MarketWatchNote(id, itemId, displayName, categoryId, newNote, lastObservedAtMs, favorite);
    }

    public MarketWatchNote withFavorite(boolean newFavorite) {
        return new MarketWatchNote(id, itemId, displayName, categoryId, note, lastObservedAtMs, newFavorite);
    }

    public MarketWatchNote withLastObservedAtMs(long nowMs) {
        return new MarketWatchNote(id, itemId, displayName, categoryId, note, nowMs, favorite);
    }
}
