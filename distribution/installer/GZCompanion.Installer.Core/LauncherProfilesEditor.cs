using System.Text.Json;
using System.Text.Json.Nodes;

namespace GZCompanion.Installer.Core;

/// <summary>Thrown when launcher_profiles.json doesn't match the shape this editor knows how to handle safely.</summary>
public sealed class UnsupportedLauncherProfileSchemaException : Exception
{
    public UnsupportedLauncherProfileSchemaException(string message) : base(message) { }
}

public sealed record GzCompanionProfileSpec(string ProfileId, string Name, string GameDir, string LastVersionId, string? IconBase64);

/// <summary>
/// Safely reads, backs up, and rewrites the official launcher's launcher_profiles.json.
/// Deliberately works on the generic <see cref="JsonNode"/> tree rather than a strongly-typed
/// model of the whole file: every field this installer doesn't itself own keeps the same value
/// and structure, and only the one profile keyed by <see cref="GzCompanionProfileSpec.ProfileId"/>
/// is ever touched. Re-serializing the whole document does NOT guarantee byte-identical output
/// (whitespace/formatting can differ from whatever the launcher itself last wrote) - only that no
/// value, key, or nesting is lost, added, or reordered in a way that changes meaning. Never uses
/// regex or string surgery on the JSON text.
/// </summary>
public static class LauncherProfilesEditor
{
    private static readonly JsonSerializerOptions WriteOptions = new() { WriteIndented = true };

    /// <summary>
    /// Parses launcher_profiles.json and validates the minimum shape this editor needs
    /// (a JSON object with a "profiles" object, per the schema every known launcher version uses).
    /// Throws <see cref="UnsupportedLauncherProfileSchemaException"/> rather than guessing when
    /// that shape isn't present - the whole point is to abort safely on an unknown schema.
    /// </summary>
    public static JsonObject ParseAndValidate(string json)
    {
        JsonNode? node;
        try
        {
            node = JsonNode.Parse(json);
        }
        catch (JsonException ex)
        {
            throw new UnsupportedLauncherProfileSchemaException($"launcher_profiles.json is not valid JSON: {ex.Message}");
        }

        if (node is not JsonObject root)
        {
            throw new UnsupportedLauncherProfileSchemaException("launcher_profiles.json's top level is not a JSON object.");
        }
        if (root["profiles"] is not JsonObject)
        {
            throw new UnsupportedLauncherProfileSchemaException("launcher_profiles.json has no 'profiles' object - unrecognized schema.");
        }
        return root;
    }

    /// <summary>An empty-but-valid launcher_profiles.json, used only when the file doesn't exist yet (a brand-new launcher install).</summary>
    public static JsonObject NewEmptyDocument() => new()
    {
        ["profiles"] = new JsonObject(),
        ["settings"] = new JsonObject(),
        ["version"] = 3,
    };

    /// <summary>
    /// Inserts or updates exactly the GZ Companion profile, leaving every other key (including
    /// every other profile) untouched. Returns a NEW JsonObject (the caller decides when/whether
    /// to serialize it) so callers can inspect the result before writing anything to disk.
    /// </summary>
    public static JsonObject UpsertGzCompanionProfile(JsonObject root, GzCompanionProfileSpec spec, DateTimeOffset now)
    {
        var clone = root.DeepClone()!.AsObject();
        var profiles = clone["profiles"]!.AsObject();

        string nowIso = now.ToString("yyyy-MM-ddTHH:mm:ss.fffZ");
        bool existed = profiles[spec.ProfileId] is JsonObject;
        string createdAt = existed ? (profiles[spec.ProfileId]!["created"]?.GetValue<string>() ?? nowIso) : nowIso;

        var profile = new JsonObject
        {
            ["name"] = spec.Name,
            ["type"] = "custom",
            ["created"] = createdAt,
            ["lastUsed"] = nowIso,
            ["lastVersionId"] = spec.LastVersionId,
            ["gameDir"] = spec.GameDir,
        };
        if (spec.IconBase64 is not null)
        {
            profile["icon"] = spec.IconBase64;
        }

        profiles[spec.ProfileId] = profile;
        return clone;
    }

    /// <summary>Removes exactly the GZ Companion profile (uninstall), leaving every other profile untouched. A no-op if it's already absent.</summary>
    public static JsonObject RemoveGzCompanionProfile(JsonObject root, string profileId)
    {
        var clone = root.DeepClone()!.AsObject();
        var profiles = clone["profiles"]!.AsObject();
        profiles.Remove(profileId);
        return clone;
    }

    public static bool HasGzCompanionProfile(JsonObject root, string profileId)
        => root["profiles"] is JsonObject profiles && profiles[profileId] is JsonObject;

    /// <summary>Every OTHER profile id present, for regression tests proving nothing unrelated was touched.</summary>
    public static IReadOnlyList<string> OtherProfileIds(JsonObject root, string excludingProfileId)
        => root["profiles"] is JsonObject profiles
            ? profiles.Select(kv => kv.Key).Where(id => id != excludingProfileId).ToList()
            : new List<string>();

    /// <summary>
    /// Whether any profile OTHER than <paramref name="excludingProfileId"/> has this exact
    /// <c>lastVersionId</c>. Fabric versions/libraries live in the launcher's own shared
    /// versions/libraries directories (see <see cref="InstallPaths.DotMinecraftDir"/>), so before
    /// uninstall deletes our specific Fabric version directory, it must confirm no other profile
    /// - one the player created themselves, or another mod's installer - still points at it.
    /// </summary>
    public static bool AnyOtherProfileUsesVersion(JsonObject root, string versionId, string excludingProfileId)
    {
        if (root["profiles"] is not JsonObject profiles) return false;
        foreach (var (id, node) in profiles)
        {
            if (id == excludingProfileId) continue;
            if (node is JsonObject profile && profile["lastVersionId"]?.GetValue<string>() == versionId)
            {
                return true;
            }
        }
        return false;
    }

    public static string Serialize(JsonObject root) => root.ToJsonString(WriteOptions);

    /// <summary>
    /// Backs up the current file (if it exists) to a timestamped sibling before any write - e.g.
    /// launcher_profiles.json.backup-20260911-014500.json. Returns the backup path, or null if
    /// there was nothing to back up (first-ever install).
    /// </summary>
    public static string? BackupIfExists(string launcherProfilesPath, DateTimeOffset now)
    {
        if (!File.Exists(launcherProfilesPath)) return null;

        // The timestamp alone is only second-precision, so install -> reinstall -> uninstall run
        // back-to-back (as a smoke test, or a fast-fingered user) can request two backups within
        // the same second. Fall back to a numeric suffix rather than letting File.Copy's
        // overwrite:false throw and abort an otherwise-safe operation.
        string basePath = $"{launcherProfilesPath}.backup-{now:yyyyMMdd-HHmmss}";
        string backupPath = $"{basePath}.json";
        for (int suffix = 2; File.Exists(backupPath); suffix++)
        {
            backupPath = $"{basePath}-{suffix}.json";
        }
        File.Copy(launcherProfilesPath, backupPath, overwrite: false);
        return backupPath;
    }
}
