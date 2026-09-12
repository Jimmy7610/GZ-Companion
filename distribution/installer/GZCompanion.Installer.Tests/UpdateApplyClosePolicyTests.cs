using GZCompanion.Installer.Core;
using Xunit;

namespace GZCompanion.Installer.Tests;

/// <summary>
/// Pure policy tests for <see cref="UpdateApplyClosePolicy"/> - no WinForms window involved. Proves
/// the exact rule the update-status window must enforce: closing (Stäng/X/Alt+F4) is gated ONLY on
/// whether a run is in flight, never on which phase the UI currently believes it's in - a
/// <see cref="Progress{T}"/> phase notification can be delayed arbitrarily behind
/// <see cref="UpdateApplyCoordinator"/>'s own execution, so trusting phase for the actual close
/// decision would reopen the exact process-kill race this policy exists to close.
/// </summary>
public class UpdateApplyClosePolicyTests
{
    [Theory]
    [InlineData(UpdateApplyPhase.WaitingForMinecraft, true)]
    [InlineData(UpdateApplyPhase.Verifying, false)]
    [InlineData(UpdateApplyPhase.Installing, false)]
    [InlineData(UpdateApplyPhase.Succeeded, false)]
    [InlineData(UpdateApplyPhase.Failed, false)]
    [InlineData(UpdateApplyPhase.LauncherMustClose, false)]
    [InlineData(UpdateApplyPhase.OtherMinecraftRunning, false)]
    [InlineData(UpdateApplyPhase.Cancelled, false)]
    public void CanCancel_OnlyTrueWhileWaitingForMinecraft(UpdateApplyPhase phase, bool expected)
    {
        Assert.Equal(expected, UpdateApplyClosePolicy.CanCancel(phase));
    }

    // --- FormClosing while a run IS in flight: closing must never be allowed, regardless of phase. ---

    [Fact]
    public void FormClosing_Running_Waiting_RequestsCancelButDoesNotClose()
    {
        var action = UpdateApplyClosePolicy.DecideOnCloseRequest(running: true, UpdateApplyPhase.WaitingForMinecraft);
        Assert.Equal(UpdateApplyClosePolicy.CloseAction.RequestCancelAndBlock, action);
    }

    [Fact]
    public void FormClosing_Running_Verifying_CannotClose()
    {
        var action = UpdateApplyClosePolicy.DecideOnCloseRequest(running: true, UpdateApplyPhase.Verifying);
        Assert.Equal(UpdateApplyClosePolicy.CloseAction.BlockOnly, action);
    }

    [Fact]
    public void FormClosing_Running_Installing_CannotClose()
    {
        var action = UpdateApplyClosePolicy.DecideOnCloseRequest(running: true, UpdateApplyPhase.Installing);
        Assert.Equal(UpdateApplyClosePolicy.CloseAction.BlockOnly, action);
    }

    [Theory]
    [InlineData(UpdateApplyPhase.Succeeded)]
    [InlineData(UpdateApplyPhase.Failed)]
    [InlineData(UpdateApplyPhase.LauncherMustClose)]
    [InlineData(UpdateApplyPhase.OtherMinecraftRunning)]
    [InlineData(UpdateApplyPhase.Cancelled)]
    public void FormClosing_NotRunning_ClosesNormallyRegardlessOfPhase(UpdateApplyPhase phase)
    {
        var action = UpdateApplyClosePolicy.DecideOnCloseRequest(running: false, phase);
        Assert.Equal(UpdateApplyClosePolicy.CloseAction.AllowClose, action);
    }

    // The exact race this policy exists to close: the UI's belief about phase (_currentPhase) can be
    // STALE at WaitingForMinecraft (a delayed Progress<T> notification) while the coordinator has
    // already moved on to actually mutating files on its background thread. A running updater must
    // never become closable just because of that staleness - at most, a (harmless, see
    // UpdateApplyCoordinator) cancellation request is attempted, but the window itself stays blocked.
    [Fact]
    public void DelayedPhaseNotification_CannotMakeARunningUpdaterCloseable()
    {
        var action = UpdateApplyClosePolicy.DecideOnCloseRequest(running: true, UpdateApplyPhase.WaitingForMinecraft);
        Assert.NotEqual(UpdateApplyClosePolicy.CloseAction.AllowClose, action);
    }

    [Fact]
    public void CanStartNewRun_FalseWhileAlreadyRunning()
    {
        Assert.False(UpdateApplyClosePolicy.CanStartNewRun(alreadyRunning: true));
        Assert.True(UpdateApplyClosePolicy.CanStartNewRun(alreadyRunning: false));
    }
}
