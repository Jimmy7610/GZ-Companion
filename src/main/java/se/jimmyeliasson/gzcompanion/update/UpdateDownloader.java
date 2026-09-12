package se.jimmyeliasson.gzcompanion.update;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Downloads and verifies one update's installer - the security-critical boundary of this whole
 * feature (see docs/UPDATES.md). Every check below must pass, in order, before the file is ever
 * treated as a real, executable installer:
 *
 * <ol>
 *   <li>HTTPS only (enforced by {@link UpdateByteSource} implementations).</li>
 *   <li>Downloaded to a {@code .part} staging file - never the final name until fully verified.</li>
 *   <li>Actual byte count compared against the manifest's declared size.</li>
 *   <li>SHA-256 computed locally from the downloaded bytes, compared against the manifest.</li>
 *   <li>If GitHub's own release-asset API provided a digest, that is checked too (defense in
 *       depth - never a substitute for the manifest check above).</li>
 *   <li>Only after ALL of the above succeed: the {@code .part} file is atomically renamed to its
 *       final name. A failure at any step deletes the {@code .part} file and leaves the current
 *       installation completely untouched.</li>
 * </ol>
 */
public final class UpdateDownloader {
    public interface ProgressListener {
        void onProgress(long downloadedBytes, long totalBytes);
    }

    public sealed interface Result {
        record Success(Path installerPath) implements Result {}
        record Failure(String reason) implements Result {}
    }

    private final UpdateByteSource byteSource;

    public UpdateDownloader(UpdateByteSource byteSource) {
        this.byteSource = byteSource;
    }

    public Result downloadAndVerify(UpdateRelease update, ProgressListener listener) {
        UpdateManifest manifest = update.manifest();
        Path finalPath = UpdatePaths.installerPath(update.version().toDisplayString(), manifest.installerFileName());
        Path partPath = UpdatePaths.partPath(finalPath);

        try {
            Files.createDirectories(finalPath.getParent());
            Files.deleteIfExists(partPath); // never resume/trust a leftover .part from an earlier abandoned attempt

            byteSource.download(update.installerAsset().browserDownloadUrl(), partPath,
                    downloaded -> {
                        if (listener != null) listener.onProgress(downloaded, manifest.installerSizeBytes());
                    });

            long actualSize = Files.size(partPath);
            if (actualSize != manifest.installerSizeBytes()) {
                Files.deleteIfExists(partPath);
                return new Result.Failure("Nedladdningen avbröts eller är ofullständig. Din nuvarande version fungerar fortfarande.");
            }

            String actualSha256 = Sha256Utils.hex(partPath);
            if (!actualSha256.equalsIgnoreCase(manifest.installerSha256())) {
                Files.deleteIfExists(partPath);
                return new Result.Failure("Uppdateringen kunde inte verifieras och installerades inte. Din nuvarande version har inte ändrats.");
            }

            String githubDigest = update.installerAsset().digestSha256();
            if (githubDigest != null && !githubDigest.equalsIgnoreCase(actualSha256)) {
                Files.deleteIfExists(partPath);
                return new Result.Failure("Uppdateringen kunde inte verifieras och installerades inte. Din nuvarande version har inte ändrats.");
            }

            moveIntoPlace(partPath, finalPath);
            return new Result.Success(finalPath);
        } catch (IOException e) {
            deleteQuietly(partPath);
            return new Result.Failure("Kunde inte hämta uppdateringen. Din nuvarande version fungerar fortfarande.");
        }
    }

    private static void moveIntoPlace(Path from, Path to) throws IOException {
        try {
            Files.move(from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Best-effort cleanup only - never let a failed delete mask the real error.
        }
    }
}
