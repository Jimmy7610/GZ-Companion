package se.jimmyeliasson.gzcompanion.update;

/** One fully-validated, selectable update: a GitHub release whose manifest was fetched and parsed successfully. */
public record UpdateRelease(GitHubRelease release, UpdateManifest manifest, GitHubReleaseAsset installerAsset, SemanticVersion version) {
}
