package se.jimmyeliasson.gzcompanion.update;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.function.LongConsumer;

/**
 * The real {@link UpdateByteSource} - HTTPS only, streams the response body directly to disk in
 * fixed-size chunks so real download progress (not a fake percentage) can be reported as bytes
 * actually arrive. A sane maximum size guards against a runaway/unexpected response body.
 */
public final class HttpUpdateByteSource implements UpdateByteSource {
    private static final long MAX_DOWNLOAD_BYTES = 500L * 1024 * 1024; // 500 MB - generous ceiling for a ~50 MB installer
    private static final int CHUNK_SIZE = 64 * 1024;

    private final HttpClient httpClient;
    private final String userAgent;

    public HttpUpdateByteSource(String companionVersion) {
        // NORMAL follows redirects but NEVER downgrades HTTPS to HTTP - required because GitHub
        // release-asset browser_download_url values redirect to GitHub's CDN, and Java's
        // HttpClient does not follow redirects by default.
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).followRedirects(HttpClient.Redirect.NORMAL).build();
        this.userAgent = "GZCompanion-Updater/" + companionVersion + " (+https://github.com/Jimmy7610/GZ-Companion)";
    }

    /** Test-only introspection so a future change can never silently remove the redirect policy. */
    HttpClient.Redirect redirectPolicyForTesting() {
        return httpClient.followRedirects();
    }

    @Override
    public void download(String url, Path destination, LongConsumer progressCallback) throws IOException {
        if (!url.startsWith("https://")) {
            throw new IOException("Refusing non-HTTPS download URL");
        }
        Files.createDirectories(destination.getParent());

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", userAgent)
                .timeout(Duration.ofMinutes(5))
                .GET()
                .build();

        try {
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() / 100 != 2) {
                throw new IOException("Download failed with HTTP " + response.statusCode());
            }
            long total = 0L;
            try (InputStream in = response.body(); OutputStream out = Files.newOutputStream(destination)) {
                byte[] buffer = new byte[CHUNK_SIZE];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    total += read;
                    if (total > MAX_DOWNLOAD_BYTES) {
                        throw new IOException("Download exceeded the maximum allowed installer size");
                    }
                    out.write(buffer, 0, read);
                    if (progressCallback != null) progressCallback.accept(total);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while downloading", e);
        }
    }
}
