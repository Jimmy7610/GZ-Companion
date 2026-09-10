package se.jimmyeliasson.gzcompanion.marketwatch.storage;

/**
 * Local-first persistence boundary for the MarketWatch local watchlist/notes.
 */
public interface MarketWatchNotesStore {
    MarketWatchNotesLoadResult load();

    void save(MarketWatchNotesData data);
}
