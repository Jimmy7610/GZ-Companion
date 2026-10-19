package se.jimmyeliasson.gzcompanion.usage;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** The heartbeat body contains exactly the anonymous id and the version - nothing else, ever. */
class HeartbeatPayloadTest {

    @Test
    @DisplayName("JSON body is exactly {installationId, version}")
    void exactlyTwoFields() {
        UUID id = UUID.randomUUID();
        JsonObject json = JsonParser.parseString(new HeartbeatPayload(id, "0.1.0-alpha.12").toJson()).getAsJsonObject();
        assertEquals(Set.of("installationId", "version"), json.keySet());
        assertEquals(id.toString(), json.get("installationId").getAsString());
        assertEquals("0.1.0-alpha.12", json.get("version").getAsString());
    }

    @Test
    @DisplayName("The payload type itself has no room for more data (guards future additions)")
    void recordHasOnlyTwoComponents() {
        List<String> components = Arrays.stream(HeartbeatPayload.class.getRecordComponents()).map(RecordComponent::getName).toList();
        assertEquals(List.of("installationId", "version"), components);
    }

    @Test
    @DisplayName("Missing or absurd versions and a missing id are rejected")
    void validation() {
        assertThrows(NullPointerException.class, () -> new HeartbeatPayload(null, "1"));
        for (String bad : new String[]{null, "", "  ", "x".repeat(65)}) {
            assertThrows(IllegalArgumentException.class, () -> new HeartbeatPayload(UUID.randomUUID(), bad), String.valueOf(bad));
        }
        assertEquals("0.1.0", new HeartbeatPayload(UUID.randomUUID(), " 0.1.0 ").version());
    }

    @Test
    @DisplayName("Production endpoint is the gzcompanion-usage heartbeat on gzcompanion.taren.se over HTTPS")
    void productionEndpointConfigured() {
        URI uri = HeartbeatEndpoint.PRODUCTION.orElseThrow();
        assertEquals(URI.create("https://gzcompanion.taren.se/api/gzcompanion/heartbeat"), uri);
        assertEquals("https", uri.getScheme());
        assertTrue(HeartbeatEndpoint.isValid(uri));
    }

    @Test
    @DisplayName("Endpoint validation: http(s), a host, exactly /api/gzcompanion/heartbeat, no query or credentials")
    void endpointValidation() {
        assertTrue(HeartbeatEndpoint.isValid(URI.create("https://stats.example.org/api/gzcompanion/heartbeat")));
        assertTrue(HeartbeatEndpoint.isValid(URI.create("http://127.0.0.1:8080/api/gzcompanion/heartbeat")));
        for (String bad : new String[]{"https://stats.example.org/api/heartbeat", "ftp://h/api/gzcompanion/heartbeat",
                "https://h/api/gzcompanion/heartbeat?u=me", "https://user:pw@h/api/gzcompanion/heartbeat", "/api/gzcompanion/heartbeat"}) {
            assertFalse(HeartbeatEndpoint.isValid(URI.create(bad)), bad);
        }
        assertFalse(HeartbeatEndpoint.isValid(null));
    }
}
