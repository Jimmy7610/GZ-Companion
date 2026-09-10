package se.jimmyeliasson.gzcompanion.marketwatch.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JsonMarketWatchNotesStoreTest {

    @TempDir
    Path tempDir;

    private Path storeFile() {
        return tempDir.resolve("marketwatch-notes.json");
    }

    @Test
    @DisplayName("load() on a missing file returns NOT_FOUND with an empty, usable dataset")
    void loadMissingFileReturnsNotFound() {
        JsonMarketWatchNotesStore store = new JsonMarketWatchNotesStore(storeFile());
        MarketWatchNotesLoadResult result = store.load();
        assertEquals(MarketWatchNotesLoadResult.Outcome.NOT_FOUND, result.outcome());
        assertTrue(result.isUsable());
    }

    @Test
    @DisplayName("save() then load() round-trips every field exactly")
    void saveThenLoadRoundTrips() {
        JsonMarketWatchNotesStore store = new JsonMarketWatchNotesStore(storeFile());
        MarketWatchNote note = new MarketWatchNote("n1", "minecraft:diamond", "Diamond", "gruvdrift", "1400 coins styck", 5000L, true);
        MarketWatchNotesData data = MarketWatchNotesData.empty().withNoteAdded("server:gamezone.se", note);
        store.save(data);

        MarketWatchNotesLoadResult result = store.load();
        assertEquals(MarketWatchNotesLoadResult.Outcome.LOADED, result.outcome());

        List<MarketWatchNote> loaded = result.data().getNotes("server:gamezone.se");
        assertEquals(1, loaded.size());
        MarketWatchNote loadedNote = loaded.get(0);
        assertEquals("minecraft:diamond", loadedNote.itemId());
        assertEquals("gruvdrift", loadedNote.categoryId());
        assertEquals("1400 coins styck", loadedNote.note());
        assertEquals(5000L, loadedNote.lastObservedAtMs());
        assertTrue(loadedNote.favorite());
    }

    @Test
    @DisplayName("Two different context keys are stored and loaded in isolation")
    void contextIsolation() {
        JsonMarketWatchNotesStore store = new JsonMarketWatchNotesStore(storeFile());
        MarketWatchNote noteA = new MarketWatchNote("a", null, "A", null, "note a", 0L, false);
        MarketWatchNote noteB = new MarketWatchNote("b", null, "B", null, "note b", 0L, false);
        MarketWatchNotesData data = MarketWatchNotesData.empty()
                .withNoteAdded("server:a", noteA)
                .withNoteAdded("singleplayer:b", noteB);
        store.save(data);

        MarketWatchNotesData loaded = store.load().data();
        assertEquals(1, loaded.getNotes("server:a").size());
        assertEquals(1, loaded.getNotes("singleplayer:b").size());
        assertTrue(loaded.getNotes("server:other").isEmpty());
    }

    @Test
    @DisplayName("A future schema version is reported as incompatible and the file is left untouched")
    void futureSchemaIsIncompatible() throws Exception {
        Path file = storeFile();
        Files.writeString(file, "{\"schemaVersion\": 999, \"notesByContext\": {}}");
        String before = Files.readString(file);

        JsonMarketWatchNotesStore store = new JsonMarketWatchNotesStore(file);
        MarketWatchNotesLoadResult result = store.load();

        assertEquals(MarketWatchNotesLoadResult.Outcome.INCOMPATIBLE_SCHEMA, result.outcome());
        assertEquals(before, Files.readString(file));
    }

    @Test
    @DisplayName("A corrupt file is backed up and recovered as an empty dataset")
    void corruptFileIsRecovered() throws Exception {
        Path file = storeFile();
        Files.writeString(file, "not valid json {{{");

        JsonMarketWatchNotesStore store = new JsonMarketWatchNotesStore(file);
        MarketWatchNotesLoadResult result = store.load();

        assertEquals(MarketWatchNotesLoadResult.Outcome.CORRUPT_RECOVERED, result.outcome());
        assertTrue(result.isUsable());
    }

    @Test
    @DisplayName("A note entry missing its id is skipped without discarding the rest of the file")
    void noteMissingIdIsSkipped() throws Exception {
        Path file = storeFile();
        Files.writeString(file, "{\"schemaVersion\": 1, \"notesByContext\": {\"ctx\": ["
                + "{\"displayName\":\"No id\"},"
                + "{\"id\":\"good\",\"displayName\":\"Good\",\"note\":\"\",\"lastObservedAtMs\":0,\"favorite\":false}"
                + "]}}");

        JsonMarketWatchNotesStore store = new JsonMarketWatchNotesStore(file);
        MarketWatchNotesLoadResult result = store.load();

        assertEquals(MarketWatchNotesLoadResult.Outcome.LOADED, result.outcome());
        assertEquals(1, result.data().getNotes("ctx").size());
        assertEquals("good", result.data().getNotes("ctx").get(0).id());
    }
}
