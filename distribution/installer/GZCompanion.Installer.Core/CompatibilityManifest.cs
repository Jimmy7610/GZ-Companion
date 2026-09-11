using System.Text.Json;
using System.Text.Json.Serialization;

namespace GZCompanion.Installer.Core;

/// <summary>
/// Compatibility status for one supported combination, mirroring distribution/compatibility.json.
/// The installer must never install anything not present here with status VERIFIED - see
/// CompatibilityManifest.Policy for the exact rule.
/// </summary>
public enum SupportStatus
{
    /// <summary>Built, real-launcher QA tested, and the only status this installer will actually install.</summary>
    Verified,

    /// <summary>Reserved for future releases believed to work but not yet fully QA-verified.</summary>
    Compatible,

    /// <summary>Reserved for future releases explicitly known not to work - the installer must refuse these.</summary>
    Unsupported,
}

public sealed record CompanionJarSpec(string FileName, string Sha256, long SizeBytes, string Source, string? Note);

public sealed record FabricLoaderFallback(string MavenCoordinate, string FileName, string Sha256, long SizeBytes);

public sealed record FabricLoaderSpec(string ProfileId, string ProfileJsonUrl, string? Note, FabricLoaderFallback? KnownGoodFallback);

public sealed record FabricApiSpec(string FileName, string DownloadUrl, string Source, string Sha256, string? Sha512, long SizeBytes, string? Note);

public sealed record SupportedEntry(
    [property: JsonConverter(typeof(JsonStringEnumConverter))] SupportStatus Status,
    string CompanionVersion,
    string MinecraftVersion,
    string FabricLoaderVersion,
    string FabricApiVersion,
    CompanionJarSpec CompanionJar,
    FabricLoaderSpec FabricLoader,
    FabricApiSpec FabricApi);

public sealed record CompatibilityManifest(int SchemaVersion, string GeneratedAt, List<SupportedEntry> Supported)
{
    private static readonly JsonSerializerOptions JsonOptions = new(JsonSerializerDefaults.Web)
    {
        PropertyNameCaseInsensitive = true,
    };

    /// <summary>
    /// Parses distribution/compatibility.json. Throws <see cref="ManifestParseException"/> rather
    /// than letting a malformed manifest silently produce a half-populated object - an installer
    /// must never guess at compatibility data.
    /// </summary>
    public static CompatibilityManifest Parse(string json)
    {
        try
        {
            var manifest = JsonSerializer.Deserialize<CompatibilityManifest>(json, JsonOptions);
            if (manifest is null || manifest.Supported is null)
            {
                throw new ManifestParseException("Manifest deserialized to null or has no 'supported' list.");
            }
            return manifest;
        }
        catch (JsonException ex)
        {
            throw new ManifestParseException($"Manifest is not valid JSON in the expected shape: {ex.Message}", ex);
        }
    }

    /// <summary>
    /// Finds the entry for an exact Minecraft version, if any. Returns null (never a guessed
    /// nearest-match) when the version is absent from the manifest entirely.
    /// </summary>
    public SupportedEntry? FindByMinecraftVersion(string minecraftVersion)
        => Supported.FirstOrDefault(e => string.Equals(e.MinecraftVersion, minecraftVersion, StringComparison.Ordinal));

    /// <summary>
    /// The installer must never install a version that is not VERIFIED. Missing-entirely and
    /// present-but-UNSUPPORTED/COMPATIBLE are both "not installable" - only VERIFIED passes.
    /// </summary>
    public bool IsInstallable(string minecraftVersion)
        => FindByMinecraftVersion(minecraftVersion) is { Status: SupportStatus.Verified };
}

public sealed class ManifestParseException : Exception
{
    public ManifestParseException(string message) : base(message) { }
    public ManifestParseException(string message, Exception inner) : base(message, inner) { }
}
