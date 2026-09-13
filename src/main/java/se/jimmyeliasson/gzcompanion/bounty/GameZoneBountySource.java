package se.jimmyeliasson.gzcompanion.bounty;

import se.jimmyeliasson.gzcompanion.gamezone.net.GameZoneLiveDataRuntime;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/**
 * The real {@link BountySource} - HTTPS only, reads ONLY GameZone's own public, unauthenticated
 * bounty registry JSON endpoint. See docs/BOUNTY-BOARD.md for the discovery process (Phase 0) and
 * the exact captured contract. No API key, no GameZone account, no Minecraft session, no cookies -
 * the request carries nothing but the shared runtime's plain User-Agent, identical to every other
 * GameZone live-data request this project makes.
 *
 * <p><b>Fair play / privacy:</b> the request is byte-for-byte identical regardless of who is
 * running Companion, what world/server they're in, or what they're doing - no player UUID,
 * username, coordinates, or session data is ever sent. This class only reads the public registry;
 * it never scans loaded entities, never infers a coordinate from the public clue, and never sends
 * a {@code /bounty}-family command on its own - see {@code BountiesTabComponent} for the
 * deliberate, user-initiated command-copy action (never auto-send).
 *
 * <p><b>Shared runtime.</b> This class holds a {@link GameZoneLiveDataRuntime} and only calls
 * {@link GameZoneLiveDataRuntime#httpClient()} at the point a request is actually sent - merely
 * constructing this class (which happens eagerly at {@code CompanionSession} startup, mirroring
 * {@code GameZoneLeaderboardSource}) creates neither an HTTP client nor its selector thread. See
 * docs/PERFORMANCE-AUDIT-ALPHA4.md.
 */
public final class GameZoneBountySource implements BountySource {
    private static final String REAL_ALLOWED_HOST = "www.gamezonemc.se";
    private static final String REAL_URL = "https://" + REAL_ALLOWED_HOST + "/api/bounties";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);
    /** Generous for a small bounty-registry JSON payload - a captured real response was 265 bytes
     * for one active bounty; this comfortably covers many dozens of entries. */
    private static final long DEFAULT_MAX_RESPONSE_CHARS = 500_000;

    private final GameZoneLiveDataRuntime runtime;
    private final String url;
    private final String allowedHost;
    private final Duration timeout;
    private final long maxResponseChars;

    public GameZoneBountySource(GameZoneLiveDataRuntime runtime) {
        this(runtime, REAL_URL, REAL_ALLOWED_HOST, DEFAULT_TIMEOUT, DEFAULT_MAX_RESPONSE_CHARS);
    }

    /** Test-only seam - overrides the target host/timeout/size-ceiling so tests can point this at a
     * local mock server instead of the real GameZone site. Production always uses the public constructor. */
    GameZoneBountySource(GameZoneLiveDataRuntime runtime, String url, String allowedHost, Duration timeout, long maxResponseChars) {
        this.runtime = runtime;
        this.url = url;
        this.allowedHost = allowedHost;
        this.timeout = timeout;
        this.maxResponseChars = maxResponseChars;
    }

    @Override
    public BountyFetchResult fetch() {
        try {
            String json = getText(url);
            List<BountyEntry> entries = BountyJsonParser.parseActiveBounties(json);
            return new BountyFetchResult.Success(entries);
        } catch (BountyIncompatibleException e) {
            return new BountyFetchResult.Incompatible(e.getMessage());
        } catch (IOException e) {
            return new BountyFetchResult.Unavailable(e.getMessage());
        } catch (Exception e) {
            // Defense in depth - a truly unexpected failure here must never crash the caller.
            return new BountyFetchResult.Unavailable("Oväntat fel: " + e.getMessage());
        }
    }

    /** Identical host-allowlist / no-redirect / two-layer size-ceiling policy as
     * {@code GameZoneLeaderboardSource.getText} - see that class for the detailed rationale. */
    private String getText(String targetUrl) throws IOException {
        URI uri = URI.create(targetUrl);
        if (!allowedHost.equals(uri.getHost())) {
            throw new IOException("Refusing request to non-allowlisted host: " + uri.getHost());
        }
        if (!"https".equals(uri.getScheme()) && !"http".equals(uri.getScheme())) {
            throw new IOException("Refusing non-HTTP(S) scheme: " + uri.getScheme());
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .header("User-Agent", runtime.userAgent())
                    .header("Accept", "application/json")
                    .timeout(timeout)
                    .GET()
                    .build();

            java.util.concurrent.atomic.AtomicBoolean rejectedForDeclaredSize = new java.util.concurrent.atomic.AtomicBoolean(false);
            HttpResponse.BodyHandler<String> sizeAwareHandler = responseInfo -> {
                java.util.OptionalLong declaredLength = responseInfo.headers().firstValueAsLong("Content-Length");
                if (declaredLength.isPresent() && declaredLength.getAsLong() > maxResponseChars) {
                    rejectedForDeclaredSize.set(true);
                    return HttpResponse.BodySubscribers.replacing("");
                }
                return HttpResponse.BodySubscribers.ofString(StandardCharsets.UTF_8);
            };

            HttpResponse<String> response = runtime.httpClient().send(request, sizeAwareHandler);
            if (rejectedForDeclaredSize.get()) {
                throw new IOException("Response's declared Content-Length exceeded the size ceiling for " + targetUrl);
            }
            int status = response.statusCode();
            if (status >= 300 && status < 400) {
                throw new IOException("Refusing to follow redirect (HTTP " + status + ") for " + targetUrl);
            }
            if (status / 100 != 2) {
                throw new IOException("GameZone returned HTTP " + status + " for " + targetUrl);
            }
            String body = response.body();
            if (body == null) {
                throw new IOException("Empty response body for " + targetUrl);
            }
            if (body.length() > maxResponseChars) {
                throw new IOException("Response exceeded the size ceiling (" + body.length() + " chars) for " + targetUrl);
            }
            return body;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while contacting GameZone", e);
        }
    }

    /** Test-only introspection so a future change can never silently remove the no-redirect policy. */
    HttpClient.Redirect redirectPolicyForTesting() {
        return runtime.httpClient().followRedirects();
    }
}
