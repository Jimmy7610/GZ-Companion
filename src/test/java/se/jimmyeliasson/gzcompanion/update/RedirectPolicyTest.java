package se.jimmyeliasson.gzcompanion.update;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression test for a real bug: GitHub release-asset {@code browser_download_url} values
 * redirect to GitHub's CDN, but Java's {@link HttpClient} does NOT follow redirects unless
 * explicitly configured (its default is {@code Redirect.NEVER}). Without this, fetching
 * update-manifest.json or the installer could silently fail or be skipped.
 *
 * <p>This asserts the actual constructed policy on both real HTTP clients so a future refactor can
 * never silently drop it. {@code Redirect.NORMAL} is required specifically because it follows
 * redirects while STILL refusing to redirect from HTTPS to HTTP - {@code Redirect.ALWAYS} would
 * not have that protection.
 */
class RedirectPolicyTest {

    @Test
    @DisplayName("GitHubReleaseSource's HttpClient follows redirects (NORMAL - never HTTPS to HTTP)")
    void gitHubReleaseSourceUsesNormalRedirectPolicy() {
        GitHubReleaseSource source = new GitHubReleaseSource("0.1.0-alpha.2");
        assertEquals(HttpClient.Redirect.NORMAL, source.redirectPolicyForTesting());
    }

    @Test
    @DisplayName("HttpUpdateByteSource's HttpClient follows redirects (NORMAL - never HTTPS to HTTP)")
    void httpUpdateByteSourceUsesNormalRedirectPolicy() {
        HttpUpdateByteSource byteSource = new HttpUpdateByteSource("0.1.0-alpha.2");
        assertEquals(HttpClient.Redirect.NORMAL, byteSource.redirectPolicyForTesting());
    }
}
