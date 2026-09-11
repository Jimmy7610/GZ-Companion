using GZCompanion.Installer.Core;
using Xunit;

namespace GZCompanion.Installer.Tests;

/// <summary>
/// Regression guard for the footer/log-box overlap human QA found: the footer used to sit at a
/// fixed offset from ClientSize.Height independent of the log box's own bottom edge, so whenever
/// the log box was visible (every install-in-progress and completed/error state) the two collided
/// by 28px. These constants are what MainForm itself uses to position both controls - see
/// InstallerWindowLayout's doc comment.
/// </summary>
public class InstallerWindowLayoutTests
{
    [Fact]
    public void FooterNeverOverlapsTheLogDiagnosticsBox()
    {
        Assert.True(InstallerWindowLayout.FooterTop > InstallerWindowLayout.LogBoxBottom,
            "The footer's top edge must be strictly below the log box's bottom edge in every state - the log box is visible during and after every install/uninstall run, not just when 'Avancerat' is toggled.");
    }

    [Fact]
    public void FooterKeepsAtLeastTheIntendedBreathingRoomBelowTheLogBox()
    {
        int actualGap = InstallerWindowLayout.FooterTop - InstallerWindowLayout.LogBoxBottom;
        Assert.Equal(InstallerWindowLayout.FooterGapAboveLogBox, actualGap);
        Assert.InRange(actualGap, 12, 16);
    }

    [Fact]
    public void FooterBottomStaysInsideTheWindowWithBottomPadding()
    {
        Assert.True(InstallerWindowLayout.FooterBottom < InstallerWindowLayout.ClientHeight,
            "The footer must not be clipped by the window's own bottom edge.");
        Assert.Equal(InstallerWindowLayout.FooterBottomPadding, InstallerWindowLayout.ClientHeight - InstallerWindowLayout.FooterBottom);
    }

    [Fact]
    public void CompletionPanelNeverReachesDownIntoTheLogBoxOrFooterArea()
    {
        // ShowCompletion() never moves or resizes any control - this is a static proof that its
        // fixed bounds cannot encroach on the log box or footer regardless of run state.
        Assert.True(InstallerWindowLayout.CompletionPanelBottom <= InstallerWindowLayout.LogBoxTop,
            "The completion panel must stay clear of the log box, which becomes visible during/after every install run.");
    }

    [Fact]
    public void EveryBottomSectionElementIsFullyOrderedTopToBottomWithoutOverlap()
    {
        Assert.True(InstallerWindowLayout.CompletionPanelBottom <= InstallerWindowLayout.LogBoxTop);
        Assert.True(InstallerWindowLayout.LogBoxBottom < InstallerWindowLayout.FooterTop);
        Assert.True(InstallerWindowLayout.FooterBottom < InstallerWindowLayout.ClientHeight);
    }
}
