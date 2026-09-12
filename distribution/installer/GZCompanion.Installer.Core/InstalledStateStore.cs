using System.Text.Json;
using System.Text.Json.Nodes;

namespace GZCompanion.Installer.Core;

/// <summary>
/// GZ Companion's own small install-state file (installed.json, inside the installer's private
/// state directory - never inside the game directory itself). Tracks what this installer put in
/// place, purely so reinstall/update/uninstall can act safely without re-deriving guesses. This
/// is NOT launcher_profiles.json and carries none of that file's compatibility constraints.
///
/// <para><see cref="SchemaVersion"/> 2 adds explicit file-ownership tracking
/// (<see cref="CompanionJarFileName"/>, <see cref="FabricApiFileName"/>) - a schema 1 document
/// (written by 0.1.0-alpha.2's original release) has neither field and deserializes with them
/// null, which every reader treats as "ownership of that specific file is NOT confidently known"
/// rather than guessing a filename from a naming convention. This is what lets
/// <see cref="InstallEngine.RunFastUpdateAsync"/> safely retire a Fabric API jar whose filename
/// changed between versions: it only ever acts on <see cref="FabricApiFileName"/> when a previous
/// run actually recorded it, never on a guessed "fabric-api-&lt;version&gt;.jar" pattern.</para>
/// </summary>
public sealed record InstalledState(
    string CompanionVersion,
    string MinecraftVersion,
    string FabricLoaderVersion,
    string FabricApiVersion,
    string InstalledAtUtc,
    int SchemaVersion = 1,
    string? CompanionJarFileName = null,
    string? FabricApiFileName = null);

public static class InstalledStateStore
{
    private static readonly JsonSerializerOptions WriteOptions = new(JsonSerializerDefaults.Web) { WriteIndented = true };

    public static void Write(string path, InstalledState state)
    {
        Directory.CreateDirectory(Path.GetDirectoryName(path)!);
        AtomicFileWriter.WriteAllTextAtomically(path, Serialize(state));
    }

    /// <summary>Exposed separately so callers that need an injectable write seam (see <see cref="IFastUpdateFileOps"/>) can serialize here and write through their own seam.</summary>
    public static string Serialize(InstalledState state) => JsonSerializer.Serialize(state, WriteOptions);

    public static InstalledState? TryRead(string path)
    {
        try
        {
            string json = File.ReadAllText(path);
            return JsonSerializer.Deserialize<InstalledState>(json, WriteOptions);
        }
        catch
        {
            // A corrupt or foreign installed.json must never crash detection - treat it as "we
            // don't confidently know what's here" rather than guessing.
            return null;
        }
    }

    public static string? TryReadCompanionVersion(string path)
    {
        try
        {
            var node = JsonNode.Parse(File.ReadAllText(path));
            return node?["companionVersion"]?.GetValue<string>();
        }
        catch
        {
            return null;
        }
    }
}
