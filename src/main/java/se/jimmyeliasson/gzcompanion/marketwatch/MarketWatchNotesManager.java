package se.jimmyeliasson.gzcompanion.marketwatch;

import se.jimmyeliasson.gzcompanion.marketwatch.storage.MarketWatchNote;
import se.jimmyeliasson.gzcompanion.marketwatch.storage.MarketWatchNotesData;
import se.jimmyeliasson.gzcompanion.marketwatch.storage.MarketWatchNotesStatus;
import se.jimmyeliasson.gzcompanion.marketwatch.storage.MarketWatchNotesStore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Runtime coordinator for the local MarketWatch watchlist/notes, isolated per
 * GameZone-server-vs-singleplayer context exactly like {@code ChestManager}/
 * {@code SettlementPlannerManager}/{@code BuildingPlanManager}. These are always explicitly the
 * player's OWN local notes - never a claim of GameZone's actual live market/demand state.
 */
public class MarketWatchNotesManager {
    private final MarketWatchNotesStore store;
    private MarketWatchNotesData data;
    private MarketWatchNotesStatus status = MarketWatchNotesStatus.UNAVAILABLE;

    public MarketWatchNotesManager(MarketWatchNotesStore store) {
        this.store = store;
    }

    public void initialize() {
        try {
            var result = store.load();
            if (result == null) {
                this.data = MarketWatchNotesData.empty();
                this.status = MarketWatchNotesStatus.ERROR;
                return;
            }
            switch (result.outcome()) {
                case NOT_FOUND, LOADED, CORRUPT_RECOVERED -> {
                    this.data = result.data();
                    this.status = MarketWatchNotesStatus.LOADED;
                }
                case INCOMPATIBLE_SCHEMA -> {
                    this.data = MarketWatchNotesData.empty();
                    this.status = MarketWatchNotesStatus.INCOMPATIBLE;
                }
                case ERROR -> {
                    this.data = MarketWatchNotesData.empty();
                    this.status = MarketWatchNotesStatus.ERROR;
                }
            }
        } catch (Exception e) {
            this.data = MarketWatchNotesData.empty();
            this.status = MarketWatchNotesStatus.ERROR;
        }
    }

    public MarketWatchNotesStatus getStatus() {
        return status;
    }

    private boolean requireLoaded() {
        return status == MarketWatchNotesStatus.LOADED;
    }

    public List<MarketWatchNote> getNotes(String contextKey) {
        if (contextKey == null || data == null) return List.of();
        return data.getNotes(contextKey);
    }

    public String addNote(String contextKey, String itemId, String displayName, String categoryId, String note, long nowMs) {
        if (!requireLoaded() || contextKey == null) return null;
        String id = UUID.randomUUID().toString();
        MarketWatchNote entry = new MarketWatchNote(id, itemId, displayName, categoryId, note, nowMs, false);
        data = data.withNoteAdded(contextKey, entry);
        store.save(data);
        return id;
    }

    public boolean updateNote(String contextKey, MarketWatchNote updated) {
        if (!requireLoaded() || contextKey == null || updated == null) return false;
        data = data.withNoteReplaced(contextKey, updated);
        store.save(data);
        return true;
    }

    public boolean toggleFavorite(String contextKey, String noteId) {
        if (!requireLoaded() || contextKey == null || noteId == null) return false;
        MarketWatchNote existing = getNotes(contextKey).stream().filter(n -> n.id().equals(noteId)).findFirst().orElse(null);
        if (existing == null) return false;
        data = data.withNoteReplaced(contextKey, existing.withFavorite(!existing.favorite()));
        store.save(data);
        return true;
    }

    public boolean markObservedNow(String contextKey, String noteId, long nowMs) {
        if (!requireLoaded() || contextKey == null || noteId == null) return false;
        MarketWatchNote existing = getNotes(contextKey).stream().filter(n -> n.id().equals(noteId)).findFirst().orElse(null);
        if (existing == null) return false;
        data = data.withNoteReplaced(contextKey, existing.withLastObservedAtMs(nowMs));
        store.save(data);
        return true;
    }

    public boolean deleteNote(String contextKey, String noteId) {
        if (!requireLoaded() || contextKey == null || noteId == null) return false;
        data = data.withNoteRemoved(contextKey, noteId);
        store.save(data);
        return true;
    }

    /** Deletes every local MarketWatch note for one context. Used by the Settings tab's "Rensa MarketWatch-anteckningar" action. */
    public boolean clearContext(String contextKey) {
        if (!requireLoaded() || contextKey == null) return false;
        data = data.withNotes(contextKey, List.of());
        store.save(data);
        return true;
    }

    /** Local-only search + sort: favorites first, then most recently observed. Never touches disk/network beyond the initial load. */
    public List<MarketWatchNote> search(String contextKey, String query) {
        String q = (query != null && !query.isBlank()) ? query.trim().toLowerCase(Locale.ROOT) : null;
        List<MarketWatchNote> result = new ArrayList<>();
        for (MarketWatchNote note : getNotes(contextKey)) {
            if (q != null && !matches(note, q)) continue;
            result.add(note);
        }
        result.sort(Comparator.comparing(MarketWatchNote::favorite).reversed()
                .thenComparing(Comparator.comparingLong(MarketWatchNote::lastObservedAtMs).reversed()));
        return result;
    }

    private boolean matches(MarketWatchNote note, String q) {
        if (note.displayName().toLowerCase(Locale.ROOT).contains(q)) return true;
        if (note.itemId() != null && note.itemId().toLowerCase(Locale.ROOT).contains(q)) return true;
        if (note.note().toLowerCase(Locale.ROOT).contains(q)) return true;
        return note.categoryId() != null && note.categoryId().toLowerCase(Locale.ROOT).contains(q);
    }
}
