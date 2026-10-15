package se.jimmyeliasson.gzcompanion.events;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Structural guarantees for the Events feature, enforced by scanning its source (like
 * {@code BountyFairPlayTest}): strictly read-only GETs to the one public endpoint, no world/entity
 * access, no chat or commands, no credentials.
 */
class EventsFairPlayTest {
    private static final Path EVENTS_DIR = Path.of("src", "main", "java", "se", "jimmyeliasson", "gzcompanion", "events");
    private static final Path TAB_FILE = Path.of("src", "main", "java", "se", "jimmyeliasson", "gzcompanion", "ui", "tabs", "EventsTabComponent.java");

    private static List<String> sources() throws IOException {
        List<Path> files = new ArrayList<>();
        try (Stream<Path> s = Files.walk(EVENTS_DIR)) {
            s.filter(p -> p.toString().endsWith(".java")).forEach(files::add);
        }
        files.add(TAB_FILE);
        List<String> contents = new ArrayList<>();
        for (Path p : files) contents.add(Files.readString(p));
        assertTrue(contents.size() >= 10, "expected the events sources");
        return contents;
    }

    @Test
    @DisplayName("Only read-only GET requests - never POST/PUT/DELETE/PATCH or a request body")
    void readOnly() throws IOException {
        for (String src : sources()) {
            for (String forbidden : List.of(".POST(", ".PUT(", ".DELETE(", "\"PATCH\"", "method(\"", "BodyPublishers")) {
                assertFalse(src.contains(forbidden), "Events must never write to GameZoneEngine: " + forbidden);
            }
        }
    }

    @Test
    @DisplayName("No credentials, cookies or player identity in requests")
    void noCredentials() throws IOException {
        for (String src : sources()) {
            for (String forbidden : List.of("Authorization", "Cookie", "api_key", "apiKey", "getUUID", "getGameProfile", "getName()")) {
                assertFalse(src.contains(forbidden), "Events must not send identity/credentials: " + forbidden);
            }
        }
    }

    @Test
    @DisplayName("No world, entity, chat or command access - the schedule comes only from the public API")
    void noGameAccess() throws IOException {
        for (String src : sources()) {
            for (String forbidden : List.of("client.level", "getEntities", "BlockEntity", "sendChat", "sendCommand", "connection.send", "Hologram")) {
                assertFalse(src.contains(forbidden), "Events must not read the world or send anything: " + forbidden);
            }
        }
    }

    @Test
    @DisplayName("The only endpoint is the public events-schedule URL on the allowlisted host")
    void singleEndpoint() throws IOException {
        String source = Files.readString(EVENTS_DIR.resolve("GameZoneEventScheduleSource.java"));
        assertTrue(source.contains("REAL_ALLOWED_HOST = \"162.120.2.221\""));
        assertTrue(source.contains(":25569/api/v1/events/schedule\""));
        assertEquals(1, source.split("http://", -1).length - 1, "exactly one URL literal");
    }
}
