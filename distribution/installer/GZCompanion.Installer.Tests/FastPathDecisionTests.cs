using GZCompanion.Installer.Core;
using Xunit;

namespace GZCompanion.Installer.Tests;

public class FastPathDecisionTests
{
    private static SupportedEntry Target(string mc = "26.1.2", string loader = "0.19.5") => new(
        SupportStatus.Verified, "0.1.0-alpha.3", mc, loader, "0.155.3+26.1.2",
        new CompanionJarSpec("gzcompanion-0.1.0-alpha.3.jar", "a".PadRight(64, 'a'), 100, "embedded", null),
        new FabricLoaderSpec($"fabric-loader-{loader}-{mc}", "https://example.invalid/profile.json", null, null),
        new FabricApiSpec("fabric-api-0.155.3+26.1.2.jar", "https://example.invalid/fabric-api.jar", "modrinth", "b".PadRight(64, 'b'), null, 200, null));

    [Fact]
    public void SameMinecraftAndLoaderVersion_UsesFastPath()
    {
        var installed = new InstalledState("0.1.0-alpha.2", "26.1.2", "0.19.5", "0.155.3+26.1.2", "2026-09-12T00:00:00Z");
        Assert.True(FastPathDecision.CanUseFastPath(installed, Target()));
    }

    [Fact]
    public void DifferentMinecraftVersion_RequiresFullPath()
    {
        var installed = new InstalledState("0.1.0-alpha.2", "25.0.0", "0.19.5", "0.155.3+26.1.2", "2026-09-12T00:00:00Z");
        Assert.False(FastPathDecision.CanUseFastPath(installed, Target()));
    }

    [Fact]
    public void DifferentFabricLoaderVersion_RequiresFullPath()
    {
        var installed = new InstalledState("0.1.0-alpha.2", "26.1.2", "0.20.0", "0.155.3+26.1.2", "2026-09-12T00:00:00Z");
        Assert.False(FastPathDecision.CanUseFastPath(installed, Target()));
    }

    [Fact]
    public void NoPreviousInstalledState_RequiresFullPath()
    {
        Assert.False(FastPathDecision.CanUseFastPath(null, Target()));
    }

    [Fact]
    public void DifferentFabricApiVersionAlone_StillUsesFastPath()
    {
        // Fabric API changing alone does not require launcher_profiles.json changes - only
        // Minecraft/Fabric Loader version changes do, since those are what the launcher profile's
        // lastVersionId (a shared, launcher-owned concept) actually encodes.
        var installed = new InstalledState("0.1.0-alpha.2", "26.1.2", "0.19.5", "0.140.0+26.1.2", "2026-09-12T00:00:00Z");
        Assert.True(FastPathDecision.CanUseFastPath(installed, Target()));
    }

    [Fact]
    public void Alpha12On26_1_2_UpgradingToTheReal26_3Manifest_RequiresFullPath()
    {
        var dir = new DirectoryInfo(AppContext.BaseDirectory);
        while (dir is not null && !dir.EnumerateFiles("GZCompanion.Installer.slnx").Any()) dir = dir.Parent;
        Assert.NotNull(dir);
        var target = CompatibilityManifest.Parse(File.ReadAllText(Path.Combine(dir!.FullName, "..", "compatibility.json"))).FindInstallTarget();
        Assert.NotNull(target);

        // What a player's installed.json says after installing the published v0.1.0-alpha.12.
        var installed = new InstalledState("0.1.0-alpha.12", "26.1.2", "0.19.5", "0.155.3+26.1.2", "2026-10-19T00:00:00Z");
        Assert.False(FastPathDecision.CanUseFastPath(installed, target!),
            "Moving to a new Minecraft version must reinstall the Fabric profile (full path), even though the loader version is unchanged.");
    }
}
