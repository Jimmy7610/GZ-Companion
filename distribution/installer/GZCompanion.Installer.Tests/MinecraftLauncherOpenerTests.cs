using GZCompanion.Installer.Core;
using Xunit;

namespace GZCompanion.Installer.Tests;

internal sealed class FakeLauncherDiscovery : IInstalledLauncherDiscovery
{
    public List<LauncherCandidate> Candidates { get; } = new();
    public Exception? ThrowOnDiscover { get; set; }

    public IReadOnlyList<LauncherCandidate> DiscoverCandidates()
    {
        if (ThrowOnDiscover is not null) throw ThrowOnDiscover;
        return Candidates;
    }
}

internal sealed class SpyLauncherActivator : ILauncherActivator
{
    public List<LauncherCandidate> LaunchedCandidates { get; } = new();
    public Exception? ThrowOnLaunch { get; set; }

    public void Launch(LauncherCandidate candidate)
    {
        if (ThrowOnLaunch is not null) throw ThrowOnLaunch;
        LaunchedCandidates.Add(candidate);
    }
}

/// <summary>
/// Pure MinecraftLauncherResolver.Resolve() tests - no discovery, no launching, just the selection
/// logic given a fixed set of candidates.
/// </summary>
public class MinecraftLauncherResolverTests
{
    private static LauncherCandidate Win32Launcher(string path = @"C:\Users\test\AppData\Local\Programs\Minecraft Launcher\MinecraftLauncher.exe")
        => new(LauncherCandidateKind.Win32, "Minecraft Launcher", path);

    private static LauncherCandidate PackagedLauncher(string aumid = "Microsoft.4297127D64EC6_8wekyb3d8bbwe!Minecraft")
        => new(LauncherCandidateKind.Packaged, "Minecraft Launcher", aumid);

    private static LauncherCandidate PackagedBedrockHub()
        => new(LauncherCandidateKind.Packaged, "Minecraft for Windows", "Microsoft.MinecraftUWP_8wekyb3d8bbwe!Game");

    [Fact]
    public void DetectedWin32MinecraftLauncher_IsChosenExactly()
    {
        var win32 = Win32Launcher();
        var chosen = MinecraftLauncherResolver.Resolve(new[] { win32 });

        Assert.Equal(win32, chosen);
    }

    [Fact]
    public void DetectedMicrosoftStoreMinecraftLauncher_IsChosenExactly()
    {
        var packaged = PackagedLauncher();
        var chosen = MinecraftLauncherResolver.Resolve(new[] { packaged });

        Assert.Equal(packaged, chosen);
    }

    [Fact]
    public void BothWin32AndPackagedLauncherPresent_DeterministicallyPrefersWin32()
    {
        var win32 = Win32Launcher();
        var packaged = PackagedLauncher();

        var chosenOneOrder = MinecraftLauncherResolver.Resolve(new[] { win32, packaged });
        var chosenOtherOrder = MinecraftLauncherResolver.Resolve(new[] { packaged, win32 });

        Assert.Equal(win32, chosenOneOrder);
        Assert.Equal(win32, chosenOtherOrder);
    }

    [Fact]
    public void NoValidMinecraftLauncherCandidate_ResolvesToNull()
    {
        var chosen = MinecraftLauncherResolver.Resolve(Array.Empty<LauncherCandidate>());
        Assert.Null(chosen);
    }

    [Fact]
    public void GenericMinecraftForWindowsHubAppAlongsideRealLauncher_RealLauncherWins()
    {
        var hub = PackagedBedrockHub();
        var realLauncher = PackagedLauncher();

        var chosen = MinecraftLauncherResolver.Resolve(new[] { hub, realLauncher });

        Assert.Equal(realLauncher, chosen);
    }

    [Fact]
    public void OnlyTheUnrelatedMinecraftForWindowsHubAppExists_IsNeverChosen()
    {
        var chosen = MinecraftLauncherResolver.Resolve(new[] { PackagedBedrockHub() });
        Assert.Null(chosen);
    }

    [Fact]
    public void UnrelatedAppsWithSimilarNamesAreNeverChosen()
    {
        var unrelated = new LauncherCandidate(LauncherCandidateKind.Win32, "Minecraft", @"C:\some\other\minecraft.exe");
        var chosen = MinecraftLauncherResolver.Resolve(new[] { unrelated });
        Assert.Null(chosen);
    }
}

/// <summary>
/// MinecraftLauncherOpener.TryOpen() end-to-end (with fake discovery/activation, never touching
/// real processes or the registry) - covers the button's actual behavior, including the required
/// "never falls back to minecraft://" guarantee (there is no such fallback anywhere in this class
/// at all - the failure path only ever returns a message).
/// </summary>
public class MinecraftLauncherOpenerTests
{
    private static LauncherCandidate Win32Launcher()
        => new(LauncherCandidateKind.Win32, "Minecraft Launcher", @"C:\Users\test\AppData\Local\Programs\Minecraft Launcher\MinecraftLauncher.exe");

    [Fact]
    public void ValidWin32Candidate_IsLaunchedAndReportsSuccess()
    {
        var discovery = new FakeLauncherDiscovery();
        discovery.Candidates.Add(Win32Launcher());
        var activator = new SpyLauncherActivator();
        var opener = new MinecraftLauncherOpener(discovery, activator);

        var result = opener.TryOpen();

        Assert.True(result.Success);
        Assert.Null(result.UserMessageIfFailed);
        Assert.Single(activator.LaunchedCandidates);
        Assert.Equal(LauncherCandidateKind.Win32, activator.LaunchedCandidates[0].Kind);
    }

    [Fact]
    public void NoValidCandidate_ReturnsCleanFailureAndNeverLaunchesAnything()
    {
        var discovery = new FakeLauncherDiscovery();
        discovery.Candidates.Add(new LauncherCandidate(LauncherCandidateKind.Packaged, "Minecraft for Windows", "Microsoft.MinecraftUWP_8wekyb3d8bbwe!Game"));
        var activator = new SpyLauncherActivator();
        var opener = new MinecraftLauncherOpener(discovery, activator);

        var result = opener.TryOpen();

        Assert.False(result.Success);
        Assert.Equal(MinecraftLauncherOpener.FallbackMessage, result.UserMessageIfFailed);
        Assert.Empty(activator.LaunchedCandidates);
        Assert.DoesNotContain("minecraft://", result.UserMessageIfFailed);
    }

    [Fact]
    public void EmptyDiscoveryResult_ReturnsCleanFailureAndNeverLaunchesAnything()
    {
        var opener = new MinecraftLauncherOpener(new FakeLauncherDiscovery(), new SpyLauncherActivator());

        var result = opener.TryOpen();

        Assert.False(result.Success);
        Assert.Equal(MinecraftLauncherOpener.FallbackMessage, result.UserMessageIfFailed);
    }

    [Fact]
    public void DiscoveryThrows_ReturnsCleanFailureRatherThanCrashing()
    {
        var discovery = new FakeLauncherDiscovery { ThrowOnDiscover = new InvalidOperationException("registry unavailable") };
        var activator = new SpyLauncherActivator();
        var opener = new MinecraftLauncherOpener(discovery, activator);

        var result = opener.TryOpen();

        Assert.False(result.Success);
        Assert.Equal(MinecraftLauncherOpener.FallbackMessage, result.UserMessageIfFailed);
        Assert.Empty(activator.LaunchedCandidates);
    }

    [Fact]
    public void LaunchThrows_ReturnsCleanFailureRatherThanCrashingTheInstaller()
    {
        var discovery = new FakeLauncherDiscovery();
        discovery.Candidates.Add(Win32Launcher());
        var activator = new SpyLauncherActivator { ThrowOnLaunch = new InvalidOperationException("ActivateApplication failed") };
        var opener = new MinecraftLauncherOpener(discovery, activator);

        var result = opener.TryOpen();

        Assert.False(result.Success);
        Assert.Equal(MinecraftLauncherOpener.FallbackMessage, result.UserMessageIfFailed);
    }

    [Fact]
    public void PackagedCandidate_IsLaunchedAndReportsSuccess()
    {
        var discovery = new FakeLauncherDiscovery();
        discovery.Candidates.Add(new LauncherCandidate(LauncherCandidateKind.Packaged, "Minecraft Launcher", "Microsoft.4297127D64EC6_8wekyb3d8bbwe!Minecraft"));
        var activator = new SpyLauncherActivator();
        var opener = new MinecraftLauncherOpener(discovery, activator);

        var result = opener.TryOpen();

        Assert.True(result.Success);
        Assert.Single(activator.LaunchedCandidates);
        Assert.Equal(LauncherCandidateKind.Packaged, activator.LaunchedCandidates[0].Kind);
        Assert.Equal("Microsoft.4297127D64EC6_8wekyb3d8bbwe!Minecraft", activator.LaunchedCandidates[0].LaunchTarget);
    }
}
