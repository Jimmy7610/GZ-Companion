using System.Security.Cryptography;
using GZCompanion.Installer.Core;
using Xunit;

namespace GZCompanion.Installer.Tests;

/// <summary>
/// A fake <see cref="IFastUpdateFileOps"/> that delegates to the real filesystem by default, but
/// can be told to throw on the Nth call, or on a call matching a specific path - the seam this
/// project's failure-injection tests need to deterministically prove rollback behavior at each
/// commit stage, rather than relying on real filesystem race conditions.
/// </summary>
internal sealed class FakeFastUpdateFileOps : IFastUpdateFileOps
{
    public int MoveCallCount { get; private set; }
    public int DeleteCallCount { get; private set; }
    public Func<string, string, bool>? ThrowOnMove { get; set; }
    public Func<string, bool>? ThrowOnDelete { get; set; }
    public Func<string, bool>? ThrowOnWriteAllText { get; set; }

    public bool Exists(string path) => File.Exists(path);

    public void WriteAllText(string path, string content)
    {
        if (ThrowOnWriteAllText?.Invoke(path) == true)
        {
            throw new IOException($"Simulated write failure: {path}");
        }
        File.WriteAllText(path, content);
    }

    public void Move(string from, string to)
    {
        MoveCallCount++;
        if (ThrowOnMove?.Invoke(from, to) == true)
        {
            throw new IOException($"Simulated move failure: {from} -> {to}");
        }
        File.Move(from, to, overwrite: true);
    }

    public void Delete(string path)
    {
        DeleteCallCount++;
        if (ThrowOnDelete?.Invoke(path) == true)
        {
            throw new IOException($"Simulated delete failure: {path}");
        }
        File.Delete(path);
    }
}

/// <summary>
/// The SAFE UPDATE FAST PATH (<see cref="InstallEngine.RunFastUpdateAsync"/>) - a genuine
/// transaction. Every important commit stage has a real failure-injection test proving the
/// previous, working installation is restored exactly - never a duplicate active Companion jar,
/// never a false success.
/// </summary>
public class InstallEngineFastUpdateTests : IDisposable
{
    private readonly string _tempRoot = Directory.CreateTempSubdirectory("gzc-fastupdate-test-").FullName;
    public void Dispose() { try { Directory.Delete(_tempRoot, recursive: true); } catch { } }

    private static string HashOf(byte[] bytes) => Convert.ToHexStringLower(SHA256.HashData(bytes));

    private const string FabricApiUrl = "https://example.invalid/fabric-api-new.jar";
    private const string OldCompanionVersion = "0.1.0-alpha.2";
    private static readonly byte[] OldCompanionJarBytes = { 1, 1, 1 };
    private static readonly byte[] OldFabricApiBytes = { 5, 5, 5 };
    private static readonly byte[] NewFabricApiBytes = { 7, 7, 7, 7 };
    private static readonly byte[] NewCompanionJarBytes = { 4, 2, 4, 2, 4, 2 };

    private InstalledState PreviouslyInstalled => new(OldCompanionVersion, "26.1.2", "0.19.5", "0.155.3+26.1.2", "2026-09-01T00:00:00Z");

    private (InstallPaths paths, SupportedEntry target, FakeDownloader downloader, FakeFastUpdateFileOps fileOps, InstallEngine engine) Build(
        byte[]? embeddedJarBytes = null, bool seedOldJar = true, bool seedOldFabricApi = true)
    {
        var paths = new InstallPaths(Path.Combine(_tempRoot, "roaming"), Path.Combine(_tempRoot, "local"));
        Directory.CreateDirectory(paths.GzCompanionModsDir);
        Directory.CreateDirectory(paths.GzCompanionConfigDir);

        if (seedOldJar)
        {
            File.WriteAllBytes(Path.Combine(paths.GzCompanionModsDir, $"gzcompanion-{OldCompanionVersion}.jar"), OldCompanionJarBytes);
        }
        if (seedOldFabricApi)
        {
            File.WriteAllBytes(Path.Combine(paths.GzCompanionModsDir, "fabric-api-0.155.3+26.1.2.jar"), OldFabricApiBytes);
        }

        var downloader = new FakeDownloader();
        downloader.FileResponses[FabricApiUrl] = NewFabricApiBytes;

        byte[] jarBytes = embeddedJarBytes ?? NewCompanionJarBytes;
        var target = new SupportedEntry(
            SupportStatus.Verified, "0.1.0-alpha.3", "26.1.2", "0.19.5", "0.155.3+26.1.2",
            new CompanionJarSpec("gzcompanion-0.1.0-alpha.3.jar", HashOf(NewCompanionJarBytes), NewCompanionJarBytes.Length, "embedded", null),
            new FabricLoaderSpec("fabric-loader-0.19.5-26.1.2", "https://example.invalid/profile.json", null, null),
            new FabricApiSpec("fabric-api-0.155.3+26.1.2.jar", FabricApiUrl, "modrinth", HashOf(NewFabricApiBytes), null, NewFabricApiBytes.Length, null));

        var fileOps = new FakeFastUpdateFileOps();
        var deps = new InstallEngineDependencies
        {
            Paths = paths,
            Downloader = downloader,
            LoadEmbeddedCompanionJar = () => jarBytes,
            Clock = () => DateTimeOffset.Parse("2026-09-12T12:00:00Z"),
            IsLauncherRunning = () => throw new InvalidOperationException("Fast path must never check IsLauncherRunning."),
            FastUpdateFileOps = fileOps,
        };
        return (paths, target, downloader, fileOps, new InstallEngine(deps));
    }

    private static string OldJarPath(InstallPaths paths) => Path.Combine(paths.GzCompanionModsDir, $"gzcompanion-{OldCompanionVersion}.jar");
    private static string NewJarPath(InstallPaths paths, SupportedEntry target) => Path.Combine(paths.GzCompanionModsDir, target.CompanionJar.FileName);
    private static string FabricApiPath(InstallPaths paths, SupportedEntry target) => Path.Combine(paths.GzCompanionModsDir, target.FabricApi.FileName);

    // ------------------------------------------------------------------
    // 1. Staged Companion hash failure -> previous installation unchanged
    // ------------------------------------------------------------------
    [Fact]
    public async Task StagedCompanionHashFailure_PreviousInstallationUnchanged()
    {
        var (paths, target, _, fileOps, engine) = Build(embeddedJarBytes: new byte[] { 0xBA, 0xD0 }); // wrong hash

        var outcome = await engine.RunFastUpdateAsync(target, PreviouslyInstalled, dryRun: false, log: null, CancellationToken.None);

        Assert.False(outcome.Success);
        Assert.True(File.Exists(OldJarPath(paths)), "old jar must survive - hash verification happens before any move");
        Assert.Equal(OldCompanionJarBytes, File.ReadAllBytes(OldJarPath(paths)));
        Assert.False(File.Exists(NewJarPath(paths, target)));
        Assert.Equal(0, fileOps.MoveCallCount); // failed before any commit-phase move even started
        AssertExactlyOneCompanionJar(paths, OldJarPath(paths));
    }

    // ------------------------------------------------------------------
    // 2. Staged Fabric API failure -> previous installation unchanged
    // ------------------------------------------------------------------
    [Fact]
    public async Task StagedFabricApiFailure_PreviousInstallationUnchanged()
    {
        var (paths, target, downloader, fileOps, engine) = Build();
        downloader.FileResponses[FabricApiUrl] = new byte[] { 0, 0 }; // will fail hash verification

        var outcome = await engine.RunFastUpdateAsync(target, PreviouslyInstalled, dryRun: false, log: null, CancellationToken.None);

        Assert.False(outcome.Success);
        Assert.True(File.Exists(OldJarPath(paths)));
        Assert.Equal(OldFabricApiBytes, File.ReadAllBytes(FabricApiPath(paths, target)));
        Assert.False(File.Exists(NewJarPath(paths, target)));
        Assert.Equal(0, fileOps.MoveCallCount);
    }

    // ------------------------------------------------------------------
    // 3. Failure moving new Companion into place -> previous jar restored/unchanged
    // ------------------------------------------------------------------
    [Fact]
    public async Task FailureMovingNewCompanionIntoPlace_PreviousJarRestored()
    {
        var (paths, target, _, fileOps, engine) = Build();
        string newJarPath = NewJarPath(paths, target);
        fileOps.ThrowOnMove = (from, to) => to == newJarPath; // fail exactly the "staging -> final" move

        var outcome = await engine.RunFastUpdateAsync(target, PreviouslyInstalled, dryRun: false, log: null, CancellationToken.None);

        Assert.False(outcome.Success);
        Assert.True(File.Exists(OldJarPath(paths)), "the old jar (renamed to .update-backup) must be restored");
        Assert.Equal(OldCompanionJarBytes, File.ReadAllBytes(OldJarPath(paths)));
        Assert.False(File.Exists(newJarPath));
        AssertExactlyOneCompanionJar(paths, OldJarPath(paths));
    }

    // ------------------------------------------------------------------
    // 4. Failure AFTER new Companion has been placed but before cleanup -> previous installation restored
    // ------------------------------------------------------------------
    [Fact]
    public async Task FailureAfterNewCompanionPlacedButBeforeCleanup_PreviousInstallationRestored()
    {
        var (paths, target, _, fileOps, engine) = Build();
        string fabricApiFinalPath = FabricApiPath(paths, target);
        // The new Companion jar move succeeds; fail the SUBSEQUENT Fabric API "staging -> final"
        // move exactly once (a realistic transient failure, e.g. a split-second antivirus lock) -
        // the rollback's own later move of the OLD Fabric API back to this same final path must
        // still be able to succeed, exactly like a real transient failure would clear up on retry.
        int fabricApiFinalMoveAttempts = 0;
        fileOps.ThrowOnMove = (from, to) =>
        {
            if (to != fabricApiFinalPath) return false;
            fabricApiFinalMoveAttempts++;
            return fabricApiFinalMoveAttempts == 1;
        };

        var outcome = await engine.RunFastUpdateAsync(target, PreviouslyInstalled, dryRun: false, log: null, CancellationToken.None);

        Assert.False(outcome.Success);
        Assert.True(File.Exists(OldJarPath(paths)), "the old Companion jar must be restored even though it was already successfully swapped");
        Assert.False(File.Exists(NewJarPath(paths, target)), "the new Companion jar must be rolled back too - all or nothing");
        Assert.Equal(OldFabricApiBytes, File.ReadAllBytes(fabricApiFinalPath));
        AssertExactlyOneCompanionJar(paths, OldJarPath(paths));
    }

    // ------------------------------------------------------------------
    // 5. Stale-jar cleanup failure -> no false success and no duplicate active Companion jar
    // ------------------------------------------------------------------
    [Fact]
    public async Task StaleJarCleanupFailure_NoFalseSuccessAndNoDuplicateJar()
    {
        var (paths, target, _, fileOps, engine) = Build();
        // An unrelated pre-existing stray jar that the cleanup pass will try (and fail) to delete.
        string strayJarPath = Path.Combine(paths.GzCompanionModsDir, "gzcompanion-0.1.0-alpha.1.jar");
        File.WriteAllBytes(strayJarPath, new byte[] { 9 });
        fileOps.ThrowOnDelete = path => path == strayJarPath;

        var outcome = await engine.RunFastUpdateAsync(target, PreviouslyInstalled, dryRun: false, log: null, CancellationToken.None);

        Assert.False(outcome.Success, "a cleanup failure must never be reported as success");
        // The transaction rolls back to exactly the previous working state - the old owned jar is restored.
        Assert.True(File.Exists(OldJarPath(paths)));
        Assert.False(File.Exists(NewJarPath(paths, target)), "the new jar must not remain active after a failed transaction");
    }

    // ------------------------------------------------------------------
    // 6. Installed-state write failure -> old jar + old Fabric API + old installed-state restored
    // ------------------------------------------------------------------
    [Fact]
    public async Task InstalledStateWriteFailure_EverythingRestored()
    {
        var (paths, target, _, fileOps, engine) = Build();
        InstalledStateStore.Write(paths.InstalledManifestPath, PreviouslyInstalled);
        string originalJson = File.ReadAllText(paths.InstalledManifestPath);

        // The jar and Fabric API swaps both succeed - only the FINAL installed-state write fails.
        fileOps.ThrowOnWriteAllText = path => path == paths.InstalledManifestPath;

        var outcome = await engine.RunFastUpdateAsync(target, PreviouslyInstalled, dryRun: false, log: null, CancellationToken.None);

        Assert.False(outcome.Success);
        Assert.Equal(originalJson, File.ReadAllText(paths.InstalledManifestPath));
        Assert.True(File.Exists(OldJarPath(paths)), "the old jar must be restored even though it was already successfully swapped");
        Assert.False(File.Exists(NewJarPath(paths, target)), "the new jar must be rolled back too");
        Assert.Equal(OldFabricApiBytes, File.ReadAllBytes(FabricApiPath(paths, target)));
        AssertExactlyOneCompanionJar(paths, OldJarPath(paths));
    }

    // ------------------------------------------------------------------
    // 7. Success -> exactly one current Companion jar
    // ------------------------------------------------------------------
    [Fact]
    public async Task Success_ExactlyOneCurrentCompanionJar()
    {
        var (paths, target, _, _, engine) = Build();

        var outcome = await engine.RunFastUpdateAsync(target, PreviouslyInstalled, dryRun: false, log: null, CancellationToken.None);

        Assert.True(outcome.Success, outcome.ErrorMessage);
        AssertExactlyOneCompanionJar(paths, NewJarPath(paths, target));
        Assert.False(File.Exists(OldJarPath(paths) + ".update-backup"), "backups must be cleaned up after a confirmed success");
        var state = InstalledStateStore.TryRead(paths.InstalledManifestPath);
        Assert.NotNull(state);
        Assert.Equal("0.1.0-alpha.3", state!.CompanionVersion);
    }

    // ------------------------------------------------------------------
    // 8-10. User data preservation
    // ------------------------------------------------------------------
    [Fact]
    public async Task ConfigDirectoryIsPreserved()
    {
        var (paths, target, _, _, engine) = Build();
        string userFile = Path.Combine(paths.GzCompanionConfigDir, "settings.json");
        File.WriteAllText(userFile, """{"companionNotificationsEnabled":true}""");

        var outcome = await engine.RunFastUpdateAsync(target, PreviouslyInstalled, dryRun: false, log: null, CancellationToken.None);

        Assert.True(outcome.Success, outcome.ErrorMessage);
        Assert.Equal("""{"companionNotificationsEnabled":true}""", File.ReadAllText(userFile));
    }

    [Fact]
    public async Task SavesArePreserved()
    {
        var (paths, target, _, _, engine) = Build();
        string savesDir = Path.Combine(paths.GzCompanionGameDir, "saves", "MyWorld");
        Directory.CreateDirectory(savesDir);
        File.WriteAllText(Path.Combine(savesDir, "level.dat"), "world-data");

        var outcome = await engine.RunFastUpdateAsync(target, PreviouslyInstalled, dryRun: false, log: null, CancellationToken.None);

        Assert.True(outcome.Success, outcome.ErrorMessage);
        Assert.True(File.Exists(Path.Combine(savesDir, "level.dat")));
    }

    [Fact]
    public async Task ServersDatIsPreserved()
    {
        var (paths, target, _, _, engine) = Build();
        File.WriteAllBytes(paths.GzCompanionServersDatPath, new byte[] { 9, 9, 9 });

        var outcome = await engine.RunFastUpdateAsync(target, PreviouslyInstalled, dryRun: false, log: null, CancellationToken.None);

        Assert.True(outcome.Success, outcome.ErrorMessage);
        Assert.Equal(new byte[] { 9, 9, 9 }, File.ReadAllBytes(paths.GzCompanionServersDatPath));
    }

    // ------------------------------------------------------------------
    // 11. launcher_profiles byte-for-byte untouched on fast path
    // ------------------------------------------------------------------
    [Fact]
    public async Task LauncherProfilesFileIsByteForByteUntouched()
    {
        var (paths, target, _, _, engine) = Build();
        Directory.CreateDirectory(paths.DotMinecraftDir);
        const string profilesContent = """{"profiles":{"gzcompanion-gameZone":{"name":"GZ Companion"}}}""";
        File.WriteAllText(paths.Win32LauncherProfilesPath, profilesContent);

        var outcome = await engine.RunFastUpdateAsync(target, PreviouslyInstalled, dryRun: false, log: null, CancellationToken.None);

        Assert.True(outcome.Success, outcome.ErrorMessage);
        Assert.Equal(profilesContent, File.ReadAllText(paths.Win32LauncherProfilesPath));
    }

    [Fact]
    public async Task FabricApiIsOnlyReDownloadedWhenHashDiffers()
    {
        var (paths, target, downloader, _, engine) = Build();
        File.WriteAllBytes(FabricApiPath(paths, target), NewFabricApiBytes); // already correct

        var outcome = await engine.RunFastUpdateAsync(target, PreviouslyInstalled, dryRun: false, log: null, CancellationToken.None);

        Assert.True(outcome.Success, outcome.ErrorMessage);
        Assert.DoesNotContain(FabricApiUrl, downloader.RequestedUrls);
    }

    [Fact]
    public async Task DoesNotCheckWhetherTheLauncherIsRunning()
    {
        var (_, target, _, _, engine) = Build();
        var outcome = await engine.RunFastUpdateAsync(target, PreviouslyInstalled, dryRun: false, log: null, CancellationToken.None);
        Assert.True(outcome.Success, outcome.ErrorMessage);
    }

    [Fact]
    public async Task DryRun_ChangesNothingOnDisk()
    {
        var (paths, target, downloader, _, engine) = Build();

        var outcome = await engine.RunFastUpdateAsync(target, PreviouslyInstalled, dryRun: true, log: null, CancellationToken.None);

        Assert.True(outcome.Success, outcome.ErrorMessage);
        Assert.True(outcome.DryRun);
        Assert.Empty(downloader.RequestedUrls);
        Assert.False(File.Exists(NewJarPath(paths, target)));
        Assert.True(File.Exists(OldJarPath(paths)));
    }

    private static void AssertExactlyOneCompanionJar(InstallPaths paths, string expectedActiveJarPath)
    {
        var jars = Directory.GetFiles(paths.GzCompanionModsDir, "gzcompanion-*.jar")
            .Where(f => !f.EndsWith(".update-backup", StringComparison.OrdinalIgnoreCase))
            .ToList();
        Assert.Single(jars);
        Assert.Equal(Path.GetFullPath(expectedActiveJarPath), Path.GetFullPath(jars[0]));
    }
}
