package se.jimmyeliasson.gzcompanion.events;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import se.jimmyeliasson.gzcompanion.gamezone.net.GameZoneLiveDataRuntime;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link GameZoneEventScheduleSource} end to end against a local JDK {@link HttpServer} - no real
 * network. Covers the HTTP plumbing (status codes, timeout, redirects, host allowlist, size
 * ceiling, request shape) on top of the parser tests.
 */
class GameZoneEventScheduleSourceTest {
    private HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) server.stop(0);
    }

    private String serve(HttpHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", handler);
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/api/v1/events/schedule";
    }

    private static GameZoneEventScheduleSource source(String url) {
        return new GameZoneEventScheduleSource(new GameZoneLiveDataRuntime("0.1.0-test"), url, "127.0.0.1", Duration.ofSeconds(1), 10_000);
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    @Test
    @DisplayName("HTTP 200 with the real JSON shape -> Success with the parsed schedule")
    void httpSuccess() throws Exception {
        String url = serve(ex -> respond(ex, 200, EventsTestData.REAL_JSON));
        EventFetchResult result = source(url).fetch();
        EventFetchResult.Success success = assertInstanceOf(EventFetchResult.Success.class, result);
        assertEquals(14, success.schedule().entries().size());
        assertEquals(EventsTestData.STOCKHOLM, success.schedule().zone());
    }

    @Test
    @DisplayName("The request is a plain read-only GET over HTTP/1.1 with no credentials or cookies")
    void requestShape() throws Exception {
        AtomicReference<String> method = new AtomicReference<>();
        AtomicReference<String> protocol = new AtomicReference<>();
        AtomicReference<String> accept = new AtomicReference<>();
        AtomicReference<Boolean> hasCredentials = new AtomicReference<>();
        AtomicReference<Boolean> hasUpgrade = new AtomicReference<>();
        String url = serve(ex -> {
            method.set(ex.getRequestMethod());
            protocol.set(ex.getProtocol());
            accept.set(ex.getRequestHeaders().getFirst("Accept"));
            hasCredentials.set(ex.getRequestHeaders().containsKey("Authorization") || ex.getRequestHeaders().containsKey("Cookie"));
            hasUpgrade.set(ex.getRequestHeaders().containsKey("Upgrade"));
            respond(ex, 200, EventsTestData.REAL_JSON);
        });
        source(url).fetch();
        assertEquals("GET", method.get());
        assertEquals("HTTP/1.1", protocol.get());
        assertEquals("application/json", accept.get());
        assertFalse(hasCredentials.get());
        assertFalse(hasUpgrade.get(), "No h2c upgrade attempt against the plain-HTTP API");
    }

    @Test
    @DisplayName("Non-2xx responses -> Unavailable, never a crash")
    void httpError() throws Exception {
        for (int status : new int[]{404, 500, 503}) {
            String url = serve(ex -> respond(ex, status, "{\"status\":\"error\"}"));
            EventFetchResult.Unavailable u = assertInstanceOf(EventFetchResult.Unavailable.class, source(url).fetch(), "HTTP " + status);
            assertTrue(u.reason().contains(String.valueOf(status)));
            stop();
        }
    }

    @Test
    @DisplayName("A response slower than the request timeout -> Unavailable with a Swedish timeout reason")
    void timeout() throws Exception {
        String url = serve(ex -> {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            respond(ex, 200, EventsTestData.REAL_JSON);
        });
        long start = System.nanoTime();
        EventFetchResult.Unavailable u = assertInstanceOf(EventFetchResult.Unavailable.class, source(url).fetch());
        assertTrue(Duration.ofNanos(System.nanoTime() - start).toMillis() < 2900, "Gave up at the timeout, not after the slow response");
        assertEquals("Eventschemat svarade inte i tid.", u.reason());
    }

    @Test
    @DisplayName("HTTP 200 with malformed JSON or status != success -> Incompatible")
    void incompatibleBodies() throws Exception {
        for (String body : new String[]{"not json", "{\"status\":\"error\",\"timeZone\":\"Europe/Stockholm\",\"data\":[]}",
                "{\"status\":\"success\",\"timeZone\":\"Nowhere/City\",\"data\":[]}"}) {
            String url = serve(ex -> respond(ex, 200, body));
            assertInstanceOf(EventFetchResult.Incompatible.class, source(url).fetch(), body);
            stop();
        }
    }

    @Test
    @DisplayName("An empty schedule is a Success with no entries")
    void emptySchedule() throws Exception {
        String url = serve(ex -> respond(ex, 200, EventsTestData.json("")));
        assertTrue(assertInstanceOf(EventFetchResult.Success.class, source(url).fetch()).schedule().entries().isEmpty());
    }

    @Test
    @DisplayName("Redirects are refused, not followed")
    void redirectRefused() throws Exception {
        String url = serve(ex -> {
            ex.getResponseHeaders().add("Location", "http://127.0.0.1:1/elsewhere");
            respond(ex, 302, "");
        });
        assertInstanceOf(EventFetchResult.Unavailable.class, source(url).fetch());
    }

    @Test
    @DisplayName("A URL whose host is not the allowlisted one is refused before any request")
    void hostAllowlist() {
        GameZoneEventScheduleSource s = new GameZoneEventScheduleSource(new GameZoneLiveDataRuntime("0.1.0-test"),
                "http://example.com/api/v1/events/schedule", "127.0.0.1", Duration.ofSeconds(1), 10_000);
        EventFetchResult.Unavailable u = assertInstanceOf(EventFetchResult.Unavailable.class, s.fetch());
        assertTrue(u.reason().contains("non-allowlisted"));
    }

    @Test
    @DisplayName("A response over the size ceiling is refused")
    void sizeCeiling() throws Exception {
        String url = serve(ex -> respond(ex, 200, "x".repeat(20_000)));
        assertInstanceOf(EventFetchResult.Unavailable.class, source(url).fetch());
    }

    @Test
    @DisplayName("An unreachable server -> Unavailable")
    void unreachable() throws Exception {
        String url = serve(ex -> respond(ex, 200, ""));
        stop();
        server = null;
        assertInstanceOf(EventFetchResult.Unavailable.class, source(url).fetch());
    }
}
