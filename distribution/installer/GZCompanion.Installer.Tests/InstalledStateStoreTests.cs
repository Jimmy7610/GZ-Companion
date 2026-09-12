using GZCompanion.Installer.Core;
using Xunit;

namespace GZCompanion.Installer.Tests;

/// <summary>
/// <see cref="InstalledState"/> schema v2 added explicit file-ownership tracking
/// (<see cref="InstalledState.CompanionJarFileName"/>, <see cref="InstalledState.FabricApiFileName"/>)
/// on top of the original schema v1 shape written by 0.1.0-alpha.2's original release. These tests
/// cover the store's own read/write round-trip and, most importantly, that a genuine schema v1
/// document (missing both new fields and schemaVersion entirely) still parses without throwing -
/// see FabricApiOwnershipTests for how callers then treat that "ownership not confidently known"
/// case conservatively.
/// </summary>
public class InstalledStateStoreTests : IDisposable
{
    private readonly string _tempRoot = Directory.CreateTempSubdirectory("gzc-installedstate-test-").FullName;
    public void Dispose() { try { Directory.Delete(_tempRoot, recursive: true); } catch { } }

    [Fact]
    public void RoundTrip_PreservesOwnershipFields()
    {
        string path = Path.Combine(_tempRoot, "installed.json");
        var state = new InstalledState(
            "0.1.0-alpha.2", "26.1.2", "0.19.5", "0.155.3+26.1.2", "2026-09-12T12:00:00Z",
            SchemaVersion: 2, CompanionJarFileName: "gzcompanion-0.1.0-alpha.2.jar", FabricApiFileName: "fabric-api-0.155.3+26.1.2.jar");

        InstalledStateStore.Write(path, state);
        var read = InstalledStateStore.TryRead(path);

        Assert.NotNull(read);
        Assert.Equal(2, read!.SchemaVersion);
        Assert.Equal("gzcompanion-0.1.0-alpha.2.jar", read.CompanionJarFileName);
        Assert.Equal("fabric-api-0.155.3+26.1.2.jar", read.FabricApiFileName);
    }

    [Fact]
    public void LegacySchemaV1Document_ParsesSafelyWithNullOwnershipFields()
    {
        // A genuine 0.1.0-alpha.2-original-release installed.json - written before schema v2
        // existed, so it has none of the new fields and no schemaVersion at all.
        string path = Path.Combine(_tempRoot, "installed.json");
        File.WriteAllText(path, """
        {
          "companionVersion": "0.1.0-alpha.2",
          "minecraftVersion": "26.1.2",
          "fabricLoaderVersion": "0.19.5",
          "fabricApiVersion": "0.155.3+26.1.2",
          "installedAtUtc": "2026-09-01T00:00:00Z"
        }
        """);

        var read = InstalledStateStore.TryRead(path);

        Assert.NotNull(read);
        Assert.Equal("0.1.0-alpha.2", read!.CompanionVersion);
        Assert.Equal(1, read.SchemaVersion); // defaults to 1 when the field is absent
        Assert.Null(read.CompanionJarFileName); // "not confidently known" - never guessed here
        Assert.Null(read.FabricApiFileName);
    }

    [Fact]
    public void MissingFile_ReturnsNullRatherThanThrowing()
    {
        var read = InstalledStateStore.TryRead(Path.Combine(_tempRoot, "does-not-exist.json"));
        Assert.Null(read);
    }

    [Fact]
    public void CorruptJson_ReturnsNullRatherThanThrowing()
    {
        string path = Path.Combine(_tempRoot, "installed.json");
        File.WriteAllText(path, "{ not valid json");

        var read = InstalledStateStore.TryRead(path);

        Assert.Null(read);
    }
}
