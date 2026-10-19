package se.jimmyeliasson.gzcompanion.usage;

import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import se.jimmyeliasson.gzcompanion.gamezone.net.GameZoneLiveDataRuntime;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** The HTTP heartbeat against a local server: exact request shape and harmless failures. */
class HttpHeartbeatTransportTest {
    private HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) server.stop(0);
    }

    private URI serve(HttpHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", handler);
        server.start();
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + HeartbeatEndpoint.PATH);
    }

    private static HttpHeartbeatTransport transport(URI uri) {
        return new HttpHeartbeatTransport(new GameZoneLiveDataRuntime("0.1.0-test"), uri, Duration.ofSeconds(1));
    }

    private static void respond(HttpExchange ex, int status) throws IOException {
        ex.getRequestBody().readAllBytes();
        ex.sendResponseHeaders(status, -1);
        ex.close();
    }

    @Test
    @DisplayName("POST of exactly {installationId, version} as JSON over HTTP/1.1, with no cookies, credentials or upgrade")
    void requestShape() throws Exception {
        AtomicReference<String> method = new AtomicReference<>();
        AtomicReference<String> path = new AtomicReference<>();
        AtomicReference<String> protocol = new AtomicReference<>();
        AtomicReference<String> contentType = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        AtomicReference<Set<String>> headers = new AtomicReference<>();
        URI uri = serve(ex -> {
            method.set(ex.getRequestMethod());
            path.set(ex.getRequestURI().getPath());
            protocol.set(ex.getProtocol());
            contentType.set(ex.getRequestHeaders().getFirst("Content-Type"));
            headers.set(ex.getRequestHeaders().keySet());
            body.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            ex.sendResponseHeaders(204, -1);
            ex.close();
        });
        UUID id = UUID.randomUUID();
        assertTrue(transport(uri).send(new HeartbeatPayload(id, "0.1.0-alpha.12")));
        assertEquals("POST", method.get());
        assertEquals("/api/gzcompanion/heartbeat", path.get());
        assertEquals("HTTP/1.1", protocol.get());
        assertTrue(contentType.get().startsWith("application/json"));
        var json = JsonParser.parseString(body.get()).getAsJsonObject();
        assertEquals(Set.of("installationId", "version"), json.keySet());
        assertEquals(id.toString(), json.get("installationId").getAsString());
        for (String forbidden : new String[]{"Cookie", "Authorization", "Upgrade"}) {
            assertTrue(headers.get().stream().noneMatch(h -> h.equalsIgnoreCase(forbidden)), forbidden);
        }
    }

    @Test
    @DisplayName("2xx accepted; non-2xx, redirect, timeout and unreachable are harmless failures")
    void outcomes() throws Exception {
        HeartbeatPayload p = new HeartbeatPayload(UUID.randomUUID(), "1");
        assertTrue(transport(serve(ex -> respond(ex, 200))).send(p));
        stop();
        for (int status : new int[]{400, 429, 500, 503}) {
            assertFalse(transport(serve(ex -> respond(ex, status))).send(p), "HTTP " + status);
            stop();
        }
        URI redirect = serve(ex -> {
            ex.getResponseHeaders().add("Location", "http://127.0.0.1:1" + HeartbeatEndpoint.PATH);
            respond(ex, 302);
        });
        assertFalse(transport(redirect).send(p), "Redirects are not followed");
        stop();
        URI slow = serve(ex -> {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            respond(ex, 200);
        });
        assertFalse(transport(slow).send(p), "Timeout");
        stop();
        URI closed = serve(ex -> respond(ex, 200));
        stop();
        server = null;
        assertFalse(transport(closed).send(p), "Unreachable");
        assertFalse(transport(closed).send(null));
    }

    @Test
    @DisplayName("Only a valid heartbeat endpoint can be configured")
    void invalidEndpointRejected() {
        GameZoneLiveDataRuntime runtime = new GameZoneLiveDataRuntime("0.1.0-test");
        assertThrows(IllegalArgumentException.class, () -> new HttpHeartbeatTransport(runtime, URI.create("http://h/other")));
        assertThrows(IllegalArgumentException.class, () -> new HttpHeartbeatTransport(runtime, null));
    }
}
