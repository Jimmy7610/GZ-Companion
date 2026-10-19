package se.jimmyeliasson.gzcompanion.usage;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** The random installation id and its local persistence, including corrupt and future state. */
class InstallationIdentityStoreTest {
    @TempDir
    Path tempDir;

    private Path file() {
        return tempDir.resolve("installation.json");
    }

    @Test
    @DisplayName("Generated ids are random version-4 UUIDs and never repeat")
    void generatedIdsAreRandom() {
        Set<UUID> seen = new HashSet<>();
        for (int i = 0; i < 2000; i++) {
            UUID id = InstallationIdentity.generate().installationId();
            assertEquals(4, id.version(), "random (v4) UUID");
            assertEquals(2, id.variant(), "IETF variant");
            assertTrue(seen.add(id), "duplicate id");
        }
    }

    @Test
    @DisplayName("No file -> empty; saved identity round-trips exactly, with only the expected keys on disk")
    void roundTrip() throws Exception {
        JsonInstallationIdentityStore store = new JsonInstallationIdentityStore(file());
        assertTrue(store.load().isEmpty());
        InstallationIdentity id = InstallationIdentity.generate()
                .withAttemptAt(Instant.parse("2026-10-19T10:00:00Z")).withSuccessAt(Instant.parse("2026-10-19T10:00:01Z"));
        store.save(id);
        assertEquals(Optional.of(id), new JsonInstallationIdentityStore(file()).load());
        var keys = JsonParser.parseString(Files.readString(file())).getAsJsonObject().keySet();
        assertEquals(Set.of("schemaVersion", "installationId", "lastAttemptAt", "lastSuccessAt"), keys, "Nothing else is stored");
    }

    @Test
    @DisplayName("A fresh identity stores only the schema and the id")
    void freshIdentityKeys() throws Exception {
        new JsonInstallationIdentityStore(file()).save(InstallationIdentity.generate());
        assertEquals(Set.of("schemaVersion", "installationId"), JsonParser.parseString(Files.readString(file())).getAsJsonObject().keySet());
    }

    @Test
    @DisplayName("Corrupt or invalid files are backed up and treated as absent (a new random id will be made), never a crash")
    void corruptStateRecovers() throws Exception {
        String[] bad = {
            "{not json",
            "[]",
            "{\"schemaVersion\":1}",
            "{\"schemaVersion\":1,\"installationId\":\"not-a-uuid\"}",
            "{\"schemaVersion\":1,\"installationId\":42}",
            "{\"schemaVersion\":1,\"installationId\":\"00000000-0000-1000-8000-000000000000\"}", // not random (v1)
            "{\"schemaVersion\":1,\"installationId\":\"0-0-4-8-0\"}", // non-canonical
        };
        for (String content : bad) {
            Files.writeString(file(), content);
            assertTrue(new JsonInstallationIdentityStore(file()).load().isEmpty(), content);
            assertFalse(Files.exists(file()), "corrupt file moved aside: " + content);
            try (var s = Files.list(tempDir)) {
                assertTrue(s.anyMatch(p -> p.getFileName().toString().startsWith("installation.json.corrupt.")));
            }
        }
    }

    @Test
    @DisplayName("Unparseable timestamps degrade to 'never' but keep the id")
    void badTimestampsKeepId() throws Exception {
        UUID id = UUID.randomUUID();
        Files.writeString(file(), "{\"schemaVersion\":1,\"installationId\":\"" + id + "\",\"lastAttemptAt\":\"yesterday\",\"lastSuccessAt\":7}");
        InstallationIdentity loaded = new JsonInstallationIdentityStore(file()).load().orElseThrow();
        assertEquals(id, loaded.installationId());
        assertNull(loaded.lastAttemptAt());
        assertNull(loaded.lastSuccessAt());
    }

    @Test
    @DisplayName("A future schema is left untouched")
    void futureSchemaUntouched() throws Exception {
        String content = "{\"schemaVersion\":2,\"installationId\":\"" + UUID.randomUUID() + "\"}";
        Files.writeString(file(), content);
        assertTrue(new JsonInstallationIdentityStore(file()).load().isEmpty());
        assertEquals(content, Files.readString(file()));
    }

    @Test
    @DisplayName("Saving to an unwritable location fails quietly")
    void unwritableIsHarmless() throws Exception {
        Path blocker = tempDir.resolve("blocker");
        Files.writeString(blocker, "x"); // a file where a directory is needed
        assertDoesNotThrow(() -> new JsonInstallationIdentityStore(blocker.resolve("installation.json")).save(InstallationIdentity.generate()));
    }
}
