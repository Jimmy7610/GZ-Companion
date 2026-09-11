using GZCompanion.Installer.Core;
using Xunit;

namespace GZCompanion.Installer.Tests;

/// <summary>
/// Independently re-verifies what build-installer.ps1 already hard-fails the build on: the exact
/// jar embedded into the installer must match distribution/compatibility.json's pinned
/// companionJar hash/size. build-installer.ps1 runs this test suite AFTER copying the mod jar
/// into GZCompanion.Installer.App/Assets and BEFORE publishing, so this is a genuine second,
/// independent gate - not just documentation of the PowerShell check.
///
/// On a fresh checkout (before build-installer.ps1 has ever run), Assets/gzcompanion.jar does not
/// exist yet - that is a normal, expected state (not a broken build), so this test passes-through
/// rather than failing when the asset is simply absent.
/// </summary>
public class EmbeddedResourceIntegrityTests
{
    private static string? FindSolutionRoot()
    {
        var dir = new DirectoryInfo(AppContext.BaseDirectory);
        while (dir is not null)
        {
            if (dir.EnumerateFiles("GZCompanion.Installer.slnx").Any())
            {
                return dir.FullName;
            }
            dir = dir.Parent;
        }
        return null;
    }

    [Fact]
    public void EmbeddedCompanionJar_MatchesCompatibilityManifestHashAndSize()
    {
        string? solutionRoot = FindSolutionRoot();
        Assert.NotNull(solutionRoot); // The test project always lives under the solution - this finding null indicates a broken test-run layout, not a missing asset.

        string assetPath = Path.Combine(solutionRoot!, "GZCompanion.Installer.App", "Assets", "gzcompanion.jar");
        if (!File.Exists(assetPath))
        {
            // build-installer.ps1 hasn't run yet in this checkout - nothing to verify.
            return;
        }

        string manifestPath = Path.Combine(solutionRoot!, "..", "compatibility.json");
        Assert.True(File.Exists(manifestPath), $"compatibility.json not found at expected location: {manifestPath}");

        var manifest = CompatibilityManifest.Parse(File.ReadAllText(manifestPath));
        var verified = manifest.Supported.First(e => e.Status == SupportStatus.Verified);

        long actualSize = new FileInfo(assetPath).Length;
        string actualHash = Sha256.ComputeFileHashHex(assetPath);

        Assert.Equal(verified.CompanionJar.SizeBytes, actualSize);
        Assert.Equal(verified.CompanionJar.Sha256, actualHash, StringComparer.OrdinalIgnoreCase);
    }
}
