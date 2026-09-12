using System.Linq;
using GZCompanion.Installer.Core;
using Xunit;

namespace GZCompanion.Installer.Tests;

/// <summary>
/// Coordinator-level tests: unlike <see cref="MinecraftExitGuardTests"/> (pure guard logic in
/// isolation), these prove the coordinator actually WIRES the guard in first - that an unsafe
/// guard result stops everything before <see cref="InstallEngine"/> is ever constructed, let alone
/// invoked, with zero downloads and zero files touched.
/// </summary>
public class UpdateApplyCoordinatorTests : IDisposable
{
    private readonly string _tempRoot = Directory.CreateTempSubdirectory("gzc-coordinator-test-").FullName;
    public void Dispose() { try { Directory.Delete(_tempRoot, recursive: true); } catch { } }

    private static readonly CompanionJarSpec DummyJarSpec = new("gzcompanion-0.1.0-alpha.2.jar", new string('0', 64), 1, "embedded", null);
    private static readonly FabricLoaderSpec DummyLoaderSpec = new("fabric-loader-0.19.5-26.1.2", "https://meta.fabricmc.net/v2/versions/loader/26.1.2/0.19.5/profile/json", null, null);
    private static readonly FabricApiSpec DummyApiSpec = new("fabric-api-0.155.3+26.1.2.jar", "https://example.invalid/fabric-api.jar", "test", new string('0', 64), null, 1, null);

    private static readonly SupportedEntry Target = new(
        SupportStatus.Verified, "0.1.0-alpha.2", "26.1.2", "0.19.5", "0.155.3+26.1.2",
        DummyJarSpec, DummyLoaderSpec, DummyApiSpec);

    private static readonly CompatibilityManifest Manifest = new(1, "2026-09-01T00:00:00Z", new List<SupportedEntry> { Target });

    private UpdateApplyRequest BuildRequest(
        Func<int, bool> isPidRunning, Func<bool> isAnyMinecraftGameRunning,
        int pidMaxPolls = 3, int otherProcessMaxPolls = 3, FakeDownloader? downloader = null)
    {
        var paths = new InstallPaths(Path.Combine(_tempRoot, "roaming"), Path.Combine(_tempRoot, "local"));
        return new UpdateApplyRequest(
            Paths: paths,
            WaitPid: 1234,
            FromVersion: "0.1.0-alpha.1",
            IsPidRunning: isPidRunning,
            IsAnyMinecraftGameRunning: isAnyMinecraftGameRunning,
            DelayAsync: _ => Task.CompletedTask,
            PidMaxPolls: pidMaxPolls,
            OtherProcessMaxPolls: otherProcessMaxPolls,
            Downloader: downloader ?? new FakeDownloader(),
            LoadEmbeddedCompanionJar: () => new byte[] { 1, 2, 3 },
            Clock: () => DateTimeOffset.UtcNow,
            IsLauncherRunning: () => false,
            Manifest: Manifest);
    }

    [Fact]
    public async Task TargetPidNeverExits_ReturnsOtherMinecraftRunning_NoDownloadsNoFilesTouched()
    {
        var downloader = new FakeDownloader();
        var request = BuildRequest(isPidRunning: _ => true, isAnyMinecraftGameRunning: () => false, downloader: downloader);

        var outcome = await new UpdateApplyCoordinator().RunAsync(request, progress: null, CancellationToken.None);

        Assert.Equal(UpdateApplyPhase.OtherMinecraftRunning, outcome.Phase);
        Assert.Null(outcome.InstallResult); // proves InstallEngine was never even constructed
        Assert.Empty(downloader.RequestedUrls); // proves nothing was ever downloaded
        Assert.False(Directory.Exists(Path.Combine(_tempRoot, "local"))); // proves nothing was ever written
    }

    [Fact]
    public async Task OtherMinecraftProcessStillRunningAfterGracePeriod_ReturnsOtherMinecraftRunning_NoDownloads()
    {
        var downloader = new FakeDownloader();
        var request = BuildRequest(isPidRunning: _ => false, isAnyMinecraftGameRunning: () => true, downloader: downloader);

        var outcome = await new UpdateApplyCoordinator().RunAsync(request, progress: null, CancellationToken.None);

        Assert.Equal(UpdateApplyPhase.OtherMinecraftRunning, outcome.Phase);
        Assert.Null(outcome.InstallResult);
        Assert.Empty(downloader.RequestedUrls);
    }

    /// <summary>Reports synchronously, unlike <see cref="Progress{T}"/> (which posts via a captured
    /// <see cref="SynchronizationContext"/>, or the thread pool when there is none - both async, and
    /// not guaranteed to have run yet by the time an awaited call returns) - deterministic for tests.</summary>
    private sealed class SyncProgress<T> : IProgress<T>
    {
        public List<T> Reported { get; } = new();
        public void Report(T value) => Reported.Add(value);
    }

    [Fact]
    public async Task ReportsProgressPhasesInOrderUpToTheAbort()
    {
        var progress = new SyncProgress<UpdateApplyProgress>();
        var request = BuildRequest(isPidRunning: _ => true, isAnyMinecraftGameRunning: () => false);

        await new UpdateApplyCoordinator().RunAsync(request, progress, CancellationToken.None);

        Assert.Equal(
            new[] { UpdateApplyPhase.WaitingForMinecraft, UpdateApplyPhase.OtherMinecraftRunning },
            progress.Reported.Select(p => p.Phase));
    }

    [Fact]
    public async Task SafeToMutate_NoPriorInstalledState_FallsBackToFullPathAndInvokesEngine()
    {
        // No installed.json at all -> FastPathDecision.CanUseFastPath is false -> full path -> the
        // launcher-running check gates it, and since IsLauncherRunning() is false here, it must
        // actually reach InstallEngine.RunAsync (not just return early) - this proves the "safe"
        // branch really does wire the engine in, as the mirror image of the abort-path tests above.
        // A minimal launcher_profiles.json is seeded so RunAsync gets past its own precondition
        // check and actually reaches the downloader.
        var downloader = new FakeDownloader();
        var request = BuildRequest(isPidRunning: _ => false, isAnyMinecraftGameRunning: () => false, downloader: downloader);
        Directory.CreateDirectory(request.Paths.DotMinecraftDir);
        File.WriteAllText(request.Paths.Win32LauncherProfilesPath, """{"profiles":{}}""");

        var outcome = await new UpdateApplyCoordinator().RunAsync(request, progress: null, CancellationToken.None);

        Assert.NotNull(outcome.InstallResult); // InstallEngine.RunAsync was actually invoked
        Assert.False(outcome.UsedFastPath);
        Assert.NotEmpty(downloader.RequestedUrls); // it genuinely tried to fetch the loader profile
    }

    // --- Cancellation (Blocker 1B): the user may only cancel before mutation could have started. ---

    [Fact]
    public async Task CancelledBeforePidExits_ReturnsCancelled_NoDownloadsNoEngineInvoked()
    {
        var downloader = new FakeDownloader();
        using var cts = new CancellationTokenSource();
        cts.Cancel(); // simulates the user clicking "Avbryt" while still WaitingForMinecraft
        var request = BuildRequest(isPidRunning: _ => true, isAnyMinecraftGameRunning: () => false, downloader: downloader);

        var outcome = await new UpdateApplyCoordinator().RunAsync(request, progress: null, cts.Token);

        Assert.Equal(UpdateApplyPhase.Cancelled, outcome.Phase);
        Assert.Null(outcome.InstallResult); // InstallEngine never constructed
        Assert.Empty(downloader.RequestedUrls); // zero file mutation
        Assert.False(Directory.Exists(Path.Combine(_tempRoot, "local")));
    }

    [Fact]
    public async Task CancelledBeforePidExits_ReportsWaitingThenCancelledPhases()
    {
        var progress = new SyncProgress<UpdateApplyProgress>();
        using var cts = new CancellationTokenSource();
        cts.Cancel();
        var request = BuildRequest(isPidRunning: _ => true, isAnyMinecraftGameRunning: () => false);

        await new UpdateApplyCoordinator().RunAsync(request, progress, cts.Token);

        Assert.Equal(
            new[] { UpdateApplyPhase.WaitingForMinecraft, UpdateApplyPhase.Cancelled },
            progress.Reported.Select(p => p.Phase));
    }
}
