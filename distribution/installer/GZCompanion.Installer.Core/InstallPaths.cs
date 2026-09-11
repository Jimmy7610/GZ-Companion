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

    /// <summary>
    /// The official Minecraft Launcher's own data/install directory. Confirmed against the
    /// official Fabric Installer's own source (FabricMC/fabric-installer, Apache-2.0):
    /// <c>ClientInstaller.install(Path mcDir, ...)</c> writes the Fabric version JSON to
    /// <c>mcDir/versions/&lt;id&gt;/&lt;id&gt;.json</c> and every library to
    /// <c>mcDir/libraries/...</c>, and <c>ProfileInstaller</c> never sets a <c>gameDir</c> on the
    /// profile it creates at all - Fabric's own installer always resolves versions/libraries from
    /// this same root the launcher itself uses, never from a profile's custom game directory. A
    /// profile's <c>gameDir</c> only relocates the game's OWN working directory (mods/config/
    /// saves/logs) - the launcher resolves <c>lastVersionId</c> and its libraries from here
    /// regardless of what <c>gameDir</c> says. This is why Fabric versions/libraries are shared,
    /// launcher-owned infrastructure and must never be bulk-deleted on uninstall (see
    /// <see cref="InstallEngine.UninstallAsync"/>).
    /// </summary>
    public string DotMinecraftDir => Path.Combine(AppDataDir, ".minecraft");

    /// <summary>
    /// The official Minecraft Launcher supports TWO independent profile files, confirmed against
    /// <c>ProfileInstaller.LauncherType</c> in the official Fabric Installer source:
    /// <c>WIN32("launcher_profiles.json")</c> for the standalone/legacy launcher, and
    /// <c>MICROSOFT_STORE("launcher_profiles_microsoft_store.json")</c> for the Microsoft
    /// Store/Xbox app launcher. A machine only has the launcher(s) it actually installed
    /// initialized, so only whichever of these files already exists is ever touched - see
    /// <see cref="AllLauncherProfilePaths"/> and <see cref="EnvironmentDetection.CheckLauncher"/>.
    /// </summary>
    public string Win32LauncherProfilesPath => Path.Combine(DotMinecraftDir, "launcher_profiles.json");

    /// <summary>See <see cref="Win32LauncherProfilesPath"/>.</summary>
    public string MicrosoftStoreLauncherProfilesPath => Path.Combine(DotMinecraftDir, "launcher_profiles_microsoft_store.json");

    /// <summary>Both recognized official profile file paths, whether or not they currently exist - callers filter to existing ones themselves (see <see cref="EnvironmentDetection.CheckLauncher"/>).</summary>
    public IReadOnlyList<string> AllLauncherProfilePaths => new[] { Win32LauncherProfilesPath, MicrosoftStoreLauncherProfilesPath };

    /// <summary>Shared with every other Fabric/vanilla profile on this machine - the launcher's own version store. Never bulk-deleted; see uninstall.</summary>
    public string SharedVersionsDir => Path.Combine(DotMinecraftDir, "versions");

    /// <summary>Shared with every other Fabric/vanilla profile on this machine - the launcher's own library cache. Never deleted by this installer under any circumstance.</summary>
    public string SharedLibrariesDir => Path.Combine(DotMinecraftDir, "libraries");

    /// <summary>
    /// GZ Companion's fully isolated GAME directory - mods/config/saves/logs only, never shared
    /// with the player's real/other modded .minecraft instance. Chosen per-user, no admin rights
    /// required. Does NOT host Fabric's version JSON or libraries - see <see cref="DotMinecraftDir"/>.
    /// </summary>
    public string GzCompanionRootDir => Path.Combine(LocalAppDataDir, "GZ Companion");

    public string GzCompanionGameDir => Path.Combine(GzCompanionRootDir, "minecraft");

    public string GzCompanionModsDir => Path.Combine(GzCompanionGameDir, "mods");

    /// <summary>GZ Companion's own local user data (Guide/Settlement/chest/notes/settings) - never wiped on reinstall/update.</summary>
    public string GzCompanionConfigDir => Path.Combine(GzCompanionGameDir, "config", "gzcompanion");

    /// <summary>Where the installer keeps its own bookkeeping (install manifest, logs, backups) - never mixed into the game directory itself.</summary>
    public string GzCompanionInstallerStateDir => Path.Combine(GzCompanionRootDir, "installer");

    public string InstalledManifestPath => Path.Combine(GzCompanionInstallerStateDir, "installed.json");

    public string LogFilePath => Path.Combine(GzCompanionInstallerStateDir, "install.log");
}
