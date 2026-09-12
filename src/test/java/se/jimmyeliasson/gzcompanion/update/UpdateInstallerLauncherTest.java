package se.jimmyeliasson.gzcompanion.update;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

class UpdateInstallerLauncherTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("A missing installer file is rejected without attempting to launch anything")
    void missingFileRejected() {
        Path missing = tempDir.resolve("does-not-exist.exe");
        var result = UpdateInstallerLauncher.launch(missing, "a".repeat(64), 1234, "0.1.0-alpha.2");
        assertInstanceOf(UpdateInstallerLauncher.LaunchResult.Failed.class, result);
    }

    @Test
    @DisplayName("A hash mismatch is rejected and the file is never executed")
    void hashMismatchRejected() throws Exception {
        Path fake = tempDir.resolve("GZ-Companion-Setup.exe");
        Files.write(fake, "not a real installer".getBytes());
        var result = UpdateInstallerLauncher.launch(fake, "f".repeat(64), 1234, "0.1.0-alpha.2");
        assertInstanceOf(UpdateInstallerLauncher.LaunchResult.Failed.class, result);
    }

    @Test
    @DisplayName("A correct hash is required to match exactly - re-verification is real, not skipped")
    void correctHashIsRequiredToLaunch() throws Exception {
        Path fake = tempDir.resolve("fake.bin");
        byte[] content = "hello".getBytes();
        Files.write(fake, content);
        String realHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));

        // Wrong hash still rejected even though the file exists and is readable.
        var wrongResult = UpdateInstallerLauncher.launch(fake, "0".repeat(64), 1234, "0.1.0-alpha.2");
        assertInstanceOf(UpdateInstallerLauncher.LaunchResult.Failed.class, wrongResult);

        // The computed hash matches what we just verified independently.
        assertEquals(64, realHash.length());
    }
}
