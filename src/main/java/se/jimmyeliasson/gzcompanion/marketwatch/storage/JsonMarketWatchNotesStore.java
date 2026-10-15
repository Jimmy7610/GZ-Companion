package se.jimmyeliasson.gzcompanion.marketwatch.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Robust JSON-backed {@link MarketWatchNotesStore} located at
 * config/gzcompanion/marketwatch-notes.json. Mirrors {@code JsonBuildingPlanStore}/
 * {@code JsonSettlementPlannerStore}/{@code JsonChestIndexStore}: atomic writes, corruption
 * backup and recovery, and safe rejection of unsupported future schema versions.
 */
public class JsonMarketWatchNotesStore implements MarketWatchNotesStore {
    private static final Logger LOGGER = LoggerFactory.getLogger(JsonMarketWatchNotesStore.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path filePath;

    public JsonMarketWatchNotesStore(Path filePath) {
        this.filePath = filePath;
    }

    public static JsonMarketWatchNotesStore createDefault() {
        Path configDir = Path.of("config", "gzcompanion");
        return new JsonMarketWatchNotesStore(configDir.resolve("marketwatch-notes.json"));
    }

    @Override
    public synchronized MarketWatchNotesLoadResult load() {
        if (!Files.exists(filePath)) {
            return MarketWatchNotesLoadResult.notFound();
        }

        try (FileReader reader = new FileReader(filePath.toFile())) {
            JsonElement rootElement = JsonParser.parseReader(reader);
            if (rootElement == null || !rootElement.isJsonObject()) {
                LOGGER.warn("MarketWatch notes file did not contain a JSON object. Backing up and starting fresh.");
                backupCorruptFile();
                return MarketWatchNotesLoadResult.corruptRecovered();
            }
            JsonObject root = rootElement.getAsJsonObject();

            int schemaVersion = (root.has("schemaVersion") && !root.get("schemaVersion").isJsonNull())
                    ? root.get("schemaVersion").getAsInt() : 1;
            if (schemaVersion < 1) {
                LOGGER.warn("MarketWatch notes file declares an invalid schemaVersion {}. Backing up and starting fresh.", schemaVersion);
                backupCorruptFile();
                return MarketWatchNotesLoadResult.corruptRecovered();
            }
            if (schemaVersion > MarketWatchNotesData.CURRENT_SCHEMA) {
                LOGGER.warn("Unsupported future MarketWatch notes schema version: {} (this build understands up to {}). Leaving the file untouched.",
                        schemaVersion, MarketWatchNotesData.CURRENT_SCHEMA);
                return MarketWatchNotesLoadResult.incompatibleSchema();
            }

            Map<String, List<MarketWatchNote>> notesByContext = new HashMap<>();
            if (root.has("notesByContext") && root.get("notesByContext").isJsonObject()) {
                JsonObject contextsObj = root.getAsJsonObject("notesByContext");
                for (Map.Entry<String, JsonElement> entry : contextsObj.entrySet()) {
                    if (!entry.getValue().isJsonArray()) continue;
                    notesByContext.put(entry.getKey(), parseNotes(entry.getValue().getAsJsonArray(), entry.getKey()));
                }
            }

            return MarketWatchNotesLoadResult.loaded(new MarketWatchNotesData(schemaVersion, notesByContext));
        } catch (Exception e) {
            LOGGER.error("Failed to parse MarketWatch notes file {}. Preserving corrupt file.", filePath, e);
            backupCorruptFile();
            return MarketWatchNotesLoadResult.corruptRecovered();
        }
    }

    private List<MarketWatchNote> parseNotes(JsonArray arr, String contextKey) {
        List<MarketWatchNote> result = new ArrayList<>();
        for (JsonElement el : arr) {
            if (!el.isJsonObject()) continue;
            try {
                JsonObject obj = el.getAsJsonObject();
                String id = obj.has("id") && !obj.get("id").isJsonNull() ? obj.get("id").getAsString() : null;
                if (id == null || id.isBlank()) continue;

                String itemId = obj.has("itemId") && !obj.get("itemId").isJsonNull() ? obj.get("itemId").getAsString() : null;
                String displayName = obj.has("displayName") && !obj.get("displayName").isJsonNull() ? obj.get("displayName").getAsString() : null;
                String categoryId = obj.has("categoryId") && !obj.get("categoryId").isJsonNull() ? obj.get("categoryId").getAsString() : null;
                String note = obj.has("note") && !obj.get("note").isJsonNull() ? obj.get("note").getAsString() : null;
                long lastObservedAtMs = obj.has("lastObservedAtMs") && obj.get("lastObservedAtMs").isJsonPrimitive() ? obj.get("lastObservedAtMs").getAsLong() : 0L;
                boolean favorite = obj.has("favorite") && obj.get("favorite").isJsonPrimitive() && obj.get("favorite").getAsBoolean();

                MarketWatchSavedLocation location = parseLocation(obj, contextKey, id);

                result.add(new MarketWatchNote(id, itemId, displayName, categoryId, note, lastObservedAtMs, favorite, location));
            } catch (Exception ex) {
                LOGGER.warn("Skipping malformed MarketWatch note entry in context '{}': {}", contextKey, ex.getMessage());
            }
        }
        return result;
    }

    /**
     * The optional, additive {@code "location"} object (absent on every note written before it
     * existed). An incomplete or invalid location is dropped on its own - the note itself still
     * loads, just without "Hitta" - and is never repaired into a guessed position.
     */
    private MarketWatchSavedLocation parseLocation(JsonObject noteObj, String contextKey, String noteId) {
        if (!noteObj.has("location") || !noteObj.get("location").isJsonObject()) return null;
        JsonObject loc = noteObj.getAsJsonObject("location");
        try {
            String dimension = loc.has("dimension") && loc.get("dimension").isJsonPrimitive() ? loc.get("dimension").getAsString() : null;
            Integer x = intOrNull(loc, "x");
            Integer y = intOrNull(loc, "y");
            Integer z = intOrNull(loc, "z");
            long savedAtMs = loc.has("savedAtMs") && loc.get("savedAtMs").isJsonPrimitive() ? loc.get("savedAtMs").getAsLong() : 0L;
            if (dimension != null && x != null && y != null && z != null) {
                var parsed = MarketWatchSavedLocation.of(dimension, x, y, z, savedAtMs);
                if (parsed.isPresent()) return parsed.get();
            }
        } catch (Exception ignored) {
            // Fall through to the warning below.
        }
        LOGGER.warn("Ignoring invalid saved location on MarketWatch note '{}' in context '{}'.", noteId, contextKey);
        return null;
    }

    private static Integer intOrNull(JsonObject obj, String key) {
        if (!obj.has(key) || !obj.get(key).isJsonPrimitive() || !obj.get(key).getAsJsonPrimitive().isNumber()) return null;
        double value = obj.get(key).getAsDouble();
        if (value != Math.rint(value) || value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) return null;
        return (int) value;
    }

    @Override
    public synchronized void save(MarketWatchNotesData data) {
        if (data == null) return;
        try {
            Path parent = filePath.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }

            JsonObject root = serialize(data);

            Path tmpFile = filePath.resolveSibling(filePath.getFileName().toString() + ".tmp");
            try (FileWriter writer = new FileWriter(tmpFile.toFile())) {
                GSON.toJson(root, writer);
            }

            Files.move(tmpFile, filePath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOGGER.error("Failed to save MarketWatch notes data to {}", filePath, e);
        }
    }

    private JsonObject serialize(MarketWatchNotesData data) {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", data.schemaVersion());

        JsonObject contextsObj = new JsonObject();
        for (Map.Entry<String, List<MarketWatchNote>> entry : data.notesByContext().entrySet()) {
            JsonArray arr = new JsonArray();
            for (MarketWatchNote note : entry.getValue()) {
                arr.add(serializeNote(note));
            }
            contextsObj.add(entry.getKey(), arr);
        }
        root.add("notesByContext", contextsObj);
        return root;
    }

    private JsonObject serializeNote(MarketWatchNote note) {
        JsonObject obj = new JsonObject();
        obj.addProperty("id", note.id());
        if (note.itemId() != null) obj.addProperty("itemId", note.itemId());
        obj.addProperty("displayName", note.displayName());
        if (note.categoryId() != null) obj.addProperty("categoryId", note.categoryId());
        obj.addProperty("note", note.note());
        obj.addProperty("lastObservedAtMs", note.lastObservedAtMs());
        obj.addProperty("favorite", note.favorite());
        MarketWatchSavedLocation location = note.location();
        if (location != null) {
            JsonObject loc = new JsonObject();
            loc.addProperty("dimension", location.dimensionKey());
            loc.addProperty("x", location.x());
            loc.addProperty("y", location.y());
            loc.addProperty("z", location.z());
            loc.addProperty("savedAtMs", location.savedAtMs());
            obj.add("location", loc);
        }
        return obj;
    }

    private void backupCorruptFile() {
        try {
            if (Files.exists(filePath)) {
                Path corruptPath = filePath.resolveSibling(filePath.getFileName().toString() + ".corrupt." + System.currentTimeMillis());
                Files.move(filePath, corruptPath, StandardCopyOption.REPLACE_EXISTING);
                LOGGER.info("Moved corrupt MarketWatch notes file to {}", corruptPath);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to backup corrupt MarketWatch notes file", e);
        }
    }
}
