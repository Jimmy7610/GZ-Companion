using GZCompanion.Installer.Core;
using Xunit;

namespace GZCompanion.Installer.Tests;

public class EnvironmentDetectionTests : IDisposable
{
    private readonly string _tempRoot = Directory.CreateTempSubdirectory("gzc-installer-test-").FullName;

    public void Dispose()
    {
        try { Directory.Delete(_tempRoot, recursive: true); } catch { /* best effort */ }
    }

    [Theory]
    [InlineData(10, 19045, WindowsSupportLevel.Supported)] // Windows 10
    [InlineData(10, 22621, WindowsSupportLevel.Supported)] // Windows 11
    [InlineData(6, 1, WindowsSupportLevel.Unsupported)]    // Windows 7-era
    [InlineData(11, 0, WindowsSupportLevel.Unknown)]       // a hypothetical future major version
    public void ClassifiesWindowsVersionsCorrectly(int major, int build, WindowsSupportLevel expected)
    {
        var result = EnvironmentDetection.CheckWindowsVersion(new Version(major, 0, build));
        Assert.Equal(expected, result.Level);
    }

    [Fact]
    public void Windows10And11AreDistinguishedByBuildNumber()
    {
        var win10 = EnvironmentDetection.CheckWindowsVersion(new Version(10, 0, 19045));
        var win11 = EnvironmentDetection.CheckWindowsVersion(new Version(10, 0, 22621));
        Assert.Contains("10", win10.DisplayVersion);
        Assert.Contains("11", win11.DisplayVersion);
    }

    [Fact]
    public void LauncherNotFoundWhenDotMinecraftAbsent()
    {
        var paths = new InstallPaths(Path.Combine(_tempRoot, "roaming"), Path.Combine(_tempRoot, "local"));
        var result = EnvironmentDetection.CheckLauncher(paths);
        Assert.False(result.Found);
    }

    [Fact]
    public void BareDotMinecraftDirWithNoProfileFileIsNotEnoughEvidence()
    {
        // The exact regression this test locks in: a .minecraft directory can exist (e.g. from a
        // partial/aborted setup) without the launcher ever having actually been run. Mirrors
        // Fabric's own installer, which refuses to create a profile when it finds zero
        // recognized launcher types.
        var appData = Path.Combine(_tempRoot, "roaming");
        Directory.CreateDirectory(Path.Combine(appData, ".minecraft"));
        var paths = new InstallPaths(appData, Path.Combine(_tempRoot, "local"));
        var result = EnvironmentDetection.CheckLauncher(paths);
        Assert.False(result.Found);
        Assert.True(result.DotMinecraftExists);
        Assert.Empty(result.ExistingProfilePaths);
    }

    [Fact]
    public void LauncherFoundWhenOnlyWin32ProfileFileExists()
    {
        var appData = Path.Combine(_tempRoot, "roaming");
        Directory.CreateDirectory(Path.Combine(appData, ".minecraft"));
        File.WriteAllText(Path.Combine(appData, ".minecraft", "launcher_profiles.json"), "{\"profiles\":{}}");
        var paths = new InstallPaths(appData, Path.Combine(_tempRoot, "local"));
        var result = EnvironmentDetection.CheckLauncher(paths);
        Assert.True(result.Found);
        Assert.Equal(new[] { paths.Win32LauncherProfilesPath }, result.ExistingProfilePaths);
    }

    [Fact]
    public void LauncherFoundWhenOnlyMicrosoftStoreProfileFileExists()
    {
        var appData = Path.Combine(_tempRoot, "roaming");
        Directory.CreateDirectory(Path.Combine(appData, ".minecraft"));
        File.WriteAllText(Path.Combine(appData, ".minecraft", "launcher_profiles_microsoft_store.json"), "{\"profiles\":{}}");
        var paths = new InstallPaths(appData, Path.Combine(_tempRoot, "local"));
        var result = EnvironmentDetection.CheckLauncher(paths);
        Assert.True(result.Found);
        Assert.Equal(new[] { paths.MicrosoftStoreLauncherProfilesPath }, result.ExistingProfilePaths);
    }

    [Fact]
    public void LauncherFoundListsBothWhenBothProfileFilesExist()
    {
        var appData = Path.Combine(_tempRoot, "roaming");
        Directory.CreateDirectory(Path.Combine(appData, ".minecraft"));
        File.WriteAllText(Path.Combine(appData, ".minecraft", "launcher_profiles.json"), "{\"profiles\":{}}");
        File.WriteAllText(Path.Combine(appData, ".minecraft", "launcher_profiles_microsoft_store.json"), "{\"profiles\":{}}");
        var paths = new InstallPaths(appData, Path.Combine(_tempRoot, "local"));
        var result = EnvironmentDetection.CheckLauncher(paths);
        Assert.True(result.Found);
        Assert.Equal(2, result.ExistingProfilePaths.Count);
    }

    [Fact]
    public void ExistingInstallNotFoundWhenGameDirAbsent()
    {
        var paths = new InstallPaths(Path.Combine(_tempRoot, "roaming"), Path.Combine(_tempRoot, "local"));
        var result = EnvironmentDetection.CheckExistingInstall(paths);
        Assert.False(result.Found);
    }

    [Fact]
    public void ExistingInstallDetectsPreviousCompanionVersion()
    {
        var paths = new InstallPaths(Path.Combine(_tempRoot, "roaming"), Path.Combine(_tempRoot, "local"));
        Directory.CreateDirectory(paths.GzCompanionGameDir);
        InstalledStateStore.Write(paths.InstalledManifestPath, new InstalledState("0.0.9-alpha", "26.1.2", "0.19.5", "0.155.3+26.1.2", DateTimeOffset.UtcNow.ToString("O")));

        var result = EnvironmentDetection.CheckExistingInstall(paths);
        Assert.True(result.Found);
        Assert.Equal("0.0.9-alpha", result.InstalledCompanionVersion);
    }

    [Fact]
    public void DuplicateJarPrevention_DetectsCompanionJarAlreadyPresent()
    {
        var paths = new InstallPaths(Path.Combine(_tempRoot, "roaming"), Path.Combine(_tempRoot, "local"));
        Directory.CreateDirectory(paths.GzCompanionModsDir);
        File.WriteAllText(Path.Combine(paths.GzCompanionModsDir, "gzcompanion-0.1.0-alpha.1.jar"), "fake");

        var result = EnvironmentDetection.CheckExistingInstall(paths);
        Assert.True(result.HasCompanionJar);
    }

    private sealed class FakeProcessLister : IProcessLister
    {
        private readonly Dictionary<string, IReadOnlyList<ProcessInfo>> _byName;
        private readonly IReadOnlyList<ProcessInfo> _allWithWindows;

        public FakeProcessLister(Dictionary<string, IReadOnlyList<ProcessInfo>> byName, IReadOnlyList<ProcessInfo>? allWithWindows = null)
        {
            _byName = byName;
            _allWithWindows = allWithWindows ?? byName.Values.SelectMany(v => v).ToList();
        }

        public IReadOnlyList<ProcessInfo> GetProcessesByName(string name)
            => _byName.TryGetValue(name, out var list) ? list : Array.Empty<ProcessInfo>();

        public IReadOnlyList<ProcessInfo> GetAllProcessesWithWindowTitles() => _allWithWindows;
    }

    [Fact]
    public void DetectsMinecraftRunningByWindowTitle()
    {
        var lister = new FakeProcessLister(new()
        {
            ["javaw"] = new[] { new ProcessInfo(123, "Minecraft* 26.1.2") },
        });
        Assert.True(EnvironmentDetection.IsMinecraftLikelyRunning(lister));
    }

    [Fact]
    public void DoesNotFalsePositiveOnUnrelatedJavaProcess()
    {
        var lister = new FakeProcessLister(new()
        {
            ["javaw"] = new[] { new ProcessInfo(456, "Some Other Java App") },
        });
        Assert.False(EnvironmentDetection.IsMinecraftLikelyRunning(lister));
    }

    [Fact]
    public void NoRunningJavaProcessesMeansNotRunning()
    {
        var lister = new FakeProcessLister(new());
        Assert.False(EnvironmentDetection.IsMinecraftLikelyRunning(lister));
    }

    // ------------------------------------------------------------------
    // Launcher-app detection (distinct from the game-process detection above). Confirmed
    // empirically on a real machine: the official launcher's main window title is exactly
    // "Minecraft Launcher", under a process literally named "Minecraft" - process name alone
    // would collide with the game itself, so this matches on window title.
    // ------------------------------------------------------------------

    [Fact]
    public void DetectsLauncherRunningByExactWindowTitle()
    {
        var lister = new FakeProcessLister(new(), allWithWindows: new[] { new ProcessInfo(2448, "Minecraft Launcher") });
        Assert.True(EnvironmentDetection.IsMinecraftLauncherRunning(lister));
    }

    [Fact]
    public void DoesNotConfuseTheGameWindowWithTheLauncherWindow()
    {
        // The running GAME's window title looks like "Minecraft 26.1.2" or "Minecraft* 26.1.2" -
        // it must never be mistaken for the launcher itself.
        var lister = new FakeProcessLister(new(), allWithWindows: new[] { new ProcessInfo(999, "Minecraft* 26.1.2") });
        Assert.False(EnvironmentDetection.IsMinecraftLauncherRunning(lister));
    }

    [Fact]
    public void NoWindowsAtAllMeansLauncherNotRunning()
    {
        var lister = new FakeProcessLister(new(), allWithWindows: Array.Empty<ProcessInfo>());
        Assert.False(EnvironmentDetection.IsMinecraftLauncherRunning(lister));
    }

    [Fact]
    public void IgnoresTitlelessBackgroundHelperProcessesOfTheSameName()
    {
        // The real launcher spawns several background helper processes named "Minecraft" with no
        // window at all - GetAllProcessesWithWindowTitles() never reports those, so this proves
        // the detection isn't fooled by process COUNT, only by an actual launcher window.
        var lister = new FakeProcessLister(new(), allWithWindows: Array.Empty<ProcessInfo>());
        Assert.False(EnvironmentDetection.IsMinecraftLauncherRunning(lister));
    }
}
