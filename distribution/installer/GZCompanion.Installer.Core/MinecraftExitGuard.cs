namespace GZCompanion.Installer.Core;

public sealed record MinecraftExitGuardResult(bool SafeToMutate, string? AbortReason);

/// <summary>
/// Decides whether it is safe to start mutating installed files. This is deliberately a SEPARATE,
/// pure decision from actually applying an update - <see cref="InstallEngine"/> is never invoked
/// unless this positively confirms it's safe.
///
/// <para>The specific Minecraft process (by PID) must have exited AND no other relevant Minecraft
/// game process may still be running - checked AGAIN after any grace-period wait, never merely
/// "we waited a while so it's probably fine by now." A previous version of this logic waited for
/// other Minecraft processes to close but never re-checked the condition afterward, so it could
/// proceed to mutate files while Minecraft was still actually running - this class exists
/// specifically to make that impossible to regress into silently.</para>
/// </summary>
public static class MinecraftExitGuard
{
    /// <param name="targetPid">The specific Minecraft process PID the update worker was told to wait for.</param>
    /// <param name="isPidRunning">Real: <see cref="PidWaiter.IsProcessRunning"/>. Injectable for tests.</param>
    /// <param name="isAnyMinecraftGameRunning">Real: <see cref="EnvironmentDetection.IsMinecraftLikelyRunning"/>. Injectable for tests.</param>
    /// <param name="delay">Real: a short sleep. A no-op in tests - this method never sleeps for real itself.</param>
    /// <param name="pidMaxPolls">Poll budget for waiting on the specific PID.</param>
    /// <param name="otherProcessMaxPolls">Grace-period poll budget for any OTHER Minecraft game process to also close.</param>
    public static MinecraftExitGuardResult WaitUntilSafeToMutate(
        int targetPid,
        Func<int, bool> isPidRunning,
        Func<bool> isAnyMinecraftGameRunning,
        Action delay,
        int pidMaxPolls,
        int otherProcessMaxPolls)
    {
        bool targetExited = PidWaiter.WaitForExit(targetPid, isPidRunning, delay, pidMaxPolls);

        if (targetExited)
        {
            // Grace period: give any OTHER Minecraft game process a chance to also close on its own.
            for (int i = 0; i < otherProcessMaxPolls && isAnyMinecraftGameRunning(); i++)
            {
                delay();
            }
        }

        // The actual fix: POSITIVELY re-check after every wait, rather than assuming the loop
        // above finishing means the condition became false. A timed-out target PID also aborts
        // here, even if isAnyMinecraftGameRunning() happens to report false.
        bool stillUnsafe = !targetExited || isAnyMinecraftGameRunning();
        if (stillUnsafe)
        {
            return new MinecraftExitGuardResult(false, "Minecraft kör fortfarande. Stäng Minecraft och försök igen.");
        }
        return new MinecraftExitGuardResult(true, null);
    }
}
