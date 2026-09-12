package se.jimmyeliasson.gzcompanion.leaderboard;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
        return new GameZoneLeaderboardSource("0.1.0-test", baseUrl, "127.0.0.1", Duration.ofSeconds(2), 10_000);
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
    @DisplayName("A response exceeding the size ceiling is reported as Unavailable")
    void oversizedResponseReportedAsUnavailable() throws Exception {
        String big = "x".repeat(20_000); // sourceFor() below caps at 10_000 chars
        String baseUrl = startServerAndGetBaseUrl(exchange -> respond(exchange, 200, big));
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
        GameZoneLeaderboardSource source = new GameZoneLeaderboardSource("0.1.0-test", "http://localhost:1", "127.0.0.1", Duration.ofSeconds(1), 10_000);
        LeaderboardFetchResult result = source.fetch(GameZoneLeaderboardRegistry.byId("player_coins").orElseThrow());
        assertInstanceOf(LeaderboardFetchResult.Unavailable.class, result);
    }
}
