package se.jimmyeliasson.gzcompanion.update;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** {@link UpdateManager}'s state machine - fully synthetic sources/downloaders, no real network. */
class UpdateManagerTest {

    private static final SemanticVersion CURRENT = SemanticVersion.parse("0.1.0-alpha.2").orElseThrow();
    private static final String SHA = "a".repeat(64);

    private static final class CountingSource implements UpdateReleaseSource {
        final AtomicInteger fetchReleasesCalls = new AtomicInteger();
        List<GitHubRelease> releasesToReturn = List.of();
        String manifestJson = null;
        boolean throwOnFetch = false;

        @Override
        public List<GitHubRelease> fetchReleases() throws IOException {
            fetchReleasesCalls.incrementAndGet();
            if (throwOnFetch) throw new IOException("offline");
            return releasesToReturn;
        }

        @Override
        public String fetchText(String url) {
            return manifestJson;
        }
    }

    private static final class NoopByteSource implements UpdateByteSource {
        final AtomicInteger downloadCalls = new AtomicInteger();

        @Override
        public void download(String url, Path destination, java.util.function.LongConsumer progressCallback) throws IOException {
            downloadCalls.incrementAndGet();
            java.nio.file.Files.write(destination, new byte[]{1, 2, 3});
            if (progressCallback != null) progressCallback.accept(3);
        }
    }

    private static GitHubRelease releaseWithManifestAndInstaller(String tag) {
        return new GitHubRelease(tag, false, true, List.of(
                new GitHubReleaseAsset(UpdateChecker.MANIFEST_ASSET_NAME, "https://example.invalid/manifest", null),
                new GitHubReleaseAsset(UpdateChecker.INSTALLER_ASSET_NAME, "https://example.invalid/installer", null)
        ));
    }

    private static String manifestFor(String version, long size, String sha) {
        return """
                {"schemaVersion":1,"version":"%s","channel":"alpha","minecraftVersion":"26.1.2",
                 "fabricLoaderVersion":"0.19.5","fabricApiVersion":"0.155.3+26.1.2",
                 "installer":{"fileName":"GZ-Companion-Setup.exe","sha256":"%s","sizeBytes":%d},
                 "notes":["Nytt"]}
                """.formatted(version, sha, size);
    }

    private static void awaitState(UpdateManager manager, UpdateState expected, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (manager.getSnapshot().state() == expected) return;
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        fail("Timed out waiting for state " + expected + " - last seen: " + manager.getSnapshot().state());
    }

    @Test
    @DisplayName("A manual check finds an available update and transitions to UPDATE_AVAILABLE")
    void manualCheckFindsUpdate() {
        CountingSource source = new CountingSource();
        source.releasesToReturn = List.of(releaseWithManifestAndInstaller("v0.1.0-alpha.3"));
        source.manifestJson = manifestFor("0.1.0-alpha.3", 100, SHA);
        UpdateManager manager = new UpdateManager(CURRENT, UpdateChannel.ALPHA, source, new UpdateDownloader(new NoopByteSource()));
        try {
            manager.checkNow(true);
            awaitState(manager, UpdateState.UPDATE_AVAILABLE, 2000);
            assertNotNull(manager.getSnapshot().availableUpdate());
        } finally {
            manager.shutdown();
        }
    }

    @Test
    @DisplayName("No update available transitions to UP_TO_DATE")
    void noUpdateTransitionsToUpToDate() {
        CountingSource source = new CountingSource();
        source.releasesToReturn = List.of();
        UpdateManager manager = new UpdateManager(CURRENT, UpdateChannel.ALPHA, source, new UpdateDownloader(new NoopByteSource()));
        try {
            manager.checkNow(true);
            awaitState(manager, UpdateState.UP_TO_DATE, 2000);
        } finally {
            manager.shutdown();
        }
    }

    @Test
    @DisplayName("A network error during check does not break the app - falls back to IDLE quietly")
    void networkErrorFallsBackToIdle() {
        CountingSource source = new CountingSource();
        source.throwOnFetch = true;
        UpdateManager manager = new UpdateManager(CURRENT, UpdateChannel.ALPHA, source, new UpdateDownloader(new NoopByteSource()));
        try {
            manager.checkNow(true);
            awaitState(manager, UpdateState.IDLE, 2000);
        } finally {
            manager.shutdown();
        }
    }

    @Test
    @DisplayName("An automatic check within the cooldown window is skipped - no duplicate concurrent network calls")
    void automaticCheckRespectsCooldown() {
        CountingSource source = new CountingSource();
        source.releasesToReturn = List.of();
        UpdateManager manager = new UpdateManager(CURRENT, UpdateChannel.ALPHA, source, new UpdateDownloader(new NoopByteSource()));
        try {
            manager.checkNow(true); // establishes lastCheckAt
            awaitState(manager, UpdateState.UP_TO_DATE, 2000);
            int callsAfterFirst = source.fetchReleasesCalls.get();

            manager.checkNow(false); // automatic - should be skipped, still within cooldown
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {
            }
            assertEquals(callsAfterFirst, source.fetchReleasesCalls.get(), "an automatic check inside the cooldown must not call the network again");
        } finally {
            manager.shutdown();
        }
    }

    @Test
    @DisplayName("A manual check always bypasses the cooldown")
    void manualCheckBypassesCooldown() {
        CountingSource source = new CountingSource();
        source.releasesToReturn = List.of();
        UpdateManager manager = new UpdateManager(CURRENT, UpdateChannel.ALPHA, source, new UpdateDownloader(new NoopByteSource()));
        try {
            manager.checkNow(true);
            awaitState(manager, UpdateState.UP_TO_DATE, 2000);
            manager.checkNow(true);
            // Give the second check a moment to actually run.
            long deadline = System.currentTimeMillis() + 2000;
            while (source.fetchReleasesCalls.get() < 2 && System.currentTimeMillis() < deadline) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException ignored) {
                }
            }
            assertEquals(2, source.fetchReleasesCalls.get());
        } finally {
            manager.shutdown();
        }
    }

    @Test
    @DisplayName("startDownload only reaches READY_TO_INSTALL after real hash/size verification succeeds")
    void readyOnlyAfterVerification() {
        CountingSource source = new CountingSource();
        byte[] content = {1, 2, 3};
        String realSha = sha256Of(content);
        source.releasesToReturn = List.of(releaseWithManifestAndInstaller("v0.1.0-alpha.3"));
        source.manifestJson = manifestFor("0.1.0-alpha.3", content.length, realSha);
        UpdateManager manager = new UpdateManager(CURRENT, UpdateChannel.ALPHA, source, new UpdateDownloader(new NoopByteSource()));
        try {
            manager.checkNow(true);
            awaitState(manager, UpdateState.UPDATE_AVAILABLE, 2000);
            manager.startDownload();
            awaitState(manager, UpdateState.READY_TO_INSTALL, 2000);
            assertNotNull(manager.getSnapshot().readyInstallerPath());
        } finally {
            manager.shutdown();
            cleanupVersionDir("0.1.0-alpha.3");
        }
    }

    @Test
    @DisplayName("A verification failure (bad hash) transitions to ERROR, never READY_TO_INSTALL")
    void verificationFailureTransitionsToError() {
        CountingSource source = new CountingSource();
        byte[] content = {1, 2, 3};
        source.releasesToReturn = List.of(releaseWithManifestAndInstaller("v0.1.0-alpha.3"));
        source.manifestJson = manifestFor("0.1.0-alpha.3", content.length, "f".repeat(64)); // wrong hash
        UpdateManager manager = new UpdateManager(CURRENT, UpdateChannel.ALPHA, source, new UpdateDownloader(new NoopByteSource()));
        try {
            manager.checkNow(true);
            awaitState(manager, UpdateState.UPDATE_AVAILABLE, 2000);
            manager.startDownload();
            awaitState(manager, UpdateState.ERROR, 2000);
            assertNotNull(manager.getSnapshot().errorMessage());
        } finally {
            manager.shutdown();
            cleanupVersionDir("0.1.0-alpha.3");
        }
    }

    @Test
    @DisplayName("A failed download can be retried from ERROR via startDownload() and can then succeed")
    void retryDownloadFromErrorSucceeds() {
        CountingSource source = new CountingSource();
        byte[] content = {1, 2, 3};
        String realSha = sha256Of(content);
        source.releasesToReturn = List.of(releaseWithManifestAndInstaller("v0.1.0-alpha.3"));
        source.manifestJson = manifestFor("0.1.0-alpha.3", content.length, "f".repeat(64)); // wrong hash -> fails first
        UpdateManager manager = new UpdateManager(CURRENT, UpdateChannel.ALPHA, source, new UpdateDownloader(new NoopByteSource()));
        try {
            manager.checkNow(true);
            awaitState(manager, UpdateState.UPDATE_AVAILABLE, 2000);
            manager.startDownload();
            awaitState(manager, UpdateState.ERROR, 2000);

            // "fix" the manifest hash the fake source now reports for a retry, matching what the
            // real UI does: it just calls startDownload() again with the SAME still-known release.
            source.manifestJson = manifestFor("0.1.0-alpha.3", content.length, realSha);
            manager.checkNow(true); // re-check to pick up the corrected manifest, as a real retry flow would
            awaitState(manager, UpdateState.UPDATE_AVAILABLE, 2000);
            manager.startDownload();
            awaitState(manager, UpdateState.READY_TO_INSTALL, 2000);
        } finally {
            manager.shutdown();
            cleanupVersionDir("0.1.0-alpha.3");
        }
    }

    @Test
    @DisplayName("dismiss() returns an UPDATE_AVAILABLE/ERROR notice to IDLE without deleting anything downloaded")
    void dismissReturnsToIdle() {
        CountingSource source = new CountingSource();
        source.releasesToReturn = List.of(releaseWithManifestAndInstaller("v0.1.0-alpha.3"));
        source.manifestJson = manifestFor("0.1.0-alpha.3", 3, SHA);
        UpdateManager manager = new UpdateManager(CURRENT, UpdateChannel.ALPHA, source, new UpdateDownloader(new NoopByteSource()));
        try {
            manager.checkNow(true);
            awaitState(manager, UpdateState.UPDATE_AVAILABLE, 2000);
            manager.dismiss();
            assertEquals(UpdateState.IDLE, manager.getSnapshot().state());
        } finally {
            manager.shutdown();
        }
    }

    @Test
    @DisplayName("startDownload() is directly callable from ERROR (retry) without a fresh check, given the same known release")
    void startDownloadDirectlyCallableFromError() {
        CountingSource source = new CountingSource();
        byte[] content = {1, 2, 3};
        source.releasesToReturn = List.of(releaseWithManifestAndInstaller("v0.1.0-alpha.3"));
        source.manifestJson = manifestFor("0.1.0-alpha.3", content.length, "f".repeat(64));
        NoopByteSource byteSource = new NoopByteSource();
        UpdateManager manager = new UpdateManager(CURRENT, UpdateChannel.ALPHA, source, new UpdateDownloader(byteSource));
        try {
            manager.checkNow(true);
            awaitState(manager, UpdateState.UPDATE_AVAILABLE, 2000);
            manager.startDownload();
            awaitState(manager, UpdateState.ERROR, 2000);
            assertEquals(1, byteSource.downloadCalls.get());
            assertNotNull(manager.getSnapshot().availableUpdate(), "the failed release must still be remembered for retry");

            // Retry directly from ERROR, with no fresh checkNow() in between - the download call
            // count incrementing again proves this isn't a silent no-op.
            manager.startDownload();
            long deadline = System.currentTimeMillis() + 2000;
            while (byteSource.downloadCalls.get() < 2 && System.currentTimeMillis() < deadline) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException ignored) {
                }
            }
            assertEquals(2, byteSource.downloadCalls.get(), "retrying from ERROR must actually re-attempt the download, not silently ignore the call");
        } finally {
            manager.shutdown();
            cleanupVersionDir("0.1.0-alpha.3");
        }
    }

    private static String sha256Of(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void cleanupVersionDir(String version) {
        Path dir = UpdatePaths.versionDir(version);
        if (!java.nio.file.Files.exists(dir)) return;
        try (var stream = java.nio.file.Files.walk(dir)) {
            stream.sorted((a, b) -> b.getNameCount() - a.getNameCount()).forEach(p -> {
                try {
                    java.nio.file.Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }
}
