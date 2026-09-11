using GZCompanion.Installer.Core;
using Xunit;

namespace GZCompanion.Installer.Tests;

public class CompatibilityManifestTests
{
    private const string SampleJson = """
    {
      "schemaVersion": 1,
      "generatedAt": "2026-09-11T00:00:00Z",
      "supported": [
        {
          "status": "VERIFIED",
          "companionVersion": "0.1.0-alpha.1",
          "minecraftVersion": "26.1.2",
          "fabricLoaderVersion": "0.19.5",
          "fabricApiVersion": "0.155.3+26.1.2",
          "companionJar": { "fileName": "gzcompanion-0.1.0-alpha.1.jar", "sha256": "abc123", "sizeBytes": 100, "source": "embedded", "note": null },
          "fabricLoader": { "profileId": "fabric-loader-0.19.5-26.1.2", "profileJsonUrl": "https://meta.fabricmc.net/v2/versions/loader/26.1.2/0.19.5/profile/json", "note": null, "knownGoodFallback": null },
          "fabricApi": { "fileName": "fabric-api-0.155.3+26.1.2.jar", "downloadUrl": "https://cdn.modrinth.com/x.jar", "source": "modrinth", "sha256": "def456", "sha512": null, "sizeBytes": 200, "note": null }
        },
        {
          "status": "UNSUPPORTED",
          "companionVersion": "0.2.0",
          "minecraftVersion": "27.0.0",
          "fabricLoaderVersion": "0.20.0",
          "fabricApiVersion": "0.160.0",
          "companionJar": { "fileName": "x.jar", "sha256": "x", "sizeBytes": 1, "source": "embedded", "note": null },
          "fabricLoader": { "profileId": "x", "profileJsonUrl": "https://meta.fabricmc.net/x", "note": null, "knownGoodFallback": null },
          "fabricApi": { "fileName": "x.jar", "downloadUrl": "https://cdn.modrinth.com/x.jar", "source": "modrinth", "sha256": "x", "sha512": null, "sizeBytes": 1, "note": null }
        }
      ]
    }
    """;

    [Fact]
    public void ParsesRealManifestShape()
    {
        var manifest = CompatibilityManifest.Parse(SampleJson);
        Assert.Equal(2, manifest.Supported.Count);
        Assert.Equal(SupportStatus.Verified, manifest.Supported[0].Status);
    }

    [Fact]
    public void VerifiedVersionIsInstallable()
    {
        var manifest = CompatibilityManifest.Parse(SampleJson);
        Assert.True(manifest.IsInstallable("26.1.2"));
    }

    [Fact]
    public void UnsupportedVersionIsNeverInstallable()
    {
        var manifest = CompatibilityManifest.Parse(SampleJson);
        Assert.False(manifest.IsInstallable("27.0.0"));
    }

    [Fact]
    public void MissingVersionIsNeverInstallable()
    {
        var manifest = CompatibilityManifest.Parse(SampleJson);
        Assert.False(manifest.IsInstallable("1.20.1"), "A version entirely absent from the manifest must never be silently treated as installable.");
        Assert.Null(manifest.FindByMinecraftVersion("1.20.1"));
    }

    [Fact]
    public void MalformedJsonThrowsManifestParseException()
    {
        Assert.Throws<ManifestParseException>(() => CompatibilityManifest.Parse("{ not json"));
    }

    [Fact]
    public void EmptySupportedListThrowsRatherThanNullReference()
    {
        Assert.Throws<ManifestParseException>(() => CompatibilityManifest.Parse("{}"));
    }
}
