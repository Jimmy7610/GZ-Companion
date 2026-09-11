using System.Text.Json.Nodes;

namespace GZCompanion.Installer.Core;

public sealed record InstallStepResult(string Step, bool WouldExecuteOrExecuted, string Detail);

public sealed record InstallOutcome(bool Success, bool DryRun, IReadOnlyList<InstallStepResult> Steps, string? ErrorMessage);

/// <summary>Everything the engine needs that isn't pure data - all real I/O goes through these seams so orchestration is unit-testable.</summary>
public sealed class InstallEngineDependencies
{
    public required InstallPaths Paths { get; init; }
    public required IFileDownloader Downloader { get; init; }
    public required Func<byte[]> LoadEmbeddedCompanionJar { get; init; }
    public required Func<DateTimeOffset> Clock { get; init; }
}

/// <summary>
/// Orchestrates one full install/update. Every step is guarded by <c>dryRun</c>: in dry-run mode
/// every step still runs its detection/decision logic and is recorded, but no file is created,
/// downloaded, moved, or deleted, and launcher_profiles.json is never opened for writing.
/// </summary>
public sealed class InstallEngine
{
    private const string ProfileId = "gzcompanion-gameZone";
    private const string ProfileName = "GZ Companion - GameZone";
    private const string UserAgent = "GZCompanionInstaller/0.1.0-alpha.1 (+https://github.com/Jimmy7610/GZ-Companion)";

    private readonly InstallEngineDependencies _deps;

    public InstallEngine(InstallEngineDependencies deps) => _deps = deps;

    public async Task<InstallOutcome> RunAsync(SupportedEntry target, bool dryRun, IProgress<string>? log, CancellationToken ct)
    {
        var steps = new List<InstallStepResult>();
        void Record(string step, string detail) => steps.Add(new InstallStepResult(step, true, detail));
        void Emit(string message) { log?.Report(message); }

        try
        {
            var paths = _deps.Paths;

            // 1. Isolated directories.
            Emit("Skapar isolerad spelkatalog...");
            if (!dryRun)
            {
                Directory.CreateDirectory(paths.GzCompanionModsDir);
                Directory.CreateDirectory(paths.GzCompanionVersionsDir);
                Directory.CreateDirectory(paths.GzCompanionLibrariesDir);
                Directory.CreateDirectory(paths.GzCompanionConfigDir);
                Directory.CreateDirectory(paths.GzCompanionInstallerStateDir);
            }
            Record("directories", paths.GzCompanionGameDir);

            // 2. Fabric loader version profile - fetched live from the official Fabric Meta API.
            Emit("Hämtar Fabric Loader-profil...");
            string versionDir = Path.Combine(paths.GzCompanionVersionsDir, target.FabricLoader.ProfileId);
            string versionJsonPath = Path.Combine(versionDir, $"{target.FabricLoader.ProfileId}.json");
            string profileJson = await _deps.Downloader.DownloadTextAsync(new Uri(target.FabricLoader.ProfileJsonUrl), ct).ConfigureAwait(false);
            var loaderProfile = FabricLoaderProfile.Parse(profileJson);
            if (!string.Equals(loaderProfile.Id, target.FabricLoader.ProfileId, StringComparison.Ordinal))
            {
                throw new ManifestParseException(
                    $"Fabric Meta API returned profile id '{loaderProfile.Id}', expected '{target.FabricLoader.ProfileId}'. Refusing to install a mismatched profile.");
            }
            if (!dryRun)
            {
                Directory.CreateDirectory(versionDir);
                AtomicFileWriter.WriteAllTextAtomically(versionJsonPath, profileJson);
            }
            Record("fabric-loader-profile", loaderProfile.Id);

            // 3. Every library the profile lists, verified against the hash IT provides (falling
            //    back to our own independently-verified hash only for the one entry Fabric's API
            //    itself doesn't hash - the loader jar).
            foreach (var lib in loaderProfile.Libraries)
            {
                ct.ThrowIfCancellationRequested();
                string relPath = lib.ToMavenPath();
                string destPath = Path.Combine(paths.GzCompanionLibrariesDir, relPath.Replace('/', Path.DirectorySeparatorChar));
                string? expectedSha256 = lib.Sha256;
                if (expectedSha256 is null && lib.Name == target.FabricLoader.KnownGoodFallback?.MavenCoordinate)
                {
                    expectedSha256 = target.FabricLoader.KnownGoodFallback.Sha256;
                }

                Emit($"Hämtar bibliotek: {lib.Name}");
                if (!dryRun)
                {
                    if (File.Exists(destPath) && expectedSha256 is not null && HashMatches(destPath, expectedSha256))
                    {
                        // Already present and verified from a previous install - avoid a
                        // redundant download.
                    }
                    else
                    {
                        var libraryUrl = new Uri(new Uri(lib.Url), relPath);
                        await _deps.Downloader.DownloadToFileAsync(libraryUrl, destPath, progress: null, ct).ConfigureAwait(false);
                        if (expectedSha256 is not null)
                        {
                            Sha256.VerifyOrThrow(destPath, expectedSha256, lib.Name);
                        }
                    }
                }
                Record("library", lib.Name);
            }

            // 4. Fabric API - downloaded from the official Modrinth CDN URL pinned (with its own
            //    independently-verified checksum) in our manifest.
            Emit("Hämtar Fabric API...");
            string fabricApiPath = Path.Combine(paths.GzCompanionModsDir, target.FabricApi.FileName);
            if (!dryRun)
            {
                bool alreadyGood = File.Exists(fabricApiPath) && HashMatches(fabricApiPath, target.FabricApi.Sha256);
                if (!alreadyGood)
                {
                    await _deps.Downloader.DownloadToFileAsync(new Uri(target.FabricApi.DownloadUrl), fabricApiPath, progress: null, ct).ConfigureAwait(false);
                    Sha256.VerifyOrThrow(fabricApiPath, target.FabricApi.Sha256, "Fabric API");
                }
            }
            Record("fabric-api", target.FabricApi.FileName);

            // 5. GZ Companion's own jar - embedded inside this installer, never downloaded, and
            //    any older gzcompanion-*.jar in mods is removed first so a reinstall/update never
            //    leaves two copies loaded at once.
            Emit("Installerar GZ Companion...");
            string companionJarPath = Path.Combine(paths.GzCompanionModsDir, target.CompanionJar.FileName);
            if (!dryRun)
            {
                if (Directory.Exists(paths.GzCompanionModsDir))
                {
                    foreach (var stale in Directory.EnumerateFiles(paths.GzCompanionModsDir, "gzcompanion-*.jar"))
                    {
                        if (!string.Equals(Path.GetFileName(stale), target.CompanionJar.FileName, StringComparison.OrdinalIgnoreCase))
                        {
                            File.Delete(stale);
                        }
                    }
                }

                byte[] jarBytes = _deps.LoadEmbeddedCompanionJar();
                string tempJarPath = companionJarPath + ".staging";
                await File.WriteAllBytesAsync(tempJarPath, jarBytes, ct).ConfigureAwait(false);
                Sha256.VerifyOrThrow(tempJarPath, target.CompanionJar.Sha256, "GZ Companion.jar (embedded)");
                File.Move(tempJarPath, companionJarPath, overwrite: true);
            }
            Record("companion-jar", target.CompanionJar.FileName);

            // 6. Launcher profile - the only file outside our isolated directory this installer
            //    ever writes. Backed up first, parsed/merged as JSON (never regex), and every
            //    other profile is preserved untouched.
            Emit("Uppdaterar Minecraft Launcher-profil...");
            if (!dryRun)
            {
                LauncherProfilesEditor.BackupIfExists(paths.LauncherProfilesPath, _deps.Clock());
                JsonObject root = File.Exists(paths.LauncherProfilesPath)
                    ? LauncherProfilesEditor.ParseAndValidate(File.ReadAllText(paths.LauncherProfilesPath))
                    : LauncherProfilesEditor.NewEmptyDocument();

                var spec = new GzCompanionProfileSpec(ProfileId, ProfileName, paths.GzCompanionGameDir, loaderProfile.Id, IconBase64: null);
                JsonObject updated = LauncherProfilesEditor.UpsertGzCompanionProfile(root, spec, _deps.Clock());
                AtomicFileWriter.WriteAllTextAtomically(paths.LauncherProfilesPath, LauncherProfilesEditor.Serialize(updated));
            }
            Record("launcher-profile", ProfileName);

            // 7. Our own install-state bookkeeping (for future reinstall/update/uninstall).
            if (!dryRun)
            {
                InstalledStateStore.Write(paths.InstalledManifestPath, new InstalledState(
                    target.CompanionVersion, target.MinecraftVersion, target.FabricLoaderVersion, target.FabricApiVersion,
                    _deps.Clock().ToString("O")));
            }
            Record("installed-state", target.CompanionVersion);

            return new InstallOutcome(Success: true, DryRun: dryRun, Steps: steps, ErrorMessage: null);
        }
        catch (Exception ex)
        {
            return new InstallOutcome(Success: false, DryRun: dryRun, Steps: steps, ErrorMessage: ex.Message);
        }
    }

    /// <summary>
    /// Removes the launcher profile and (unless the caller asks to keep it) the isolated game
    /// directory. GZ Companion's own local user data lives under the same game directory - callers
    /// that want "Behåll mina GZ Companion-data" must pass keepUserData=true, which preserves the
    /// config directory tree while still removing mods/versions/libraries.
    /// </summary>
    public async Task<InstallOutcome> UninstallAsync(bool keepUserData, bool dryRun, IProgress<string>? log, CancellationToken ct)
    {
        await Task.Yield();
        var steps = new List<InstallStepResult>();
        var paths = _deps.Paths;
        try
        {
            log?.Report("Tar bort Minecraft Launcher-profil...");
            if (File.Exists(paths.LauncherProfilesPath))
            {
                if (!dryRun)
                {
                    LauncherProfilesEditor.BackupIfExists(paths.LauncherProfilesPath, _deps.Clock());
                    var root = LauncherProfilesEditor.ParseAndValidate(File.ReadAllText(paths.LauncherProfilesPath));
                    var updated = LauncherProfilesEditor.RemoveGzCompanionProfile(root, ProfileId);
                    AtomicFileWriter.WriteAllTextAtomically(paths.LauncherProfilesPath, LauncherProfilesEditor.Serialize(updated));
                }
            }
            steps.Add(new InstallStepResult("launcher-profile-removed", true, ProfileId));

            log?.Report(keepUserData ? "Tar bort installationsfiler (behåller dina data)..." : "Tar bort alla GZ Companion-filer...");
            if (!dryRun && Directory.Exists(paths.GzCompanionGameDir))
            {
                if (keepUserData)
                {
                    foreach (var dir in new[] { paths.GzCompanionModsDir, paths.GzCompanionVersionsDir, paths.GzCompanionLibrariesDir })
                    {
                        if (Directory.Exists(dir)) Directory.Delete(dir, recursive: true);
                    }
                }
                else
                {
                    Directory.Delete(paths.GzCompanionGameDir, recursive: true);
                }
            }
            steps.Add(new InstallStepResult("files-removed", true, keepUserData ? "kept user data" : "removed everything"));

            return new InstallOutcome(true, dryRun, steps, null);
        }
        catch (Exception ex)
        {
            return new InstallOutcome(false, dryRun, steps, ex.Message);
        }
    }

    private static bool HashMatches(string path, string expectedSha256)
    {
        try { return string.Equals(Sha256.ComputeFileHashHex(path), expectedSha256, StringComparison.OrdinalIgnoreCase); }
        catch { return false; }
    }
}
