package se.jimmyeliasson.gzcompanion.usage;

import java.net.URI;
import java.util.Optional;

/**
 * The single place the usage backend's address is configured.
 *
 * <p>The backend contract is {@code POST /api/gzcompanion/heartbeat} with a JSON body of exactly
 * {@code {"installationId": "...", "version": "..."}} (see docs/PRIVACY.md). The production backend
 * is the standalone {@code gzcompanion-usage} service (usage-server/) at gzcompanion.taren.se. If
 * {@link #PRODUCTION} were empty the counter would be completely inactive: no id file, no thread,
 * nothing sent.
 */
public final class HeartbeatEndpoint {
    private HeartbeatEndpoint() {}

    /** The real gzcompanion-usage heartbeat endpoint - never a placeholder or test address. */
    public static final Optional<URI> PRODUCTION =
            Optional.of(URI.create("https://gzcompanion.taren.se/api/gzcompanion/heartbeat"));

    /** Path every heartbeat endpoint must have. */
    public static final String PATH = "/api/gzcompanion/heartbeat";

    /** True if {@code uri} is a usable heartbeat endpoint: http(s), a host, and exactly {@link #PATH}. */
    public static boolean isValid(URI uri) {
        return uri != null && uri.getHost() != null && PATH.equals(uri.getPath())
                && ("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))
                && uri.getQuery() == null && uri.getUserInfo() == null;
    }
}
