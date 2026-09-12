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

    /// <summary>
    /// Checked first, before any step of install or uninstall. Wire this to
    /// <see cref="EnvironmentDetection.IsMinecraftLauncherRunning"/> for a real run against a real
    /// launcher; an isolated developer smoke test (a throwaway --test-root that never touches a
    /// real launcher_profiles.json) should wire <c>() => false</c> instead, since there is nothing
    /// real for this safety check to protect there.
    /// </summary>
    public required Func<bool> IsLauncherRunning { get; init; }

    /// <summary>
    /// Optional seam for <see cref="InstallEngine.RunFastUpdateAsync"/>'s file swap/rollback steps
    /// only - defaults to real file operations when omitted (every existing caller of
    /// <see cref="InstallEngine.RunAsync"/>/<see cref="InstallEngine.UninstallAsync"/> is unaffected
    /// and never needs to set this). Tests inject a fake that can be told to throw at a specific
    /// call, to deterministically prove rollback behavior at each commit stage without relying on
    /// real filesystem race conditions.
    /// </summary>
    public IFastUpdateFileOps? FastUpdateFileOps { get; init; }
}

/// <summary>See <see cref="InstallEngineDependencies.FastUpdateFileOps"/>.</summary>
public interface IFastUpdateFileOps
{
    bool Exists(string path);
    void Move(string from, string to);
    void Delete(string path);

    /// <summary>Used ONLY for the final installed.json write, via <see cref="AtomicFileWriter"/> in the real implementation - the one write test 6 (installed-state write failure) needs to be able to inject.</summary>
    void WriteAllText(string path, string content);
}

/// <summary>The real, production <see cref="IFastUpdateFileOps"/> - plain <see cref="File"/> calls.</summary>
public sealed class RealFastUpdateFileOps : IFastUpdateFileOps
{
    public void WriteAllText(string path, string content) => AtomicFileWriter.WriteAllTextAtomically(path, content);
    public bool Exists(string path) => File.Exists(path);
    public void Move(string from, string to) => File.Move(from, to, overwrite: true);
    public void Delete(string path) => File.Delete(path);
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

    /// <summary>Thrown (and turned into a clean, non-stack-trace InstallOutcome) when the launcher app itself is open.</summary>
    public sealed class LauncherRunningException : Exception
    {
        public LauncherRunningException() : base("Minecraft Launcher är öppen. Stäng Minecraft Launcher innan installationen fortsätter.") { }
    }

    /// <summary>
    /// Thrown when neither official profile file exists yet. Mirrors Fabric's own installer
    /// (<c>ClientHandler.doInstall</c> throws "no.launcher.profile" when
    /// <c>ProfileInstaller.getInstalledLauncherTypes()</c> returns zero types) - a bare
    /// <c>.minecraft</c> directory is not evidence the launcher has ever actually been run, and we
    /// must never invent a profile file for a launcher channel the player doesn't use.
    /// </summary>
    public sealed class LauncherNotInitializedException : Exception
    {
        public LauncherNotInitializedException() : base("Minecraft Launcher är inte färdigkonfigurerad. Starta den officiella Minecraft Launcher en gång och försök igen.") { }
    }

    /// <summary>
    /// Thrown when an existing profile file can't be safely parsed. With two independent profile
    /// files, the first could be perfectly valid while the second is corrupt/unsupported - this is
    /// why EVERY existing file is pre-parsed and validated before any directory is created, any
    /// byte is downloaded, or any file (including the OTHER, valid profile file) is written. We
    /// never repair, overwrite, or guess at malformed launcher data.
    /// </summary>
    public sealed class LauncherProfileUnreadableException : Exception
    {
        public LauncherProfileUnreadableException(string fileName)
            : base($"{fileName} kunde inte läsas säkert. Ingen installation har gjorts.") { }
    }

    private readonly InstallEngineDependencies _deps;

    public InstallEngine(InstallEngineDependencies deps) => _deps = deps;

    public async Task<InstallOutcome> RunAsync(SupportedEntry target, bool dryRun, IProgress<string>? log, CancellationToken ct)
    {
        var steps = new List<InstallStepResult>();
        void Record(string step, string detail) => steps.Add(new InstallStepResult(step, true, detail));
        void Emit(string message) { log?.Report(message); }

        try
        {
            // Official Fabric installation guidance requires the launcher to be closed before
            // touching launcher_profiles.json. Checked first, before any download even starts.
            if (_deps.IsLauncherRunning())
            {
                throw new LauncherRunningException();
            }

            var paths = _deps.Paths;

            // At least one of the two official profile files must already exist - see
            // LauncherNotInitializedException. Determined up front, before any download, exactly
            // like the launcher-running check above.
            var existingProfilePaths = paths.AllLauncherProfilePaths.Where(File.Exists).ToList();
            if (existingProfilePaths.Count == 0)
            {
                throw new LauncherNotInitializedException();
            }

            // Pre-parse and validate EVERY existing profile file now, before creating a single
            // directory or downloading a single byte. With two independent files, one could be
            // valid while the other is corrupt/unsupported - if we only discovered that while
            // writing (step 6, after everything else already happened), we could leave a partial
            // install and/or have already updated the FIRST (valid) file before failing on the
            // second. Validated documents are kept in memory and reused verbatim in step 6 rather
            // than re-read, so what we validate here is exactly what gets written later.
            var parsedProfilesByPath = new Dictionary<string, JsonObject>();
            foreach (var profilePath in existingProfilePaths)
            {
                try
                {
                    parsedProfilesByPath[profilePath] = LauncherProfilesEditor.ParseAndValidateFile(profilePath);
                }
                catch (Exception)
                {
                    throw new LauncherProfileUnreadableException(Path.GetFileName(profilePath));
                }
            }

            // 1. Directories. GzCompanion's own mods/config are isolated; Fabric's version JSON
            //    and libraries are launcher-owned shared infrastructure (see InstallPaths.DotMinecraftDir)
            //    and simply need to exist - they are never exclusively "ours".
            Emit("Skapar kataloger...");
            if (!dryRun)
            {
                Directory.CreateDirectory(paths.GzCompanionModsDir);
                Directory.CreateDirectory(paths.GzCompanionConfigDir);
                Directory.CreateDirectory(paths.GzCompanionInstallerStateDir);
                Directory.CreateDirectory(paths.SharedVersionsDir);
                Directory.CreateDirectory(paths.SharedLibrariesDir);
            }
            Record("directories", paths.GzCompanionGameDir);

            // 1b. Fresh-install-only: seed the ISOLATED profile's own multiplayer server list with
            //     GameZoneMC, so a friend's first Multiplayer screen isn't empty and they never have
            //     to type play.gamezonemc.se by hand. Lives entirely under GzCompanionGameDir - the
            //     player's real .minecraft/servers.dat (InstallPaths.DotMinecraftDir) is never read
            //     or written. An existing isolated servers.dat (from a previous install) is left
            //     byte-for-byte untouched - see ServersDatWriter.WriteIfAbsent.
            Emit("Förbereder GameZoneMC-server...");
            bool serversDatAlreadyExists = File.Exists(paths.GzCompanionServersDatPath);
            if (!dryRun && !serversDatAlreadyExists)
            {
                ServersDatWriter.WriteIfAbsent(paths.GzCompanionServersDatPath, "GameZoneMC", "play.gamezonemc.se");
            }
            Record("servers-dat-seeded", serversDatAlreadyExists ? "preserved existing servers.dat" : "created with GameZoneMC");

            // 2. Fabric loader version profile - fetched live from the official Fabric Meta API,
            //    and written under the launcher's OWN versions directory (confirmed against the
            //    official Fabric Installer source: it always resolves versions/libraries from the
            //    same root the launcher itself uses, never from a profile's custom gameDir).
            Emit("Hämtar Fabric Loader-profil...");
            string versionDir = Path.Combine(paths.SharedVersionsDir, target.FabricLoader.ProfileId);
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
            //    itself doesn't hash - the loader jar). Written into the launcher's shared library
            //    cache, exactly like Fabric's own installer and the launcher's own downloader do.
            foreach (var lib in loaderProfile.Libraries)
            {
                ct.ThrowIfCancellationRequested();
                string relPath = lib.ToMavenPath();
                string destPath = Path.Combine(paths.SharedLibrariesDir, relPath.Replace('/', Path.DirectorySeparatorChar));
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
                        // Already present and verified - possibly from another Fabric profile
                        // entirely, since this cache is shared. Avoid a redundant download.
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

            // 4. Fabric API - a MOD jar, so it belongs in GZ Companion's own isolated mods
            //    directory (unlike the loader itself, this is never shared launcher infrastructure).
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

            // 6. Launcher profile(s) - the only files outside our isolated directory this
            //    installer ever writes. Fabric's own GUI installer asks the player to pick ONE
            //    launcher when both profile files exist (ClientHandler.showLauncherTypeSelection);
            //    we deliberately diverge for this zero-knowledge friend installer and update
            //    every existing one instead, so the profile appears in whichever launcher the
            //    player actually opens without them needing to know or guess which "type" they
            //    have. No technical corruption risk was found in doing this - the two files are
            //    fully independent, each backed up and merged on its own. Every other profile and
            //    every field this editor doesn't own keeps its value and structure (never regex).
            Emit("Uppdaterar Minecraft Launcher-profil...");
            var spec = new GzCompanionProfileSpec(ProfileId, ProfileName, paths.GzCompanionGameDir, loaderProfile.Id, IconBase64: null);
            foreach (var (profilePath, root) in parsedProfilesByPath)
            {
                if (!dryRun)
                {
                    LauncherProfilesEditor.BackupIfExists(profilePath, _deps.Clock());
                    JsonObject updated = LauncherProfilesEditor.UpsertGzCompanionProfile(root, spec, _deps.Clock());
                    AtomicFileWriter.WriteAllTextAtomically(profilePath, LauncherProfilesEditor.Serialize(updated));
                }
                Record("launcher-profile", Path.GetFileName(profilePath));
            }

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
    /// The SAFE UPDATE FAST PATH for <c>--apply-update</c> (see <see cref="FastPathDecision.CanUseFastPath"/>):
    /// updates ONLY the files GZ Companion exclusively owns (its own mod jar, and Fabric API if its
    /// hash changed) and rewrites <c>installed.json</c> - and NOTHING else. Deliberately does NOT:
    /// check whether the Minecraft Launcher app is open (safe, since launcher_profiles.json is never
    /// touched), create the shared Fabric versions/libraries directories, contact the Fabric Meta
    /// API, seed servers.dat, or write to any launcher profile file. The caller is responsible for
    /// having already confirmed the Minecraft GAME process has exited before calling this.
    ///
    /// <para><b>This is a genuine transaction, not merely staged-then-verified:</b></para>
    /// <list type="number">
    ///   <item>Ownership of the currently-active jar is derived from <paramref name="previouslyInstalled"/>
    ///     (<c>gzcompanion-&lt;installed CompanionVersion&gt;.jar</c>) - never guessed from a directory scan.</item>
    ///   <item>The new Fabric API (if needed) and the new Companion jar are BOTH staged and
    ///     hash-verified in full BEFORE anything old is touched.</item>
    ///   <item>Only then does the commit begin: the old owned jar (and old Fabric API, if it's
    ///     being replaced) are renamed to a <c>.update-backup</c> file - never deleted outright -
    ///     before the new, verified file is moved into the active place.</item>
    ///   <item>Any other stale <c>gzcompanion-*.jar</c> is removed only after the new jar is
    ///     confirmed active.</item>
    ///   <item><c>installed.json</c> is written LAST, only once every file operation above
    ///     succeeded.</item>
    ///   <item>Only once installed-state is confirmed written are the <c>.update-backup</c> files
    ///     deleted for good.</item>
    ///   <item>If ANY step from the commit phase onward throws, EVERYTHING done since backups were
    ///     created is undone: the new files are removed, the old jar/Fabric API are restored from
    ///     their backups, and installed-state is restored to its previous content (or removed if it
    ///     didn't exist before) - leaving exactly the previous working version active, never two
    ///     Companion jars and never a false success.</item>
    /// </list>
    /// </summary>
    public async Task<InstallOutcome> RunFastUpdateAsync(SupportedEntry target, InstalledState previouslyInstalled, bool dryRun, IProgress<string>? log, CancellationToken ct)
    {
        var steps = new List<InstallStepResult>();
        void Record(string step, string detail) => steps.Add(new InstallStepResult(step, true, detail));
        void Emit(string message) => log?.Report(message);
        var paths = _deps.Paths;
        var fileOps = _deps.FastUpdateFileOps ?? new RealFastUpdateFileOps();

        string ownedJarFileName = $"gzcompanion-{previouslyInstalled.CompanionVersion}.jar";
        string oldJarPath = Path.Combine(paths.GzCompanionModsDir, ownedJarFileName);
        string newJarPath = Path.Combine(paths.GzCompanionModsDir, target.CompanionJar.FileName);
        string newJarStagingPath = newJarPath + ".staging";
        string jarBackupPath = oldJarPath + ".update-backup";

        string fabricApiPath = Path.Combine(paths.GzCompanionModsDir, target.FabricApi.FileName);
        string fabricApiStagingPath = fabricApiPath + ".staging";
        string fabricApiBackupPath = fabricApiPath + ".update-backup";

        bool sameJarName = string.Equals(oldJarPath, newJarPath, StringComparison.OrdinalIgnoreCase);
        bool jarBackedUp = false;
        bool newJarPlaced = false;
        bool fabricApiNeedsReplace = false;
        bool fabricApiBackedUp = false;
        bool fabricApiPlaced = false;
        bool installedStateWritten = false;
        string? previousInstalledStateJson = null;

        try
        {
            Emit("Uppdaterar GZ Companion (snabb uppdatering - Minecraft Launcher påverkas inte)...");
            if (!dryRun)
            {
                Directory.CreateDirectory(paths.GzCompanionModsDir);
            }

            // --- STAGE everything new FIRST - nothing old is touched yet. ---
            Emit("Kontrollerar Fabric API...");
            fabricApiNeedsReplace = !(fileOps.Exists(fabricApiPath) && HashMatches(fabricApiPath, target.FabricApi.Sha256));
            if (!dryRun && fabricApiNeedsReplace)
            {
                await _deps.Downloader.DownloadToFileAsync(new Uri(target.FabricApi.DownloadUrl), fabricApiStagingPath, progress: null, ct).ConfigureAwait(false);
                Sha256.VerifyOrThrow(fabricApiStagingPath, target.FabricApi.Sha256, "Fabric API");
            }

            Emit("Hämtar ny GZ Companion-version...");
            if (!dryRun)
            {
                byte[] jarBytes = _deps.LoadEmbeddedCompanionJar();
                await File.WriteAllBytesAsync(newJarStagingPath, jarBytes, ct).ConfigureAwait(false);
                Sha256.VerifyOrThrow(newJarStagingPath, target.CompanionJar.Sha256, "GZ Companion.jar (embedded)");
            }
            Record("staged", "new files downloaded and hash-verified");

            // --- COMMIT: back up the old owned files, then swap in the new, verified ones. ---
            if (!dryRun)
            {
                Emit("Byter ut GZ Companion...");

                if (!sameJarName && fileOps.Exists(oldJarPath))
                {
                    fileOps.Move(oldJarPath, jarBackupPath);
                    jarBackedUp = true;
                }
                fileOps.Move(newJarStagingPath, newJarPath);
                newJarPlaced = true;

                if (fabricApiNeedsReplace)
                {
                    if (fileOps.Exists(fabricApiPath))
                    {
                        fileOps.Move(fabricApiPath, fabricApiBackupPath);
                        fabricApiBackedUp = true;
                    }
                    fileOps.Move(fabricApiStagingPath, fabricApiPath);
                    fabricApiPlaced = true;
                }
            }
            Record("fabric-api", target.FabricApi.FileName);
            Record("companion-jar", target.CompanionJar.FileName);

            // Remove any OTHER stale gzcompanion-*.jar - the just-backed-up old owned jar is
            // already gone from this directory listing (it was moved to its backup name above), so
            // this only ever catches a genuinely unrelated leftover. A failure here still rolls
            // back the WHOLE transaction (see catch below) - a stray extra jar must never be
            // reported as a successful, single-active-jar update.
            if (!dryRun && Directory.Exists(paths.GzCompanionModsDir))
            {
                foreach (var stale in Directory.EnumerateFiles(paths.GzCompanionModsDir, "gzcompanion-*.jar"))
                {
                    string staleName = Path.GetFileName(stale);
                    if (staleName.EndsWith(".update-backup", StringComparison.OrdinalIgnoreCase)) continue;
                    if (!string.Equals(staleName, target.CompanionJar.FileName, StringComparison.OrdinalIgnoreCase))
                    {
                        fileOps.Delete(stale);
                    }
                }
            }

            // --- installed.json is written LAST, only once every file operation above succeeded. ---
            if (!dryRun)
            {
                Directory.CreateDirectory(Path.GetDirectoryName(paths.InstalledManifestPath)!);
                if (File.Exists(paths.InstalledManifestPath))
                {
                    previousInstalledStateJson = File.ReadAllText(paths.InstalledManifestPath);
                }
                string newStateJson = InstalledStateStore.Serialize(new InstalledState(
                    target.CompanionVersion, target.MinecraftVersion, target.FabricLoaderVersion, target.FabricApiVersion,
                    _deps.Clock().ToString("O")));
                fileOps.WriteAllText(paths.InstalledManifestPath, newStateJson);
                installedStateWritten = true;
            }
            Record("installed-state", target.CompanionVersion);

            // --- Only now, after installed-state is confirmed written, discard the backups. ---
            if (!dryRun)
            {
                TryDeleteQuietly(jarBackupPath);
                TryDeleteQuietly(fabricApiBackupPath);
            }
            Record("launcher-profile-skipped", "fast path - Minecraft/Fabric Loader version unchanged, launcher_profiles.json was never opened");

            return new InstallOutcome(Success: true, DryRun: dryRun, Steps: steps, ErrorMessage: null);
        }
        catch (Exception ex)
        {
            if (!dryRun)
            {
                RollBackFastUpdate(paths, fileOps, newJarPath, newJarStagingPath, oldJarPath, jarBackupPath, jarBackedUp, newJarPlaced,
                    fabricApiPath, fabricApiStagingPath, fabricApiBackupPath, fabricApiBackedUp, fabricApiPlaced,
                    installedStateWritten, previousInstalledStateJson);
            }
            return new InstallOutcome(Success: false, DryRun: dryRun, Steps: steps, ErrorMessage: ex.Message);
        }
    }

    /// <summary>
    /// Undoes exactly what <see cref="RunFastUpdateAsync"/> had already done at the point of
    /// failure, restoring the previous working installation. Every individual restore step is
    /// best-effort (a rollback step itself must never throw and mask the real error), but each is
    /// attempted regardless of whether an earlier one failed, to restore as much as possible.
    /// </summary>
    private static void RollBackFastUpdate(
        InstallPaths paths, IFastUpdateFileOps fileOps,
        string newJarPath, string newJarStagingPath, string oldJarPath, string jarBackupPath, bool jarBackedUp, bool newJarPlaced,
        string fabricApiPath, string fabricApiStagingPath, string fabricApiBackupPath, bool fabricApiBackedUp, bool fabricApiPlaced,
        bool installedStateWritten, string? previousInstalledStateJson)
    {
        if (newJarPlaced)
        {
            TryDeleteQuietly(newJarPath);
        }
        if (jarBackedUp)
        {
            TryMoveBackQuietly(fileOps, jarBackupPath, oldJarPath);
        }
        if (fabricApiPlaced)
        {
            TryDeleteQuietly(fabricApiPath);
        }
        if (fabricApiBackedUp)
        {
            TryMoveBackQuietly(fileOps, fabricApiBackupPath, fabricApiPath);
        }
        if (installedStateWritten)
        {
            if (previousInstalledStateJson is not null)
            {
                try { File.WriteAllText(paths.InstalledManifestPath, previousInstalledStateJson); } catch { /* best-effort */ }
            }
            else
            {
                TryDeleteQuietly(paths.InstalledManifestPath);
            }
        }
        TryDeleteQuietly(newJarStagingPath);
        TryDeleteQuietly(fabricApiStagingPath);
    }

    private static void TryMoveBackQuietly(IFastUpdateFileOps fileOps, string from, string to)
    {
        try
        {
            if (fileOps.Exists(from))
            {
                fileOps.Move(from, to);
            }
        }
        catch { /* best-effort restore only - never let a failed rollback step mask the real error */ }
    }

    private static void TryDeleteQuietly(string path)
    {
        try { if (File.Exists(path)) File.Delete(path); } catch { /* best-effort cleanup only */ }
    }

    /// <summary>
    /// Removes the launcher profile from EVERY existing official profile file, and GZ Companion's
    /// own isolated files. Fabric's version JSON and libraries live in the launcher's SHARED
    /// versions/libraries directories (see <see cref="InstallPaths.DotMinecraftDir"/>) and may be
    /// used by another Fabric profile the player set up themselves, or another mod's installer -
    /// so this uses conservative ownership rules rather than deleting them outright:
    /// - The shared library cache (<see cref="InstallPaths.SharedLibrariesDir"/>) is NEVER deleted
    ///   or touched by uninstall, under any circumstance. Leaving cached jars behind is strictly
    ///   safer than risking another installation.
    /// - Our specific Fabric version directory (e.g. versions/fabric-loader-0.19.5-26.1.2/) is
    ///   only removed if we can positively confirm no OTHER profile, in EITHER existing profile
    ///   file, still references that exact version id. If neither profile file exists, or our own
    ///   installed.json can't tell us which version we installed, the version directory is left
    ///   alone rather than guessed at.
    /// Each existing profile file is backed up and rewritten independently; a missing profile
    /// file is simply skipped (never created) and does not fail the uninstall.
    /// GZ Companion's own local user data lives under the isolated game directory - callers that
    /// want "Behåll mina GZ Companion-data" must pass keepUserData=true, which preserves the
    /// config directory tree while still removing our own mods.
    /// </summary>
    public async Task<InstallOutcome> UninstallAsync(bool keepUserData, bool dryRun, IProgress<string>? log, CancellationToken ct)
    {
        await Task.Yield();
        var steps = new List<InstallStepResult>();
        var paths = _deps.Paths;
        try
        {
            if (_deps.IsLauncherRunning())
            {
                throw new LauncherRunningException();
            }

            string? ownedVersionId = InstalledStateStore.TryRead(paths.InstalledManifestPath) is { } installed
                ? $"fabric-loader-{installed.FabricLoaderVersion}-{installed.MinecraftVersion}"
                : null;

            var existingProfilePaths = paths.AllLauncherProfilePaths.Where(File.Exists).ToList();
            var parsedProfilesByPath = new Dictionary<string, JsonObject>();
            foreach (var profilePath in existingProfilePaths)
            {
                try
                {
                    parsedProfilesByPath[profilePath] = LauncherProfilesEditor.ParseAndValidateFile(profilePath);
                }
                catch (Exception)
                {
                    throw new LauncherProfileUnreadableException(Path.GetFileName(profilePath));
                }
            }

            // Safe to remove our Fabric version directory only if:
            //  - we know which version we own, AND
            //  - at least one recognized profile file exists and was actually checked, AND
            //  - no other profile in ANY of them still references it.
            // Zero existing profile files is NOT proof of safety - it means we have no evidence
            // either way, so the conservative default is to keep the directory (parsedProfilesByPath.Count == 0
            // must never make Any() vacuously "prove" nothing references it).
            bool versionStillReferenced = ownedVersionId is not null
                && parsedProfilesByPath.Values.Any(root => LauncherProfilesEditor.AnyOtherProfileUsesVersion(root, ownedVersionId, ProfileId));
            bool versionSafeToRemove = ownedVersionId is not null && parsedProfilesByPath.Count > 0 && !versionStillReferenced;

            log?.Report("Tar bort Minecraft Launcher-profil...");
            foreach (var (profilePath, root) in parsedProfilesByPath)
            {
                if (!dryRun)
                {
                    LauncherProfilesEditor.BackupIfExists(profilePath, _deps.Clock());
                    var updated = LauncherProfilesEditor.RemoveGzCompanionProfile(root, ProfileId);
                    AtomicFileWriter.WriteAllTextAtomically(profilePath, LauncherProfilesEditor.Serialize(updated));
                }
                steps.Add(new InstallStepResult("launcher-profile-removed", true, Path.GetFileName(profilePath)));
            }

            // The shared library cache is NEVER touched - see doc comment above.
            steps.Add(new InstallStepResult("shared-libraries-preserved", true, paths.SharedLibrariesDir));

            if (ownedVersionId is not null && versionSafeToRemove)
            {
                string versionDir = Path.Combine(paths.SharedVersionsDir, ownedVersionId);
                log?.Report($"Tar bort Fabric-versionen {ownedVersionId} (används inte av någon annan profil)...");
                if (!dryRun && Directory.Exists(versionDir))
                {
                    Directory.Delete(versionDir, recursive: true);
                }
                steps.Add(new InstallStepResult("fabric-version-removed", true, ownedVersionId));
            }
            else if (ownedVersionId is not null)
            {
                log?.Report($"Behåller Fabric-versionen {ownedVersionId} (används fortfarande av en annan profil eller kunde inte bekräftas säker att ta bort).");
                steps.Add(new InstallStepResult("fabric-version-kept", true, ownedVersionId));
            }

            log?.Report(keepUserData ? "Tar bort GZ Companions installationsfiler (behåller dina data)..." : "Tar bort alla GZ Companion-filer...");
            if (!dryRun && Directory.Exists(paths.GzCompanionGameDir))
            {
                if (keepUserData)
                {
                    if (Directory.Exists(paths.GzCompanionModsDir)) Directory.Delete(paths.GzCompanionModsDir, recursive: true);
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
