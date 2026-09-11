namespace GZCompanion.Installer.Core;

/// <summary>
/// Pure pixel layout for the bottom section of MainForm's window - the diagnostics/log box and the
/// footer disclaimer below it - expressed as compile-time constants MainForm itself uses to
/// position those controls, so the relationship between them is unit-testable without a live
/// WinForms Form.
///
/// Real human QA found the footer overlapping the log box by 28px whenever the log box was visible
/// (which is every install-in-progress and every completed/error state, not just the "Avancerat"
/// pre-install toggle) - the two had been positioned as independently-guessed constants that
/// happened to collide. The fix expresses the footer's position purely in terms of the log box's
/// own bottom edge plus a guaranteed minimum gap, so they can never drift back into collision.
/// </summary>
public static class InstallerWindowLayout
{
    public const int ContentMargin = 24;

    /// <summary>The completion panel that replaces the status card once install/uninstall succeeds - stays well above the log box in every state.</summary>
    public const int CompletionPanelTop = 106;
    public const int CompletionPanelHeight = 300;
    public const int CompletionPanelBottom = CompletionPanelTop + CompletionPanelHeight;

    public const int LogBoxTop = 432;
    public const int LogBoxHeight = 96;
    public const int LogBoxBottom = LogBoxTop + LogBoxHeight;

    /// <summary>Minimum breathing room the footer must keep below the log/diagnostics box.</summary>
    public const int FooterGapAboveLogBox = 16;
    public const int FooterHeight = 58;
    /// <summary>Minimum breathing room the footer must keep above the window's own bottom edge.</summary>
    public const int FooterBottomPadding = 14;

    public const int FooterTop = LogBoxBottom + FooterGapAboveLogBox;
    public const int FooterBottom = FooterTop + FooterHeight;

    /// <summary>The window's total ClientSize.Height - derived from the footer's own reserved area rather than guessed independently of it.</summary>
    public const int ClientHeight = FooterBottom + FooterBottomPadding;
}
