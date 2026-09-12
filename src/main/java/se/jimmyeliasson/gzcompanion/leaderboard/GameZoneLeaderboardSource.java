package se.jimmyeliasson.gzcompanion.leaderboard;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * The real {@link LeaderboardSource} - HTTPS only, reads ONLY GameZone's own public, unauthenticated
 * leaderboard pages at {@code https://www.gamezonemc.se/leaderboards/...}. No API key, no GameZone
 * account, no Minecraft session, no cookies - the request carries nothing but a plain User-Agent
 * identifying this feature by name and version, exactly like {@code GitHubReleaseSource}. See
 * docs/LEADERBOARDS.md for the discovery process, the exact pages this reads, and why an HTML
 * adapter was used instead of a JSON API (none exists as a genuinely public, documented contract).
 *
 * <p><b>Fair play / privacy:</b> never sends the local Minecraft username, UUID, settlement,
 * coordinates, or any other player/session data - every request is identical regardless of who is
 * running Companion or what they're doing. "DU" highlighting for the local player happens entirely
 * client-side, by comparing an already-downloaded public top-10 against the local username - see
 * {@code LeaderboardsTabComponent}.
 */
public final class GameZoneLeaderboardSource implements LeaderboardSource {
    private static final String REAL_ALLOWED_HOST = "www.gamezonemc.se";
    private static final String REAL_BASE_URL = "https://" + REAL_ALLOWED_HOST;
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);
    private static final long DEFAULT_MAX_RESPONSE_CHARS = 4_000_000; // generous for a top-25 HTML table page

    /** How long a single fetched server-stats page is reused across the 5 SERVERN definitions,
     * purely to avoid 5 near-simultaneous identical requests when a player cycles through that
     * whole group quickly - NOT a substitute for LeaderboardManager's own per-board cache window. */
    private static final Duration SERVER_PAGE_REUSE_WINDOW = Duration.ofSeconds(5);

    private final HttpClient httpClient;
    private final String userAgent;
    private final String baseUrl;
    private final String allowedHost;
    private final Duration timeout;
    private final long maxResponseChars;

    private record CachedServerPage(String html, Instant fetchedAt) {}
    private volatile CachedServerPage cachedServerPage;

    public GameZoneLeaderboardSource(String companionVersion) {
        this(companionVersion, REAL_BASE_URL, REAL_ALLOWED_HOST, DEFAULT_TIMEOUT, DEFAULT_MAX_RESPONSE_CHARS);
    }

    /** Test-only seam - overrides the target host/timeout/size-ceiling so tests can point this at a
     * local mock server instead of the real GameZone site. Production always uses the public constructor. */
    GameZoneLeaderboardSource(String companionVersion, String baseUrl, String allowedHost, Duration timeout, long maxResponseChars) {
        // NEVER follow redirects automatically - leaderboard DATA must only ever come from the one
        // explicitly-allowlisted host. If the site ever starts redirecting this request, that is
        // treated as a failure (Unavailable), not silently followed - see class doc comment and
        // docs/LEADERBOARDS.md's "HTTP safety" section.
        this.httpClient = HttpClient.newBuilder().connectTimeout(timeout).followRedirects(HttpClient.Redirect.NEVER).build();
        this.userAgent = "GZ-Companion/" + companionVersion + " (+https://github.com/Jimmy7610/GZ-Companion)";
        this.baseUrl = baseUrl;
        this.allowedHost = allowedHost;
        this.timeout = timeout;
        this.maxResponseChars = maxResponseChars;
    }

    @Override
    public LeaderboardFetchResult fetch(LeaderboardDefinition definition) {
        if (definition == null) {
            return new LeaderboardFetchResult.Unavailable("Ingen leaderboard vald.");
        }
        try {
            if (definition.isServerStat()) {
                return fetchServerStat(definition);
            }
            return fetchRankedBoard(definition);
        } catch (LeaderboardIncompatibleException e) {
            return new LeaderboardFetchResult.Incompatible(e.getMessage());
        } catch (IOException e) {
            return new LeaderboardFetchResult.Unavailable(e.getMessage());
        } catch (Exception e) {
            // Defense in depth - a truly unexpected failure here must never crash the caller or
            // take down any OTHER board's cache; report it the same as any other network failure.
            return new LeaderboardFetchResult.Unavailable("Oväntat fel: " + e.getMessage());
        }
    }

    private LeaderboardFetchResult fetchRankedBoard(LeaderboardDefinition definition) throws IOException, LeaderboardIncompatibleException {
        String html = getText(baseUrl + "/leaderboards/" + definition.id());
        List<LeaderboardEntry> entries = LeaderboardHtmlParser.parseFullTableTop10(html, definition.valueLabel());
        return new LeaderboardFetchResult.Success(entries);
    }

    private LeaderboardFetchResult fetchServerStat(LeaderboardDefinition definition) throws IOException, LeaderboardIncompatibleException {
        String html = getServerStatsPageCached();
        Map<String, LeaderboardHtmlParser.RawServerStat> byTitle = LeaderboardHtmlParser.parseServerStats(html);
        LeaderboardHtmlParser.RawServerStat raw = byTitle.get(definition.title());
        if (raw == null) {
            // The container structure parsed fine, but THIS specific stat's title wasn't among the
            // cards found - GameZone likely renamed or removed just this one stat. Narrow failure:
            // only this board is affected, not the other 4 (or any ranked board).
            return new LeaderboardFetchResult.Incompatible(
                    "Hittade inte \"" + definition.title() + "\" bland serverstatistiken.");
        }
        LeaderboardEntry entry = LeaderboardEntry.of(1, raw.entityName(), raw.value(), definition.valueLabel());
        return new LeaderboardFetchResult.Success(List.of(entry));
    }

    private synchronized String getServerStatsPageCached() throws IOException {
        CachedServerPage cached = cachedServerPage;
        if (cached != null && Duration.between(cached.fetchedAt(), Instant.now()).compareTo(SERVER_PAGE_REUSE_WINDOW) < 0) {
            return cached.html();
        }
        String html = getText(baseUrl + "/leaderboards?tab=server");
        cachedServerPage = new CachedServerPage(html, Instant.now());
        return html;
    }

    private String getText(String url) throws IOException {
        URI uri = URI.create(url);
        if (!allowedHost.equals(uri.getHost())) {
            throw new IOException("Refusing request to non-allowlisted host: " + uri.getHost());
        }
        if (!"https".equals(uri.getScheme()) && !"http".equals(uri.getScheme())) {
            throw new IOException("Refusing non-HTTP(S) scheme: " + uri.getScheme());
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .header("User-Agent", userAgent)
                    .header("Accept", "text/html")
                    .timeout(timeout)
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status >= 300 && status < 400) {
                throw new IOException("Refusing to follow redirect (HTTP " + status + ") for " + url);
            }
            if (status / 100 != 2) {
                throw new IOException("GameZone returned HTTP " + status + " for " + url);
            }
            String body = response.body();
            if (body == null) {
                throw new IOException("Empty response body for " + url);
            }
            if (body.length() > maxResponseChars) {
                throw new IOException("Response exceeded the size ceiling (" + body.length() + " chars) for " + url);
            }
            return body;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while contacting GameZone", e);
        }
    }

    /** Test-only introspection so a future change can never silently remove the no-redirect policy. */
    HttpClient.Redirect redirectPolicyForTesting() {
        return httpClient.followRedirects();
    }
}
