package se.jimmyeliasson.gzcompanion.marketwatch.storage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Versioned root schema for {@code config/gzcompanion/marketwatch-notes.json}. Isolated per
 * GameZone-server-vs-singleplayer context, exactly like Settlement/Building planner state.
 */
public record MarketWatchNotesData(int schemaVersion, Map<String, List<MarketWatchNote>> notesByContext) {
    public static final int CURRENT_SCHEMA = 1;

    public MarketWatchNotesData {
        notesByContext = notesByContext != null ? new HashMap<>(notesByContext) : new HashMap<>();
    }

    public static MarketWatchNotesData empty() {
        return new MarketWatchNotesData(CURRENT_SCHEMA, new HashMap<>());
    }

    public List<MarketWatchNote> getNotes(String contextKey) {
        return notesByContext.getOrDefault(contextKey, List.of());
    }

    public MarketWatchNotesData withNotes(String contextKey, List<MarketWatchNote> updated) {
        Map<String, List<MarketWatchNote>> copy = new HashMap<>(notesByContext);
        copy.put(contextKey, List.copyOf(updated));
        return new MarketWatchNotesData(schemaVersion, copy);
    }

    public MarketWatchNotesData withNoteAdded(String contextKey, MarketWatchNote note) {
        List<MarketWatchNote> updated = new ArrayList<>(getNotes(contextKey));
        updated.add(note);
        return withNotes(contextKey, updated);
    }

    public MarketWatchNotesData withNoteReplaced(String contextKey, MarketWatchNote note) {
        List<MarketWatchNote> updated = new ArrayList<>();
        for (MarketWatchNote existing : getNotes(contextKey)) {
            updated.add(existing.id().equals(note.id()) ? note : existing);
        }
        return withNotes(contextKey, updated);
    }

    public MarketWatchNotesData withNoteRemoved(String contextKey, String noteId) {
        List<MarketWatchNote> updated = new ArrayList<>();
        for (MarketWatchNote existing : getNotes(contextKey)) {
            if (!existing.id().equals(noteId)) updated.add(existing);
        }
        return withNotes(contextKey, updated);
    }
}
