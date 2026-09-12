using GZCompanion.Installer.Core;
using Xunit;

namespace GZCompanion.Installer.Tests;

/// <summary>
/// Pure policy tests for <see cref="UpdateApplyClosePolicy"/> - no WinForms window involved. Proves
/// the exact rule the update-status window must enforce: closing (Stäng/X/Alt+F4) is blocked only
/// while file mutation could possibly be in progress (Verifying/Installing); cancelling is only ever
/// offered before that point; and a new run can never start while one is already in flight.
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

    [Theory]
    [InlineData(UpdateApplyPhase.WaitingForMinecraft, true)]
    [InlineData(UpdateApplyPhase.Verifying, false)]
    [InlineData(UpdateApplyPhase.Installing, false)]
    [InlineData(UpdateApplyPhase.Succeeded, true)]
    [InlineData(UpdateApplyPhase.Failed, true)]
    [InlineData(UpdateApplyPhase.LauncherMustClose, true)]
    [InlineData(UpdateApplyPhase.OtherMinecraftRunning, true)]
    [InlineData(UpdateApplyPhase.Cancelled, true)]
    public void CanClose_BlockedOnlyDuringVerifyingOrInstalling(UpdateApplyPhase phase, bool expected)
    {
        Assert.Equal(expected, UpdateApplyClosePolicy.CanClose(phase));
    }

    [Fact]
    public void CanStartNewRun_FalseWhileAlreadyRunning()
    {
        Assert.False(UpdateApplyClosePolicy.CanStartNewRun(alreadyRunning: true));
        Assert.True(UpdateApplyClosePolicy.CanStartNewRun(alreadyRunning: false));
    }
}
