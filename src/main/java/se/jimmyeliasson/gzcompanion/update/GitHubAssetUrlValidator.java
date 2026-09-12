package se.jimmyeliasson.gzcompanion.update;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Validates that an asset URL from a GitHub Releases API response genuinely belongs to
 * {@code Jimmy7610/GZ-Companion} BEFORE this mod ever requests it - never merely
 * {@code startsWith("https://")}, which would accept any HTTPS URL an attacker-controlled or
 * compromised response happened to include.
 *
 * <p>The trust chain this enforces is: fixed official GitHub Releases API URL (hardcoded in
 * {@link GitHubReleaseSource}) → a {@code browser_download_url} whose INITIAL request must target
 * {@code https://github.com/Jimmy7610/GZ-Companion/releases/download/...} → GitHub's own safe
 * HTTPS redirect to its release-asset delivery infrastructure (followed by
 * {@code HttpClient.Redirect.NORMAL}, which never downgrades HTTPS to HTTP). The CDN hostname a
 * redirect ultimately lands on is deliberately NOT hardcoded here - GitHub controls and can change
 * it - only the INITIAL request this mod makes is validated.
 */
public final class GitHubAssetUrlValidator {
    private static final String EXPECTED_SCHEME = "https";
    private static final String EXPECTED_HOST = "github.com";
    private static final String EXPECTED_PATH_PREFIX = "/Jimmy7610/GZ-Companion/releases/download/";

    private GitHubAssetUrlValidator() {}

    public static boolean isSafeInitialAssetUrl(String url) {
        if (url == null || url.isBlank()) return false;
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            String path = uri.getRawPath();

            if (scheme == null || !scheme.equalsIgnoreCase(EXPECTED_SCHEME)) return false;
            // Exact match, never endsWith/contains - "github.com.evil.example" or
            // "evilgithub.com" must never pass just because they mention "github.com".
            if (host == null || !host.equals(EXPECTED_HOST)) return false;
            if (path == null || !path.startsWith(EXPECTED_PATH_PREFIX)) return false;
            if (path.contains("..")) return false; // defense in depth; GitHub's own paths never contain this
            return true;
        } catch (URISyntaxException e) {
            return false;
        }
    }
}
