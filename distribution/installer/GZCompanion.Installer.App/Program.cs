using System.Reflection;
using GZCompanion.Installer.Core;

namespace GZCompanion.Installer.App;

internal static class Program
{
    public const string InstallerVersion = "0.1.0-alpha.1";

    [STAThread]
    private static int Main(string[] args)
    {
        bool dryRun = args.Any(a => a.Equals("--dry-run", StringComparison.OrdinalIgnoreCase));
        bool verbose = args.Any(a => a.Equals("--verbose", StringComparison.OrdinalIgnoreCase));
        bool uninstall = args.Any(a => a.Equals("--uninstall", StringComparison.OrdinalIgnoreCase));
        string? testRoot = ReadOptionValue(args, "--test-root");

        var options = new AppOptions(dryRun, verbose, uninstall, testRoot);

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

        bool running = options.TestRoot is null && EnvironmentDetection.IsMinecraftLikelyRunning(new RealProcessLister());
        Log($"Minecraft körs just nu: {running}");

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
}

public sealed record AppOptions(bool DryRun, bool Verbose, bool Uninstall, string? TestRoot = null);
