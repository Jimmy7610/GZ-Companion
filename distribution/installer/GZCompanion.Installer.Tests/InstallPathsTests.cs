using GZCompanion.Installer.Core;
using Xunit;

namespace GZCompanion.Installer.Tests;

public class InstallPathsTests
{
    [Fact]
    public void ComposesExpectedIsolatedGameDir()
    {
        var paths = new InstallPaths(@"C:\Users\test\AppData\Roaming", @"C:\Users\test\AppData\Local");
        Assert.Equal(@"C:\Users\test\AppData\Local\GZ Companion\minecraft", paths.GzCompanionGameDir);
        Assert.Equal(@"C:\Users\test\AppData\Roaming\.minecraft", paths.DotMinecraftDir);
        Assert.Equal(@"C:\Users\test\AppData\Roaming\.minecraft\launcher_profiles.json", paths.Win32LauncherProfilesPath);
        Assert.Equal(@"C:\Users\test\AppData\Roaming\.minecraft\launcher_profiles_microsoft_store.json", paths.MicrosoftStoreLauncherProfilesPath);
        Assert.Equal(new[] { paths.Win32LauncherProfilesPath, paths.MicrosoftStoreLauncherProfilesPath }, paths.AllLauncherProfilePaths);
    }

    [Fact]
    public void IsolatedGameDirsAreAllUnderTheGzCompanionRoot()
    {
        var paths = new InstallPaths(@"C:\a", @"C:\b");
        foreach (var dir in new[] { paths.GzCompanionModsDir, paths.GzCompanionConfigDir, paths.GzCompanionInstallerStateDir })
        {
            Assert.StartsWith(paths.GzCompanionRootDir, dir);
        }
        // Never touches the vanilla launcher's own directory.
        Assert.DoesNotContain(paths.DotMinecraftDir, paths.GzCompanionGameDir);
    }

    [Fact]
    public void SharedFabricInfrastructureLivesUnderDotMinecraftNotTheIsolatedGameDir()
    {
        // Confirmed against the official Fabric Installer source: versions/libraries are always
        // resolved from the launcher's own root, never from a profile's custom gameDir.
        var paths = new InstallPaths(@"C:\a", @"C:\b");
        Assert.StartsWith(paths.DotMinecraftDir, paths.SharedVersionsDir);
        Assert.StartsWith(paths.DotMinecraftDir, paths.SharedLibrariesDir);
        Assert.DoesNotContain(paths.GzCompanionRootDir, paths.SharedVersionsDir);
        Assert.DoesNotContain(paths.GzCompanionRootDir, paths.SharedLibrariesDir);
    }

    [Theory]
    [InlineData(@"C:\Users\Jimmy Eliasson\AppData\Local")]      // space
    [InlineData(@"C:\Users\Åsa Öberg\AppData\Local")]           // non-ASCII
    [InlineData(@"C:\Users\J\AppData\Local (x86)")]              // parentheses
    public void HandlesSpacesAndNonAsciiInRoots(string localAppData)
    {
        var paths = new InstallPaths(@"C:\Users\test\AppData\Roaming", localAppData);
        string gameDir = paths.GzCompanionGameDir;
        Assert.StartsWith(localAppData, gameDir);
        Assert.EndsWith(@"GZ Companion\minecraft", gameDir);
    }

    [Fact]
    public void RejectsNullRoots()
    {
        Assert.Throws<ArgumentNullException>(() => new InstallPaths(null!, @"C:\b"));
        Assert.Throws<ArgumentNullException>(() => new InstallPaths(@"C:\a", null!));
    }
}
