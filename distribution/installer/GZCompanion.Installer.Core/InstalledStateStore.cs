using System.Text.Json;
using System.Text.Json.Nodes;

namespace GZCompanion.Installer.Core;

/// <summary>
/// GZ Companion's own small install-state file (installed.json, inside the installer's private
/// state directory - never inside the game directory itself). Tracks what this installer put in
/// place, purely so reinstall/update/uninstall can act safely without re-deriving guesses. This
/// is NOT launcher_profiles.json and carries none of that file's compatibility constraints.
/// </summary>
public sealed record InstalledState(string CompanionVersion, string MinecraftVersion, string FabricLoaderVersion, string FabricApiVersion, string InstalledAtUtc);

public static class InstalledStateStore
{
    private static readonly JsonSerializerOptions WriteOptions = new(JsonSerializerDefaults.Web) { WriteIndented = true };

    public static void Write(string path, InstalledState state)
    {
        Directory.CreateDirectory(Path.GetDirectoryName(path)!);
        string json = JsonSerializer.Serialize(state, WriteOptions);
        AtomicFileWriter.WriteAllTextAtomically(path, json);
    }

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
