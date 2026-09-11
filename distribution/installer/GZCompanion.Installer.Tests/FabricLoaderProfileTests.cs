using GZCompanion.Installer.Core;
using Xunit;

namespace GZCompanion.Installer.Tests;

public class FabricLoaderProfileTests
{
    // A trimmed real response from https://meta.fabricmc.net/v2/versions/loader/26.1.2/0.19.5/profile/json
    private const string RealSampleJson = """
    {
      "id": "fabric-loader-0.19.5-26.1.2",
      "inheritsFrom": "26.1.2",
      "mainClass": "net.fabricmc.loader.impl.launch.knot.KnotClient",
      "libraries": [
        { "name": "org.ow2.asm:asm:9.10.1", "url": "https://maven.fabricmc.net/", "sha256": "ed825d10ab1399c8c0cb669e688cf0c8c82629b4c8399b58352b68e92ca10fc", "size": 126151 },
        { "name": "net.fabricmc:fabric-loader:0.19.5", "url": "https://maven.fabricmc.net/" }
      ]
    }
    """;

    [Fact]
    public void ParsesRealFabricMetaApiResponseShape()
    {
        var profile = FabricLoaderProfile.Parse(RealSampleJson);
        Assert.Equal("fabric-loader-0.19.5-26.1.2", profile.Id);
        Assert.Equal("26.1.2", profile.InheritsFrom);
        Assert.Equal(2, profile.Libraries.Count);
    }

    [Fact]
    public void MavenCoordinateConvertsToExpectedRelativePath()
    {
        var lib = new FabricLoaderLibrary("org.ow2.asm:asm:9.10.1", "https://maven.fabricmc.net/", "hash", 126151);
        Assert.Equal("org/ow2/asm/asm/9.10.1/asm-9.10.1.jar", lib.ToMavenPath());
    }

    [Fact]
    public void MavenCoordinateHandlesMultiSegmentGroupIds()
    {
        var lib = new FabricLoaderLibrary("net.fabricmc:fabric-loader:0.19.5", "https://maven.fabricmc.net/", null, null);
        Assert.Equal("net/fabricmc/fabric-loader/0.19.5/fabric-loader-0.19.5.jar", lib.ToMavenPath());
    }

    [Fact]
    public void LibraryWithoutAHash_HasNullSha256()
    {
        var profile = FabricLoaderProfile.Parse(RealSampleJson);
        var loaderLib = profile.Libraries.Single(l => l.Name == "net.fabricmc:fabric-loader:0.19.5");
        Assert.Null(loaderLib.Sha256);
    }

    [Fact]
    public void MalformedCoordinateThrowsRatherThanProducingAWrongPath()
    {
        var lib = new FabricLoaderLibrary("not-a-valid-coordinate", "https://maven.fabricmc.net/", null, null);
        Assert.Throws<FormatException>(() => lib.ToMavenPath());
    }

    [Fact]
    public void MissingRequiredFieldsThrowsManifestParseException()
    {
        Assert.Throws<ManifestParseException>(() => FabricLoaderProfile.Parse("{}"));
    }
}
