package se.jimmyeliasson.gzcompanion.update;

import java.nio.file.Path;

/**
 * Local storage locations for downloaded updates - always under GZ Companion's own dedicated
 * folder, NEVER under {@code .minecraft}, {@code mods}, {@code saves}, {@code config}, the
 * Desktop, or Downloads. Mirrors the installer's own {@code InstallPaths.GzCompanionRootDir}
 * ({@code %LOCALAPPDATA%\GZ Companion}) so both sides of this feature agree on where things live.
 */
public final class UpdatePaths {
    private UpdatePaths() {}

    public static Path updatesRootDir() {
        String localAppData = System.getenv("LOCALAPPDATA");
        Path base = (localAppData != null && !localAppData.isBlank())
                ? Path.of(localAppData)
                : Path.of(System.getProperty("user.home", "."), "AppData", "Local");
        return base.resolve("GZ Companion").resolve("updates");
    }

    /** One version's own subfolder - each update version gets its own directory so old/new never collide mid-download. */
    public static Path versionDir(String version) {
        return updatesRootDir().resolve(sanitize(version));
    }

    public static Path installerPath(String version, String installerFileName) {
        return versionDir(version).resolve(sanitize(installerFileName));
    }

    /** The staging file a download writes to before it's verified and renamed into place. */
    public static Path partPath(Path finalPath) {
        return finalPath.resolveSibling(finalPath.getFileName().toString() + ".part");
    }

    /**
     * Defends against a malformed/malicious version or file name string being used to build a
     * path (e.g. containing {@code ..} or a path separator) - collapses anything not a plain safe
     * character to {@code _} rather than ever passing it through to {@link Path#resolve} as-is.
     */
    private static String sanitize(String value) {
        String cleaned = value.replaceAll("[^A-Za-z0-9._-]", "_");
        // The character class above allows "." (legitimate in both version strings and file
        // extensions), so a ".." traversal sequence would otherwise survive it untouched -
        // collapse any run of two-or-more dots separately.
        cleaned = cleaned.replaceAll("\\.{2,}", "_");
        return cleaned.isBlank() ? "_" : cleaned;
    }
}
