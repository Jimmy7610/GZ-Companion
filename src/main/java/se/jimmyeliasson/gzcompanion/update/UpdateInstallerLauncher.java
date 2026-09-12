package se.jimmyeliasson.gzcompanion.update;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Starts the downloaded {@code GZ-Companion-Setup.exe} in its update-apply mode
 * ({@code --apply-update --wait-pid <pid> --from-version <v>}). This is the ONLY class in this
 * package that ever executes a downloaded file, and it does so only after re-verifying the SHA-256
 * hash immediately before launch (the file could theoretically have been tampered with or
 * corrupted on disk between download-time verification and this moment, however unlikely) - a
 * hash-mismatched file is never executed under any circumstance.
 *
 * <p>Per the required process ordering: this only ever STARTS the installer process. The caller
 * (see {@code se.jimmyeliasson.gzcompanion.update.UpdateManager#applyUpdate}) is responsible for
 * only requesting a Minecraft shutdown AFTER this returns success - never before, so a failure to
 * start the updater never leaves the game already closed with nothing to apply the update.
 */
public final class UpdateInstallerLauncher {
    private UpdateInstallerLauncher() {}

    public sealed interface LaunchResult {
        record Started() implements LaunchResult {}
        record Failed(String reason) implements LaunchResult {}
    }

    public static LaunchResult launch(Path installerPath, String expectedSha256, long minecraftPid, String fromVersion) {
        try {
            if (!Files.isRegularFile(installerPath)) {
                return new LaunchResult.Failed("Den nedladdade uppdateringsfilen saknas.");
            }
            String actualSha256 = Sha256Utils.hex(installerPath);
            if (!actualSha256.equalsIgnoreCase(expectedSha256)) {
                return new LaunchResult.Failed("Uppdateringen kunde inte verifieras och installerades inte.");
            }

            ProcessBuilder builder = new ProcessBuilder(
                    installerPath.toString(),
                    "--apply-update",
                    "--wait-pid", Long.toString(minecraftPid),
                    "--from-version", fromVersion
            );
            builder.directory(installerPath.getParent().toFile());
            builder.start();
            return new LaunchResult.Started();
        } catch (IOException e) {
            return new LaunchResult.Failed("Kunde inte starta uppdateraren.");
        }
    }
}
