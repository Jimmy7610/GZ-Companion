namespace GZCompanion.Installer.Core;

/// <summary>
/// Pure, testable policy for what the update-status window (<c>UpdateApplyForm</c> in
/// GZCompanion.Installer.App) may let the player do at each <see cref="UpdateApplyPhase"/> -
/// extracted out of WinForms event handlers so it can be unit tested without a real window.
///
/// <para>The one rule that matters: once file mutation could possibly have started
/// (<see cref="UpdateApplyPhase.Verifying"/>/<see cref="UpdateApplyPhase.Installing"/>), the window
/// must not be closable by any means (Stäng, the title bar X, Alt+F4) - killing the OS process mid-
/// transaction gives <see cref="InstallEngine"/>'s own rollback code no chance to run at all. Before
/// that point, nothing has been touched yet, so cancelling (or closing, which is equivalent there) is
/// always safe.</para>
/// </summary>
public static class UpdateApplyClosePolicy
{
    /// <summary>Whether the "Avbryt" cancel action may be offered right now - only before any file
    /// mutation could possibly have started.</summary>
    public static bool CanCancel(UpdateApplyPhase phase) => phase == UpdateApplyPhase.WaitingForMinecraft;

    /// <summary>
    /// Whether the window may be closed right now (the "Stäng" button, the title bar X, or
    /// Alt+F4 - WinForms reports all three as the same <c>FormClosing</c> event, so one check covers
    /// all of them). False only for <see cref="UpdateApplyPhase.Verifying"/> and
    /// <see cref="UpdateApplyPhase.Installing"/>, where <see cref="InstallEngine"/> may be actively
    /// mutating files - forcibly terminating the process there could interrupt a transaction with no
    /// chance for its own rollback to run. Closing during <see cref="UpdateApplyPhase.WaitingForMinecraft"/>
    /// is safe and behaves like cancelling (nothing has been touched yet); every terminal outcome
    /// (<see cref="UpdateApplyPhase.Succeeded"/>, <see cref="UpdateApplyPhase.Failed"/>,
    /// <see cref="UpdateApplyPhase.LauncherMustClose"/>, <see cref="UpdateApplyPhase.OtherMinecraftRunning"/>,
    /// <see cref="UpdateApplyPhase.Cancelled"/>) is always closable.
    /// </summary>
    public static bool CanClose(UpdateApplyPhase phase) => phase is not (UpdateApplyPhase.Verifying or UpdateApplyPhase.Installing);

    /// <summary>
    /// Message to show if the player attempts to close while <see cref="CanClose"/> is false, so the
    /// window makes clear WHY it refused to close rather than silently ignoring the click/X/Alt+F4.
    /// </summary>
    public const string BlockedCloseMessage = "Uppdateringen installeras.\nVänta tills den är klar.";

    /// <summary>A new <c>RunAsync</c> (fresh attempt or "Försök igen" retry) may only start when no
    /// run is already in flight - prevents two concurrent transactions against the same files.</summary>
    public static bool CanStartNewRun(bool alreadyRunning) => !alreadyRunning;
}
