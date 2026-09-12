package se.jimmyeliasson.gzcompanion.update;

/**
 * One asset attached to a GitHub Release. {@code digestSha256} comes from GitHub's OWN
 * release-asset API digest field (when present) - an extra, independent integrity signal checked
 * ALONGSIDE (never instead of) the SHA-256 this mod computes itself from the downloaded bytes and
 * compares against {@code update-manifest.json}. Never trusted as the sole source of truth.
 */
public record GitHubReleaseAsset(String name, String browserDownloadUrl, String digestSha256) {
}
