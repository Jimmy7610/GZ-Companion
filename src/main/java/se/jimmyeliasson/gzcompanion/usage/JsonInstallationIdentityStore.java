package se.jimmyeliasson.gzcompanion.usage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.UUID;

/**
 * {@link InstallationIdentityStore} at {@code config/gzcompanion/installation.json} - its own file,
 * separate from {@code settings.json}, so resetting settings never changes the installation id,
 * and kept across normal GZ Companion updates (the config folder is never replaced by an update).
 *
 * <pre>{"schemaVersion":1,"installationId":"<random UUID>","lastAttemptAt":"...","lastSuccessAt":"..."}</pre>
 *
 * Atomic writes. An unreadable/corrupt file (or an invalid id) is backed up and treated as absent,
 * so a fresh random id is generated; a future schema is left untouched and also treated as absent.
 * Unparseable timestamps degrade to "never", which can only cause one extra heartbeat.
 */
public final class JsonInstallationIdentityStore implements InstallationIdentityStore {
    private static final Logger LOGGER = LoggerFactory.getLogger(JsonInstallationIdentityStore.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    static final int CURRENT_SCHEMA = 1;

    private final Path filePath;

    public JsonInstallationIdentityStore(Path filePath) {
        this.filePath = filePath;
    }

    public static JsonInstallationIdentityStore createDefault() {
        return new JsonInstallationIdentityStore(Path.of("config", "gzcompanion", "installation.json"));
    }

    @Override
    public synchronized Optional<InstallationIdentity> load() {
        if (!Files.exists(filePath)) return Optional.empty();
        try {
            JsonElement root = JsonParser.parseString(Files.readString(filePath, StandardCharsets.UTF_8));
            if (root == null || !root.isJsonObject()) {
                backupCorrupt();
                return Optional.empty();
            }
            JsonObject obj = root.getAsJsonObject();
            int schema = obj.has("schemaVersion") && obj.get("schemaVersion").isJsonPrimitive() ? obj.get("schemaVersion").getAsInt() : 1;
            if (schema > CURRENT_SCHEMA) {
                LOGGER.warn("installation.json has a future schema {} - leaving it untouched.", schema);
                return Optional.empty();
            }
            String id = string(obj, "installationId");
            UUID uuid = id == null ? null : parseUuid(id);
            if (uuid == null) {
                backupCorrupt();
                return Optional.empty();
            }
            return Optional.of(new InstallationIdentity(uuid, instant(obj, "lastAttemptAt"), instant(obj, "lastSuccessAt")));
        } catch (Exception e) {
            LOGGER.warn("installation.json could not be read ({}); a new anonymous installation id will be created.", e.getMessage());
            backupCorrupt();
            return Optional.empty();
        }
    }

    @Override
    public synchronized void save(InstallationIdentity identity) {
        if (identity == null) return;
        try {
            Path parent = filePath.getParent();
            if (parent != null) Files.createDirectories(parent);
            JsonObject obj = new JsonObject();
            obj.addProperty("schemaVersion", CURRENT_SCHEMA);
            obj.addProperty("installationId", identity.installationId().toString());
            if (identity.lastAttemptAt() != null) obj.addProperty("lastAttemptAt", identity.lastAttemptAt().toString());
            if (identity.lastSuccessAt() != null) obj.addProperty("lastSuccessAt", identity.lastSuccessAt().toString());
            Path tmp = filePath.resolveSibling(filePath.getFileName() + ".tmp");
            Files.writeString(tmp, GSON.toJson(obj), StandardCharsets.UTF_8);
            Files.move(tmp, filePath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOGGER.warn("Could not save installation.json: {}", e.getMessage());
        }
    }

    /** Only canonical random (version 4) UUIDs are accepted - anything else is treated as corrupt. */
    private static UUID parseUuid(String s) {
        try {
            UUID uuid = UUID.fromString(s.trim());
            return uuid.version() == 4 && uuid.toString().equalsIgnoreCase(s.trim()) ? uuid : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String string(JsonObject obj, String key) {
        JsonElement el = obj.get(key);
        return el != null && el.isJsonPrimitive() && el.getAsJsonPrimitive().isString() ? el.getAsString() : null;
    }

    private static Instant instant(JsonObject obj, String key) {
        String s = string(obj, key);
        if (s == null) return null;
        try {
            return Instant.parse(s);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private void backupCorrupt() {
        try {
            if (Files.exists(filePath)) {
                Files.move(filePath, filePath.resolveSibling(filePath.getFileName() + ".corrupt." + System.currentTimeMillis()),
                        StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            LOGGER.warn("Could not back up corrupt installation.json: {}", e.getMessage());
        }
    }
}
