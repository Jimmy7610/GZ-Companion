using System.Security.Cryptography;
using System.Text.Json.Nodes;
using GZCompanion.Installer.Core;
using Xunit;

namespace GZCompanion.Installer.Tests;

internal sealed class FakeDownloader : IFileDownloader
{
    public Dictionary<string, string> TextResponses { get; } = new();
    public Dictionary<string, byte[]> FileResponses { get; } = new();
    public List<string> RequestedUrls { get; } = new();

    public Task<string> DownloadTextAsync(Uri url, CancellationToken ct)
    {
        RequestedUrls.Add(url.ToString());
        if (!TextResponses.TryGetValue(url.ToString(), out var text))
            throw new InvalidOperationException($"FakeDownloader has no text response configured for {url}");
        return Task.FromResult(text);
    }

    public Task DownloadToFileAsync(Uri url, string destinationPath, IProgress<double>? progress, CancellationToken ct)
    {
        RequestedUrls.Add(url.ToString());
        if (!FileResponses.TryGetValue(url.ToString(), out var bytes))
            throw new InvalidOperationException($"FakeDownloader has no file response configured for {url}");
        Directory.CreateDirectory(Path.GetDirectoryName(destinationPath)!);
        File.WriteAllBytes(destinationPath, bytes);
        return Task.CompletedTask;
    }
}

public class InstallEngineTests : IDisposable
{
    private readonly string _tempRoot = Directory.CreateTempSubdirectory("gzc-engine-test-").FullName;
    public void Dispose() { try { Directory.Delete(_tempRoot, recursive: true); } catch { } }

    private static string HashOf(byte[] bytes) => Convert.ToHexStringLower(SHA256.HashData(bytes));

    private const string ProfileJsonUrl = "https://meta.fabricmc.net/v2/versions/loader/26.1.2/0.19.5/profile/json";
    private const string LibraryBaseUrl = "https://maven.fabricmc.net/";
    private const string FabricApiUrl = "https://cdn.modrinth.com/data/P7dR8mSH/versions/x/fabric-api-0.155.3+26.1.2.jar";
    private const string OwnedVersionId = "fabric-loader-0.19.5-26.1.2";

    private static readonly byte[] LibraryBytes = { 10, 20, 30, 40 };
    private static readonly byte[] FabricApiBytes = { 1, 2, 3, 4, 5 };
    private static readonly byte[] CompanionJarBytes = { 9, 9, 9 };

    private bool _launcherRunning;

    private (InstallPaths paths, SupportedEntry target, FakeDownloader downloader, InstallEngine engine) Build()
    {
        var paths = new InstallPaths(Path.Combine(_tempRoot, "roaming"), Path.Combine(_tempRoot, "local"));
        Directory.CreateDirectory(Path.Combine(paths.AppDataDir, ".minecraft"));

        var downloader = new FakeDownloader();
        string loaderProfileJson = $$"""
        {
          "id": "fabric-loader-0.19.5-26.1.2",
          "inheritsFrom": "26.1.2",
          "mainClass": "net.fabricmc.loader.impl.launch.knot.KnotClient",
          "libraries": [
            { "name": "org.ow2.asm:asm:9.10.1", "url": "{{LibraryBaseUrl}}", "sha256": "{{HashOf(LibraryBytes)}}", "size": {{LibraryBytes.Length}} }
          ]
        }
        """;
        downloader.TextResponses[ProfileJsonUrl] = loaderProfileJson;
        downloader.FileResponses[LibraryBaseUrl + "org/ow2/asm/asm/9.10.1/asm-9.10.1.jar"] = LibraryBytes;
        downloader.FileResponses[FabricApiUrl] = FabricApiBytes;

        var target = new SupportedEntry(
            SupportStatus.Verified, "0.1.0-alpha.1", "26.1.2", "0.19.5", "0.155.3+26.1.2",
            new CompanionJarSpec("gzcompanion-0.1.0-alpha.1.jar", HashOf(CompanionJarBytes), CompanionJarBytes.Length, "embedded", null),
            new FabricLoaderSpec("fabric-loader-0.19.5-26.1.2", ProfileJsonUrl, null, null),
            new FabricApiSpec("fabric-api-0.155.3+26.1.2.jar", FabricApiUrl, "modrinth", HashOf(FabricApiBytes), null, FabricApiBytes.Length, null));

        var deps = new InstallEngineDependencies
        {
            Paths = paths,
            Downloader = downloader,
            LoadEmbeddedCompanionJar = () => CompanionJarBytes,
            Clock = () => DateTimeOffset.Parse("2026-09-11T12:00:00Z"),
            IsLauncherRunning = () => _launcherRunning,
        };
        return (paths, target, downloader, new InstallEngine(deps));
    }

    [Fact]
    public async Task DryRun_ChangesNothingOnDisk()
    {
        var (paths, target, _, engine) = Build();

        var outcome = await engine.RunAsync(target, dryRun: true, log: null, CancellationToken.None);

        Assert.True(outcome.Success);
        Assert.True(outcome.DryRun);
        Assert.False(Directory.Exists(paths.GzCompanionGameDir), "Dry-run must create no directories at all.");
        Assert.False(File.Exists(paths.LauncherProfilesPath), "Dry-run must never write launcher_profiles.json.");
    }

    [Fact]
    public async Task RealRun_InstallsEverythingAndVerifiesChecksums()
    {
        var (paths, target, downloader, engine) = Build();

        var outcome = await engine.RunAsync(target, dryRun: false, log: null, CancellationToken.None);

        Assert.True(outcome.Success, outcome.ErrorMessage);
        // Mods (including Fabric API, a mod jar) are isolated under GZ Companion's own game dir.
        Assert.True(File.Exists(Path.Combine(paths.GzCompanionModsDir, "gzcompanion-0.1.0-alpha.1.jar")));
        Assert.True(File.Exists(Path.Combine(paths.GzCompanionModsDir, "fabric-api-0.155.3+26.1.2.jar")));
        // Fabric's own version JSON and libraries are launcher-owned shared infrastructure -
        // confirmed against the official Fabric Installer source - and must live under the
        // launcher's own .minecraft root, NOT the isolated GZ Companion game directory.
        Assert.True(File.Exists(Path.Combine(paths.SharedLibrariesDir, "org", "ow2", "asm", "asm", "9.10.1", "asm-9.10.1.jar")));
        Assert.True(File.Exists(Path.Combine(paths.SharedVersionsDir, "fabric-loader-0.19.5-26.1.2", "fabric-loader-0.19.5-26.1.2.json")));
        Assert.True(File.Exists(paths.LauncherProfilesPath));

        var root = LauncherProfilesEditor.ParseAndValidate(File.ReadAllText(paths.LauncherProfilesPath));
        Assert.True(LauncherProfilesEditor.HasGzCompanionProfile(root, "gzcompanion-gameZone"));
        // gameDir still points at the isolated directory (mods/config/saves) - only versions/
        // libraries moved to the shared root, per the corrected architecture.
        Assert.Equal(paths.GzCompanionGameDir, root["profiles"]!["gzcompanion-gameZone"]!["gameDir"]!.GetValue<string>());
        Assert.Equal("fabric-loader-0.19.5-26.1.2", root["profiles"]!["gzcompanion-gameZone"]!["lastVersionId"]!.GetValue<string>());

        Assert.True(File.Exists(paths.InstalledManifestPath));
    }

    [Fact]
    public async Task RealRun_PreservesExistingOtherLauncherProfiles()
    {
        var (paths, target, _, engine) = Build();
        Directory.CreateDirectory(Path.GetDirectoryName(paths.LauncherProfilesPath)!);
        File.WriteAllText(paths.LauncherProfilesPath, """{"profiles":{"vanilla":{"name":"Vanilla","type":"latest-release"}},"version":3}""");

        var outcome = await engine.RunAsync(target, dryRun: false, log: null, CancellationToken.None);

        Assert.True(outcome.Success, outcome.ErrorMessage);
        var root = LauncherProfilesEditor.ParseAndValidate(File.ReadAllText(paths.LauncherProfilesPath));
        Assert.NotNull(root["profiles"]!["vanilla"]);
        Assert.Equal("Vanilla", root["profiles"]!["vanilla"]!["name"]!.GetValue<string>());
    }

    [Fact]
    public async Task RealRun_BacksUpLauncherProfilesBeforeWriting()
    {
        var (paths, target, _, engine) = Build();
        Directory.CreateDirectory(Path.GetDirectoryName(paths.LauncherProfilesPath)!);
        File.WriteAllText(paths.LauncherProfilesPath, """{"profiles":{}}""");

        await engine.RunAsync(target, dryRun: false, log: null, CancellationToken.None);

        string dir = Path.GetDirectoryName(paths.LauncherProfilesPath)!;
        Assert.Contains(Directory.GetFiles(dir), f => f.Contains(".backup-"));
    }

    [Fact]
    public async Task ChecksumMismatch_FailsAndNeverWritesLauncherProfile()
    {
        var (paths, target, downloader, engine) = Build();
        // Corrupt the fake Fabric API response so its hash no longer matches the manifest.
        downloader.FileResponses[FabricApiUrl] = new byte[] { 99, 99, 99 };

        var outcome = await engine.RunAsync(target, dryRun: false, log: null, CancellationToken.None);

        Assert.False(outcome.Success);
        Assert.Contains("Checksum mismatch", outcome.ErrorMessage);
        Assert.False(File.Exists(paths.LauncherProfilesPath), "A failed install must never reach (or partially write) the launcher profile step.");
    }

    [Fact]
    public async Task DuplicateJarPrevention_RemovesStaleOlderCompanionJarOnUpdate()
    {
        var (paths, target, _, engine) = Build();
        Directory.CreateDirectory(paths.GzCompanionModsDir);
        string staleJar = Path.Combine(paths.GzCompanionModsDir, "gzcompanion-0.0.9-alpha.jar");
        File.WriteAllText(staleJar, "old version");

        var outcome = await engine.RunAsync(target, dryRun: false, log: null, CancellationToken.None);

        Assert.True(outcome.Success, outcome.ErrorMessage);
        Assert.False(File.Exists(staleJar), "An older gzcompanion-*.jar must be removed so two versions are never loaded at once.");
        Assert.Single(Directory.GetFiles(paths.GzCompanionModsDir, "gzcompanion-*.jar"));
    }

    [Fact]
    public async Task Reinstall_NeverWipesExistingLocalUserData()
    {
        var (paths, target, _, engine) = Build();
        Directory.CreateDirectory(paths.GzCompanionConfigDir);
        string userDataFile = Path.Combine(paths.GzCompanionConfigDir, "guide-progress.json");
        File.WriteAllText(userDataFile, """{"completedSteps": 7}""");

        var outcome = await engine.RunAsync(target, dryRun: false, log: null, CancellationToken.None);

        Assert.True(outcome.Success, outcome.ErrorMessage);
        Assert.True(File.Exists(userDataFile), "Local GZ Companion user data must never be silently wiped by a (re)install.");
        Assert.Equal("""{"completedSteps": 7}""", File.ReadAllText(userDataFile));
    }

    // ------------------------------------------------------------------
    // Critical issue 2: the launcher app itself must be closed before any profile mutation.
    // ------------------------------------------------------------------

    [Fact]
    public async Task LauncherRunning_BlocksInstallBeforeAnyDownloadOrWrite()
    {
        var (paths, target, downloader, engine) = Build();
        _launcherRunning = true;

        var outcome = await engine.RunAsync(target, dryRun: false, log: null, CancellationToken.None);

        Assert.False(outcome.Success);
        Assert.Contains("Minecraft Launcher är öppen", outcome.ErrorMessage);
        Assert.Empty(downloader.RequestedUrls);
        Assert.False(Directory.Exists(paths.GzCompanionGameDir), "Nothing should be touched at all once the launcher is detected as open.");
    }

    [Fact]
    public async Task LauncherRunning_BlocksUninstall()
    {
        var (paths, target, _, engine) = Build();
        await engine.RunAsync(target, dryRun: false, log: null, CancellationToken.None);
        _launcherRunning = true;

        var outcome = await engine.UninstallAsync(keepUserData: true, dryRun: false, log: null, CancellationToken.None);

        Assert.False(outcome.Success);
        Assert.Contains("Minecraft Launcher är öppen", outcome.ErrorMessage);
        Assert.True(LauncherProfilesEditor.HasGzCompanionProfile(
            LauncherProfilesEditor.ParseAndValidate(File.ReadAllText(paths.LauncherProfilesPath)), "gzcompanion-gameZone"),
            "Nothing should be removed once the launcher is detected as open.");
    }

    [Fact]
    public async Task LauncherNotRunning_InstallProceedsNormally()
    {
        var (_, target, _, engine) = Build();
        _launcherRunning = false;

        var outcome = await engine.RunAsync(target, dryRun: false, log: null, CancellationToken.None);

        Assert.True(outcome.Success, outcome.ErrorMessage);
    }

    // ------------------------------------------------------------------
    // Uninstall: shared Fabric library/version ownership safety.
    // ------------------------------------------------------------------

    [Fact]
    public async Task Uninstall_NeverDeletesTheSharedLibrariesCache()
    {
        var (paths, target, _, engine) = Build();
        await engine.RunAsync(target, dryRun: false, log: null, CancellationToken.None);
        Assert.True(Directory.Exists(paths.SharedLibrariesDir));

        await engine.UninstallAsync(keepUserData: false, dryRun: false, log: null, CancellationToken.None);

        Assert.True(Directory.Exists(paths.SharedLibrariesDir), "The shared library cache must never be deleted by uninstall, under any circumstance.");
        Assert.True(File.Exists(Path.Combine(paths.SharedLibrariesDir, "org", "ow2", "asm", "asm", "9.10.1", "asm-9.10.1.jar")),
            "A cached library jar must survive uninstall even when nothing else claims it - leaving it cached is safer than risking another installation.");
    }

    [Fact]
    public async Task Uninstall_RemovesOwnedFabricVersionDirectoryWhenNoOtherProfileReferencesIt()
    {
        var (paths, target, _, engine) = Build();
        await engine.RunAsync(target, dryRun: false, log: null, CancellationToken.None);
        string versionDir = Path.Combine(paths.SharedVersionsDir, OwnedVersionId);
        Assert.True(Directory.Exists(versionDir));

        var outcome = await engine.UninstallAsync(keepUserData: false, dryRun: false, log: null, CancellationToken.None);

        Assert.True(outcome.Success, outcome.ErrorMessage);
        Assert.False(Directory.Exists(versionDir), "Our own exact Fabric version directory is safe to remove once no other profile references it.");
        Assert.Contains(outcome.Steps, s => s.Step == "fabric-version-removed");
    }

    [Fact]
    public async Task Uninstall_KeepsOwnedFabricVersionDirectoryWhenAnotherProfileStillReferencesIt()
    {
        var (paths, target, _, engine) = Build();
        await engine.RunAsync(target, dryRun: false, log: null, CancellationToken.None);
        string versionDir = Path.Combine(paths.SharedVersionsDir, OwnedVersionId);

        // Simulate the player having a second, unrelated profile that happens to use the exact
        // same Fabric loader/Minecraft version combination.
        var root = LauncherProfilesEditor.ParseAndValidate(File.ReadAllText(paths.LauncherProfilesPath));
        var withOther = LauncherProfilesEditor.UpsertGzCompanionProfile(root,
            new GzCompanionProfileSpec("my-other-fabric-profile", "My Other Fabric Profile", @"C:\Users\test\.minecraft", OwnedVersionId, null),
            DateTimeOffset.UtcNow);
        File.WriteAllText(paths.LauncherProfilesPath, LauncherProfilesEditor.Serialize(withOther));

        var outcome = await engine.UninstallAsync(keepUserData: false, dryRun: false, log: null, CancellationToken.None);

        Assert.True(outcome.Success, outcome.ErrorMessage);
        Assert.True(Directory.Exists(versionDir), "A Fabric version directory still referenced by another profile must never be deleted.");
        Assert.Contains(outcome.Steps, s => s.Step == "fabric-version-kept");
        Assert.True(LauncherProfilesEditor.HasGzCompanionProfile(
            LauncherProfilesEditor.ParseAndValidate(File.ReadAllText(paths.LauncherProfilesPath)), "my-other-fabric-profile"));
    }

    [Fact]
    public async Task Uninstall_LeavesFabricVersionDirectoryAloneWhenOwnershipCannotBeConfirmed()
    {
        // No installed.json (e.g. a foreign/pre-existing install this installer didn't create) -
        // must never guess which version directory is "ours" to delete.
        var (paths, target, _, engine) = Build();
        Directory.CreateDirectory(paths.GzCompanionGameDir);
        Directory.CreateDirectory(Path.GetDirectoryName(paths.LauncherProfilesPath)!);
        string versionDir = Path.Combine(paths.SharedVersionsDir, OwnedVersionId);
        Directory.CreateDirectory(versionDir);
        File.WriteAllText(Path.Combine(versionDir, OwnedVersionId + ".json"), "{}");
        File.WriteAllText(paths.LauncherProfilesPath, """{"profiles":{}}""");

        var outcome = await engine.UninstallAsync(keepUserData: false, dryRun: false, log: null, CancellationToken.None);

        Assert.True(outcome.Success, outcome.ErrorMessage);
        Assert.True(Directory.Exists(versionDir), "Without installed.json telling us which version we own, never guess and delete.");
        Assert.DoesNotContain(outcome.Steps, s => s.Step is "fabric-version-removed" or "fabric-version-kept");
    }

    [Fact]
    public async Task DryRunUninstall_NeverRemovesTheFabricVersionDirectoryEitherWayEvenWhenSafe()
    {
        var (paths, target, _, engine) = Build();
        await engine.RunAsync(target, dryRun: false, log: null, CancellationToken.None);
        string versionDir = Path.Combine(paths.SharedVersionsDir, OwnedVersionId);

        var outcome = await engine.UninstallAsync(keepUserData: false, dryRun: true, log: null, CancellationToken.None);

        Assert.True(outcome.Success);
        Assert.True(Directory.Exists(versionDir), "Dry-run must change nothing, even a directory it determined would be safe to remove.");
    }

    [Fact]
    public async Task Uninstall_KeepUserData_RemovesModsButPreservesConfig()
    {
        var (paths, target, _, engine) = Build();
        await engine.RunAsync(target, dryRun: false, log: null, CancellationToken.None);
        string userDataFile = Path.Combine(paths.GzCompanionConfigDir, "settings.json");
        File.WriteAllText(userDataFile, "{}");

        var outcome = await engine.UninstallAsync(keepUserData: true, dryRun: false, log: null, CancellationToken.None);

        Assert.True(outcome.Success, outcome.ErrorMessage);
        Assert.False(Directory.Exists(paths.GzCompanionModsDir));
        Assert.True(File.Exists(userDataFile), "keepUserData=true must preserve the config directory.");
        Assert.False(LauncherProfilesEditor.HasGzCompanionProfile(
            LauncherProfilesEditor.ParseAndValidate(File.ReadAllText(paths.LauncherProfilesPath)), "gzcompanion-gameZone"));
    }

    [Fact]
    public async Task Uninstall_WithoutKeepUserData_RemovesIsolatedGameDirEntirely()
    {
        var (paths, target, _, engine) = Build();
        await engine.RunAsync(target, dryRun: false, log: null, CancellationToken.None);

        var outcome = await engine.UninstallAsync(keepUserData: false, dryRun: false, log: null, CancellationToken.None);

        Assert.True(outcome.Success, outcome.ErrorMessage);
        Assert.False(Directory.Exists(paths.GzCompanionGameDir));
        // The shared launcher infrastructure is a separate concern from the isolated game dir -
        // covered by the dedicated shared-library/version tests above.
    }

    [Fact]
    public async Task Uninstall_NeverTouchesUnrelatedLauncherProfiles()
    {
        var (paths, target, _, engine) = Build();
        await engine.RunAsync(target, dryRun: false, log: null, CancellationToken.None);
        var beforeRoot = LauncherProfilesEditor.ParseAndValidate(File.ReadAllText(paths.LauncherProfilesPath));
        var updated = LauncherProfilesEditor.UpsertGzCompanionProfile(beforeRoot, new GzCompanionProfileSpec("someone-elses-modpack", "Someone Else's Modpack", @"D:\Other\Dir", "forge-1.20.1", null), DateTimeOffset.UtcNow);
        File.WriteAllText(paths.LauncherProfilesPath, LauncherProfilesEditor.Serialize(updated));

        await engine.UninstallAsync(keepUserData: true, dryRun: false, log: null, CancellationToken.None);

        var afterRoot = LauncherProfilesEditor.ParseAndValidate(File.ReadAllText(paths.LauncherProfilesPath));
        Assert.True(LauncherProfilesEditor.HasGzCompanionProfile(afterRoot, "someone-elses-modpack"));
        Assert.False(LauncherProfilesEditor.HasGzCompanionProfile(afterRoot, "gzcompanion-gameZone"));
    }

    [Fact]
    public async Task DryRunUninstall_ChangesNothing()
    {
        var (paths, target, _, engine) = Build();
        await engine.RunAsync(target, dryRun: false, log: null, CancellationToken.None);

        var outcome = await engine.UninstallAsync(keepUserData: true, dryRun: true, log: null, CancellationToken.None);

        Assert.True(outcome.Success);
        Assert.True(Directory.Exists(paths.GzCompanionGameDir), "Dry-run uninstall must not remove anything.");
        Assert.True(LauncherProfilesEditor.HasGzCompanionProfile(
            LauncherProfilesEditor.ParseAndValidate(File.ReadAllText(paths.LauncherProfilesPath)), "gzcompanion-gameZone"));
    }

    [Fact]
    public async Task MismatchedFabricLoaderProfileId_IsRejected()
    {
        var (paths, target, downloader, engine) = Build();
        downloader.TextResponses[ProfileJsonUrl] = """
        {
          "id": "fabric-loader-9.9.9-99.9.9",
          "inheritsFrom": "99.9.9",
          "mainClass": "x",
          "libraries": []
        }
        """;

        var outcome = await engine.RunAsync(target, dryRun: false, log: null, CancellationToken.None);

        Assert.False(outcome.Success);
        Assert.Contains("mismatched profile", outcome.ErrorMessage, StringComparison.OrdinalIgnoreCase);
    }

    [Fact]
    public async Task InstalledFilesystemLayout_MatchesOfficialLauncherFabricRequirements()
    {
        // Validates the corrected architecture end-to-end: Fabric's version JSON/libraries under
        // the launcher's OWN root, mods isolated in GZ Companion's own game dir, and the profile
        // pointing lastVersionId/gameDir at exactly those two locations respectively - the shape
        // Fabric's official installer itself produces (minus the isolated gameDir, which is a
        // legitimate, separate launcher feature Fabric's installer simply doesn't use).
        var (paths, target, _, engine) = Build();

        var outcome = await engine.RunAsync(target, dryRun: false, log: null, CancellationToken.None);
        Assert.True(outcome.Success, outcome.ErrorMessage);

        // Fabric version JSON lives at <.minecraft>/versions/<id>/<id>.json.
        string versionJson = Path.Combine(paths.SharedVersionsDir, OwnedVersionId, OwnedVersionId + ".json");
        Assert.True(File.Exists(versionJson));
        Assert.StartsWith(paths.DotMinecraftDir, versionJson);

        // Fabric libraries live at <.minecraft>/libraries/<maven path>.
        string libraryJar = Path.Combine(paths.SharedLibrariesDir, "org", "ow2", "asm", "asm", "9.10.1", "asm-9.10.1.jar");
        Assert.True(File.Exists(libraryJar));
        Assert.StartsWith(paths.DotMinecraftDir, libraryJar);

        // Mods (Fabric API + GZ Companion) live in the ISOLATED game dir, not under .minecraft.
        string modsDir = paths.GzCompanionModsDir;
        Assert.True(Directory.Exists(modsDir));
        Assert.DoesNotContain(paths.DotMinecraftDir, modsDir);
        Assert.Equal(2, Directory.GetFiles(modsDir, "*.jar").Length);

        // The profile's lastVersionId matches the shared version directory name exactly, and
        // gameDir points at the isolated directory - never at .minecraft itself.
        var root = LauncherProfilesEditor.ParseAndValidate(File.ReadAllText(paths.LauncherProfilesPath));
        var profile = root["profiles"]!["gzcompanion-gameZone"]!;
        string lastVersionId = profile["lastVersionId"]!.GetValue<string>();
        Assert.True(Directory.Exists(Path.Combine(paths.SharedVersionsDir, lastVersionId)));
        Assert.Equal(paths.GzCompanionGameDir, profile["gameDir"]!.GetValue<string>());
        Assert.NotEqual(paths.DotMinecraftDir, profile["gameDir"]!.GetValue<string>());
    }
}
