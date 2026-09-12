using System.Reflection;
using System.Threading;
using GZCompanion.Installer.Core;

namespace GZCompanion.Installer.App;

internal static class Program
{
    /// <summary>Single canonical product version - see <see cref="GeneratedProductVersion"/>.</summary>
    public const string InstallerVersion = GeneratedProductVersion.Value;

    [STAThread]
    private static int Main(string[] args)
    {
        bool dryRun = args.Any(a => a.Equals("--dry-run", StringComparison.OrdinalIgnoreCase));
        bool verbose = args.Any(a => a.Equals("--verbose", StringComparison.OrdinalIgnoreCase));
        bool uninstall = args.Any(a => a.Equals("--uninstall", StringComparison.OrdinalIgnoreCase));
        bool applyUpdate = args.Any(a => a.Equals("--apply-update", StringComparison.OrdinalIgnoreCase));
        string? testRoot = ReadOptionValue(args, "--test-root");

        var options = new AppOptions(dryRun, verbose, uninstall, testRoot);

        // --apply-update: a REAL run (no --test-root) shows the dedicated update-status window -
        // the whole point of this feature is a one-click update for a non-technical player, and a
        // console window that vanishes the instant Minecraft closes is not that. --test-root stays
        // headless so automated smoke tests can drive it without a real window.
        if (applyUpdate)
        {
            string? waitPidRaw = ReadOptionValue(args, "--wait-pid");
            string fromVersion = ReadOptionValue(args, "--from-version") ?? "okänd";
            if (waitPidRaw is null || !int.TryParse(waitPidRaw, out int waitPid))
            {
                try { Console.OutputEncoding = System.Text.Encoding.UTF8; } catch { }
                Console.WriteLine("FEL: --apply-update kräver ett giltigt --wait-pid <pid>.");
                return 1;
            }

            if (testRoot is not null)
            {
                try { Console.OutputEncoding = System.Text.Encoding.UTF8; } catch { }
                return RunApplyUpdateHeadless(options, waitPid, fromVersion).GetAwaiter().GetResult();
            }

            ApplicationConfiguration.Initialize();
            var request = BuildRealApplyUpdateRequest(waitPid, fromVersion);
            Application.Run(new UpdateApplyForm(request, onSuccess: () => TryOpenLauncherIfNotOpen(new RealProcessLister())));
            return 0;
        }

        // --dry-run (headless, no window - scriptable) and --test-root (headless, points every
        // path at a throwaway directory instead of the real user profile - for smoke-testing the
        // REAL download/verify/write pipeline without touching a real Minecraft installation)
        // both skip the GUI entirely.
        if (dryRun || testRoot is not null)
        {
            try { Console.OutputEncoding = System.Text.Encoding.UTF8; } catch { /* redirected/non-console stdout - ignore */ }
            return RunHeadless(options).GetAwaiter().GetResult();
        }

        ApplicationConfiguration.Initialize();
        Application.Run(new MainForm(options));
        return 0;
    }

    private static string? ReadOptionValue(string[] args, string name)
    {
        int i = Array.FindIndex(args, a => a.Equals(name, StringComparison.OrdinalIgnoreCase));
        return i >= 0 && i + 1 < args.Length ? args[i + 1] : null;
    }

    private static async Task<int> RunHeadless(AppOptions options)
    {
        void Log(string line) => Console.WriteLine(line);

        string modeLabel = options.DryRun ? "TORRKÖRNING (--dry-run). Inget ändras." : "TESTKÖRNING mot --test-root (riktiga nedladdningar, isolerad katalog).";
        Log($"GZ Companion Setup {InstallerVersion} - {modeLabel}");
        Log("");

        var paths = options.TestRoot is not null
            ? new InstallPaths(Path.Combine(options.TestRoot, "AppData", "Roaming"), Path.Combine(options.TestRoot, "AppData", "Local"))
            : InstallPaths.FromEnvironment();

        var windows = EnvironmentDetection.CheckWindowsVersion(Environment.OSVersion.Version);
        Log($"Windows: {windows.DisplayVersion} ({windows.Level})");

        var launcher = EnvironmentDetection.CheckLauncher(paths);
        Log($"Minecraft Launcher hittad: {launcher.Found} (.minecraft: {launcher.DotMinecraftDir})");
        if (launcher.ExistingProfilePaths.Count > 0)
        {
            Log(launcher.ExistingProfilePaths.Count == 1 ? "Launcherprofil:" : "Launcherprofiler:");
            foreach (var p in launcher.ExistingProfilePaths)
            {
                Log($"  {Path.GetFileName(p)}");
            }
        }
        else
        {
            Log("Launcherprofil: ingen hittades");
        }

        var realProcessLister = new RealProcessLister();
        bool running = options.TestRoot is null && EnvironmentDetection.IsMinecraftLikelyRunning(realProcessLister);
        Log($"Minecraft körs just nu: {running}");
        bool launcherAppRunning = options.TestRoot is null && EnvironmentDetection.IsMinecraftLauncherRunning(realProcessLister);
        Log($"Minecraft Launcher öppen: {launcherAppRunning}");

        var existing = EnvironmentDetection.CheckExistingInstall(paths);
        Log($"Befintlig GZ Companion-installation: {existing.Found} (jar hittad: {existing.HasCompanionJar}, tidigare version: {existing.InstalledCompanionVersion ?? "okänd"})");

        CompatibilityManifest manifest;
        using (var stream = Assembly.GetExecutingAssembly().GetManifestResourceStream("compatibility.json")!)
        using (var reader = new StreamReader(stream))
        {
            manifest = CompatibilityManifest.Parse(await reader.ReadToEndAsync());
        }
        var target = manifest.FindByMinecraftVersion("26.1.2");
        if (target is null)
        {
            Log("FEL: Minecraft 26.1.2 finns inte i den här installerarens kompatibilitetsmanifest.");
            return 1;
        }
        Log($"Mål: Minecraft {target.MinecraftVersion}, Fabric Loader {target.FabricLoaderVersion}, Fabric API {target.FabricApiVersion}, GZ Companion {target.CompanionVersion}");
        Log($"Isolerad spelkatalog: {paths.GzCompanionGameDir}");
        Log("");
        Log($"Läge: {(options.Uninstall ? "AVINSTALLERA" : "INSTALLERA")}{(options.DryRun ? " (torrkörning)" : "")}");

        using var downloader = new HttpsFileDownloader($"GZCompanionInstaller/{InstallerVersion}");
        var deps = new InstallEngineDependencies
        {
            Paths = paths,
            Downloader = downloader,
            LoadEmbeddedCompanionJar = () =>
            {
                using var s = Assembly.GetExecutingAssembly().GetManifestResourceStream("gzcompanion.jar")!;
                using var mem = new MemoryStream();
                s.CopyTo(mem);
                return mem.ToArray();
            },
            Clock = () => DateTimeOffset.Now,
            // A --test-root run is an isolated developer sandbox that never touches the real
            // launcher_profiles.json, so the real "is the launcher open" safety check is moot
            // there; a plain --dry-run against the real environment still honors it.
            IsLauncherRunning = () => options.TestRoot is null && EnvironmentDetection.IsMinecraftLauncherRunning(realProcessLister),
        };
        var engine = new InstallEngine(deps);
        var progress = new Progress<string>(Log);

        var outcome = options.Uninstall
            ? await engine.UninstallAsync(keepUserData: true, options.DryRun, progress, CancellationToken.None)
            : await engine.RunAsync(target, options.DryRun, progress, CancellationToken.None);

        Log("");
        Log(outcome.Success ? "KLAR." : $"MISSLYCKADES: {outcome.ErrorMessage}");
        foreach (var step in outcome.Steps)
        {
            Log($"  [{(outcome.Success ? "OK" : "?")}] {step.Step}: {step.Detail}");
        }
        return outcome.Success ? 0 : 1;
    }

    /// <summary>
    /// The headless (<c>--test-root</c>) update worker entry point, for automated smoke testing -
    /// drives the exact same <see cref="UpdateApplyCoordinator"/> the real WinForms window does,
    /// just rendering its progress to the console instead. See <see cref="UpdateApplyForm"/> for
    /// the real, user-visible equivalent.
    /// </summary>
    private static async Task<int> RunApplyUpdateHeadless(AppOptions options, int waitPid, string fromVersion)
    {
        void Log(string line) => Console.WriteLine(line);
        Log("GZ COMPANION UPDATE (--test-root, headless)");
        Log($"Uppdaterar från {fromVersion}...");
        Log("");

        var request = BuildTestRootApplyUpdateRequest(options.TestRoot!, waitPid, fromVersion);
        var progress = new Progress<UpdateApplyProgress>(p => Log($"[{p.Phase}] {p.Message}"));
        var outcome = await new UpdateApplyCoordinator().RunAsync(request, progress, CancellationToken.None);

        Log("");
        if (outcome.InstallResult is not null)
        {
            foreach (var step in outcome.InstallResult.Steps)
            {
                Log($"  [{(outcome.InstallResult.Success ? "OK" : "?")}] {step.Step}: {step.Detail}");
            }
        }
        Log(outcome.Phase == UpdateApplyPhase.Succeeded ? "KLAR." : $"MISSLYCKADES ({outcome.Phase}): {outcome.Message}");

        return outcome.Phase switch
        {
            UpdateApplyPhase.Succeeded => 0,
            UpdateApplyPhase.LauncherMustClose => 2, // distinct exit code: mod recognizes "full update blocked by open Launcher"
            UpdateApplyPhase.OtherMinecraftRunning => 3, // distinct exit code: "Minecraft never actually closed"
            _ => 1,
        };
    }

    private static UpdateApplyRequest BuildTestRootApplyUpdateRequest(string testRoot, int waitPid, string fromVersion)
    {
        var paths = new InstallPaths(Path.Combine(testRoot, "AppData", "Roaming"), Path.Combine(testRoot, "AppData", "Local"));
        return BuildApplyUpdateRequest(paths, waitPid, fromVersion,
            isPidRunning: _ => false, // an isolated test-root run has no real PID to wait for
            isAnyMinecraftGameRunning: () => false,
            pidMaxPolls: 0, otherProcessMaxPolls: 0,
            isLauncherRunning: () => false, // isolated sandbox - never touches a real launcher_profiles.json
            delayAsync: _ => Task.CompletedTask); // headless smoke test - never a real sleep
    }

    private static UpdateApplyRequest BuildRealApplyUpdateRequest(int waitPid, string fromVersion)
    {
        var paths = InstallPaths.FromEnvironment();
        var realProcessLister = new RealProcessLister();
        return BuildApplyUpdateRequest(paths, waitPid, fromVersion,
            isPidRunning: PidWaiter.IsProcessRunning,
            isAnyMinecraftGameRunning: () => EnvironmentDetection.IsMinecraftLikelyRunning(realProcessLister),
            pidMaxPolls: 300, otherProcessMaxPolls: 60, // ~5 minutes, then ~1 more minute grace for any other MC process
            isLauncherRunning: () => EnvironmentDetection.IsMinecraftLauncherRunning(realProcessLister),
            delayAsync: ct => Task.Delay(1000, ct)); // genuinely async - never blocks the WinForms UI thread
    }

    private static UpdateApplyRequest BuildApplyUpdateRequest(
        InstallPaths paths, int waitPid, string fromVersion,
        Func<int, bool> isPidRunning, Func<bool> isAnyMinecraftGameRunning,
        int pidMaxPolls, int otherProcessMaxPolls, Func<bool> isLauncherRunning,
        Func<CancellationToken, Task> delayAsync)
    {
        CompatibilityManifest manifest;
        using (var stream = Assembly.GetExecutingAssembly().GetManifestResourceStream("compatibility.json")!)
        using (var reader = new StreamReader(stream))
        {
            manifest = CompatibilityManifest.Parse(reader.ReadToEnd());
        }

        return new UpdateApplyRequest(
            Paths: paths,
            WaitPid: waitPid,
            FromVersion: fromVersion,
            IsPidRunning: isPidRunning,
            IsAnyMinecraftGameRunning: isAnyMinecraftGameRunning,
            DelayAsync: delayAsync,
            PidMaxPolls: pidMaxPolls,
            OtherProcessMaxPolls: otherProcessMaxPolls,
            Downloader: new HttpsFileDownloader($"GZCompanionInstaller/{InstallerVersion}"),
            LoadEmbeddedCompanionJar: () =>
            {
                using var s = Assembly.GetExecutingAssembly().GetManifestResourceStream("gzcompanion.jar")!;
                using var mem = new MemoryStream();
                s.CopyTo(mem);
                return mem.ToArray();
            },
            Clock: () => DateTimeOffset.Now,
            IsLauncherRunning: isLauncherRunning,
            Manifest: manifest);
    }

    /// <summary>Reuses the existing, proven MinecraftLauncherOpener - never force-closes anything, never called if the Launcher is already open.</summary>
    private static void TryOpenLauncherIfNotOpen(RealProcessLister processLister)
    {
        try
        {
            if (EnvironmentDetection.IsMinecraftLauncherRunning(processLister)) return;
            var opener = new MinecraftLauncherOpener(new WindowsInstalledLauncherDiscovery(), new WindowsLauncherActivator());
            opener.TryOpen();
        }
        catch { /* best-effort convenience only - never let this affect the already-reported successful update */ }
    }
}

public sealed record AppOptions(bool DryRun, bool Verbose, bool Uninstall, string? TestRoot = null);
