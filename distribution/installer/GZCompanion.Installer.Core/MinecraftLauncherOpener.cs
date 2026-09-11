namespace GZCompanion.Installer.Core;

public enum LauncherCandidateKind
{
    /// <summary>A standalone/legacy installation - <see cref="LauncherCandidate.LaunchTarget"/> is a full path to an executable.</summary>
    Win32,

    /// <summary>A Microsoft Store/packaged installation - <see cref="LauncherCandidate.LaunchTarget"/> is an AUMID ("PackageFamilyName!AppId").</summary>
    Packaged,
}

/// <summary>
/// One installed application discovery found that MIGHT be the official Minecraft Launcher.
/// Discovery makes no judgement about which candidate actually is the launcher - that's
/// <see cref="MinecraftLauncherResolver"/>'s job, matching on <see cref="DisplayName"/> alone, so a
/// completely unrelated app (or Mojang's newer "Minecraft for Windows" hub/Bedrock app, which is a
/// SEPARATE package from the actual Java Edition-capable "Minecraft Launcher") is never launched by
/// mistake just because it happened to be discovered.
/// </summary>
public sealed record LauncherCandidate(LauncherCandidateKind Kind, string DisplayName, string LaunchTarget);

/// <summary>Finds every installed application that could plausibly be the official Minecraft Launcher - real implementation in <see cref="WindowsInstalledLauncherDiscovery"/>.</summary>
public interface IInstalledLauncherDiscovery
{
    IReadOnlyList<LauncherCandidate> DiscoverCandidates();
}

/// <summary>Actually launches one resolved candidate - real implementation in <see cref="WindowsLauncherActivator"/>.</summary>
public interface ILauncherActivator
{
    void Launch(LauncherCandidate candidate);
}

/// <summary>
/// Pure selection logic - given whatever <see cref="IInstalledLauncherDiscovery"/> found, decides
/// which single candidate (if any) is safe to launch as "the" official Minecraft Launcher.
/// Deliberately an exact, case-sensitive match against the literal display name Mojang/Microsoft
/// publish for the real launcher app, confirmed against a real machine (see
/// EnvironmentDetection.IsMinecraftLauncherRunning's doc comment - the same app reports this exact
/// window title too). A generic "Minecraft" or "Minecraft for Windows" app is a DIFFERENT product
/// (the Bedrock/hub app) and must never match.
/// </summary>
public static class MinecraftLauncherResolver
{
    public const string ExpectedDisplayName = "Minecraft Launcher";

    /// <summary>
    /// Returns the one candidate to launch, or null if none qualify - callers must never fall back
    /// to launching anything else (e.g. a minecraft:// URI, which on a real machine was confirmed to
    /// open the wrong app entirely) when this returns null.
    /// </summary>
    public static LauncherCandidate? Resolve(IReadOnlyList<LauncherCandidate> candidates)
    {
        var matches = candidates
            .Where(c => string.Equals(c.DisplayName, ExpectedDisplayName, StringComparison.Ordinal))
            // Deterministic tie-break when both a Win32 and a packaged "Minecraft Launcher" are
            // installed at once: prefer Win32 - it starts directly via its own executable, with no
            // shell/COM activation involved. Enum declaration order (Win32 = 0) makes this the
            // natural sort key; a further ordinal sort on the launch target keeps the choice fully
            // deterministic even with multiple candidates of the same kind.
            .OrderBy(c => c.Kind)
            .ThenBy(c => c.LaunchTarget, StringComparer.Ordinal)
            .ToList();
        return matches.Count > 0 ? matches[0] : null;
    }
}

public sealed record LauncherOpenResult(bool Success, string? UserMessageIfFailed);

/// <summary>
/// Orchestrates discovery -> resolution -> launch for the "ÖPPNA MINECRAFT LAUNCHER" button.
/// Never falls back to a minecraft:// URI (confirmed on a real machine to open Mojang's newer
/// "Minecraft for Windows" hub app instead of the actual Java Edition-capable launcher that shows
/// our installed profile) - if no safe target can be resolved, or launching it fails for any
/// reason, this reports a clean Swedish failure message instead.
/// </summary>
public sealed class MinecraftLauncherOpener
{
    public const string FallbackMessage = "Kunde inte öppna Minecraft Launcher automatiskt. Öppna \"Minecraft Launcher\" från Start-menyn.";

    private readonly IInstalledLauncherDiscovery _discovery;
    private readonly ILauncherActivator _activator;

    public MinecraftLauncherOpener(IInstalledLauncherDiscovery discovery, ILauncherActivator activator)
    {
        _discovery = discovery;
        _activator = activator;
    }

    public LauncherOpenResult TryOpen()
    {
        IReadOnlyList<LauncherCandidate> candidates;
        try
        {
            candidates = _discovery.DiscoverCandidates();
        }
        catch
        {
            return new LauncherOpenResult(false, FallbackMessage);
        }

        var chosen = MinecraftLauncherResolver.Resolve(candidates);
        if (chosen is null)
        {
            return new LauncherOpenResult(false, FallbackMessage);
        }

        try
        {
            _activator.Launch(chosen);
            return new LauncherOpenResult(true, null);
        }
        catch
        {
            return new LauncherOpenResult(false, FallbackMessage);
        }
    }
}
