using System.Text.Json.Nodes;
using GZCompanion.Installer.Core;
using Xunit;

namespace GZCompanion.Installer.Tests;

public class LauncherProfilesEditorTests : IDisposable
{
    private readonly string _tempRoot = Directory.CreateTempSubdirectory("gzc-profiles-test-").FullName;
    public void Dispose() { try { Directory.Delete(_tempRoot, recursive: true); } catch { } }

    private const string RealisticProfilesJson = """
    {
      "profiles": {
        "release-1.20.1": {
          "name": "Vanilla 1.20.1",
          "type": "latest-release",
          "created": "2024-01-01T00:00:00.000Z",
          "lastUsed": "2024-06-01T00:00:00.000Z",
          "icon": "Grass",
          "someUnknownFutureField": { "nested": [1, 2, 3] }
        },
        "my-forge-modpack": {
          "name": "My Forge Modpack",
          "type": "custom",
          "created": "2024-02-01T00:00:00.000Z",
          "lastUsed": "2024-06-02T00:00:00.000Z",
          "gameDir": "D:\\Games\\MyModpack",
          "lastVersionId": "forge-47.2.0",
          "javaArgs": "-Xmx4G"
        }
      },
      "settings": { "enableSnapshots": false, "someLauncherSetting": true },
      "version": 3,
      "clientToken": "some-opaque-token-we-must-never-touch"
    }
    """;

    [Fact]
    public void ParsesRealisticLauncherProfilesFile()
    {
        var root = LauncherProfilesEditor.ParseAndValidate(RealisticProfilesJson);
        Assert.NotNull(root["profiles"]);
        Assert.Equal(2, root["profiles"]!.AsObject().Count);
    }

    [Fact]
    public void RejectsMalformedJson()
    {
        Assert.Throws<UnsupportedLauncherProfileSchemaException>(() => LauncherProfilesEditor.ParseAndValidate("{ not json"));
    }

    [Fact]
    public void RejectsJsonWithoutProfilesObject_AbortsSafelyOnUnknownSchema()
    {
        Assert.Throws<UnsupportedLauncherProfileSchemaException>(() => LauncherProfilesEditor.ParseAndValidate("""{"somethingElse": true}"""));
        Assert.Throws<UnsupportedLauncherProfileSchemaException>(() => LauncherProfilesEditor.ParseAndValidate("[1,2,3]"));
    }

    [Fact]
    public void UpsertAddsNewProfileWithoutTouchingExistingOnes()
    {
        var root = LauncherProfilesEditor.ParseAndValidate(RealisticProfilesJson);
        var spec = new GzCompanionProfileSpec("gzcompanion-gameZone", "GZ Companion - GameZone", @"C:\Users\test\AppData\Local\GZ Companion\minecraft", "fabric-loader-0.19.5-26.1.2", null);
        var updated = LauncherProfilesEditor.UpsertGzCompanionProfile(root, spec, DateTimeOffset.Parse("2026-09-11T00:00:00Z"));

        Assert.Equal(3, updated["profiles"]!.AsObject().Count);
        var otherIds = LauncherProfilesEditor.OtherProfileIds(updated, spec.ProfileId);
        Assert.Contains("release-1.20.1", otherIds);
        Assert.Contains("my-forge-modpack", otherIds);

        // The pre-existing profiles must keep the same value and structure - including a field
        // this editor has never heard of. (Re-serializing the JSON tree doesn't guarantee
        // byte-identical whitespace/formatting, only unchanged values/keys/nesting.)
        Assert.Equal("Grass", updated["profiles"]!["release-1.20.1"]!["icon"]!.GetValue<string>());
        Assert.Equal(3, updated["profiles"]!["release-1.20.1"]!["someUnknownFutureField"]!["nested"]!.AsArray().Count);
        Assert.Equal("-Xmx4G", updated["profiles"]!["my-forge-modpack"]!["javaArgs"]!.GetValue<string>());

        // Unrelated top-level fields (including an opaque token) are preserved untouched too.
        Assert.Equal("some-opaque-token-we-must-never-touch", updated["clientToken"]!.GetValue<string>());
        Assert.True(updated["settings"]!["someLauncherSetting"]!.GetValue<bool>());
    }

    [Fact]
    public void UpsertSetsExpectedFieldsOnTheGzCompanionProfile()
    {
        var root = LauncherProfilesEditor.ParseAndValidate(RealisticProfilesJson);
        var spec = new GzCompanionProfileSpec("gzcompanion-gameZone", "GZ Companion - GameZone", @"C:\iso\minecraft", "fabric-loader-0.19.5-26.1.2", null);
        var updated = LauncherProfilesEditor.UpsertGzCompanionProfile(root, spec, DateTimeOffset.Parse("2026-09-11T00:00:00Z"));

        var profile = updated["profiles"]!["gzcompanion-gameZone"]!;
        Assert.Equal("GZ Companion - GameZone", profile["name"]!.GetValue<string>());
        Assert.Equal("custom", profile["type"]!.GetValue<string>());
        Assert.Equal(@"C:\iso\minecraft", profile["gameDir"]!.GetValue<string>());
        Assert.Equal("fabric-loader-0.19.5-26.1.2", profile["lastVersionId"]!.GetValue<string>());
    }

    [Fact]
    public void ReUpsertPreservesOriginalCreatedTimestampButBumpsLastUsed()
    {
        var root = LauncherProfilesEditor.ParseAndValidate(RealisticProfilesJson);
        var spec = new GzCompanionProfileSpec("gzcompanion-gameZone", "GZ Companion - GameZone", @"C:\iso\minecraft", "fabric-loader-0.19.5-26.1.2", null);
        var firstInstall = LauncherProfilesEditor.UpsertGzCompanionProfile(root, spec, DateTimeOffset.Parse("2026-01-01T00:00:00Z"));
        var reinstall = LauncherProfilesEditor.UpsertGzCompanionProfile(firstInstall, spec, DateTimeOffset.Parse("2026-09-11T00:00:00Z"));

        Assert.Equal("2026-01-01T00:00:00.000Z", reinstall["profiles"]!["gzcompanion-gameZone"]!["created"]!.GetValue<string>());
        Assert.Equal("2026-09-11T00:00:00.000Z", reinstall["profiles"]!["gzcompanion-gameZone"]!["lastUsed"]!.GetValue<string>());
    }

    [Fact]
    public void RemoveDeletesOnlyTheGzCompanionProfile()
    {
        var root = LauncherProfilesEditor.ParseAndValidate(RealisticProfilesJson);
        var spec = new GzCompanionProfileSpec("gzcompanion-gameZone", "GZ Companion - GameZone", @"C:\iso\minecraft", "fabric-loader-0.19.5-26.1.2", null);
        var withOurs = LauncherProfilesEditor.UpsertGzCompanionProfile(root, spec, DateTimeOffset.UtcNow);

        var removed = LauncherProfilesEditor.RemoveGzCompanionProfile(withOurs, spec.ProfileId);

        Assert.False(LauncherProfilesEditor.HasGzCompanionProfile(removed, spec.ProfileId));
        Assert.Equal(2, removed["profiles"]!.AsObject().Count);
        Assert.NotNull(removed["profiles"]!["release-1.20.1"]);
        Assert.NotNull(removed["profiles"]!["my-forge-modpack"]);
    }

    [Fact]
    public void RemoveIsANoOpWhenProfileAlreadyAbsent()
    {
        var root = LauncherProfilesEditor.ParseAndValidate(RealisticProfilesJson);
        var removed = LauncherProfilesEditor.RemoveGzCompanionProfile(root, "gzcompanion-gameZone");
        Assert.Equal(2, removed["profiles"]!.AsObject().Count);
    }

    [Fact]
    public void NewEmptyDocumentIsValidForABrandNewLauncherInstall()
    {
        var doc = LauncherProfilesEditor.NewEmptyDocument();
        var spec = new GzCompanionProfileSpec("gzcompanion-gameZone", "GZ Companion - GameZone", @"C:\iso\minecraft", "fabric-loader-0.19.5-26.1.2", null);
        var updated = LauncherProfilesEditor.UpsertGzCompanionProfile(doc, spec, DateTimeOffset.UtcNow);
        Assert.Single(updated["profiles"]!.AsObject());
    }

    [Fact]
    public void BackupCreatesTimestampedCopyAndLeavesOriginalUntouched()
    {
        string path = Path.Combine(_tempRoot, "launcher_profiles.json");
        File.WriteAllText(path, RealisticProfilesJson);
        var now = DateTimeOffset.Parse("2026-09-11T01:45:00Z");

        string? backupPath = LauncherProfilesEditor.BackupIfExists(path, now);

        Assert.NotNull(backupPath);
        Assert.True(File.Exists(backupPath));
        Assert.Equal(RealisticProfilesJson, File.ReadAllText(backupPath!));
        Assert.Equal(RealisticProfilesJson, File.ReadAllText(path));
        Assert.Contains("20260911-014500", backupPath);
    }

    [Fact]
    public void BackupIsNoOpWhenFileDoesNotExistYet()
    {
        string path = Path.Combine(_tempRoot, "does-not-exist.json");
        Assert.Null(LauncherProfilesEditor.BackupIfExists(path, DateTimeOffset.UtcNow));
    }

    [Fact]
    public void SerializeRoundTripsThroughParseAndValidate()
    {
        var root = LauncherProfilesEditor.ParseAndValidate(RealisticProfilesJson);
        string serialized = LauncherProfilesEditor.Serialize(root);
        var reparsed = LauncherProfilesEditor.ParseAndValidate(serialized);
        Assert.Equal(2, reparsed["profiles"]!.AsObject().Count);
    }

    [Fact]
    public void BackupTwiceWithinTheSameSecondProducesTwoDistinctFilesInsteadOfThrowing()
    {
        // Found via a real install -> reinstall -> uninstall smoke test run back-to-back: the
        // second-precision timestamp collided and File.Copy's overwrite:false threw, aborting an
        // otherwise-safe uninstall. Must never happen again.
        string path = Path.Combine(_tempRoot, "launcher_profiles.json");
        File.WriteAllText(path, RealisticProfilesJson);
        var now = DateTimeOffset.Parse("2026-09-11T08:01:54Z");

        string? first = LauncherProfilesEditor.BackupIfExists(path, now);
        string? second = LauncherProfilesEditor.BackupIfExists(path, now);

        Assert.NotNull(first);
        Assert.NotNull(second);
        Assert.NotEqual(first, second);
        Assert.True(File.Exists(first));
        Assert.True(File.Exists(second));
    }
}
