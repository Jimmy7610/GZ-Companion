package se.jimmyeliasson.gzcompanion.events;

import se.jimmyeliasson.gzcompanion.gamezone.net.GameZoneLiveDataRuntime;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The real {@link EventScheduleSource}: one read-only {@code GET} of GameZoneEngine's public
 * events-schedule endpoint - the same schedule source as the in-game hologram. No API key, no
 * account, no cookies, no player identity: the request is identical for every player and carries
 * only the shared runtime's plain User-Agent. Never POST/PUT/DELETE.
 *
 * <p>Same transport policy as {@code GameZoneBountySource}: only the allowlisted host, no
 * redirects (the shared client never follows them, and a 3xx is a failure), a request timeout,
 * and a two-layer response-size ceiling. The endpoint is plain HTTP on a fixed IP and port, as
 * GameZone publishes it; the data is public and display-only, so nothing sensitive travels either
 * way. The request pins HTTP/1.1 so the shared client never sends an {@code h2c} upgrade to this
 * small API server.
 *
 * <p>Uses the shared {@link GameZoneLiveDataRuntime}: no executor or HTTP client of its own, and
 * nothing is created until the first real fetch.
 */
public final class GameZoneEventScheduleSource implements EventScheduleSource {
    private static final String REAL_ALLOWED_HOST = "162.120.2.221";
    /** The default 7-day window - enough for "today, tomorrow and the coming week". */
    private static final String REAL_URL = "http://" + REAL_ALLOWED_HOST + ":25569/api/v1/events/schedule";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(8);
    /** A real 7-day response is ~1 KB (14 events); this leaves ample room without accepting junk. */
    private static final long DEFAULT_MAX_RESPONSE_CHARS = 200_000;

    private final GameZoneLiveDataRuntime runtime;
    private final String url;
    private final String allowedHost;
    private final Duration timeout;
    private final long maxResponseChars;

    public GameZoneEventScheduleSource(GameZoneLiveDataRuntime runtime) {
        this(runtime, REAL_URL, REAL_ALLOWED_HOST, DEFAULT_TIMEOUT, DEFAULT_MAX_RESPONSE_CHARS);
    }

    /** Test-only seam: point at a local mock server with its own host/timeout/size ceiling. */
    GameZoneEventScheduleSource(GameZoneLiveDataRuntime runtime, String url, String allowedHost, Duration timeout, long maxResponseChars) {
        this.runtime = runtime;
        this.url = url;
        this.allowedHost = allowedHost;
        this.timeout = timeout;
        this.maxResponseChars = maxResponseChars;
    }

    @Override
    public EventFetchResult fetch() {
        try {
            EventScheduleDto dto = EventScheduleJsonParser.parse(getText());
            return new EventFetchResult.Success(EventSchedule.from(dto));
        } catch (EventScheduleIncompatibleException e) {
            return new EventFetchResult.Incompatible(e.getMessage());
        } catch (HttpTimeoutException e) {
            return new EventFetchResult.Unavailable("Eventschemat svarade inte i tid.");
        } catch (IOException e) {
            return new EventFetchResult.Unavailable(e.getMessage());
        } catch (Exception e) {
            // Defense in depth: nothing unexpected here may ever reach the render thread as a crash.
            return new EventFetchResult.Unavailable("Oväntat fel: " + e.getMessage());
        }
    }

    private String getText() throws IOException {
        URI uri = URI.create(url);
        if (!allowedHost.equals(uri.getHost())) {
            throw new IOException("Refusing request to non-allowlisted host: " + uri.getHost());
        }
        if (!"http".equals(uri.getScheme()) && !"https".equals(uri.getScheme())) {
            throw new IOException("Refusing non-HTTP(S) scheme: " + uri.getScheme());
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .version(HttpClient.Version.HTTP_1_1)
                    .header("User-Agent", runtime.userAgent())
                    .header("Accept", "application/json")
                    .timeout(timeout)
                    .GET()
                    .build();

            AtomicBoolean rejectedForDeclaredSize = new AtomicBoolean(false);
            HttpResponse.BodyHandler<String> sizeAwareHandler = responseInfo -> {
                OptionalLong declaredLength = responseInfo.headers().firstValueAsLong("Content-Length");
                if (declaredLength.isPresent() && declaredLength.getAsLong() > maxResponseChars) {
                    rejectedForDeclaredSize.set(true);
                    return HttpResponse.BodySubscribers.replacing("");
                }
                return HttpResponse.BodySubscribers.ofString(StandardCharsets.UTF_8);
            };

            HttpResponse<String> response = runtime.httpClient().send(request, sizeAwareHandler);
            if (rejectedForDeclaredSize.get()) {
                throw new IOException("Eventschemats svar var för stort.");
            }
            int status = response.statusCode();
            if (status >= 300 && status < 400) {
                throw new IOException("Eventschemat försökte omdirigera (HTTP " + status + ").");
            }
            if (status / 100 != 2) {
                throw new IOException("Eventschemat svarade HTTP " + status + ".");
            }
            String body = response.body();
            if (body == null || body.isEmpty()) {
                throw new IOException("Eventschemat svarade utan innehåll.");
            }
            if (body.length() > maxResponseChars) {
                throw new IOException("Eventschemats svar var för stort.");
            }
            return body;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Avbruten medan eventschemat hämtades.", e);
        }
    }
}
