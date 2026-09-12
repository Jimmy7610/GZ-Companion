namespace GZCompanion.Installer.Core;

/// <summary>
/// Pure, testable "wait for a specific process to exit" logic for <c>--apply-update</c> mode -
/// the process probe and the sleep action are both injected so tests never actually sleep or spawn
/// real processes. The real entry point (<see cref="WaitForRealProcessExit"/>) uses
/// <see cref="System.Diagnostics.Process.GetProcessById"/>, which throws
/// <see cref="ArgumentException"/> for a PID that no longer exists - exactly the "already exited"
/// signal this needs.
/// </summary>
public static class PidWaiter
{
    /// <summary>
    /// Polls <paramref name="isStillRunning"/> up to <paramref name="maxPolls"/> times, calling
    /// <paramref name="delay"/> between polls. Returns true the moment the process is reported
    /// exited; false if it never did within the poll budget (the caller decides what "timed out"
    /// means - this never fails safe by pretending exit at all).
    /// </summary>
    public static bool WaitForExit(int pid, Func<int, bool> isStillRunning, Action delay, int maxPolls)
    {
        for (int i = 0; i < maxPolls; i++)
        {
            if (!isStillRunning(pid))
            {
                return true;
            }
            delay();
        }
        return !isStillRunning(pid);
    }

    /// <summary>Real "is this PID still running" probe - a PID that no longer exists reports NOT running (already exited).</summary>
    public static bool IsProcessRunning(int pid)
    {
        try
        {
            using var process = System.Diagnostics.Process.GetProcessById(pid);
            return !process.HasExited;
        }
        catch (ArgumentException)
        {
            return false; // no such process - it has exited (or never existed)
        }
        catch (InvalidOperationException)
        {
            return false; // process has already exited and its handle was released
        }
    }
}

/// <summary>
/// Decides whether an update can take the SAFE FAST PATH (Minecraft Launcher may stay open,
/// launcher_profiles.json is never touched) or must fall back to the FULL installer behavior.
/// Pure and side-effect free - given only the previously-installed state and the new update's
/// target requirements, never touches the filesystem itself.
/// </summary>
public static class FastPathDecision
{
    /// <summary>
    /// The fast path is safe ONLY when we have positive, confident knowledge (a readable previous
    /// <see cref="InstalledState"/>) that BOTH the Minecraft version and the Fabric Loader version
    /// are unchanged - anything else (no prior state at all, or either version changing) falls back
    /// to the full installer path, which is always safe. This is a deliberately conservative
    /// decision: "unknown" always means "not fast", never "assume it's fine."
    /// </summary>
    public static bool CanUseFastPath(InstalledState? previouslyInstalled, SupportedEntry target)
    {
        if (previouslyInstalled is null) return false;
        return string.Equals(previouslyInstalled.MinecraftVersion, target.MinecraftVersion, StringComparison.Ordinal)
            && string.Equals(previouslyInstalled.FabricLoaderVersion, target.FabricLoaderVersion, StringComparison.Ordinal);
    }
}
