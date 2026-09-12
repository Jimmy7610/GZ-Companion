using GZCompanion.Installer.Core;
using Xunit;

namespace GZCompanion.Installer.Tests;

/// <summary>
/// Async mirror of <see cref="MinecraftExitGuardTests"/> for
/// <see cref="MinecraftExitGuard.WaitUntilSafeToMutateAsync"/> - the version actually used by
/// <see cref="UpdateApplyCoordinator"/> so the real poll budgets (up to ~6 minutes) never block a
/// caller's thread (most importantly the WinForms UI thread). <c>delayAsync</c> is always
/// <c>_ =&gt; Task.CompletedTask</c> here - never a real <see cref="Task.Delay(int)"/> - so these
/// tests run instantly regardless of the poll counts used.
/// </summary>
public class MinecraftExitGuardAsyncTests
{
    private static Func<CancellationToken, Task> NoDelay() => _ => Task.CompletedTask;

    [Fact]
    public async Task TargetExitsAndNoOtherMinecraft_SafeToMutate()
    {
        var result = await MinecraftExitGuard.WaitUntilSafeToMutateAsync(
            targetPid: 1234,
            isPidRunning: _ => false,
            isAnyMinecraftGameRunning: () => false,
            delayAsync: NoDelay(),
            pidMaxPolls: 10,
            otherProcessMaxPolls: 10,
            ct: CancellationToken.None);

        Assert.True(result.SafeToMutate);
        Assert.Null(result.AbortReason);
    }

    [Fact]
    public async Task TargetNeverExits_AbortsWithZeroMutationSignal()
    {
        var result = await MinecraftExitGuard.WaitUntilSafeToMutateAsync(
            targetPid: 1234,
            isPidRunning: _ => true,
            isAnyMinecraftGameRunning: () => false,
            delayAsync: NoDelay(),
            pidMaxPolls: 5,
            otherProcessMaxPolls: 10,
            ct: CancellationToken.None);

        Assert.False(result.SafeToMutate);
        Assert.Equal("Minecraft kör fortfarande. Stäng Minecraft och försök igen.", result.AbortReason);
    }

    [Fact]
    public async Task OtherMinecraftStaysAliveThroughGracePeriod_AbortsWithZeroMutationSignal()
    {
        var result = await MinecraftExitGuard.WaitUntilSafeToMutateAsync(
            targetPid: 1234,
            isPidRunning: _ => false,
            isAnyMinecraftGameRunning: () => true,
            delayAsync: NoDelay(),
            pidMaxPolls: 10,
            otherProcessMaxPolls: 5,
            ct: CancellationToken.None);

        Assert.False(result.SafeToMutate);
    }

    [Fact]
    public async Task OtherMinecraftClosesDuringGracePeriod_SafeToMutate()
    {
        int remaining = 3;
        bool IsAnyMinecraftGameRunning()
        {
            if (remaining <= 0) return false;
            remaining--;
            return true;
        }

        var result = await MinecraftExitGuard.WaitUntilSafeToMutateAsync(
            targetPid: 1234,
            isPidRunning: _ => false,
            isAnyMinecraftGameRunning: IsAnyMinecraftGameRunning,
            delayAsync: NoDelay(),
            pidMaxPolls: 10,
            otherProcessMaxPolls: 10,
            ct: CancellationToken.None);

        Assert.True(result.SafeToMutate);
    }

    // --- Cancellation: the specific behavior this async version exists to support. ---

    [Fact]
    public async Task CancelledBeforePidExits_ThrowsOperationCanceled_NeverReportsSafe()
    {
        using var cts = new CancellationTokenSource();
        cts.Cancel();

        await Assert.ThrowsAnyAsync<OperationCanceledException>(() =>
            MinecraftExitGuard.WaitUntilSafeToMutateAsync(
                targetPid: 1234,
                isPidRunning: _ => true, // still "running" - would otherwise poll forever
                isAnyMinecraftGameRunning: () => false,
                delayAsync: NoDelay(),
                pidMaxPolls: 1000,
                otherProcessMaxPolls: 1000,
                ct: cts.Token));
    }

    [Fact]
    public async Task CancelledDuringOtherProcessGracePeriod_ThrowsOperationCanceled()
    {
        using var cts = new CancellationTokenSource();
        bool targetAlreadyExited = true;
        int callCount = 0;
        bool IsAnyMinecraftGameRunning()
        {
            callCount++;
            if (callCount == 2) cts.Cancel(); // cancel partway through the grace-period loop
            return true; // never closes on its own
        }

        await Assert.ThrowsAnyAsync<OperationCanceledException>(() =>
            MinecraftExitGuard.WaitUntilSafeToMutateAsync(
                targetPid: 1234,
                isPidRunning: _ => !targetAlreadyExited,
                isAnyMinecraftGameRunning: IsAnyMinecraftGameRunning,
                delayAsync: NoDelay(),
                pidMaxPolls: 10,
                otherProcessMaxPolls: 1000,
                ct: cts.Token));
    }

    [Fact]
    public async Task CancelledWait_NeverReachesTheFinalPositiveRecheck()
    {
        // If cancellation were swallowed instead of propagated, this would fall through to the
        // final re-check and (since nothing else is "unsafe" here) incorrectly report safe. It must
        // throw instead.
        using var cts = new CancellationTokenSource();
        cts.Cancel();

        bool recheckCalled = false;
        await Assert.ThrowsAnyAsync<OperationCanceledException>(() =>
            MinecraftExitGuard.WaitUntilSafeToMutateAsync(
                targetPid: 1234,
                isPidRunning: _ => { recheckCalled = true; return false; },
                isAnyMinecraftGameRunning: () => false,
                delayAsync: NoDelay(),
                pidMaxPolls: 10,
                otherProcessMaxPolls: 10,
                ct: cts.Token));

        Assert.False(recheckCalled, "a pre-cancelled token must stop before even the first PID probe");
    }
}
