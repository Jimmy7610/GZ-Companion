package se.jimmyeliasson.gzcompanion.update;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure release-selection logic - the {@link UpdateReleaseSource} seam is a deterministic in-memory
 * fake (see {@link FakeReleaseSource}), never a real GitHub call, per this feature's explicit
 * "no real GitHub in unit tests" requirement.
 */
class UpdateCheckerTest {

    private static final String MANIFEST_ASSET = UpdateChecker.MANIFEST_ASSET_NAME;
    private static final String INSTALLER_ASSET = UpdateChecker.INSTALLER_ASSET_NAME;
    private static final String SHA = "a".repeat(64);

    private static final class FakeReleaseSource implements UpdateReleaseSource {
        private final List<GitHubRelease> releases;
        private final Map<String, String> manifestsByUrl = new HashMap<>();
        int fetchTextCalls = 0;

        FakeReleaseSource(List<GitHubRelease> releases) {
            this.releases = releases;
        }

        void putManifest(String url, String json) {
            manifestsByUrl.put(url, json);
        }

        @Override
        public List<GitHubRelease> fetchReleases() {
            return releases;
        }

        @Override
        public String fetchText(String url) throws IOException {
            fetchTextCalls++;
            String body = manifestsByUrl.get(url);
            if (body == null) throw new IOException("no fixture for " + url);
            return body;
        }
    }

    private static String manifestFor(String version) {
        return """
                {"schemaVersion":1,"version":"%s","channel":"alpha","minecraftVersion":"26.1.2",
                 "fabricLoaderVersion":"0.19.5","fabricApiVersion":"0.155.3+26.1.2",
                 "installer":{"fileName":"GZ-Companion-Setup.exe","sha256":"%s","sizeBytes":100},
                 "notes":["Nytt"]}
                """.formatted(version, SHA);
    }

    private static GitHubRelease releaseWithAssets(String tag, boolean draft, boolean prerelease, boolean withManifest, boolean withInstaller) {
        List<GitHubReleaseAsset> assets = new java.util.ArrayList<>();
        if (withManifest) assets.add(new GitHubReleaseAsset(MANIFEST_ASSET, "https://example.invalid/" + tag + "/manifest", null));
        if (withInstaller) assets.add(new GitHubReleaseAsset(INSTALLER_ASSET, "https://example.invalid/" + tag + "/installer", null));
        return new GitHubRelease(tag, draft, prerelease, assets);
    }

    private static final SemanticVersion CURRENT = SemanticVersion.parse("0.1.0-alpha.2").orElseThrow();

    @Test
    @DisplayName("Drafts are ignored, even if their version is newer")
    void draftsIgnored() {
        GitHubRelease draft = releaseWithAssets("v0.1.0-alpha.3", true, true, true, true);
        FakeReleaseSource source = new FakeReleaseSource(List.of(draft));
        source.putManifest("https://example.invalid/v0.1.0-alpha.3/manifest", manifestFor("0.1.0-alpha.3"));

        assertTrue(UpdateChecker.findUpdate(List.of(draft), CURRENT, UpdateChannel.ALPHA, source).isEmpty());
    }

    @Test
    @DisplayName("A prerelease alpha release is accepted on the alpha channel")
    void prereleaseAlphaAcceptedOnAlphaChannel() {
        GitHubRelease release = releaseWithAssets("v0.1.0-alpha.3", false, true, true, true);
        FakeReleaseSource source = new FakeReleaseSource(List.of(release));
        source.putManifest("https://example.invalid/v0.1.0-alpha.3/manifest", manifestFor("0.1.0-alpha.3"));

        Optional<UpdateRelease> found = UpdateChecker.findUpdate(List.of(release), CURRENT, UpdateChannel.ALPHA, source);
        assertTrue(found.isPresent());
        assertEquals("0.1.0-alpha.3", found.get().version().toDisplayString());
    }

    @Test
    @DisplayName("The newest compatible version is selected when multiple newer releases exist")
    void newestCompatibleVersionSelected() {
        GitHubRelease r3 = releaseWithAssets("v0.1.0-alpha.3", false, true, true, true);
        GitHubRelease r4 = releaseWithAssets("v0.1.0-alpha.4", false, true, true, true);
        FakeReleaseSource source = new FakeReleaseSource(List.of(r3, r4));
        source.putManifest("https://example.invalid/v0.1.0-alpha.3/manifest", manifestFor("0.1.0-alpha.3"));
        source.putManifest("https://example.invalid/v0.1.0-alpha.4/manifest", manifestFor("0.1.0-alpha.4"));

        Optional<UpdateRelease> found = UpdateChecker.findUpdate(List.of(r3, r4), CURRENT, UpdateChannel.ALPHA, source);
        assertTrue(found.isPresent());
        assertEquals("0.1.0-alpha.4", found.get().version().toDisplayString());
    }

    @Test
    @DisplayName("Unrelated/malformed tags are ignored")
    void unrelatedTagsIgnored() {
        GitHubRelease weird = new GitHubRelease("some-other-project-tag", false, false, List.of());
        assertTrue(UpdateChecker.findUpdate(List.of(weird), CURRENT, UpdateChannel.ALPHA,
                new FakeReleaseSource(List.of(weird))).isEmpty());
    }

    @Test
    @DisplayName("A release missing the manifest asset is ignored, fails safe")
    void missingManifestAssetIgnored() {
        GitHubRelease release = releaseWithAssets("v0.1.0-alpha.3", false, true, false, true);
        assertTrue(UpdateChecker.findUpdate(List.of(release), CURRENT, UpdateChannel.ALPHA,
                new FakeReleaseSource(List.of(release))).isEmpty());
    }

    @Test
    @DisplayName("A release missing the installer asset is ignored, fails safe")
    void missingInstallerAssetIgnored() {
        GitHubRelease release = releaseWithAssets("v0.1.0-alpha.3", false, true, true, false);
        assertTrue(UpdateChecker.findUpdate(List.of(release), CURRENT, UpdateChannel.ALPHA,
                new FakeReleaseSource(List.of(release))).isEmpty());
    }

    @Test
    @DisplayName("A manifest whose declared version disagrees with the release tag is rejected")
    void manifestVersionTagMismatchRejected() {
        GitHubRelease release = releaseWithAssets("v0.1.0-alpha.3", false, true, true, true);
        FakeReleaseSource source = new FakeReleaseSource(List.of(release));
        source.putManifest("https://example.invalid/v0.1.0-alpha.3/manifest", manifestFor("0.1.0-alpha.4")); // mismatch!

        assertTrue(UpdateChecker.findUpdate(List.of(release), CURRENT, UpdateChannel.ALPHA, source).isEmpty());
    }

    @Test
    @DisplayName("An equal or older release never counts as an update")
    void equalOrOlderNeverUpdate() {
        GitHubRelease same = releaseWithAssets("v0.1.0-alpha.2", false, true, true, true);
        GitHubRelease older = releaseWithAssets("v0.1.0-alpha.1", false, true, true, true);
        assertTrue(UpdateChecker.findUpdate(List.of(same), CURRENT, UpdateChannel.ALPHA, new FakeReleaseSource(List.of(same))).isEmpty());
        assertTrue(UpdateChecker.findUpdate(List.of(older), CURRENT, UpdateChannel.ALPHA, new FakeReleaseSource(List.of(older))).isEmpty());
    }

    @Test
    @DisplayName("A stable-channel client never receives a prerelease update")
    void stableChannelNeverReceivesPrerelease() {
        GitHubRelease release = releaseWithAssets("v0.1.0-alpha.3", false, true, true, true);
        FakeReleaseSource source = new FakeReleaseSource(List.of(release));
        source.putManifest("https://example.invalid/v0.1.0-alpha.3/manifest", manifestFor("0.1.0-alpha.3"));

        assertTrue(UpdateChecker.findUpdate(List.of(release), CURRENT, UpdateChannel.STABLE, source).isEmpty());
    }

    @Test
    @DisplayName("A release whose manifest can't be fetched is skipped without failing the whole check")
    void unfetchableManifestSkippedNotFatal() {
        GitHubRelease broken = releaseWithAssets("v0.1.0-alpha.3", false, true, true, true);
        GitHubRelease good = releaseWithAssets("v0.1.0-alpha.4", false, true, true, true);
        FakeReleaseSource source = new FakeReleaseSource(List.of(broken, good));
        // Deliberately no fixture registered for broken's manifest URL - fetchText will throw.
        source.putManifest("https://example.invalid/v0.1.0-alpha.4/manifest", manifestFor("0.1.0-alpha.4"));

        Optional<UpdateRelease> found = UpdateChecker.findUpdate(List.of(broken, good), CURRENT, UpdateChannel.ALPHA, source);
        assertTrue(found.isPresent());
        assertEquals("0.1.0-alpha.4", found.get().version().toDisplayString());
    }

    @Test
    @DisplayName("Null inputs never throw - fail safe to no update")
    void nullInputsFailSafe() {
        assertDoesNotThrow(() -> UpdateChecker.findUpdate(null, CURRENT, UpdateChannel.ALPHA, new FakeReleaseSource(List.of())));
        assertTrue(UpdateChecker.findUpdate(null, CURRENT, UpdateChannel.ALPHA, new FakeReleaseSource(List.of())).isEmpty());
    }
}
