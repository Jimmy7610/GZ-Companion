package se.jimmyeliasson.gzcompanion.marketwatch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import se.jimmyeliasson.gzcompanion.marketwatch.storage.MarketWatchNote;
import se.jimmyeliasson.gzcompanion.marketwatch.storage.MarketWatchNotesData;
import se.jimmyeliasson.gzcompanion.marketwatch.storage.MarketWatchNotesLoadResult;
import se.jimmyeliasson.gzcompanion.marketwatch.storage.MarketWatchNotesStatus;
import se.jimmyeliasson.gzcompanion.marketwatch.storage.MarketWatchNotesStore;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MarketWatchNotesManagerTest {

    private static final class InMemoryStore implements MarketWatchNotesStore {
        private MarketWatchNotesData data = MarketWatchNotesData.empty();
        private MarketWatchNotesLoadResult.Outcome loadOutcome = MarketWatchNotesLoadResult.Outcome.NOT_FOUND;

        @Override
        public MarketWatchNotesLoadResult load() {
            return new MarketWatchNotesLoadResult(loadOutcome, data);
        }

        @Override
        public void save(MarketWatchNotesData data) {
            this.data = data;
        }
    }

    @Test
    @DisplayName("initialize() with no existing file leaves the manager LOADED")
    void initializeWithNoFileIsLoaded() {
        MarketWatchNotesManager manager = new MarketWatchNotesManager(new InMemoryStore());
        manager.initialize();
        assertEquals(MarketWatchNotesStatus.LOADED, manager.getStatus());
    }

    @Test
    @DisplayName("Mutations before initialize() are refused")
    void mutationsBeforeInitializeAreRefused() {
        MarketWatchNotesManager manager = new MarketWatchNotesManager(new InMemoryStore());
        assertNull(manager.addNote("ctx", null, "Diamond", null, "note", 0L));
        assertFalse(manager.deleteNote("ctx", "n1"));
    }

    @Test
    @DisplayName("addNote then search retrieves the note by item, category, and free-text note")
    void addAndSearch() {
        MarketWatchNotesManager manager = new MarketWatchNotesManager(new InMemoryStore());
        manager.initialize();
        manager.addNote("ctx", "minecraft:diamond", null, "gruvdrift", "1400 coins", 1000L);

        assertEquals(1, manager.search("ctx", "diamond").size());
        assertEquals(1, manager.search("ctx", "gruvdrift").size());
        assertEquals(1, manager.search("ctx", "1400").size());
        assertEquals(0, manager.search("ctx", "nonexistent").size());
        assertEquals(1, manager.search("ctx", null).size());
    }

    @Test
    @DisplayName("toggleFavorite flips membership and search sorts favorites first")
    void toggleFavoriteAndSortOrder() {
        MarketWatchNotesManager manager = new MarketWatchNotesManager(new InMemoryStore());
        manager.initialize();
        String idA = manager.addNote("ctx", null, "A", null, "", 1000L);
        String idB = manager.addNote("ctx", null, "B", null, "", 2000L);

        manager.toggleFavorite("ctx", idA);
        List<MarketWatchNote> sorted = manager.search("ctx", null);
        assertEquals(idA, sorted.get(0).id(), "The favorited note must be sorted first even though it's older.");

        manager.toggleFavorite("ctx", idA);
        assertFalse(manager.getNotes("ctx").stream().filter(n -> n.id().equals(idA)).findFirst().orElseThrow().favorite());
    }

    @Test
    @DisplayName("markObservedNow updates lastObservedAtMs for exactly the targeted note")
    void markObservedNowUpdatesOnlyTargetNote() {
        MarketWatchNotesManager manager = new MarketWatchNotesManager(new InMemoryStore());
        manager.initialize();
        String id = manager.addNote("ctx", null, "A", null, "", 0L);

        manager.markObservedNow("ctx", id, 9999L);
        assertEquals(9999L, manager.getNotes("ctx").get(0).lastObservedAtMs());
    }

    @Test
    @DisplayName("deleteNote removes exactly the targeted note and leaves the rest of the context untouched")
    void deleteNoteRemovesOnlyTarget() {
        MarketWatchNotesManager manager = new MarketWatchNotesManager(new InMemoryStore());
        manager.initialize();
        String idA = manager.addNote("ctx", null, "A", null, "", 0L);
        String idB = manager.addNote("ctx", null, "B", null, "", 0L);

        assertTrue(manager.deleteNote("ctx", idA));
        List<MarketWatchNote> remaining = manager.getNotes("ctx");
        assertEquals(1, remaining.size());
        assertEquals(idB, remaining.get(0).id());
    }

    @Test
    @DisplayName("Notes in different contexts never leak into each other")
    void contextIsolation() {
        MarketWatchNotesManager manager = new MarketWatchNotesManager(new InMemoryStore());
        manager.initialize();
        manager.addNote("server:a", null, "A", null, "", 0L);
        manager.addNote("singleplayer:b", null, "B", null, "", 0L);

        assertEquals(1, manager.getNotes("server:a").size());
        assertEquals(1, manager.getNotes("singleplayer:b").size());
        assertTrue(manager.getNotes("server:other").isEmpty());
    }
}
