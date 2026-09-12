using GZCompanion.Installer.Core;
using Xunit;

namespace GZCompanion.Installer.Tests;

/// <summary>
/// Pure logic - every probe and the delay action are fakes, so these never sleep or touch a real
/// process. Covers the exact bug this class was introduced to fix: a previous version waited for
/// other Minecraft processes to close but never re-checked the condition afterward, so it could
/// report "safe" while Minecraft was still actually running.
/// </summary>
public class MinecraftExitGuardTests
{
    private static Action NoDelay() => () => { };

    [Fact]
    public void TargetExitsAndNoOtherMinecraft_SafeToMutate()
    {
        var result = MinecraftExitGuard.WaitUntilSafeToMutate(
            targetPid: 1234,
            isPidRunning: _ => false,
            isAnyMinecraftGameRunning: () => false,
            delay: NoDelay(),
            pidMaxPolls: 10,
            otherProcessMaxPolls: 10);

        Assert.True(result.SafeToMutate);
        Assert.Null(result.AbortReason);
    }

    [Fact]
    public void TargetAlreadyExited_NoOtherMinecraft_SafeToMutate()
    {
        // isPidRunning reports "not running" on the very first check - the target had already
        // exited before the guard was even asked to wait.
        int pollCalls = 0;
        var result = MinecraftExitGuard.WaitUntilSafeToMutate(
            targetPid: 1234,
            isPidRunning: _ => { pollCalls++; return false; },
            isAnyMinecraftGameRunning: () => false,
            delay: NoDelay(),
            pidMaxPolls: 10,
            otherProcessMaxPolls: 10);

        Assert.True(result.SafeToMutate);
        Assert.Equal(1, pollCalls);
    }

    [Fact]
    public void TargetNeverExits_AbortsWithZeroMutationSignal()
    {
        var result = MinecraftExitGuard.WaitUntilSafeToMutate(
            targetPid: 1234,
            isPidRunning: _ => true,
            isAnyMinecraftGameRunning: () => false,
            delay: NoDelay(),
            pidMaxPolls: 5,
            otherProcessMaxPolls: 10);

        Assert.False(result.SafeToMutate);
        Assert.Equal("Minecraft kör fortfarande. Stäng Minecraft och försök igen.", result.AbortReason);
    }

    [Fact]
    public void OtherMinecraftClosesDuringGracePeriod_SafeToMutate()
    {
        int otherMcCallsRemaining = 3;
        bool IsAnyMinecraftGameRunning()
        {
            if (otherMcCallsRemaining <= 0) return false;
            otherMcCallsRemaining--;
            return true;
        }

        var result = MinecraftExitGuard.WaitUntilSafeToMutate(
            targetPid: 1234,
            isPidRunning: _ => false, // target already exited
            isAnyMinecraftGameRunning: IsAnyMinecraftGameRunning,
            delay: NoDelay(),
            pidMaxPolls: 10,
            otherProcessMaxPolls: 10);

        Assert.True(result.SafeToMutate);
    }

    [Fact]
    public void OtherMinecraftStaysAliveThroughGracePeriod_AbortsWithZeroMutationSignal()
    {
        var result = MinecraftExitGuard.WaitUntilSafeToMutate(
            targetPid: 1234,
            isPidRunning: _ => false, // target already exited
            isAnyMinecraftGameRunning: () => true, // never closes
            delay: NoDelay(),
            pidMaxPolls: 10,
            otherProcessMaxPolls: 5);

        Assert.False(result.SafeToMutate);
        Assert.Equal("Minecraft kör fortfarande. Stäng Minecraft och försök igen.", result.AbortReason);
    }

    [Fact]
    public void OtherMinecraftReportsFalseButTargetNeverExited_StillAborts()
    {
        // The exact regression this class exists to prevent: the grace-period loop only runs
        // while isAnyMinecraftGameRunning() is true, so if the target itself never exited, the
        // loop for "other" processes never even executes - the final positive re-check must still
        // catch that the target is unsafe, rather than treating "no other MC seen" as good enough.
        var result = MinecraftExitGuard.WaitUntilSafeToMutate(
            targetPid: 1234,
            isPidRunning: _ => true, // target never exits
            isAnyMinecraftGameRunning: () => false,
            delay: NoDelay(),
            pidMaxPolls: 3,
            otherProcessMaxPolls: 10);

        Assert.False(result.SafeToMutate);
    }
}
