using GZCompanion.Installer.Core;
using Xunit;

namespace GZCompanion.Installer.Tests;

public class DiagnosticsBuilderTests
{
    private static DiagnosticsInfo SampleInfo(string? errorMessage = null) => new(
        InstallerVersion: "0.1.0-alpha.1",
        WindowsVersion: "Windows 11",
        DetectedDotMinecraftDir: @"C:\Users\JimmyEliasson\AppData\Roaming\.minecraft",
        DetectedGameDir: @"C:\Users\JimmyEliasson\AppData\Local\GZ Companion\minecraft",
        MinecraftVersion: "26.1.2",
        FabricLoaderVersion: "0.19.5",
        FabricApiVersion: "0.155.3+26.1.2",
        CompanionVersion: "0.1.0-alpha.1",
        Step: "fabric-api",
        Status: "ok",
        ErrorCode: null,
        ErrorMessage: errorMessage);

    [Fact]
    public void RedactsUsernameFromPaths()
    {
        string text = DiagnosticsBuilder.Build(SampleInfo());
        Assert.DoesNotContain("JimmyEliasson", text);
        Assert.Contains(@"Users\<user>", text);
    }

    [Fact]
    public void RedactsUsernameInsideFreeTextErrorMessages()
    {
        string text = DiagnosticsBuilder.Build(SampleInfo(errorMessage: @"Could not write to C:\Users\JimmyEliasson\AppData\Local\GZ Companion\minecraft\mods"));
        Assert.DoesNotContain("JimmyEliasson", text);
    }

    [Fact]
    public void RedactUserName_LeavesNonUserPathsAlone()
    {
        Assert.Equal(@"C:\Program Files\Minecraft Launcher\launcher.exe", DiagnosticsBuilder.RedactUserName(@"C:\Program Files\Minecraft Launcher\launcher.exe"));
    }

    [Fact]
    public void NeverContainsCommonSecretFieldNames()
    {
        string text = DiagnosticsBuilder.Build(SampleInfo());
        foreach (var forbidden in new[] { "access_token", "refresh_token", "password", "clientToken", "session" })
        {
            Assert.DoesNotContain(forbidden, text, StringComparison.OrdinalIgnoreCase);
        }
    }

    [Fact]
    public void IncludesTheExpectedVersionAndStatusFields()
    {
        string text = DiagnosticsBuilder.Build(SampleInfo());
        Assert.Contains("0.1.0-alpha.1", text);
        Assert.Contains("26.1.2", text);
        Assert.Contains("0.19.5", text);
        Assert.Contains("fabric-api", text);
    }
}
