namespace GZCompanion.Installer.Core;

/// <summary>
/// Every filesystem location the installer touches, computed from a small set of injectable
/// roots rather than <c>Environment.GetFolderPath</c> directly - so tests can point at a temp
/// directory instead of the real user profile, and so path handling (spaces, non-ASCII) is
/// exercised the same way in tests as in production.
/// </summary>
public sealed class InstallPaths
{
    /// <summary>Usually %APPDATA% (Roaming). Vanilla/Fabric convention: the official launcher's own data lives at &lt;appData&gt;\.minecraft.</summary>
    public string AppDataDir { get; }

    /// <summary>Usually %LOCALAPPDATA%. GZ Companion's isolated install lives entirely under here.</summary>
    public string LocalAppDataDir { get; }

    public InstallPaths(string appDataDir, string localAppDataDir)
    {
        AppDataDir = appDataDir ?? throw new ArgumentNullException(nameof(appDataDir));
        LocalAppDataDir = localAppDataDir ?? throw new ArgumentNullException(nameof(localAppDataDir));
    }

    public static InstallPaths FromEnvironment() => new(
        Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData),
        Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData));

    /// <summary>The official Minecraft Launcher's own data directory - never written to except launcher_profiles.json.</summary>
    public string DotMinecraftDir => Path.Combine(AppDataDir, ".minecraft");

    public string LauncherProfilesPath => Path.Combine(DotMinecraftDir, "launcher_profiles.json");

    /// <summary>
    /// GZ Companion's fully isolated game directory - never shared with the player's real/other
    /// modded .minecraft instance. Chosen per-user, no admin rights required.
    /// </summary>
    public string GzCompanionRootDir => Path.Combine(LocalAppDataDir, "GZ Companion");

    public string GzCompanionGameDir => Path.Combine(GzCompanionRootDir, "minecraft");

    public string GzCompanionModsDir => Path.Combine(GzCompanionGameDir, "mods");

    public string GzCompanionVersionsDir => Path.Combine(GzCompanionGameDir, "versions");

    public string GzCompanionLibrariesDir => Path.Combine(GzCompanionGameDir, "libraries");

    /// <summary>GZ Companion's own local user data (Guide/Settlement/chest/notes/settings) - never wiped on reinstall/update.</summary>
    public string GzCompanionConfigDir => Path.Combine(GzCompanionGameDir, "config", "gzcompanion");

    /// <summary>Where the installer keeps its own bookkeeping (install manifest, logs, backups) - never mixed into the game directory itself.</summary>
    public string GzCompanionInstallerStateDir => Path.Combine(GzCompanionRootDir, "installer");

    public string InstalledManifestPath => Path.Combine(GzCompanionInstallerStateDir, "installed.json");

    public string LogFilePath => Path.Combine(GzCompanionInstallerStateDir, "install.log");
}
