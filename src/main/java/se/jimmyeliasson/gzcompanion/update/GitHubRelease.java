package se.jimmyeliasson.gzcompanion.update;

import java.util.List;
import java.util.Optional;

/**
 * One entry from the public GitHub Releases API for {@code Jimmy7610/GZ-Companion}. Pure data -
 * no network, no I/O. Draft releases are represented faithfully here ({@link #draft()}) and MUST
 * be filtered out by the caller ({@link UpdateChecker}) before any version comparison happens.
 */
public record GitHubRelease(String tagName, boolean draft, boolean prerelease, List<GitHubReleaseAsset> assets) {
    public GitHubRelease {
        assets = assets != null ? List.copyOf(assets) : List.of();
    }

    public Optional<GitHubReleaseAsset> findAsset(String name) {
        return assets.stream().filter(a -> a != null && name.equals(a.name())).findFirst();
    }
}
