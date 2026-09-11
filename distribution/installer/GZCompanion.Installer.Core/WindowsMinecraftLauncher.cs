using System.Diagnostics;
using System.Runtime.InteropServices;
using System.Runtime.Versioning;
using System.Xml.Linq;
using Microsoft.Win32;

namespace GZCompanion.Installer.Core;

/// <summary>
/// Finds candidate "Minecraft Launcher" installations via the same registry-based mechanisms
/// Windows itself uses to enumerate installed software - no admin rights, no extra downloads, and
/// no assumption that only one install channel exists on the machine:
/// - Win32/standalone installs: the classic Uninstall registry keys (HKLM 64-bit and WOW6432Node
///   views, plus HKCU for a per-user install), matched by "DisplayName".
/// - Microsoft Store/packaged installs: the per-machine (and, defensively, per-user) AppModel
///   package repository registry, whose "Path" value gives the package's install location (read
///   to resolve the package's own AppxManifest.xml "DisplayName") and whose subkey names are
///   exactly "&lt;PackageFamilyName&gt;!&lt;AppId&gt;" - confirmed against a real machine's actual
///   Microsoft Store Minecraft Launcher install (Microsoft.4297127D64EC6_8wekyb3d8bbwe!Minecraft),
///   which is a SEPARATE package from Mojang's newer "Minecraft for Windows" hub/Bedrock app
///   (Microsoft.MinecraftUWP_8wekyb3d8bbwe!Game) - the one a bare minecraft:// URI was found to
///   open instead.
/// </summary>
[SupportedOSPlatform("windows")]
public sealed class WindowsInstalledLauncherDiscovery : IInstalledLauncherDiscovery
{
    private const string PackageRepositoryKeyPath = @"SOFTWARE\Classes\Local Settings\Software\Microsoft\Windows\CurrentVersion\AppModel\PackageRepository\Packages";

    public IReadOnlyList<LauncherCandidate> DiscoverCandidates()
    {
        var candidates = new List<LauncherCandidate>();
        candidates.AddRange(DiscoverWin32Candidates());
        candidates.AddRange(DiscoverPackagedCandidates());
        return candidates;
    }

    private static IEnumerable<LauncherCandidate> DiscoverWin32Candidates()
    {
        var results = new List<LauncherCandidate>();
        foreach (var (hive, subKeyPath) in new[]
        {
            (Registry.LocalMachine, @"SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall"),
            (Registry.LocalMachine, @"SOFTWARE\WOW6432Node\Microsoft\Windows\CurrentVersion\Uninstall"),
            (Registry.CurrentUser, @"SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall"),
        })
        {
            using var uninstallKey = hive.OpenSubKey(subKeyPath);
            if (uninstallKey is null) continue;

            foreach (var subKeyName in uninstallKey.GetSubKeyNames())
            {
                using var entry = uninstallKey.OpenSubKey(subKeyName);
                if (entry?.GetValue("DisplayName") is not string displayName) continue;

                string? exePath = ResolveWin32ExecutablePath(entry);
                if (exePath is not null)
                {
                    results.Add(new LauncherCandidate(LauncherCandidateKind.Win32, displayName, exePath));
                }
            }
        }
        return results;
    }

    private static string? ResolveWin32ExecutablePath(RegistryKey entry)
    {
        if (entry.GetValue("InstallLocation") is string installLocation
            && !string.IsNullOrWhiteSpace(installLocation)
            && Directory.Exists(installLocation))
        {
            string? exe = Directory.EnumerateFiles(installLocation, "*.exe")
                .FirstOrDefault(f => Path.GetFileNameWithoutExtension(f).Contains("MinecraftLauncher", StringComparison.OrdinalIgnoreCase));
            if (exe is not null)
            {
                return exe;
            }
        }

        // Fall back to DisplayIcon, which conventionally points straight at the app's own
        // executable (optionally with a ",<iconIndex>" suffix) even when InstallLocation is absent.
        if (entry.GetValue("DisplayIcon") is string displayIcon)
        {
            string candidate = displayIcon.Split(',')[0].Trim().Trim('"');
            if (candidate.EndsWith(".exe", StringComparison.OrdinalIgnoreCase) && File.Exists(candidate))
            {
                return candidate;
            }
        }

        return null;
    }

    private static IEnumerable<LauncherCandidate> DiscoverPackagedCandidates()
    {
        var results = new List<LauncherCandidate>();
        foreach (var hive in new[] { Registry.LocalMachine, Registry.CurrentUser })
        {
            using var packagesKey = hive.OpenSubKey(PackageRepositoryKeyPath);
            if (packagesKey is null) continue;

            foreach (var packageFullName in packagesKey.GetSubKeyNames())
            {
                using var packageKey = packagesKey.OpenSubKey(packageFullName);
                if (packageKey?.GetValue("Path") is not string installLocation) continue;

                string? displayName = TryReadPackageDisplayName(installLocation);
                if (displayName is null) continue;

                foreach (var aumidKeyName in packageKey.GetSubKeyNames())
                {
                    if (aumidKeyName.Contains('!'))
                    {
                        results.Add(new LauncherCandidate(LauncherCandidateKind.Packaged, displayName, aumidKeyName));
                    }
                }
            }
        }
        return results;
    }

    private static string? TryReadPackageDisplayName(string installLocation)
    {
        try
        {
            string manifestPath = Path.Combine(installLocation, "AppxManifest.xml");
            if (!File.Exists(manifestPath)) return null;

            var doc = XDocument.Load(manifestPath);
            XNamespace ns = doc.Root!.Name.Namespace;
            return doc.Root!.Element(ns + "Properties")?.Element(ns + "DisplayName")?.Value;
        }
        catch
        {
            // A package whose manifest can't be read for any reason is simply not a candidate -
            // never lets one bad package take down discovery of everything else.
            return null;
        }
    }
}

/// <summary>
/// Launches one resolved candidate: a Win32 candidate starts its executable directly; a packaged
/// candidate is activated by AUMID via the classic (non-WinRT) shell COM interface
/// IApplicationActivationManager - the same supported mechanism "shell:AppsFolder\...&lt;AUMID&gt;"
/// uses under the hood, without needing to spawn explorer.exe or take on a WinRT/CsWinRT
/// dependency just for this one call.
/// </summary>
[SupportedOSPlatform("windows")]
public sealed class WindowsLauncherActivator : ILauncherActivator
{
    public void Launch(LauncherCandidate candidate)
    {
        switch (candidate.Kind)
        {
            case LauncherCandidateKind.Win32:
                Process.Start(new ProcessStartInfo(candidate.LaunchTarget) { UseShellExecute = true });
                break;
            case LauncherCandidateKind.Packaged:
                ActivatePackagedApp(candidate.LaunchTarget);
                break;
            default:
                throw new ArgumentOutOfRangeException(nameof(candidate), candidate.Kind, "Unknown launcher candidate kind.");
        }
    }

    private static void ActivatePackagedApp(string appUserModelId)
    {
        var manager = (IApplicationActivationManager)new ApplicationActivationManager();
        int hr = manager.ActivateApplication(appUserModelId, string.Empty, ActivateOptions.None, out _);
        if (hr != 0)
        {
            throw new InvalidOperationException($"ActivateApplication failed for AUMID '{appUserModelId}' (HRESULT 0x{hr:X8}).");
        }
    }

    [Flags]
    private enum ActivateOptions
    {
        None = 0,
    }

    [ComImport]
    [Guid("2E941141-7F97-4756-BA1D-9DECDE894A3D")]
    [InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    private interface IApplicationActivationManager
    {
        [PreserveSig]
        int ActivateApplication(
            [MarshalAs(UnmanagedType.LPWStr)] string appUserModelId,
            [MarshalAs(UnmanagedType.LPWStr)] string arguments,
            ActivateOptions options,
            out uint processId);
    }

    [ComImport]
    [Guid("45BA127D-10A8-46EA-8AB7-56EA9078943C")]
    private class ApplicationActivationManager
    {
    }
}
