package se.jimmyeliasson.gzcompanion.update;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Pure release-selection logic - given a raw list of {@link GitHubRelease} entries (as returned by
 * {@link UpdateReleaseSource#fetchReleases()}) plus the current version/channel, decides whether an
 * update exists and, if so, exactly which one. No live network call happens inside the comparison
 * logic itself - {@link UpdateReleaseSource#fetchText} is only used to retrieve each candidate
 * release's small {@code update-manifest.json}, and the seam is injected so tests use fixed
 * fixtures, never a real HTTP call.
 *
 * <p>Rules, in order: drafts are ignored; the tag must parse as a valid SemVer version STRICTLY
 * newer than the current one; both the manifest asset and installer asset must be present; the
 * manifest itself must parse under the supported schema; the manifest's own declared version must
 * agree with the release tag (a mismatch is rejected, never silently trusted); the release's
 * channel must be one the current channel accepts (see {@link UpdateChannel#accepts}). Among every
 * release that survives all of that, the highest version wins.
 */
public final class UpdateChecker {
    public static final String MANIFEST_ASSET_NAME = "update-manifest.json";
    public static final String INSTALLER_ASSET_NAME = "GZ-Companion-Setup.exe";

    private UpdateChecker() {}

    public static Optional<UpdateRelease> findUpdate(
            List<GitHubRelease> releases,
            SemanticVersion currentVersion,
            UpdateChannel currentChannel,
            UpdateReleaseSource source
    ) {
        if (releases == null || currentVersion == null || currentChannel == null || source == null) {
            return Optional.empty();
        }

        UpdateRelease best = null;
        for (GitHubRelease release : releases) {
            if (release == null || release.draft()) continue;

            Optional<SemanticVersion> tagVersion = SemanticVersion.parse(release.tagName());
            if (tagVersion.isEmpty()) continue;
            if (tagVersion.get().compareTo(currentVersion) <= 0) continue; // equal or older - no update

            Optional<GitHubReleaseAsset> manifestAsset = release.findAsset(MANIFEST_ASSET_NAME);
            Optional<GitHubReleaseAsset> installerAsset = release.findAsset(INSTALLER_ASSET_NAME);
            if (manifestAsset.isEmpty() || installerAsset.isEmpty()) continue;

            UpdateManifest manifest;
            try {
                String manifestJson = source.fetchText(manifestAsset.get().browserDownloadUrl());
                Optional<UpdateManifest> parsed = UpdateManifest.parse(manifestJson);
                if (parsed.isEmpty()) continue;
                manifest = parsed.get();
            } catch (IOException e) {
                continue; // this one release's manifest couldn't be fetched/parsed - skip it, never fail the whole check
            }

            Optional<SemanticVersion> manifestVersion = SemanticVersion.parse(manifest.version());
            if (manifestVersion.isEmpty() || manifestVersion.get().compareTo(tagVersion.get()) != 0) {
                continue; // manifest disagrees with the tag it shipped under - reject rather than trust either blindly
            }

            UpdateChannel releaseChannel = UpdateChannel.classify(tagVersion.get());
            if (!currentChannel.accepts(releaseChannel)) continue;

            UpdateRelease candidate = new UpdateRelease(release, manifest, installerAsset.get(), tagVersion.get());
            if (best == null || candidate.version().compareTo(best.version()) > 0) {
                best = candidate;
            }
        }
        return Optional.ofNullable(best);
    }
}
