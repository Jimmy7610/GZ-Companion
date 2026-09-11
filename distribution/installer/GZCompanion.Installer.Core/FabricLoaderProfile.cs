using System.Text.Json.Serialization;

namespace GZCompanion.Installer.Core;

/// <summary>
/// Mirrors the JSON returned by the official Fabric Meta API
/// (https://meta.fabricmc.net/v2/versions/loader/{mcVersion}/{loaderVersion}/profile/json) -
/// this is the exact, ready-to-use launcher version profile Fabric's own installer drops into
/// &lt;gameDir&gt;/versions/&lt;id&gt;/&lt;id&gt;.json. Fetched live at install time rather than
/// baked into our manifest, so library coordinates/hashes always come from Fabric's own
/// authoritative, currently-published response.
/// </summary>
public sealed record FabricLoaderLibrary(string Name, string Url, string? Sha256, long? Size)
{
    /// <summary>Maven coordinate "group:artifact:version" -&gt; "group/with/slashes/artifact/version/artifact-version.jar".</summary>
    public string ToMavenPath()
    {
        var parts = Name.Split(':');
        if (parts.Length != 3)
        {
            throw new FormatException($"Unexpected Maven coordinate shape: '{Name}'");
        }
        string group = parts[0].Replace('.', '/');
        string artifact = parts[1];
        string version = parts[2];
        return $"{group}/{artifact}/{version}/{artifact}-{version}.jar";
    }
}

public sealed record FabricLoaderProfile(
    string Id,
    [property: JsonPropertyName("inheritsFrom")] string InheritsFrom,
    string MainClass,
    List<FabricLoaderLibrary> Libraries)
{
    public static FabricLoaderProfile Parse(string json)
    {
        var profile = System.Text.Json.JsonSerializer.Deserialize<FabricLoaderProfile>(json,
            new System.Text.Json.JsonSerializerOptions(System.Text.Json.JsonSerializerDefaults.Web));
        if (profile is null || string.IsNullOrEmpty(profile.Id) || profile.Libraries is null)
        {
            throw new ManifestParseException("Fabric loader profile JSON did not have the expected shape (id/libraries).");
        }
        return profile;
    }
}
