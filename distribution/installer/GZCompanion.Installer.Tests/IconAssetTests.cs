using Xunit;

namespace GZCompanion.Installer.Tests;

/// <summary>
/// Guards the checked-in branding asset itself (distribution/assets/gz-companion.ico) - independent
/// of GZCompanion.Installer.App.csproj's EnsureCompanionIconExists MSBuild target, which is what
/// actually fails a real build if this file goes missing. This test parses the raw ICO header
/// (ICONDIR + ICONDIRENTRY, per the Microsoft icon file format) rather than relying on
/// System.Drawing so it runs in a plain "dotnet test" without a Windows-only dependency.
/// </summary>
public class IconAssetTests
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

    private static string IconPath()
    {
        string? solutionRoot = FindSolutionRoot();
        Assert.NotNull(solutionRoot);
        return Path.Combine(solutionRoot!, "..", "assets", "gz-companion.ico");
    }

    [Fact]
    public void CompanionIcon_ExistsAtTheExpectedRepositoryPath()
    {
        Assert.True(File.Exists(IconPath()), $"Expected the GZ Companion icon at {IconPath()}.");
    }

    [Fact]
    public void CompanionIcon_IsAValidMultiResolutionIcoContainingEveryRequiredSize()
    {
        byte[] data = File.ReadAllBytes(IconPath());

        // ICONDIR: reserved(2)=0, type(2)=1 (icon), count(2).
        Assert.Equal(0, BitConverter.ToUInt16(data, 0));
        Assert.Equal(1, BitConverter.ToUInt16(data, 2));
        int count = BitConverter.ToUInt16(data, 4);

        var declaredSizes = new HashSet<int>();
        for (int i = 0; i < count; i++)
        {
            int entryOffset = 6 + (i * 16);
            int width = data[entryOffset];      // a raw byte value of 0 means 256px, per the ICO format
            int height = data[entryOffset + 1];
            Assert.Equal(width == 0 ? 256 : width, height == 0 ? 256 : height);
            declaredSizes.Add(width == 0 ? 256 : width);
        }

        foreach (int expected in new[] { 16, 24, 32, 48, 64, 128, 256 })
        {
            Assert.Contains(expected, declaredSizes);
        }
    }
}
