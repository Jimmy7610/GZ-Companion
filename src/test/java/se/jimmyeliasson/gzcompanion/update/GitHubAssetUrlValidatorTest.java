package se.jimmyeliasson.gzcompanion.update;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure URL-shape validation - no network. Regression coverage for a real GitHub-release-asset
 * bug: a naive {@code startsWith("https://")} check would accept ANY HTTPS URL, not just one that
 * genuinely belongs to this repository.
 */
class GitHubAssetUrlValidatorTest {

    @Test
    @DisplayName("A genuine official browser_download_url is accepted")
    void safeOfficialUrlAccepted() {
        assertTrue(GitHubAssetUrlValidator.isSafeInitialAssetUrl(
                "https://github.com/Jimmy7610/GZ-Companion/releases/download/v0.1.0-alpha.3/GZ-Companion-Setup.exe"));
    }

    @Test
    @DisplayName("A URL pointing at a DIFFERENT GitHub repository is rejected")
    void otherRepositoryRejected() {
        assertFalse(GitHubAssetUrlValidator.isSafeInitialAssetUrl(
                "https://github.com/SomeoneElse/OtherRepo/releases/download/v1.0.0/evil.exe"));
    }

    @Test
    @DisplayName("A lookalike host (github.com.evil.example) is rejected - never a substring/endsWith check")
    void lookalikeHostRejected() {
        assertFalse(GitHubAssetUrlValidator.isSafeInitialAssetUrl(
                "https://github.com.evil.example/Jimmy7610/GZ-Companion/releases/download/v0.1.0-alpha.3/x.exe"));
        assertFalse(GitHubAssetUrlValidator.isSafeInitialAssetUrl(
                "https://evilgithub.com/Jimmy7610/GZ-Companion/releases/download/v0.1.0-alpha.3/x.exe"));
        assertFalse(GitHubAssetUrlValidator.isSafeInitialAssetUrl(
                "https://notgithub.com/Jimmy7610/GZ-Companion/releases/download/v0.1.0-alpha.3/x.exe"));
    }

    @Test
    @DisplayName("Plain HTTP is rejected outright, never just 'not HTTPS'")
    void httpRejected() {
        assertFalse(GitHubAssetUrlValidator.isSafeInitialAssetUrl(
                "http://github.com/Jimmy7610/GZ-Companion/releases/download/v0.1.0-alpha.3/x.exe"));
    }

    @Test
    @DisplayName("A wrong path on the correct host is rejected")
    void wrongPathRejected() {
        assertFalse(GitHubAssetUrlValidator.isSafeInitialAssetUrl("https://github.com/Jimmy7610/GZ-Companion/blob/main/README.md"));
        assertFalse(GitHubAssetUrlValidator.isSafeInitialAssetUrl("https://github.com/some/other/path"));
    }

    @Test
    @DisplayName("A path-traversal-shaped segment is rejected")
    void pathTraversalRejected() {
        assertFalse(GitHubAssetUrlValidator.isSafeInitialAssetUrl(
                "https://github.com/Jimmy7610/GZ-Companion/releases/download/../../etc/passwd"));
    }

    @Test
    @DisplayName("Null/blank/malformed input fails safe, never throws")
    void malformedInputFailsSafe() {
        assertDoesNotThrow(() -> GitHubAssetUrlValidator.isSafeInitialAssetUrl(null));
        assertFalse(GitHubAssetUrlValidator.isSafeInitialAssetUrl(null));
        assertFalse(GitHubAssetUrlValidator.isSafeInitialAssetUrl(""));
        assertFalse(GitHubAssetUrlValidator.isSafeInitialAssetUrl("not a url at all"));
        assertFalse(GitHubAssetUrlValidator.isSafeInitialAssetUrl("ftp://github.com/Jimmy7610/GZ-Companion/releases/download/x"));
    }
}
