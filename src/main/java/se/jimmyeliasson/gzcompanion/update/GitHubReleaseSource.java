package se.jimmyeliasson.gzcompanion.update;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * The real {@link UpdateReleaseSource} - HTTPS only, reads ONLY the public GitHub Releases API for
 * {@code Jimmy7610/GZ-Companion}. No API key, no token, no GitHub credentials of any kind, and no
 * gameplay/user data is ever sent - the request carries nothing but a plain User-Agent header
 * (GitHub's API requires one) identifying this updater by name and version only.
 *
 * <p>Fair play / privacy: this class never reads Minecraft state, GameZone state, or any Companion
 * settings - it is pure infrastructure with a single, fixed, hardcoded destination.
 */
public final class GitHubReleaseSource implements UpdateReleaseSource {
    private static final String RELEASES_URL = "https://api.github.com/repos/Jimmy7610/GZ-Companion/releases?per_page=10";
    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    private final HttpClient httpClient;
    private final String userAgent;

    public GitHubReleaseSource(String companionVersion) {
        this.httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
        this.userAgent = "GZCompanion-Updater/" + companionVersion + " (+https://github.com/Jimmy7610/GZ-Companion)";
    }

    @Override
    public List<GitHubRelease> fetchReleases() throws IOException {
        String body = fetchText(RELEASES_URL);
        List<GitHubRelease> releases = new ArrayList<>();
        JsonElement root = JsonParser.parseString(body);
        if (root == null || !root.isJsonArray()) return releases;

        for (JsonElement el : root.getAsJsonArray()) {
            if (el == null || !el.isJsonObject()) continue;
            JsonObject obj = el.getAsJsonObject();
            String tagName = optString(obj, "tag_name");
            if (tagName == null) continue;
            boolean draft = optBoolean(obj, "draft");
            boolean prerelease = optBoolean(obj, "prerelease");

            List<GitHubReleaseAsset> assets = new ArrayList<>();
            if (obj.has("assets") && obj.get("assets").isJsonArray()) {
                for (JsonElement assetEl : obj.getAsJsonArray("assets")) {
                    if (assetEl == null || !assetEl.isJsonObject()) continue;
                    JsonObject assetObj = assetEl.getAsJsonObject();
                    String name = optString(assetObj, "name");
                    String downloadUrl = optString(assetObj, "browser_download_url");
                    if (name == null || downloadUrl == null) continue;
                    assets.add(new GitHubReleaseAsset(name, downloadUrl, optDigest(assetObj)));
                }
            }
            releases.add(new GitHubRelease(tagName, draft, prerelease, assets));
        }
        return releases;
    }

    @Override
    public String fetchText(String url) throws IOException {
        if (!url.startsWith("https://")) {
            throw new IOException("Refusing non-HTTPS URL: " + url);
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .header("User-Agent", userAgent)
                    .header("Accept", "application/vnd.github+json")
                    .timeout(TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new IOException("GitHub returned HTTP " + response.statusCode() + " for " + url);
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while contacting GitHub", e);
        }
    }

    private static String optString(JsonObject obj, String key) {
        return obj.has(key) && obj.get(key).isJsonPrimitive() ? obj.get(key).getAsString() : null;
    }

    private static boolean optBoolean(JsonObject obj, String key) {
        return obj.has(key) && obj.get(key).isJsonPrimitive() && obj.get(key).getAsBoolean();
    }

    /** GitHub's newer release-asset API may include a "digest" field shaped like "sha256:<hex>". */
    private static String optDigest(JsonObject assetObj) {
        String digest = optString(assetObj, "digest");
        if (digest == null || !digest.startsWith("sha256:")) return null;
        return digest.substring("sha256:".length());
    }
}
