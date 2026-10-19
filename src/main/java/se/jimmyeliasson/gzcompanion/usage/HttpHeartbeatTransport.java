package se.jimmyeliasson.gzcompanion.usage;

import se.jimmyeliasson.gzcompanion.gamezone.net.GameZoneLiveDataRuntime;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * {@link HeartbeatTransport} over HTTP: one {@code POST} of {@link HeartbeatPayload#toJson()} to
 * the configured endpoint, through the shared {@link GameZoneLiveDataRuntime} HTTP client (no
 * client of its own; that client never follows redirects). Sends no cookies, credentials or player
 * identity - only the payload and the shared static User-Agent. Any 2xx counts as accepted;
 * everything else (non-2xx, redirect, timeout, network error) is a harmless failure.
 */
public final class HttpHeartbeatTransport implements HeartbeatTransport {
    static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);

    private final GameZoneLiveDataRuntime runtime;
    private final URI endpoint;
    private final Duration timeout;

    public HttpHeartbeatTransport(GameZoneLiveDataRuntime runtime, URI endpoint) {
        this(runtime, endpoint, DEFAULT_TIMEOUT);
    }

    HttpHeartbeatTransport(GameZoneLiveDataRuntime runtime, URI endpoint, Duration timeout) {
        if (!HeartbeatEndpoint.isValid(endpoint)) throw new IllegalArgumentException("Invalid heartbeat endpoint: " + endpoint);
        this.runtime = runtime;
        this.endpoint = endpoint;
        this.timeout = timeout;
    }

    @Override
    public boolean send(HeartbeatPayload payload) {
        if (payload == null) return false;
        try {
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .version(HttpClient.Version.HTTP_1_1)
                    .header("User-Agent", runtime.userAgent())
                    .header("Content-Type", "application/json; charset=utf-8")
                    .timeout(timeout)
                    .POST(HttpRequest.BodyPublishers.ofString(payload.toJson(), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<Void> response = runtime.httpClient().send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode() / 100 == 2;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            return false;
        }
    }
}
