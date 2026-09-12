using System.Security.Cryptography;
using GZCompanion.Installer.Core;
using Xunit;

namespace GZCompanion.Installer.Tests;

/// <summary>
/// The SAFE UPDATE FAST PATH (<see cref="InstallEngine.RunFastUpdateAsync"/>) - never touches
/// launcher_profiles.json, never checks whether the Launcher app is open, and preserves every
/// piece of user data outside the mods it exclusively owns.
/// </summary>
public class InstallEngineFastUpdateTests : IDisposable
{
    private readonly string _tempRoot = Directory.CreateTempSubdirectory("gzc-fastupdate-test-").FullName;
    public void Dispose() { try { Directory.Delete(_tempRoot, recursive: true); } catch { } }

    private static string HashOf(byte[] bytes) => Convert.ToHexStringLower(SHA256.HashData(bytes));

    private const string FabricApiUrl = "https://example.invalid/fabric-api-new.jar";
    private static readonly byte[] NewFabricApiBytes = { 7, 7, 7, 7 };
    private static readonly byte[] NewCompanionJarBytes = { 4, 2, 4, 2, 4, 2 };

    private (InstallPaths paths, SupportedEntry target, FakeDownloader downloader, InstallEngine engine) Build(byte[]? embeddedJarBytes = null)
    {
        var paths = new InstallPaths(Path.Combine(_tempRoot, "roaming"), Path.Combine(_tempRoot, "local"));
        Directory.CreateDirectory(paths.GzCompanionModsDir);
        Directory.CreateDirectory(paths.GzCompanionConfigDir);

        var downloader = new FakeDownloader();
        downloader.FileResponses[FabricApiUrl] = NewFabricApiBytes;

        byte[] jarBytes = embeddedJarBytes ?? NewCompanionJarBytes;
        var target = new SupportedEntry(
            SupportStatus.Verified, "0.1.0-alpha.3", "26.1.2", "0.19.5", "0.155.3+26.1.2",
            new CompanionJarSpec("gzcompanion-0.1.0-alpha.3.jar", HashOf(NewCompanionJarBytes), NewCompanionJarBytes.Length, "embedded", null),
            new FabricLoaderSpec("fabric-loader-0.19.5-26.1.2", "https://example.invalid/profile.json", null, null),
            new FabricApiSpec("fabric-api-0.155.3+26.1.2.jar", FabricApiUrl, "modrinth", HashOf(NewFabricApiBytes), null, NewFabricApiBytes.Length, null));

        var deps = new InstallEngineDependencies
        {
            Paths = paths,
            Downloader = downloader,
            LoadEmbeddedCompanionJar = () => jarBytes,
            Clock = () => DateTimeOffset.Parse("2026-09-12T12:00:00Z"),
            // Deliberately throws if ever called - the fast path must NEVER check this.
            IsLauncherRunning = () => throw new InvalidOperationException("Fast path must never check IsLauncherRunning."),
        };
        return (paths, target, downloader, new InstallEngine(deps));
    }

    [Fact]
    public async Task InstallsNewCompanionJarAndRemovesOnlyTheOldOne()
    {
        var (paths, target, _, engine) = Build();
        string oldJarPath = Path.Combine(paths.GzCompanionModsDir, "gzcompanion-0.1.0-alpha.2.jar");
        File.WriteAllBytes(oldJarPath, new byte[] { 1, 1, 1 });

        var outcome = await engine.RunFastUpdateAsync(target, dryRun: false, log: null, CancellationToken.None);

        Assert.True(outcome.Success, outcome.ErrorMessage);
        Assert.False(File.Exists(oldJarPath), "the old version's jar must be removed after a successful update");
        string newJarPath = Path.Combine(paths.GzCompanionModsDir, "gzcompanion-0.1.0-alpha.3.jar");
        Assert.True(File.Exists(newJarPath));
        Assert.Equal(NewCompanionJarBytes, File.ReadAllBytes(newJarPath));

        var jars = Directory.GetFiles(paths.GzCompanionModsDir, "gzcompanion-*.jar");
        Assert.Single(jars); // never two companion jars at once
    }

    [Fact]
    public async Task FabricApiIsOnlyReDownloadedWhenHashDiffers()
    {
        var (paths, target, downloader, engine) = Build();
        string fabricApiPath = Path.Combine(paths.GzCompanionModsDir, "fabric-api-0.155.3+26.1.2.jar");
        File.WriteAllBytes(fabricApiPath, NewFabricApiBytes); // already correct - matches target hash

        var outcome = await engine.RunFastUpdateAsync(target, dryRun: false, log: null, CancellationToken.None);

        Assert.True(outcome.Success, outcome.ErrorMessage);
        Assert.DoesNotContain(FabricApiUrl, downloader.RequestedUrls); // never re-downloaded - already good
    }

    [Fact]
    public async Task FabricApiIsReDownloadedWhenHashDoesNotMatch()
    {
        var (paths, target, downloader, engine) = Build();
        string fabricApiPath = Path.Combine(paths.GzCompanionModsDir, "fabric-api-0.155.3+26.1.2.jar");
        File.WriteAllBytes(fabricApiPath, new byte[] { 0, 0, 0 }); // stale/wrong content

        var outcome = await engine.RunFastUpdateAsync(target, dryRun: false, log: null, CancellationToken.None);

        Assert.True(outcome.Success, outcome.ErrorMessage);
        Assert.Contains(FabricApiUrl, downloader.RequestedUrls);
        Assert.Equal(NewFabricApiBytes, File.ReadAllBytes(fabricApiPath));
    }

    [Fact]
    public async Task LauncherProfilesFileIsByteForByteUntouched()
    {
        var (paths, target, _, engine) = Build();
        Directory.CreateDirectory(paths.DotMinecraftDir);
        const string profilesContent = """{"profiles":{"gzcompanion-gameZone":{"name":"GZ Companion"}}}""";
        File.WriteAllText(paths.Win32LauncherProfilesPath, profilesContent);

        var outcome = await engine.RunFastUpdateAsync(target, dryRun: false, log: null, CancellationToken.None);

        Assert.True(outcome.Success, outcome.ErrorMessage);
        Assert.Equal(profilesContent, File.ReadAllText(paths.Win32LauncherProfilesPath));
    }

    [Fact]
    public async Task DoesNotCheckWhetherTheLauncherIsRunning()
    {
        // Build()'s IsLauncherRunning throws if ever invoked - a successful outcome here proves
        // the fast path never calls it, matching "Launcher may stay open" for this common case.
        var (_, target, _, engine) = Build();
        var outcome = await engine.RunFastUpdateAsync(target, dryRun: false, log: null, CancellationToken.None);
        Assert.True(outcome.Success, outcome.ErrorMessage);
    }

    [Fact]
    public async Task ConfigDirectoryIsPreserved()
    {
        var (paths, target, _, engine) = Build();
        string userFile = Path.Combine(paths.GzCompanionConfigDir, "settings.json");
        File.WriteAllText(userFile, """{"companionNotificationsEnabled":true}""");

        var outcome = await engine.RunFastUpdateAsync(target, dryRun: false, log: null, CancellationToken.None);

        Assert.True(outcome.Success, outcome.ErrorMessage);
        Assert.True(File.Exists(userFile));
        Assert.Equal("""{"companionNotificationsEnabled":true}""", File.ReadAllText(userFile));
    }

    [Fact]
    public async Task SavesAndServersDatArePreserved()
    {
        var (paths, target, _, engine) = Build();
        string savesDir = Path.Combine(paths.GzCompanionGameDir, "saves", "MyWorld");
        Directory.CreateDirectory(savesDir);
        File.WriteAllText(Path.Combine(savesDir, "level.dat"), "world-data");
        File.WriteAllBytes(paths.GzCompanionServersDatPath, new byte[] { 9, 9, 9 });

        var outcome = await engine.RunFastUpdateAsync(target, dryRun: false, log: null, CancellationToken.None);

        Assert.True(outcome.Success, outcome.ErrorMessage);
        Assert.True(File.Exists(Path.Combine(savesDir, "level.dat")));
        Assert.Equal(new byte[] { 9, 9, 9 }, File.ReadAllBytes(paths.GzCompanionServersDatPath));
    }

    [Fact]
    public async Task InstalledStateIsUpdatedToTheNewVersion()
    {
        var (paths, target, _, engine) = Build();
        InstalledStateStore.Write(paths.InstalledManifestPath, new InstalledState("0.1.0-alpha.2", "26.1.2", "0.19.5", "0.155.3+26.1.2", "2026-09-01T00:00:00Z"));

        var outcome = await engine.RunFastUpdateAsync(target, dryRun: false, log: null, CancellationToken.None);

        Assert.True(outcome.Success, outcome.ErrorMessage);
        var state = InstalledStateStore.TryRead(paths.InstalledManifestPath);
        Assert.NotNull(state);
        Assert.Equal("0.1.0-alpha.3", state!.CompanionVersion);
    }

    [Fact]
    public async Task BadEmbeddedJarHash_RollsBackAndKeepsOldJarWorking()
    {
        var (paths, target, _, engine) = Build(embeddedJarBytes: new byte[] { 0xBA, 0xD0 }); // does not match target's declared hash
        string oldJarPath = Path.Combine(paths.GzCompanionModsDir, "gzcompanion-0.1.0-alpha.2.jar");
        File.WriteAllBytes(oldJarPath, new byte[] { 1, 1, 1 });

        var outcome = await engine.RunFastUpdateAsync(target, dryRun: false, log: null, CancellationToken.None);

        Assert.False(outcome.Success);
        Assert.True(File.Exists(oldJarPath), "the old, working jar must survive a failed verification");
        Assert.False(File.Exists(Path.Combine(paths.GzCompanionModsDir, "gzcompanion-0.1.0-alpha.3.jar")));
        Assert.False(File.Exists(Path.Combine(paths.GzCompanionModsDir, "gzcompanion-0.1.0-alpha.3.jar.staging")), "no leftover staging file");

        // Installed-state must never claim the new version installed successfully.
        var state = InstalledStateStore.TryRead(paths.InstalledManifestPath);
        Assert.Null(state);
    }

    [Fact]
    public async Task DryRun_ChangesNothingOnDisk()
    {
        var (paths, target, downloader, engine) = Build();

        var outcome = await engine.RunFastUpdateAsync(target, dryRun: true, log: null, CancellationToken.None);

        Assert.True(outcome.Success, outcome.ErrorMessage);
        Assert.True(outcome.DryRun);
        Assert.Empty(downloader.RequestedUrls);
        Assert.False(File.Exists(Path.Combine(paths.GzCompanionModsDir, "gzcompanion-0.1.0-alpha.3.jar")));
        Assert.False(File.Exists(paths.InstalledManifestPath));
    }
}
