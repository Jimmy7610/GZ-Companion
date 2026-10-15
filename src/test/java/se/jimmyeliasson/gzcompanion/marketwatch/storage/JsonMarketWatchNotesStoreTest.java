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

    @Test
    @DisplayName("A player-saved location round-trips exactly")
    void savedLocationRoundTrips() {
        JsonMarketWatchNotesStore store = new JsonMarketWatchNotesStore(storeFile());
        MarketWatchSavedLocation loc = MarketWatchSavedLocation.of("minecraft:overworld", -847, 70, 12, 5000L).orElseThrow();
        MarketWatchNote note = new MarketWatchNote("n1", "minecraft:beef", "Raw Beef", null, "Olivres Shop", 1L, false, loc);
        store.save(MarketWatchNotesData.empty().withNoteAdded("ctx", note));

        MarketWatchNote loaded = store.load().data().getNotes("ctx").get(0);
        assertEquals(loc, loaded.location());
        assertEquals(note, loaded);
    }

    @Test
    @DisplayName("Notes without a location are written exactly as before - no location key at all")
    void noLocationWritesNoLocationKey() throws Exception {
        JsonMarketWatchNotesStore store = new JsonMarketWatchNotesStore(storeFile());
        store.save(MarketWatchNotesData.empty().withNoteAdded("ctx", new MarketWatchNote("n1", null, "A", null, "x", 0L, false)));
        String json = Files.readString(storeFile());
        assertFalse(json.contains("location"), json);
        assertTrue(json.contains("\"schemaVersion\": 1"), "Additive field - no schema bump");
    }

    @Test
    @DisplayName("A legacy file written before locations existed loads unchanged, with no location")
    void legacyFileLoadsWithoutLocation() throws Exception {
        Files.writeString(storeFile(), "{\"schemaVersion\": 1, \"notesByContext\": {\"ctx\": ["
                + "{\"id\":\"old\",\"itemId\":\"minecraft:diamond\",\"displayName\":\"Diamond\",\"categoryId\":\"gruvdrift\","
                + "\"note\":\"1400 coins\",\"lastObservedAtMs\":42,\"favorite\":true}"
                + "]}}");
        MarketWatchNotesLoadResult result = new JsonMarketWatchNotesStore(storeFile()).load();
        assertEquals(MarketWatchNotesLoadResult.Outcome.LOADED, result.outcome());
        MarketWatchNote note = result.data().getNotes("ctx").get(0);
        assertEquals(new MarketWatchNote("old", "minecraft:diamond", "Diamond", "gruvdrift", "1400 coins", 42L, true), note);
        assertFalse(note.hasLocation());
    }

    @Test
    @DisplayName("An incomplete or invalid location is dropped on its own - the note still loads, never with a guessed position")
    void invalidLocationIsDroppedNotRepaired() throws Exception {
        String[] badLocations = {
            "null",
            "\"minecraft:overworld 1 2 3\"",
            "{}",
            "{\"dimension\":\"minecraft:overworld\",\"x\":1,\"z\":3}",
            "{\"dimension\":\"minecraft:overworld\",\"x\":1.5,\"y\":64,\"z\":3}",
            "{\"dimension\":\"minecraft:overworld\",\"x\":\"abc\",\"y\":64,\"z\":3}",
            "{\"dimension\":\"overworld\",\"x\":1,\"y\":64,\"z\":3}",
            "{\"x\":1,\"y\":64,\"z\":3}",
            "{\"dimension\":\"minecraft:overworld\",\"x\":1,\"y\":99999,\"z\":3}",
            "{\"dimension\":\"minecraft:overworld\",\"x\":1,\"y\":64,\"z\":99999999999}"
        };
        for (String bad : badLocations) {
            Files.writeString(storeFile(), "{\"schemaVersion\": 1, \"notesByContext\": {\"ctx\": ["
                    + "{\"id\":\"n\",\"displayName\":\"Raw Beef\",\"note\":\"keep me\",\"lastObservedAtMs\":0,\"favorite\":false,"
                    + "\"location\":" + bad + "}]}}");
            MarketWatchNotesLoadResult result = new JsonMarketWatchNotesStore(storeFile()).load();
            assertEquals(MarketWatchNotesLoadResult.Outcome.LOADED, result.outcome(), bad);
            List<MarketWatchNote> notes = result.data().getNotes("ctx");
            assertEquals(1, notes.size(), bad);
            assertEquals("keep me", notes.get(0).note(), bad);
            assertNull(notes.get(0).location(), bad);
        }
    }

    @Test
    @DisplayName("A note without a location is written with exactly the pre-feature keys")
    void noLocationKeySetIsUnchanged() throws Exception {
        JsonMarketWatchNotesStore store = new JsonMarketWatchNotesStore(storeFile());
        store.save(MarketWatchNotesData.empty().withNoteAdded("ctx",
                new MarketWatchNote("n1", "minecraft:beef", "Raw Beef", "boskap", "20 st", 7L, true)));
        com.google.gson.JsonObject note = com.google.gson.JsonParser.parseString(Files.readString(storeFile())).getAsJsonObject()
                .getAsJsonObject("notesByContext").getAsJsonArray("ctx").get(0).getAsJsonObject();
        assertEquals(java.util.Set.of("id", "itemId", "displayName", "categoryId", "note", "lastObservedAtMs", "favorite"), note.keySet());
    }

    @Test
    @DisplayName("Unknown JSON fields (root, note, location) never break loading")
    void unknownFieldsAreIgnored() throws Exception {
        Files.writeString(storeFile(), "{\"schemaVersion\": 1, \"futureRoot\": {\"a\": [1,2]}, \"notesByContext\": {\"ctx\": ["
                + "{\"id\":\"n\",\"displayName\":\"Raw Beef\",\"note\":\"x\",\"lastObservedAtMs\":0,\"favorite\":false,"
                + "\"shopName\":\"Olivres Shop\",\"tags\":[\"a\"],"
                + "\"location\":{\"dimension\":\"gamezone:resource_world\",\"x\":-847,\"y\":70,\"z\":12,\"savedAtMs\":5,\"yaw\":90,\"extra\":{}}}"
                + "]}}");
        MarketWatchNotesLoadResult result = new JsonMarketWatchNotesStore(storeFile()).load();
        assertEquals(MarketWatchNotesLoadResult.Outcome.LOADED, result.outcome());
        MarketWatchNote note = result.data().getNotes("ctx").get(0);
        assertEquals(MarketWatchSavedLocation.of("gamezone:resource_world", -847, 70, 12, 5L).orElseThrow(), note.location());
    }

    @Test
    @DisplayName("More malformed location shapes: the note is always kept and no position is ever made up")
    void moreMalformedLocationsKeepNote() throws Exception {
        String[] badLocations = {
            "[1, 64, 3]",
            "42",
            "{\"dimension\":7,\"x\":1,\"y\":64,\"z\":3}",
            "{\"dimension\":\"minecraft:overworld\",\"x\":null,\"y\":64,\"z\":3}",
            "{\"dimension\":\"minecraft:overworld\",\"x\":true,\"y\":64,\"z\":3}",
            "{\"dimension\":\"minecraft:overworld\",\"x\":{},\"y\":64,\"z\":3}",
            "{\"dimension\":\"minecraft:overworld\",\"x\":1,\"y\":64,\"z\":3,\"savedAtMs\":\"soon\"}",
            "{\"dimension\":\"minecraft:overworld\",\"x\":1e300,\"y\":64,\"z\":3}"
        };
        for (String bad : badLocations) {
            Files.writeString(storeFile(), "{\"schemaVersion\": 1, \"notesByContext\": {\"ctx\": ["
                    + "{\"id\":\"n\",\"displayName\":\"Raw Beef\",\"note\":\"keep me\",\"lastObservedAtMs\":0,\"favorite\":false,"
                    + "\"location\":" + bad + "},"
                    + "{\"id\":\"m\",\"displayName\":\"Other\",\"note\":\"\",\"lastObservedAtMs\":0,\"favorite\":false}]}}");
            MarketWatchNotesLoadResult result = new JsonMarketWatchNotesStore(storeFile()).load();
            assertEquals(MarketWatchNotesLoadResult.Outcome.LOADED, result.outcome(), bad);
            List<MarketWatchNote> notes = result.data().getNotes("ctx");
            assertEquals(2, notes.size(), bad);
            assertEquals("keep me", notes.get(0).note(), bad);
            assertNull(notes.get(0).location(), bad);
        }
    }

    @Test
    @DisplayName("Manually entered coordinates (negatives, dimension kept) persist and round-trip in the unchanged location object")
    void manualCoordinatesRoundTrip() throws Exception {
        MarketWatchSavedLocation manual = se.jimmyeliasson.gzcompanion.marketwatch.MarketWatchLocationInput.resolve(
                "-29999999", "-2048", "2047", "gamezone:resource_world", java.util.Optional.empty(), null, 77L).location();
        assertNotNull(manual);
        JsonMarketWatchNotesStore store = new JsonMarketWatchNotesStore(storeFile());
        store.save(MarketWatchNotesData.empty().withNoteAdded("ctx", new MarketWatchNote("m", null, "Butik", null, "", 0L, false, manual)));

        com.google.gson.JsonObject loc = com.google.gson.JsonParser.parseString(Files.readString(storeFile())).getAsJsonObject()
                .getAsJsonObject("notesByContext").getAsJsonArray("ctx").get(0).getAsJsonObject().getAsJsonObject("location");
        assertEquals(java.util.Set.of("dimension", "x", "y", "z", "savedAtMs"), loc.keySet(), "Same location object as alpha.9 - no schema change");
        assertEquals(manual, store.load().data().getNotes("ctx").get(0).location());
    }

    @Test
    @DisplayName("An alpha.9 note file with a saved location loads unchanged")
    void alpha9NoteWithLocationLoadsUnchanged() throws Exception {
        Files.writeString(storeFile(), "{\"schemaVersion\": 1, \"notesByContext\": {\"ctx\": ["
                + "{\"id\":\"a9\",\"itemId\":\"minecraft:beef\",\"displayName\":\"Raw Beef\",\"note\":\"Olivres Shop\","
                + "\"lastObservedAtMs\":42,\"favorite\":true,"
                + "\"location\":{\"dimension\":\"minecraft:overworld\",\"x\":-847,\"y\":70,\"z\":12,\"savedAtMs\":1789000000000}}"
                + "]}}");
        MarketWatchNotesLoadResult result = new JsonMarketWatchNotesStore(storeFile()).load();
        assertEquals(MarketWatchNotesLoadResult.Outcome.LOADED, result.outcome());
        assertEquals(new MarketWatchNote("a9", "minecraft:beef", "Raw Beef", null, "Olivres Shop", 42L, true,
                MarketWatchSavedLocation.of("minecraft:overworld", -847, 70, 12, 1789000000000L).orElseThrow()),
                result.data().getNotes("ctx").get(0));
    }
}
