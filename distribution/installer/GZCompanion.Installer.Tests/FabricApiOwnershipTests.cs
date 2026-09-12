using System.Security.Cryptography;
using GZCompanion.Installer.Core;
using Xunit;

namespace GZCompanion.Installer.Tests;

/// <summary>
/// Blocker 2 (2026-09-12 follow-up pass): a Fabric API VERSION bump can also change its FILENAME
/// (e.g. <c>fabric-api-0.155.3+26.1.2.jar</c> -&gt; <c>fabric-api-0.156.0+26.1.2.jar</c>), and the
/// fast path used to only ever look at the NEW target filename - it had no way to find, let alone
/// retire, a differently-named old file, so a Fabric API filename change left BOTH jars active
/// (duplicate mod loading / possible startup failure). These tests cover the fix: explicit
/// ownership tracking via <see cref="InstalledState.FabricApiFileName"/> (schema v2), and the
/// conservative "unknown ownership never guesses/deletes" behavior for a legacy schema v1 state.
/// </summary>
public class FabricApiOwnershipTests : IDisposable
{
    private readonly string _tempRoot = Directory.CreateTempSubdirectory("gzc-fabricapi-ownership-test-").FullName;
    public void Dispose() { try { Directory.Delete(_tempRoot, recursive: true); } catch { } }

    private static string HashOf(byte[] bytes) => Convert.ToHexStringLower(SHA256.HashData(bytes));

    private const string CompanionVersion = "0.1.0-alpha.2";
    private const string OldFabricApiFileName = "fabric-api-0.155.3+26.1.2.jar";
    private const string NewFabricApiFileName = "fabric-api-0.156.0+26.1.2.jar";
    private const string NewFabricApiUrl = "https://example.invalid/fabric-api-0.156.0.jar";

    private static readonly byte[] CompanionJarBytes = { 1, 1, 1 };
    private static readonly byte[] OldFabricApiBytes = { 5, 5, 5 };
    private static readonly byte[] NewFabricApiBytes = { 7, 7, 7, 7 };

    /// <param name="fabricApiOwnershipKnown">true = schema v2 previouslyInstalled with
    /// FabricApiFileName recorded; false = legacy schema v1 (field absent/null).</param>
    private (InstallPaths paths, SupportedEntry target, InstalledState previouslyInstalled, FakeDownloader downloader, FakeFastUpdateFileOps fileOps, InstallEngine engine) Build(
        bool fabricApiOwnershipKnown = true, string newFabricApiFileName = NewFabricApiFileName)
    {
        var paths = new InstallPaths(Path.Combine(_tempRoot, "roaming"), Path.Combine(_tempRoot, "local"));
        Directory.CreateDirectory(paths.GzCompanionModsDir);

        File.WriteAllBytes(Path.Combine(paths.GzCompanionModsDir, $"gzcompanion-{CompanionVersion}.jar"), CompanionJarBytes);
        File.WriteAllBytes(Path.Combine(paths.GzCompanionModsDir, OldFabricApiFileName), OldFabricApiBytes);

        var downloader = new FakeDownloader();
        downloader.FileResponses[NewFabricApiUrl] = NewFabricApiBytes;

        var target = new SupportedEntry(
            SupportStatus.Verified, CompanionVersion, "26.1.2", "0.19.5", "0.156.0+26.1.2",
            new CompanionJarSpec($"gzcompanion-{CompanionVersion}.jar", HashOf(CompanionJarBytes), CompanionJarBytes.Length, "embedded", null),
            new FabricLoaderSpec("fabric-loader-0.19.5-26.1.2", "https://example.invalid/profile.json", null, null),
            new FabricApiSpec(newFabricApiFileName, NewFabricApiUrl, "modrinth", HashOf(NewFabricApiBytes), null, NewFabricApiBytes.Length, null));

        var previouslyInstalled = new InstalledState(
            CompanionVersion, "26.1.2", "0.19.5", "0.155.3+26.1.2", "2026-09-01T00:00:00Z",
            SchemaVersion: fabricApiOwnershipKnown ? 2 : 1,
            CompanionJarFileName: $"gzcompanion-{CompanionVersion}.jar",
            FabricApiFileName: fabricApiOwnershipKnown ? OldFabricApiFileName : null);

        var fileOps = new FakeFastUpdateFileOps();
        var deps = new InstallEngineDependencies
        {
            Paths = paths,
            Downloader = downloader,
            LoadEmbeddedCompanionJar = () => CompanionJarBytes,
            Clock = () => DateTimeOffset.Parse("2026-09-12T12:00:00Z"),
            IsLauncherRunning = () => throw new InvalidOperationException("Fast path must never check IsLauncherRunning."),
            FastUpdateFileOps = fileOps,
        };
        return (paths, target, previouslyInstalled, downloader, fileOps, new InstallEngine(deps));
    }

    private static string OldApiPath(InstallPaths paths) => Path.Combine(paths.GzCompanionModsDir, OldFabricApiFileName);
    private static string NewApiPath(InstallPaths paths, string fileName) => Path.Combine(paths.GzCompanionModsDir, fileName);

    private static void AssertExactlyOneFabricApiJar(InstallPaths paths, string expectedActivePath)
    {
        var jars = Directory.GetFiles(paths.GzCompanionModsDir, "fabric-api-*.jar")
            .Where(f => !f.EndsWith(".update-backup", StringComparison.OrdinalIgnoreCase))
            .ToList();
        Assert.Single(jars);
        Assert.Equal(Path.GetFullPath(expectedActivePath), Path.GetFullPath(jars[0]));
    }

    // 7. Fabric API DIFFERENT filename, ownership known -> old removed only after successful new
    //    install -> exactly one owned API jar.
    [Fact]
    public async Task DifferentFilename_KnownOwnership_OldRetiredNewPlaced_ExactlyOneApiJar()
    {
        var (paths, target, previouslyInstalled, _, _, engine) = Build(fabricApiOwnershipKnown: true);

        var outcome = await engine.RunFastUpdateAsync(target, previouslyInstalled, dryRun: false, log: null, CancellationToken.None);

        Assert.True(outcome.Success, outcome.ErrorMessage);
        Assert.False(File.Exists(OldApiPath(paths)), "the old-named Fabric API jar must be gone after a successful filename change");
        Assert.True(File.Exists(NewApiPath(paths, NewFabricApiFileName)));
        Assert.Equal(NewFabricApiBytes, File.ReadAllBytes(NewApiPath(paths, NewFabricApiFileName)));
        AssertExactlyOneFabricApiJar(paths, NewApiPath(paths, NewFabricApiFileName));
        Assert.False(File.Exists(OldApiPath(paths) + ".update-backup"), "the backup must be discarded after a confirmed success");
    }

    // 8. changed API + new-download hash failure -> old API untouched
    [Fact]
    public async Task DifferentFilename_DownloadHashFailure_OldApiUntouched()
    {
        var (paths, target, previouslyInstalled, downloader, _, engine) = Build(fabricApiOwnershipKnown: true);
        downloader.FileResponses[NewFabricApiUrl] = new byte[] { 0, 0 }; // wrong hash

        var outcome = await engine.RunFastUpdateAsync(target, previouslyInstalled, dryRun: false, log: null, CancellationToken.None);

        Assert.False(outcome.Success);
        Assert.True(File.Exists(OldApiPath(paths)), "old API must survive - hash verification happens before any move");
        Assert.Equal(OldFabricApiBytes, File.ReadAllBytes(OldApiPath(paths)));
        Assert.False(File.Exists(NewApiPath(paths, NewFabricApiFileName)));
    }

    // 9. changed API + swap failure -> old API restored
    [Fact]
    public async Task DifferentFilename_SwapFailure_OldApiRestored()
    {
        var (paths, target, previouslyInstalled, _, fileOps, engine) = Build(fabricApiOwnershipKnown: true);
        string oldApiPath = OldApiPath(paths);
        fileOps.ThrowOnMove = (from, to) => from == oldApiPath; // fail the retire-old-API move specifically

        var outcome = await engine.RunFastUpdateAsync(target, previouslyInstalled, dryRun: false, log: null, CancellationToken.None);

        Assert.False(outcome.Success);
        Assert.True(File.Exists(oldApiPath), "the old-named API jar must be restored (or never moved) on failure");
        Assert.Equal(OldFabricApiBytes, File.ReadAllBytes(oldApiPath));
        Assert.False(File.Exists(NewApiPath(paths, NewFabricApiFileName)), "the new API jar must not remain active after a failed transaction");
    }

    // 10. changed API + installed-state write failure -> old API restored
    [Fact]
    public async Task DifferentFilename_InstalledStateWriteFailure_OldApiRestored()
    {
        var (paths, target, previouslyInstalled, _, fileOps, engine) = Build(fabricApiOwnershipKnown: true);
        InstalledStateStore.Write(paths.InstalledManifestPath, previouslyInstalled);
        string originalJson = File.ReadAllText(paths.InstalledManifestPath);
        fileOps.ThrowOnWriteAllText = path => path == paths.InstalledManifestPath;

        var outcome = await engine.RunFastUpdateAsync(target, previouslyInstalled, dryRun: false, log: null, CancellationToken.None);

        Assert.False(outcome.Success);
        Assert.Equal(originalJson, File.ReadAllText(paths.InstalledManifestPath));
        Assert.True(File.Exists(OldApiPath(paths)), "the old-named API jar must be restored even though it was already successfully retired");
        Assert.Equal(OldFabricApiBytes, File.ReadAllBytes(OldApiPath(paths)));
        Assert.False(File.Exists(NewApiPath(paths, NewFabricApiFileName)), "the new API jar must be rolled back too");
    }

    // 11. successful changed API -> new state contains new API filename
    [Fact]
    public async Task DifferentFilename_Success_NewStateRecordsNewFileName()
    {
        var (paths, target, previouslyInstalled, _, _, engine) = Build(fabricApiOwnershipKnown: true);

        var outcome = await engine.RunFastUpdateAsync(target, previouslyInstalled, dryRun: false, log: null, CancellationToken.None);

        Assert.True(outcome.Success, outcome.ErrorMessage);
        var state = InstalledStateStore.TryRead(paths.InstalledManifestPath);
        Assert.NotNull(state);
        Assert.Equal(NewFabricApiFileName, state!.FabricApiFileName);
        Assert.Equal(2, state.SchemaVersion);
    }

    // Unknown ownership (legacy schema v1): the differently-named OLD file must be left alone
    // entirely - never guessed at, never deleted - while the new file still installs correctly.
    [Fact]
    public async Task DifferentFilename_UnknownOwnership_OldFileLeftAlone_NewFilePlaced()
    {
        var (paths, target, previouslyInstalled, _, _, engine) = Build(fabricApiOwnershipKnown: false);

        var outcome = await engine.RunFastUpdateAsync(target, previouslyInstalled, dryRun: false, log: null, CancellationToken.None);

        Assert.True(outcome.Success, outcome.ErrorMessage);
        Assert.True(File.Exists(OldApiPath(paths)), "an old Fabric API jar with unknown ownership must never be guessed at or deleted");
        Assert.Equal(OldFabricApiBytes, File.ReadAllBytes(OldApiPath(paths)));
        Assert.True(File.Exists(NewApiPath(paths, NewFabricApiFileName)));
        Assert.Equal(NewFabricApiBytes, File.ReadAllBytes(NewApiPath(paths, NewFabricApiFileName)));
    }

    // Same filename, same hash -> nothing re-downloaded, exactly one jar (baseline, unaffected by ownership logic).
    [Fact]
    public async Task SameFileNameSameHash_NoRedownload_ExactlyOneJar()
    {
        var (paths, target, previouslyInstalled, downloader, _, engine) = Build(fabricApiOwnershipKnown: true, newFabricApiFileName: OldFabricApiFileName);
        // Same filename as what's already installed, and Build() seeded it with OldFabricApiBytes -
        // but target's expected hash is NewFabricApiBytes's hash, so give the ALREADY-PRESENT file
        // that exact content instead, to genuinely test the "already correct, skip download" path.
        File.WriteAllBytes(OldApiPath(paths), NewFabricApiBytes);

        var outcome = await engine.RunFastUpdateAsync(target, previouslyInstalled, dryRun: false, log: null, CancellationToken.None);

        Assert.True(outcome.Success, outcome.ErrorMessage);
        Assert.DoesNotContain(NewFabricApiUrl, downloader.RequestedUrls);
        AssertExactlyOneFabricApiJar(paths, OldApiPath(paths));
    }

    // Same filename, DIFFERENT hash -> transactional in-place replacement (pre-existing behavior,
    // re-asserted here alongside the new ownership-aware tests for a complete picture).
    [Fact]
    public async Task SameFileNameDifferentHash_TransactionalReplace_ExactlyOneJar()
    {
        var (paths, target, previouslyInstalled, _, _, engine) = Build(fabricApiOwnershipKnown: true, newFabricApiFileName: OldFabricApiFileName);

        var outcome = await engine.RunFastUpdateAsync(target, previouslyInstalled, dryRun: false, log: null, CancellationToken.None);

        Assert.True(outcome.Success, outcome.ErrorMessage);
        Assert.Equal(NewFabricApiBytes, File.ReadAllBytes(OldApiPath(paths)));
        AssertExactlyOneFabricApiJar(paths, OldApiPath(paths));
        Assert.False(File.Exists(OldApiPath(paths) + ".update-backup"));
    }

    // ------------------------------------------------------------------------------------------
    // Second follow-up pass (2026-09-12): "changed filename + target B already exists with the
    // correct hash" - retirement of the old, explicitly-owned A.jar must be independent of whether
    // B needed downloading at all. The original fix gated retirement on fabricApiNeedsDownload,
    // which stayed false whenever B already had the correct hash - silently leaving BOTH A and B
    // active. These tests cover the corrected, independent condition.
    // ------------------------------------------------------------------------------------------

    // 1. Different filename + target B absent (the original scenario) must still work exactly as
    //    before - see DifferentFilename_KnownOwnership_OldRetiredNewPlaced_ExactlyOneApiJar above
    //    for the full assertion; this just re-confirms no download was skipped incorrectly.
    [Fact]
    public async Task DifferentFilename_TargetAbsent_StillDownloadsAndRetiresOld()
    {
        var (paths, target, previouslyInstalled, downloader, _, engine) = Build(fabricApiOwnershipKnown: true);

        var outcome = await engine.RunFastUpdateAsync(target, previouslyInstalled, dryRun: false, log: null, CancellationToken.None);

        Assert.True(outcome.Success, outcome.ErrorMessage);
        Assert.Contains(NewFabricApiUrl, downloader.RequestedUrls);
        Assert.False(File.Exists(OldApiPath(paths)));
    }

    // 2. Different filename + target B ALREADY EXISTS with the correct hash -> no download, old A
    //    retired anyway, exactly one active owned API jar (B), state points to B.
    [Fact]
    public async Task DifferentFilename_TargetAlreadyGood_NoDownload_OldRetiredAnyway()
    {
        var (paths, target, previouslyInstalled, downloader, _, engine) = Build(fabricApiOwnershipKnown: true);
        // B already present with the exact bytes the target expects - simulates a leftover from a
        // previous partial run, or a file incidentally shared with another mod.
        File.WriteAllBytes(NewApiPath(paths, NewFabricApiFileName), NewFabricApiBytes);

        var outcome = await engine.RunFastUpdateAsync(target, previouslyInstalled, dryRun: false, log: null, CancellationToken.None);

        Assert.True(outcome.Success, outcome.ErrorMessage);
        Assert.DoesNotContain(NewFabricApiUrl, downloader.RequestedUrls); // B was already correct - never re-downloaded
        Assert.False(File.Exists(OldApiPath(paths)), "the old-named A.jar must be retired even though B needed no download");
        Assert.True(File.Exists(NewApiPath(paths, NewFabricApiFileName)));
        Assert.Equal(NewFabricApiBytes, File.ReadAllBytes(NewApiPath(paths, NewFabricApiFileName)));
        AssertExactlyOneFabricApiJar(paths, NewApiPath(paths, NewFabricApiFileName));
        Assert.False(File.Exists(OldApiPath(paths) + ".update-backup"), "the backup must be discarded after a confirmed success");
        var state = InstalledStateStore.TryRead(paths.InstalledManifestPath);
        Assert.Equal(NewFabricApiFileName, state!.FabricApiFileName);
    }

    // 3. Same scenario + installed-state write failure -> old A restored, the PRE-EXISTING B is
    //    left exactly as it was (this transaction never touched it, since it needed no download or
    //    move at all), previous state restored.
    [Fact]
    public async Task DifferentFilename_TargetAlreadyGood_InstalledStateWriteFailure_OldRestored_PreexistingBUntouched()
    {
        var (paths, target, previouslyInstalled, _, fileOps, engine) = Build(fabricApiOwnershipKnown: true);
        string newApiPath = NewApiPath(paths, NewFabricApiFileName);
        File.WriteAllBytes(newApiPath, NewFabricApiBytes);
        InstalledStateStore.Write(paths.InstalledManifestPath, previouslyInstalled);
        string originalJson = File.ReadAllText(paths.InstalledManifestPath);
        fileOps.ThrowOnWriteAllText = path => path == paths.InstalledManifestPath;
        DateTime bBefore = File.GetLastWriteTimeUtc(newApiPath);

        var outcome = await engine.RunFastUpdateAsync(target, previouslyInstalled, dryRun: false, log: null, CancellationToken.None);

        Assert.False(outcome.Success);
        Assert.Equal(originalJson, File.ReadAllText(paths.InstalledManifestPath));
        Assert.True(File.Exists(OldApiPath(paths)), "the old-named A.jar must be restored");
        Assert.Equal(OldFabricApiBytes, File.ReadAllBytes(OldApiPath(paths)));
        Assert.True(File.Exists(newApiPath), "the pre-existing B.jar must not be deleted by a rollback this transaction didn't create it in");
        Assert.Equal(NewFabricApiBytes, File.ReadAllBytes(newApiPath));
        Assert.Equal(bBefore, File.GetLastWriteTimeUtc(newApiPath));
    }

    // 4. Unknown old ownership + B already good -> do not guess/delete unknown A, exactly like the
    //    "needs download" case, now also proven for the "already good" case.
    [Fact]
    public async Task DifferentFilename_UnknownOwnership_TargetAlreadyGood_OldFileLeftAlone()
    {
        var (paths, target, previouslyInstalled, downloader, _, engine) = Build(fabricApiOwnershipKnown: false);
        File.WriteAllBytes(NewApiPath(paths, NewFabricApiFileName), NewFabricApiBytes);

        var outcome = await engine.RunFastUpdateAsync(target, previouslyInstalled, dryRun: false, log: null, CancellationToken.None);

        Assert.True(outcome.Success, outcome.ErrorMessage);
        Assert.DoesNotContain(NewFabricApiUrl, downloader.RequestedUrls);
        Assert.True(File.Exists(OldApiPath(paths)), "an old Fabric API jar with unknown ownership must never be guessed at or deleted");
        Assert.Equal(OldFabricApiBytes, File.ReadAllBytes(OldApiPath(paths)));
        Assert.True(File.Exists(NewApiPath(paths, NewFabricApiFileName)));
    }
}
