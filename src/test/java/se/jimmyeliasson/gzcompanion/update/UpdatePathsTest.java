package se.jimmyeliasson.gzcompanion.update;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class UpdatePathsTest {

    @Test
    @DisplayName("Updates always live under a dedicated GZ Companion folder, never .minecraft/mods/saves/config/Desktop/Downloads")
    void updatesLiveUnderDedicatedFolder() {
        String root = UpdatePaths.updatesRootDir().toString();
        assertTrue(root.contains("GZ Companion"));
        assertTrue(root.endsWith("updates") || root.endsWith("updates" + java.io.File.separator));
        for (String forbidden : new String[]{".minecraft", "mods", "saves", "config", "Desktop", "Downloads"}) {
            assertFalse(root.contains(forbidden), "must never live under " + forbidden);
        }
    }

    @Test
    @DisplayName("Each version gets its own subdirectory")
    void eachVersionGetsOwnSubdirectory() {
        Path a = UpdatePaths.versionDir("0.1.0-alpha.2");
        Path b = UpdatePaths.versionDir("0.1.0-alpha.3");
        assertNotEquals(a, b);
        assertTrue(a.startsWith(UpdatePaths.updatesRootDir()));
    }

    @Test
    @DisplayName("A path-traversal version string is sanitized rather than passed through")
    void pathTraversalVersionSanitized() {
        Path path = UpdatePaths.versionDir("../../evil");
        assertFalse(path.toString().contains(".."));
        assertTrue(path.startsWith(UpdatePaths.updatesRootDir()));
    }

    @Test
    @DisplayName("A path-traversal file name is sanitized rather than passed through")
    void pathTraversalFileNameSanitized() {
        Path path = UpdatePaths.installerPath("0.1.0-alpha.3", "../../evil.exe");
        assertFalse(path.toString().contains(".."));
    }

    @Test
    @DisplayName("The .part staging path is derived from the final path with a .part suffix")
    void partPathHasCorrectSuffix() {
        Path finalPath = UpdatePaths.installerPath("0.1.0-alpha.3", "GZ-Companion-Setup.exe");
        Path partPath = UpdatePaths.partPath(finalPath);
        assertEquals("GZ-Companion-Setup.exe.part", partPath.getFileName().toString());
        assertEquals(finalPath.getParent(), partPath.getParent());
    }
}
