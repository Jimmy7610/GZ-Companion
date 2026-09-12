using GZCompanion.Installer.Core;
using Xunit;

namespace GZCompanion.Installer.Tests;

/// <summary>
/// Pure logic - the process probe and sleep action are both fakes, so these tests never actually
/// sleep or spawn a real process (per this feature's "use an abstraction/fake, not sleeping real
/// tests" requirement).
/// </summary>
public class PidWaiterTests
{
    [Fact]
    public void ExitsImmediatelyWhenProcessIsAlreadyGone()
    {
        int delayCalls = 0;
        bool result = PidWaiter.WaitForExit(1234, pid => false, () => delayCalls++, maxPolls: 10);

        Assert.True(result);
        Assert.Equal(0, delayCalls);
    }

    [Fact]
    public void WaitsThroughSeveralStillRunningPollsThenSucceeds()
    {
        int callsRemaining = 3;
        int delayCalls = 0;
        bool IsStillRunning(int pid)
        {
            if (callsRemaining <= 0) return false;
            callsRemaining--;
            return true;
        }

        bool result = PidWaiter.WaitForExit(1234, IsStillRunning, () => delayCalls++, maxPolls: 10);

        Assert.True(result);
        Assert.Equal(3, delayCalls);
    }

    [Fact]
    public void TimesOutIfTheProcessNeverExits()
    {
        int delayCalls = 0;
        bool result = PidWaiter.WaitForExit(1234, pid => true, () => delayCalls++, maxPolls: 5);

        Assert.False(result);
        Assert.Equal(5, delayCalls);
    }

    [Fact]
    public void ZeroMaxPollsStillChecksOnceAtTheEnd()
    {
        // maxPolls=0 skips the loop entirely, but the method still checks isStillRunning once
        // more before giving up, so an already-exited process is still correctly reported.
        bool result = PidWaiter.WaitForExit(1234, pid => false, () => { }, maxPolls: 0);
        Assert.True(result);
    }

    [Fact]
    public void RealProbe_AlreadyExitedOrInvalidPidReportsNotRunning()
    {
        // A PID that (almost certainly) does not exist on this machine.
        bool running = PidWaiter.IsProcessRunning(-1);
        Assert.False(running);
    }

    [Fact]
    public void RealProbe_CurrentProcessReportsRunning()
    {
        int myPid = Environment.ProcessId;
        Assert.True(PidWaiter.IsProcessRunning(myPid));
    }
}
