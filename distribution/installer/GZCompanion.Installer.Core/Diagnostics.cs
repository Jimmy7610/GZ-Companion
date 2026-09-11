using System.Text;
using System.Text.RegularExpressions;

namespace GZCompanion.Installer.Core;

public sealed record DiagnosticsInfo(
    string InstallerVersion,
    string WindowsVersion,
    string DetectedDotMinecraftDir,
    string DetectedGameDir,
    string MinecraftVersion,
    string FabricLoaderVersion,
    string FabricApiVersion,
    string CompanionVersion,
    string Step,
    string Status,
    string? ErrorCode,
    string? ErrorMessage);

/// <summary>
/// Builds the plain-text block behind the "Kopiera diagnostik" button. Never includes secrets
/// (auth/session tokens, passwords, chat, chest contents, player notes) because it is built ONLY
/// from the fields in <see cref="DiagnosticsInfo"/> - there is no code path that reads
/// launcher_accounts.json, chat logs, or GZ Companion's own local data files.
/// </summary>
public static class DiagnosticsBuilder
{
    public static string Build(DiagnosticsInfo info)
    {
        var sb = new StringBuilder();
        sb.AppendLine("GZ Companion installer - diagnostik");
        sb.AppendLine($"Installer: {info.InstallerVersion}");
        sb.AppendLine($"Windows: {info.WindowsVersion}");
        sb.AppendLine($"Minecraft: {info.MinecraftVersion}");
        sb.AppendLine($"Fabric Loader: {info.FabricLoaderVersion}");
        sb.AppendLine($"Fabric API: {info.FabricApiVersion}");
        sb.AppendLine($"GZ Companion: {info.CompanionVersion}");
        sb.AppendLine($".minecraft: {RedactUserName(info.DetectedDotMinecraftDir)}");
        sb.AppendLine($"Spelkatalog: {RedactUserName(info.DetectedGameDir)}");
        sb.AppendLine($"Steg: {info.Step}");
        sb.AppendLine($"Status: {info.Status}");
        if (info.ErrorCode is not null)
        {
            sb.AppendLine($"Felkod: {info.ErrorCode}");
        }
        if (info.ErrorMessage is not null)
        {
            sb.AppendLine($"Felmeddelande: {RedactUserName(info.ErrorMessage)}");
        }
        return sb.ToString();
    }

    /// <summary>
    /// Replaces the Windows username segment of an absolute path (…\Users\&lt;name&gt;\…) with
    /// "&lt;user&gt;" wherever it appears, including inside a free-text error message. Best-effort:
    /// matches the standard "Users" profile layout: not a guarantee for an unusual profile root.
    /// </summary>
    public static string RedactUserName(string text)
        => UsersPathPattern.Replace(text, m => $@"{m.Groups["prefix"].Value}Users\<user>");

    private static readonly Regex UsersPathPattern = new(
        @"(?<prefix>[A-Za-z]:\\)Users\\[^\\]+",
        RegexOptions.Compiled | RegexOptions.IgnoreCase);
}
