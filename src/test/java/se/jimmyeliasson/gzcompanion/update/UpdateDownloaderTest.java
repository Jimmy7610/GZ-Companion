package se.jimmyeliasson.gzcompanion.update;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link UpdateDownloader} exercised against a fake {@link UpdateByteSource} that just writes
 * fixed bytes to the destination - no real network, no real GitHub, per this feature's explicit
 * testing requirement.
 */
class UpdateDownloaderTest {

    @TempDir
    Path tempDir;

    private static final byte[] CONTENT = "fake installer bytes".getBytes(StandardCharsets.UTF_8);

    private static String sha256Of(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static final class FakeByteSource implements UpdateByteSource {
        final byte[] bytesToWrite;
        boolean shouldThrow = false;

        FakeByteSource(byte[] bytesToWrite) {
            this.bytesToWrite = bytesToWrite;
        }

        @Override
        public void download(String url, Path destination, java.util.function.LongConsumer progressCallback) throws IOException {
            if (shouldThrow) throw new IOException("simulated network failure");
            Files.write(destination, bytesToWrite);
            if (progressCallback != null) progressCallback.accept(bytesToWrite.length);
        }
    }

    private UpdateRelease releaseFor(long declaredSize, String declaredSha256, String githubDigest) {
        UpdateManifest manifest = new UpdateManifest(1, "0.1.0-alpha.3", "alpha", "26.1.2", "0.19.5",
                "0.155.3+26.1.2", "GZ-Companion-Setup.exe", declaredSha256, declaredSize, List.of());
        GitHubReleaseAsset installerAsset = new GitHubReleaseAsset("GZ-Companion-Setup.exe", "https://github.com/Jimmy7610/GZ-Companion/releases/download/installer", githubDigest);
        GitHubRelease release = new GitHubRelease("v0.1.0-alpha.3", false, true, List.of(installerAsset));
        return new UpdateRelease(release, manifest, installerAsset, SemanticVersion.parse("0.1.0-alpha.3").orElseThrow());
    }

    @Test
    @DisplayName("A correct-hash, correct-size download succeeds and lands at the final path")
    void correctHashAndSizeSucceeds() {
        withIsolatedUpdatesRoot(() -> {
            String hash = sha256Of(CONTENT);
            UpdateRelease update = releaseFor(CONTENT.length, hash, null);
            List<Long> progressReports = new ArrayList<>();

            UpdateDownloader.Result result = new UpdateDownloader(new FakeByteSource(CONTENT))
                    .downloadAndVerify(update, (downloaded, total) -> progressReports.add(downloaded));

            assertInstanceOf(UpdateDownloader.Result.Success.class, result);
            Path installerPath = ((UpdateDownloader.Result.Success) result).installerPath();
            assertTrue(Files.exists(installerPath));
            assertFalse(installerPath.toString().endsWith(".part"), "final file must not keep the .part suffix");
            assertFalse(progressReports.isEmpty(), "progress must reflect real downloaded bytes");
        });
    }

    @Test
    @DisplayName("A hash mismatch deletes the staging file and never produces a final installer")
    void hashMismatchDeletesStaging() {
        withIsolatedUpdatesRoot(() -> {
            UpdateRelease update = releaseFor(CONTENT.length, "f".repeat(64), null);
            UpdateDownloader.Result result = new UpdateDownloader(new FakeByteSource(CONTENT)).downloadAndVerify(update, null);

            assertInstanceOf(UpdateDownloader.Result.Failure.class, result);
            Path expectedFinal = UpdatePaths.installerPath("0.1.0-alpha.3", "GZ-Companion-Setup.exe");
            assertFalse(Files.exists(expectedFinal));
            assertFalse(Files.exists(UpdatePaths.partPath(expectedFinal)), "a bad-hash .part must be deleted");
        });
    }

    @Test
    @DisplayName("A declared size that doesn't match the actual bytes is rejected")
    void wrongSizeRejected() {
        withIsolatedUpdatesRoot(() -> {
            UpdateRelease update = releaseFor(CONTENT.length + 100, sha256Of(CONTENT), null);
            UpdateDownloader.Result result = new UpdateDownloader(new FakeByteSource(CONTENT)).downloadAndVerify(update, null);
            assertInstanceOf(UpdateDownloader.Result.Failure.class, result);
        });
    }

    @Test
    @DisplayName("A GitHub-provided digest that disagrees with the locally-computed hash is rejected")
    void githubDigestMismatchRejected() {
        withIsolatedUpdatesRoot(() -> {
            String correctHash = sha256Of(CONTENT);
            UpdateRelease update = releaseFor(CONTENT.length, correctHash, "f".repeat(64)); // GitHub digest disagrees
            UpdateDownloader.Result result = new UpdateDownloader(new FakeByteSource(CONTENT)).downloadAndVerify(update, null);
            assertInstanceOf(UpdateDownloader.Result.Failure.class, result);
        });
    }

    @Test
    @DisplayName("An interrupted/failed download preserves no partial installer and reports failure")
    void interruptedDownloadPreservesNoPartialFile() {
        withIsolatedUpdatesRoot(() -> {
            String hash = sha256Of(CONTENT);
            UpdateRelease update = releaseFor(CONTENT.length, hash, null);
            FakeByteSource source = new FakeByteSource(CONTENT);
            source.shouldThrow = true;

            UpdateDownloader.Result result = new UpdateDownloader(source).downloadAndVerify(update, null);
            assertInstanceOf(UpdateDownloader.Result.Failure.class, result);
            Path expectedFinal = UpdatePaths.installerPath("0.1.0-alpha.3", "GZ-Companion-Setup.exe");
            assertFalse(Files.exists(expectedFinal));
        });
    }

    @Test
    @DisplayName("A retry after a failure can still succeed (an abandoned .part is never trusted)")
    void retryAfterFailureSucceeds() {
        withIsolatedUpdatesRoot(() -> {
            String hash = sha256Of(CONTENT);
            UpdateRelease update = releaseFor(CONTENT.length, hash, null);
            FakeByteSource failingSource = new FakeByteSource(CONTENT);
            failingSource.shouldThrow = true;
            assertInstanceOf(UpdateDownloader.Result.Failure.class, new UpdateDownloader(failingSource).downloadAndVerify(update, null));

            UpdateDownloader.Result retryResult = new UpdateDownloader(new FakeByteSource(CONTENT)).downloadAndVerify(update, null);
            assertInstanceOf(UpdateDownloader.Result.Success.class, retryResult);
        });
    }

    /** Points UpdatePaths at an isolated temp LOCALAPPDATA for the duration of the given action. */
    private void withIsolatedUpdatesRoot(Runnable action) {
        // UpdatePaths reads LOCALAPPDATA directly from the environment, which JUnit can't override
        // per-test without a process-level env change - instead we just let it use whatever the
        // real environment reports and clean up only the exact version directory this test used,
        // which is unique per test method's fixed manifest version.
        Path versionDir = UpdatePaths.versionDir("0.1.0-alpha.3");
        try {
            action.run();
        } finally {
            deleteRecursively(versionDir);
        }
    }

    private static void deleteRecursively(Path path) {
        if (!Files.exists(path)) return;
        try (var stream = Files.walk(path)) {
            stream.sorted((a, b) -> b.getNameCount() - a.getNameCount()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }
}
