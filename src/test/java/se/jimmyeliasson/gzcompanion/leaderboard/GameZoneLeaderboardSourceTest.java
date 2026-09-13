package se.jimmyeliasson.gzcompanion.leaderboard;

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
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link GameZoneLeaderboardSource} tested end to end against a local {@link HttpServer} (JDK
 * built-in, no new dependency, no real network) rather than the real GameZone site - covers the
 * network-level behaviors {@link LeaderboardHtmlParserTest} deliberately doesn't (that suite is pure
 * HTML-in, entries-out; this one proves the actual HTTP plumbing around it: timeouts, non-2xx,
 * redirect refusal, and the response-size ceiling).
 */
class GameZoneLeaderboardSourceTest {
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    private String startServerAndGetBaseUrl(com.sun.net.httpserver.HttpHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", handler);
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private GameZoneLeaderboardSource sourceFor(String baseUrl) {
        return new GameZoneLeaderboardSource(testRuntime(), baseUrl, "127.0.0.1", Duration.ofSeconds(2), 10_000);
    }

    /** A fresh runtime per call - mirrors production's "one shared runtime per session" shape
     * without letting per-test HttpClient/executor state bleed between tests. A closed port (as
     * used by the offline test below) is refused immediately by the OS regardless of connect
     * timeout, so the default (production) connect timeout is fine here. */
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

    private static String fullTableHtml(String name, String value) {
        return "<div class=\"x__fullTable\"><div class=\"x__tableRow\">"
                + "<span class=\"x__tableRank\">#<!-- -->1</span>"
                + "<span class=\"x__tableIdentity\"><a class=\"x__entityLink\">" + name + "</a></span>"
                + "<strong class=\"x__tableValue\">" + value + "</strong>"
                + "</div></div>";
    }

    @Test
    @DisplayName("A successful 200 response is parsed into a Success result with the registry's own value label attached")
    void successfulFetchReturnsParsedEntries() throws Exception {
        String baseUrl = startServerAndGetBaseUrl(exchange -> respond(exchange, 200, fullTableHtml("Alfa", "5 coins")));
        GameZoneLeaderboardSource source = sourceFor(baseUrl);

        LeaderboardFetchResult result = source.fetch(GameZoneLeaderboardRegistry.byId("player_coins").orElseThrow());

        assertInstanceOf(LeaderboardFetchResult.Success.class, result);
        LeaderboardEntry entry = ((LeaderboardFetchResult.Success) result).entries().get(0);
        assertEquals("Alfa", entry.displayName());
        assertEquals("Coins", entry.primaryLabel());
    }

    @Test
    @DisplayName("A non-2xx response is reported as Unavailable, never thrown")
    void non2xxReportedAsUnavailable() throws Exception {
        String baseUrl = startServerAndGetBaseUrl(exchange -> respond(exchange, 503, "Service unavailable"));
        GameZoneLeaderboardSource source = sourceFor(baseUrl);

        LeaderboardFetchResult result = source.fetch(GameZoneLeaderboardRegistry.byId("player_coins").orElseThrow());

        assertInstanceOf(LeaderboardFetchResult.Unavailable.class, result);
    }

    @Test
    @DisplayName("A connection to a closed port (simulating offline) is reported as Unavailable, never thrown")
    void offlineReportedAsUnavailable() {
        // Nothing listening on this port - java.net.ConnectException, exactly like a real offline case.
        GameZoneLeaderboardSource source = sourceFor("http://127.0.0.1:1");

        LeaderboardFetchResult result = source.fetch(GameZoneLeaderboardRegistry.byId("player_coins").orElseThrow());

        assertInstanceOf(LeaderboardFetchResult.Unavailable.class, result);
    }

    @Test
    @DisplayName("A redirect (3xx) is refused, never silently followed to a different host")
    void redirectIsRefused() throws Exception {
        String baseUrl = startServerAndGetBaseUrl(exchange -> {
            exchange.getResponseHeaders().add("Location", "https://evil.example/steal");
            respond(exchange, 302, "");
        });
        GameZoneLeaderboardSource source = sourceFor(baseUrl);

        LeaderboardFetchResult result = source.fetch(GameZoneLeaderboardRegistry.byId("player_coins").orElseThrow());

        assertInstanceOf(LeaderboardFetchResult.Unavailable.class, result, "a redirect must be treated as a failure, never silently followed");
    }

    @Test
    @DisplayName("A response with an honest oversized Content-Length is rejected before the body is even read")
    void oversizedDeclaredContentLengthReportedAsUnavailable() throws Exception {
        // respond() calls sendResponseHeaders(status, bytes.length), which sets a real, accurate
        // Content-Length header - this exercises the EARLY rejection layer (checked against
        // ResponseInfo before any body bytes are handled), not just the post-hoc body.length() check.
        String big = "x".repeat(20_000); // sourceFor() below caps at 10_000 chars
        String baseUrl = startServerAndGetBaseUrl(exchange -> respond(exchange, 200, big));
        GameZoneLeaderboardSource source = sourceFor(baseUrl);

        LeaderboardFetchResult result = source.fetch(GameZoneLeaderboardRegistry.byId("player_coins").orElseThrow());

        assertInstanceOf(LeaderboardFetchResult.Unavailable.class, result);
    }

    @Test
    @DisplayName("An oversized CHUNKED response (no Content-Length header at all) is still caught by the post-hoc size check")
    void oversizedChunkedResponseWithoutContentLengthStillRejected() throws Exception {
        String big = "x".repeat(20_000);
        String baseUrl = startServerAndGetBaseUrl(exchange -> {
            byte[] bytes = big.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, 0); // 0 forces chunked transfer encoding - no Content-Length header is sent
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        GameZoneLeaderboardSource source = sourceFor(baseUrl);

        LeaderboardFetchResult result = source.fetch(GameZoneLeaderboardRegistry.byId("player_coins").orElseThrow());

        assertInstanceOf(LeaderboardFetchResult.Unavailable.class, result);
    }

    @Test
    @DisplayName("An unrecognizable page structure is reported as Incompatible, not Unavailable")
    void unrecognizableStructureReportedAsIncompatible() throws Exception {
        String baseUrl = startServerAndGetBaseUrl(exchange -> respond(exchange, 200, "<html>completely different page</html>"));
        GameZoneLeaderboardSource source = sourceFor(baseUrl);

        LeaderboardFetchResult result = source.fetch(GameZoneLeaderboardRegistry.byId("player_coins").orElseThrow());

        assertInstanceOf(LeaderboardFetchResult.Incompatible.class, result);
    }

    @Test
    @DisplayName("A server-stat board fetches the shared ?tab=server page and looks itself up by title")
    void serverStatFetchesSharedPageAndMatchesByTitle() throws Exception {
        String cardHtml = "<article class=\"x__boardCard\"><h3>Coin-ekonomi</h3>"
                + "<div class=\"x__identity\"><strong>GameZone</strong></div>"
                + "<div class=\"x__value\"><strong>123 coins</strong></div></article>";
        String baseUrl = startServerAndGetBaseUrl(exchange -> respond(exchange, 200, cardHtml));
        GameZoneLeaderboardSource source = sourceFor(baseUrl);

        LeaderboardFetchResult result = source.fetch(GameZoneLeaderboardRegistry.byId("server_coin_economy").orElseThrow());

        assertInstanceOf(LeaderboardFetchResult.Success.class, result);
        LeaderboardEntry entry = ((LeaderboardFetchResult.Success) result).entries().get(0);
        assertEquals("GameZone", entry.displayName());
        assertEquals("123 coins", entry.primaryValue());
    }

    @Test
    @DisplayName("Two different server-stat definitions within the reuse window only hit the server once")
    void serverStatsPageReusedAcrossDefinitionsWithinShortWindow() throws Exception {
        String cardsHtml = "<article class=\"x__boardCard\"><h3>Coin-ekonomi</h3><div class=\"x__identity\"><strong>GameZone</strong></div><div class=\"x__value\"><strong>1 coins</strong></div></article>"
                + "<article class=\"x__boardCard\"><h3>Aktiva settlements</h3><div class=\"x__identity\"><strong>GameZone</strong></div><div class=\"x__value\"><strong>2</strong></div></article>";
        AtomicInteger requestCount = new AtomicInteger();
        String baseUrl = startServerAndGetBaseUrl(exchange -> {
            requestCount.incrementAndGet();
            respond(exchange, 200, cardsHtml);
        });
        GameZoneLeaderboardSource source = sourceFor(baseUrl);

        source.fetch(GameZoneLeaderboardRegistry.byId("server_coin_economy").orElseThrow());
        source.fetch(GameZoneLeaderboardRegistry.byId("server_active_settlements").orElseThrow());

        assertEquals(1, requestCount.get(), "both server stats should reuse the same short-lived cached page fetch");
    }

    @Test
    @DisplayName("The HttpClient never automatically follows redirects")
    void neverFollowsRedirectsAutomatically() {
        GameZoneLeaderboardSource source = sourceFor("http://127.0.0.1:1");
        assertEquals(HttpClient.Redirect.NEVER, source.redirectPolicyForTesting());
    }

    @Test
    @DisplayName("A request to a host other than the allowlisted one is refused before any request is sent")
    void nonAllowlistedHostRefused() {
        // allowedHost is "127.0.0.1" but baseUrl points elsewhere - simulates a coding mistake in URL construction.
        GameZoneLeaderboardSource source = new GameZoneLeaderboardSource(testRuntime(), "http://localhost:1", "127.0.0.1", Duration.ofSeconds(1), 10_000);
        LeaderboardFetchResult result = source.fetch(GameZoneLeaderboardRegistry.byId("player_coins").orElseThrow());
        assertInstanceOf(LeaderboardFetchResult.Unavailable.class, result);
    }

    // ------------------------------------------------------------------
    // Shared GameZone live-data runtime (2026-09-13 performance-foundation follow-up): proves the
    // migration off GameZoneLeaderboardSource's own HttpClient onto the shared, lazy
    // GameZoneLiveDataRuntime preserved every existing security/behavior guarantee above, and
    // added the new laziness guarantee.
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Constructing a GameZoneLeaderboardSource does not create the shared HttpClient")
    void constructingSourceDoesNotCreateSharedHttpClient() {
        GameZoneLiveDataRuntime runtime = testRuntime();
        new GameZoneLeaderboardSource(runtime);

        assertFalse(runtime.isHttpClientInitializedForTesting(),
                "constructing GameZoneLeaderboardSource (as CompanionSession does eagerly at startup) must not create the shared HttpClient");
    }

    @Test
    @DisplayName("An actual fetch creates the shared HttpClient, reused (not recreated) across repeated fetches")
    void actualFetchCreatesAndReusesTheSharedHttpClient() throws Exception {
        GameZoneLiveDataRuntime runtime = testRuntime();
        String baseUrl = startServerAndGetBaseUrl(exchange -> respond(exchange, 200, fullTableHtml("Alfa", "5 coins")));
        GameZoneLeaderboardSource source = new GameZoneLeaderboardSource(runtime, baseUrl, "127.0.0.1", Duration.ofSeconds(2), 10_000);

        assertFalse(runtime.isHttpClientInitializedForTesting());
        source.fetch(GameZoneLeaderboardRegistry.byId("player_coins").orElseThrow());
        assertTrue(runtime.isHttpClientInitializedForTesting(), "the first real fetch must have created the shared HttpClient");

        HttpClient afterFirstFetch = runtime.httpClient();
        source.fetch(GameZoneLeaderboardRegistry.byId("settlement_treasury").orElseThrow());
        assertSame(afterFirstFetch, runtime.httpClient(), "a second fetch must reuse the identical HttpClient instance, never recreate it");
    }

    @Test
    @DisplayName("Two independent GameZoneLeaderboardSource instances sharing one runtime reuse the same HttpClient")
    void multipleSourcesShareTheSameHttpClient() throws Exception {
        GameZoneLiveDataRuntime runtime = testRuntime();
        String baseUrl = startServerAndGetBaseUrl(exchange -> respond(exchange, 200, fullTableHtml("Alfa", "5 coins")));
        GameZoneLeaderboardSource sourceOne = new GameZoneLeaderboardSource(runtime, baseUrl, "127.0.0.1", Duration.ofSeconds(2), 10_000);
        GameZoneLeaderboardSource sourceTwo = new GameZoneLeaderboardSource(runtime, baseUrl, "127.0.0.1", Duration.ofSeconds(2), 10_000);

        sourceOne.fetch(GameZoneLeaderboardRegistry.byId("player_coins").orElseThrow());
        HttpClient afterSourceOne = runtime.httpClient();
        sourceTwo.fetch(GameZoneLeaderboardRegistry.byId("settlement_treasury").orElseThrow());

        assertSame(afterSourceOne, runtime.httpClient(), "both sources must share the identical HttpClient, never one each");
    }
}
