package se.jimmyeliasson.gzcompanion.bounty;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import se.jimmyeliasson.gzcompanion.gamezone.net.GameZoneLiveDataRuntime;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link GameZoneBountySource} tested end to end against a local {@link HttpServer} (JDK built-in,
 * no new dependency, no real network) - mirrors {@code GameZoneLeaderboardSourceTest}'s convention:
 * covers the actual HTTP plumbing (timeouts, non-2xx, redirect refusal, host allowlisting, the
 * response-size ceiling) that {@link BountyJsonParserTest} deliberately doesn't (that suite is pure
 * JSON-in, entries-out).
 */
class GameZoneBountySourceTest {
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    private String startServerAndGetBaseUrl(com.sun.net.httpserver.HttpHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", handler);
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/api/bounties";
    }

    private GameZoneBountySource sourceFor(String url) {
        return new GameZoneBountySource(testRuntime(), url, "127.0.0.1", Duration.ofSeconds(2), 10_000);
    }

    private static GameZoneLiveDataRuntime testRuntime() {
        return new GameZoneLiveDataRuntime("0.1.0-test");
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static final String ONE_ACTIVE_JSON =
            "{\"status\":\"success\",\"data\":{\"active\":[{\"name\":\"HostileBoss\",\"entityType\":\"WITHER_SKELETON\","
            + "\"reward\":7500,\"hint\":\"Test hint\",\"status\":\"ACTIVE\",\"createdAt\":\"2026-08-29T20:42:37Z\",\"expiresAt\":null}],\"count\":1}}";

    @Test
    @DisplayName("A successful 200 JSON response is parsed into a Success result")
    void successfulFetchReturnsParsedEntries() throws Exception {
        String url = startServerAndGetBaseUrl(exchange -> respond(exchange, 200, ONE_ACTIVE_JSON));
        GameZoneBountySource source = sourceFor(url);

        BountyFetchResult result = source.fetch();

        assertInstanceOf(BountyFetchResult.Success.class, result);
        BountyEntry entry = ((BountyFetchResult.Success) result).entries().get(0);
        assertEquals("HostileBoss", entry.name());
    }

    @Test
    @DisplayName("A successful 200 response with zero active bounties is a Success with an empty list - not a failure")
    void emptyActiveListIsSuccess() throws Exception {
        String url = startServerAndGetBaseUrl(exchange -> respond(exchange, 200, "{\"status\":\"success\",\"data\":{\"active\":[],\"count\":0}}"));
        GameZoneBountySource source = sourceFor(url);

        BountyFetchResult result = source.fetch();

        assertInstanceOf(BountyFetchResult.Success.class, result);
        assertTrue(((BountyFetchResult.Success) result).entries().isEmpty());
    }

    @Test
    @DisplayName("A non-2xx response is reported as Unavailable, never thrown")
    void non2xxReportedAsUnavailable() throws Exception {
        String url = startServerAndGetBaseUrl(exchange -> respond(exchange, 503, "Service unavailable"));
        GameZoneBountySource source = sourceFor(url);

        assertInstanceOf(BountyFetchResult.Unavailable.class, source.fetch());
    }

    @Test
    @DisplayName("A connection to a closed port (simulating offline) is reported as Unavailable, never thrown")
    void offlineReportedAsUnavailable() {
        GameZoneBountySource source = sourceFor("http://127.0.0.1:1/api/bounties");
        assertInstanceOf(BountyFetchResult.Unavailable.class, source.fetch());
    }

    @Test
    @DisplayName("A redirect (3xx) is refused, never silently followed to a different host")
    void redirectIsRefused() throws Exception {
        String url = startServerAndGetBaseUrl(exchange -> {
            exchange.getResponseHeaders().add("Location", "https://evil.example/steal");
            respond(exchange, 302, "");
        });
        GameZoneBountySource source = sourceFor(url);

        assertInstanceOf(BountyFetchResult.Unavailable.class, source.fetch(),
                "a redirect must be treated as a failure, never silently followed");
    }

    @Test
    @DisplayName("A response with an honest oversized Content-Length is rejected before the body is even read")
    void oversizedDeclaredContentLengthReportedAsUnavailable() throws Exception {
        String big = "x".repeat(20_000); // sourceFor() caps at 10_000 chars
        String url = startServerAndGetBaseUrl(exchange -> respond(exchange, 200, big));
        GameZoneBountySource source = sourceFor(url);

        assertInstanceOf(BountyFetchResult.Unavailable.class, source.fetch());
    }

    @Test
    @DisplayName("An oversized CHUNKED response (no Content-Length header) is still caught by the post-hoc size check")
    void oversizedChunkedResponseStillRejected() throws Exception {
        String big = "x".repeat(20_000);
        String url = startServerAndGetBaseUrl(exchange -> {
            byte[] bytes = big.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, 0); // forces chunked transfer encoding
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        GameZoneBountySource source = sourceFor(url);

        assertInstanceOf(BountyFetchResult.Unavailable.class, source.fetch());
    }

    @Test
    @DisplayName("An unrecognizable JSON structure is reported as Incompatible, not Unavailable")
    void unrecognizableStructureReportedAsIncompatible() throws Exception {
        String url = startServerAndGetBaseUrl(exchange -> respond(exchange, 200, "{\"completely\":\"different\"}"));
        GameZoneBountySource source = sourceFor(url);

        assertInstanceOf(BountyFetchResult.Incompatible.class, source.fetch());
    }

    @Test
    @DisplayName("The HttpClient never automatically follows redirects")
    void neverFollowsRedirectsAutomatically() {
        GameZoneBountySource source = sourceFor("http://127.0.0.1:1/api/bounties");
        assertEquals(HttpClient.Redirect.NEVER, source.redirectPolicyForTesting());
    }

    @Test
    @DisplayName("A request to a host other than the allowlisted one is refused before any request is sent")
    void nonAllowlistedHostRefused() {
        GameZoneBountySource source = new GameZoneBountySource(testRuntime(), "http://localhost:1/api/bounties", "127.0.0.1", Duration.ofSeconds(1), 10_000);
        assertInstanceOf(BountyFetchResult.Unavailable.class, source.fetch());
    }

    @Test
    @DisplayName("The outgoing request carries no player/session identity - only a plain User-Agent and Accept header")
    void requestCarriesNoPlayerIdentity() throws Exception {
        java.util.concurrent.atomic.AtomicReference<com.sun.net.httpserver.Headers> capturedHeaders = new java.util.concurrent.atomic.AtomicReference<>();
        String url = startServerAndGetBaseUrl(exchange -> {
            capturedHeaders.set(exchange.getRequestHeaders());
            respond(exchange, 200, ONE_ACTIVE_JSON);
        });
        GameZoneBountySource source = sourceFor(url);

        source.fetch();

        com.sun.net.httpserver.Headers headers = capturedHeaders.get();
        assertNotNull(headers);
        assertFalse(headers.containsKey("Cookie"), "must never send a cookie");
        assertFalse(headers.containsKey("Authorization"), "must never send an auth header");
        // Only the expected, identity-free headers are present.
        // Only the expected header, plus a couple of benign protocol-negotiation headers the JDK's
        // own HttpClient adds automatically (never identity-bearing), are allowed.
        java.util.Set<String> allowed = java.util.Set.of(
                "user-agent", "accept", "host", "connection", "content-length", "http2-settings", "upgrade");
        for (String key : headers.keySet()) {
            assertTrue(allowed.contains(key.toLowerCase(java.util.Locale.ROOT)), "unexpected header sent: " + key);
        }
    }
}
