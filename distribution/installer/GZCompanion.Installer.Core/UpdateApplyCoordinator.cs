namespace GZCompanion.Installer.Core;

public enum UpdateApplyPhase
{
    WaitingForMinecraft,
    /// <summary>Terminal failure - the update worker aborted with ZERO file mutation.</summary>
    OtherMinecraftRunning,
    Verifying,
    Installing,
    /// <summary>Terminal - the full-update path is required but the Launcher app is open. Never force-closed.</summary>
    LauncherMustClose,
    Succeeded,
    Failed,
}

public sealed record UpdateApplyProgress(UpdateApplyPhase Phase, string Message);

public sealed record UpdateApplyOutcome(UpdateApplyPhase Phase, InstallOutcome? InstallResult, string Message, bool UsedFastPath);

/// <summary>
/// Everything <c>--apply-update</c> needs that isn't pure orchestration logic - all real I/O and
/// process-state goes through these seams so <see cref="UpdateApplyCoordinator"/> is unit-testable
/// without a real Minecraft process, real filesystem paths, or a real network call.
/// </summary>
public sealed record UpdateApplyRequest(
    InstallPaths Paths,
    int WaitPid,
    string FromVersion,
    Func<int, bool> IsPidRunning,
    Func<bool> IsAnyMinecraftGameRunning,
    Action Delay,
    int PidMaxPolls,
    int OtherProcessMaxPolls,
    IFileDownloader Downloader,
    Func<byte[]> LoadEmbeddedCompanionJar,
    Func<DateTimeOffset> Clock,
    Func<bool> IsLauncherRunning,
    CompatibilityManifest Manifest,
    IFastUpdateFileOps? FastUpdateFileOps = null
);

/// <summary>
/// The single, UI-agnostic orchestrator for applying a downloaded update. Both the headless
/// (<c>--test-root</c>) console path and the real update-status WinForms window drive THIS class
/// and only ever render whatever <see cref="UpdateApplyProgress"/>/<see cref="UpdateApplyOutcome"/>
/// it reports - no filesystem or process logic lives inside either UI.
///
/// <para>Ordering is intentionally exactly this, and must never regress:</para>
/// <list type="number">
///   <item><see cref="MinecraftExitGuard"/> must positively confirm it is safe to mutate - if not,
///     this returns immediately with ZERO file mutation; <see cref="InstallEngine"/> is never
///     constructed, let alone invoked, on that path.</item>
///   <item>The compatibility manifest's target entry is resolved.</item>
///   <item><see cref="FastPathDecision"/> decides fast vs. full update.</item>
///   <item>Full path additionally requires the Launcher app closed - if it's open, this returns
///     <see cref="UpdateApplyPhase.LauncherMustClose"/> without ever force-closing it.</item>
/// </list>
/// </summary>
public sealed class UpdateApplyCoordinator
{
    public async Task<UpdateApplyOutcome> RunAsync(UpdateApplyRequest req, IProgress<UpdateApplyProgress>? progress, CancellationToken ct)
    {
        progress?.Report(new UpdateApplyProgress(UpdateApplyPhase.WaitingForMinecraft, "Väntar på att Minecraft ska stängas..."));

        var guard = MinecraftExitGuard.WaitUntilSafeToMutate(
            req.WaitPid, req.IsPidRunning, req.IsAnyMinecraftGameRunning, req.Delay, req.PidMaxPolls, req.OtherProcessMaxPolls);

        if (!guard.SafeToMutate)
        {
            string msg = guard.AbortReason ?? "Minecraft kör fortfarande. Stäng Minecraft och försök igen.";
            progress?.Report(new UpdateApplyProgress(UpdateApplyPhase.OtherMinecraftRunning, msg));
            return new UpdateApplyOutcome(UpdateApplyPhase.OtherMinecraftRunning, null, msg, UsedFastPath: false);
        }

        progress?.Report(new UpdateApplyProgress(UpdateApplyPhase.Verifying, "Minecraft är stängt. Verifierar installation..."));

        var target = req.Manifest.FindByMinecraftVersion("26.1.2");
        if (target is null)
        {
            const string msg = "Minecraft 26.1.2 finns inte i den här uppdateringens kompatibilitetsmanifest.";
            progress?.Report(new UpdateApplyProgress(UpdateApplyPhase.Failed, msg));
            return new UpdateApplyOutcome(UpdateApplyPhase.Failed, null, msg, UsedFastPath: false);
        }

        var previouslyInstalled = InstalledStateStore.TryRead(req.Paths.InstalledManifestPath);
        bool fastPath = FastPathDecision.CanUseFastPath(previouslyInstalled, target);

        var deps = new InstallEngineDependencies
        {
            Paths = req.Paths,
            Downloader = req.Downloader,
            LoadEmbeddedCompanionJar = req.LoadEmbeddedCompanionJar,
            Clock = req.Clock,
            IsLauncherRunning = req.IsLauncherRunning,
            FastUpdateFileOps = req.FastUpdateFileOps,
        };
        var engine = new InstallEngine(deps);
        var textProgress = new Progress<string>(line => progress?.Report(new UpdateApplyProgress(UpdateApplyPhase.Installing, line)));

        InstallOutcome outcome;
        if (fastPath)
        {
            progress?.Report(new UpdateApplyProgress(UpdateApplyPhase.Installing, "Snabb uppdatering: Minecraft Launcher behöver inte stängas."));
            outcome = await engine.RunFastUpdateAsync(target, previouslyInstalled!, dryRun: false, textProgress, ct).ConfigureAwait(false);
        }
        else if (req.IsLauncherRunning())
        {
            const string msg = "Minecraft Launcher måste stängas för den här uppdateringen.";
            progress?.Report(new UpdateApplyProgress(UpdateApplyPhase.LauncherMustClose, msg));
            return new UpdateApplyOutcome(UpdateApplyPhase.LauncherMustClose, null, msg, UsedFastPath: false);
        }
        else
        {
            progress?.Report(new UpdateApplyProgress(UpdateApplyPhase.Installing, "Den här uppdateringen kräver den fullständiga installationsprocessen."));
            outcome = await engine.RunAsync(target, dryRun: false, textProgress, ct).ConfigureAwait(false);
        }

        if (outcome.Success)
        {
            string msg = $"v{req.FromVersion} → v{target.CompanionVersion}";
            progress?.Report(new UpdateApplyProgress(UpdateApplyPhase.Succeeded, msg));
            return new UpdateApplyOutcome(UpdateApplyPhase.Succeeded, outcome, msg, fastPath);
        }
        else
        {
            string msg = outcome.ErrorMessage ?? "Okänt fel.";
            progress?.Report(new UpdateApplyProgress(UpdateApplyPhase.Failed, msg));
            return new UpdateApplyOutcome(UpdateApplyPhase.Failed, outcome, msg, fastPath);
        }
    }
}
