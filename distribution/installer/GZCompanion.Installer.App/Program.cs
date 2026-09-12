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

        // --apply-update is always headless (it's a background update worker spawned by the mod
        // itself, launched while Minecraft is still exiting - there is no install wizard to show).
        if (applyUpdate)
        {
            try { Console.OutputEncoding = System.Text.Encoding.UTF8; } catch { /* redirected/non-console stdout - ignore */ }
            string? waitPidRaw = ReadOptionValue(args, "--wait-pid");
            string fromVersion = ReadOptionValue(args, "--from-version") ?? "okänd";
            if (waitPidRaw is null || !int.TryParse(waitPidRaw, out int waitPid))
            {
                Console.WriteLine("FEL: --apply-update kräver ett giltigt --wait-pid <pid>.");
                return 1;
            }
            return RunApplyUpdate(options, waitPid, fromVersion).GetAwaiter().GetResult();
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
    /// The update worker entry point (see docs/UPDATES.md): waits for the supplied Minecraft PID to
    /// exit, double-checks no OTHER Minecraft game process is still running, then applies the
    /// update via either the SAFE FAST PATH (Minecraft/Fabric Loader version unchanged - the
    /// Launcher app may stay open, launcher_profiles.json is never touched) or, if that isn't safe,
    /// the existing FULL installer behavior (requires the Launcher app closed, exactly like a fresh
    /// install/update always has).
    /// </summary>
    private static async Task<int> RunApplyUpdate(AppOptions options, int waitPid, string fromVersion)
    {
        void Log(string line) => Console.WriteLine(line);

        Log("GZ COMPANION UPDATE");
        Log($"Uppdaterar från {fromVersion}...");
        Log("");

        var paths = options.TestRoot is not null
            ? new InstallPaths(Path.Combine(options.TestRoot, "AppData", "Roaming"), Path.Combine(options.TestRoot, "AppData", "Local"))
            : InstallPaths.FromEnvironment();
        var realProcessLister = new RealProcessLister();
        bool isRealRun = options.TestRoot is null;

        Log("✓ Uppdateringen är verifierad");
        Log("Väntar på att Minecraft ska stängas...");
        bool exited = !isRealRun || PidWaiter.WaitForExit(waitPid, PidWaiter.IsProcessRunning, () => Thread.Sleep(1000), maxPolls: 300);
        if (!exited)
        {
            Log("FEL: Kunde inte vänta ut att Minecraft stängs. Ingen uppdatering gjordes. Din nuvarande version har inte ändrats.");
            return 1;
        }

        if (isRealRun)
        {
            // Extra safety net beyond the specific PID: make sure no OTHER Minecraft game process
            // is still using this installation before any file is touched.
            for (int i = 0; i < 60 && EnvironmentDetection.IsMinecraftLikelyRunning(realProcessLister); i++)
            {
                Thread.Sleep(1000);
            }
        }
        Log("Minecraft är stängt.");

        CompatibilityManifest manifest;
        using (var stream = Assembly.GetExecutingAssembly().GetManifestResourceStream("compatibility.json")!)
        using (var reader = new StreamReader(stream))
        {
            manifest = CompatibilityManifest.Parse(await reader.ReadToEndAsync());
        }
        var target = manifest.FindByMinecraftVersion("26.1.2");
        if (target is null)
        {
            Log("FEL: Minecraft 26.1.2 finns inte i den här uppdateringens kompatibilitetsmanifest.");
            return 1;
        }

        var previouslyInstalled = InstalledStateStore.TryRead(paths.InstalledManifestPath);
        bool fastPath = FastPathDecision.CanUseFastPath(previouslyInstalled, target);

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
            IsLauncherRunning = () => isRealRun && EnvironmentDetection.IsMinecraftLauncherRunning(realProcessLister),
        };
        var engine = new InstallEngine(deps);
        var progress = new Progress<string>(Log);

        InstallOutcome outcome;
        if (fastPath)
        {
            Log("Snabb uppdatering: Minecraft/Fabric Loader-versionen är oförändrad. Minecraft Launcher behöver inte stängas.");
            outcome = await engine.RunFastUpdateAsync(target, dryRun: false, progress, CancellationToken.None);
        }
        else if (deps.IsLauncherRunning())
        {
            Log("");
            Log("Stäng Minecraft Launcher för att fortsätta den här uppdateringen.");
            Log("[ Försök igen ] - starta om uppdateringen när Launcher är stängd.");
            return 2; // distinct exit code: the mod recognizes this as "full update blocked by open Launcher"
        }
        else
        {
            Log("Den här uppdateringen kräver den fullständiga installationsprocessen.");
            outcome = await engine.RunAsync(target, dryRun: false, progress, CancellationToken.None);
        }

        Log("");
        if (outcome.Success)
        {
            Log("✓ GZ Companion har uppdaterats");
            Log($"v{fromVersion} → v{target.CompanionVersion}");
        }
        else
        {
            Log($"MISSLYCKADES: {outcome.ErrorMessage}");
            Log("Din nuvarande version har inte ändrats.");
        }
        foreach (var step in outcome.Steps)
        {
            Log($"  [{(outcome.Success ? "OK" : "?")}] {step.Step}: {step.Detail}");
        }

        if (outcome.Success && isRealRun && !EnvironmentDetection.IsMinecraftLauncherRunning(realProcessLister))
        {
            Log("Öppnar Minecraft Launcher...");
            var opener = new MinecraftLauncherOpener(new WindowsInstalledLauncherDiscovery(), new WindowsLauncherActivator());
            var openResult = opener.TryOpen();
            if (!openResult.Success)
            {
                Log(openResult.UserMessageIfFailed ?? MinecraftLauncherOpener.FallbackMessage);
            }
        }

        return outcome.Success ? 0 : 1;
    }
}

public sealed record AppOptions(bool DryRun, bool Verbose, bool Uninstall, string? TestRoot = null);
