using System.Diagnostics;

namespace GZCompanion.Installer.Core;

public enum WindowsSupportLevel
{
    Supported,
    Unknown,
    Unsupported,
}

public sealed record WindowsCheckResult(WindowsSupportLevel Level, string DisplayVersion);

public sealed record LauncherCheckResult(bool Found, bool DotMinecraftExists, bool LauncherProfilesExists, string DotMinecraftDir);

public sealed record ExistingInstallCheckResult(bool Found, string GameDir, bool HasCompanionJar, string? InstalledCompanionVersion);

/// <summary>
/// Pure detection logic (no UI). Every check reports what it found rather than throwing, so the
/// installer can show a clear Swedish message instead of a stack trace. Detection is read-only
/// and uses the real filesystem directly (via the injectable roots in <see cref="InstallPaths"/>)
/// rather than a filesystem abstraction - tests point it at a real temp directory instead of
/// faking I/O, which exercises real path handling (spaces, non-ASCII) exactly as production does.
/// </summary>
public static class EnvironmentDetection
{
    /// <summary>
    /// This installer only supports Windows 10/11. <paramref name="osVersion"/> is injected
    /// (normally <c>Environment.OSVersion.Version</c>) so this is testable without depending on
    /// the actual OS the tests run on.
    /// </summary>
    public static WindowsCheckResult CheckWindowsVersion(Version osVersion)
    {
        // Windows 10 and 11 both report major version 10 via the classic API; build number is
        // what actually distinguishes them (11 starts at build 22000). Both are supported here.
        if (osVersion.Major < 10)
        {
            return new WindowsCheckResult(WindowsSupportLevel.Unsupported, $"Windows (build {osVersion.Build})");
        }
        if (osVersion.Major == 10)
        {
            string name = osVersion.Build >= 22000 ? "Windows 11" : "Windows 10";
            return new WindowsCheckResult(WindowsSupportLevel.Supported, name);
        }
        // A future major version we haven't seen - don't claim unsupported, but don't claim
        // verified either.
        return new WindowsCheckResult(WindowsSupportLevel.Unknown, $"Windows (build {osVersion.Build})");
    }

    /// <summary>
    /// Detects the official Minecraft Launcher by its data directory, not by trying to locate its
    /// executable (which varies by install channel - Microsoft Store vs. standalone installer).
    /// Fabric's own installer uses the same signal.
    /// </summary>
    public static LauncherCheckResult CheckLauncher(InstallPaths paths)
    {
        bool dotMinecraftExists = Directory.Exists(paths.DotMinecraftDir);
        bool profilesExist = File.Exists(paths.LauncherProfilesPath);
        return new LauncherCheckResult(dotMinecraftExists || profilesExist, dotMinecraftExists, profilesExist, paths.DotMinecraftDir);
    }

    public static ExistingInstallCheckResult CheckExistingInstall(InstallPaths paths)
    {
        bool gameDirExists = Directory.Exists(paths.GzCompanionGameDir);
        if (!gameDirExists)
        {
            return new ExistingInstallCheckResult(false, paths.GzCompanionGameDir, false, null);
        }

        string? installedVersion = File.Exists(paths.InstalledManifestPath)
            ? InstalledStateStore.TryReadCompanionVersion(paths.InstalledManifestPath)
            : null;

        bool hasJar = Directory.Exists(paths.GzCompanionModsDir)
            && Directory.EnumerateFiles(paths.GzCompanionModsDir, "gzcompanion-*.jar").Any();

        return new ExistingInstallCheckResult(true, paths.GzCompanionGameDir, hasJar, installedVersion);
    }

    /// <summary>
    /// Best-effort "is Minecraft currently running" check. Process command-line inspection across
    /// users needs elevated privileges, so this uses the same signal a human would: a java/javaw
    /// process whose main window title mentions Minecraft. It can miss a headless/minimized edge
    /// case, which is why the installer also prepares/verifies a replacement file fully before
    /// ever deleting the old one (see FileReplacement) rather than relying on this check alone.
    /// </summary>
    public static bool IsMinecraftLikelyRunning(IProcessLister processLister)
    {
        foreach (var name in new[] { "javaw", "java" })
        {
            foreach (var proc in processLister.GetProcessesByName(name))
            {
                if (proc.MainWindowTitle.Contains("Minecraft", StringComparison.OrdinalIgnoreCase))
                {
                    return true;
                }
            }
        }
        return false;
    }
}

/// <summary>Minimal process-listing seam so IsMinecraftLikelyRunning is testable without real processes.</summary>
public interface IProcessLister
{
    IReadOnlyList<ProcessInfo> GetProcessesByName(string name);
}

public sealed record ProcessInfo(int Id, string MainWindowTitle);

public sealed class RealProcessLister : IProcessLister
{
    public IReadOnlyList<ProcessInfo> GetProcessesByName(string name)
    {
        var results = new List<ProcessInfo>();
        foreach (var p in Process.GetProcessesByName(name))
        {
            try
            {
                results.Add(new ProcessInfo(p.Id, p.MainWindowTitle ?? string.Empty));
            }
            catch
            {
                // A process that exits mid-enumeration, or one we can't query (permissions),
                // must never take the whole detection down.
            }
            finally
            {
                p.Dispose();
            }
        }
        return results;
    }
}
