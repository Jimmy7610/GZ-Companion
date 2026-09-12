namespace GZCompanion.Installer.Core;

/// <summary>
/// Pure, testable policy for what the update-status window (<c>UpdateApplyForm</c> in
/// GZCompanion.Installer.App) may let the player do while a run is in flight - extracted out of
/// WinForms event handlers so it can be unit tested without a real window.
///
/// <para><b>Whether the window may actually close depends ONLY on whether a run is currently in
/// flight (<c>running</c>) - never on which phase the UI currently believes it's in.</b> An earlier
/// version of this policy gated closing on <see cref="UpdateApplyPhase"/> instead, but a
/// <see cref="Progress{T}"/> phase notification can be delayed arbitrarily behind
/// <see cref="UpdateApplyCoordinator"/>'s own execution (it posts to a UI
/// <see cref="System.Threading.SynchronizationContext"/> asynchronously) - the coordinator can have
/// already entered <see cref="UpdateApplyPhase.Verifying"/>/<see cref="UpdateApplyPhase.Installing"/>
/// on a background thread before the UI thread has processed that transition, so a phase-based check
/// could see a stale <see cref="UpdateApplyPhase.WaitingForMinecraft"/> and wrongly allow the OS to
/// close (and thus kill) a process that is actively mutating files, with no chance for
/// <see cref="InstallEngine"/>'s own rollback code to run. Gating purely on <c>running</c> removes
/// this race entirely, regardless of phase-notification timing: the window can never be closed while
/// a run is in flight, full stop. See <see cref="UpdateApplyCoordinator"/>'s cancellation-boundary
/// doc comment for the matching half of this fix (a cancellation request that arrives "too late" is
/// also made harmless there, independent of this policy).</para>
/// </summary>
public static class UpdateApplyClosePolicy
{
    /// <summary>What a close request (Stäng, the title bar X, or Alt+F4 - WinForms reports all three
    /// as the same <c>FormClosing</c> event) should do right now.</summary>
    public enum CloseAction
    {
        /// <summary>No run is in flight - let the window close normally.</summary>
        AllowClose,

        /// <summary>A run is in flight and it's still safe to request cancellation (only while
        /// <see cref="UpdateApplyPhase.WaitingForMinecraft"/>) - request it, but do NOT close the
        /// window yet; it becomes closable once the run actually finishes (as
        /// <see cref="UpdateApplyPhase.Cancelled"/> if the request was honored in time, or whatever
        /// terminal outcome resulted if it was too late - see the cancellation-boundary note on
        /// <see cref="UpdateApplyCoordinator"/>).</summary>
        RequestCancelAndBlock,

        /// <summary>A run is in flight and cancellation is not offered at this phase (file mutation
        /// may be underway) - block the close with no side effect other than telling the player why.</summary>
        BlockOnly,
    }

    /// <summary>
    /// The single decision point <c>UpdateApplyForm.FormClosing</c> defers to - see this class's own
    /// doc comment for why <paramref name="running"/>, not <paramref name="phase"/>, is what actually
    /// gates closing. <paramref name="phase"/> only decides whether a cancellation request is ALSO
    /// worth attempting; it can safely be stale (or even wrong) without ever compromising safety,
    /// because a cancellation request that arrives after mutation could have started is a harmless
    /// no-op (see <see cref="UpdateApplyCoordinator"/>), not a way to bypass <see cref="BlockOnly"/>.
    /// </summary>
    public static CloseAction DecideOnCloseRequest(bool running, UpdateApplyPhase phase)
    {
        if (!running)
        {
            return CloseAction.AllowClose;
        }
        return CanCancel(phase) ? CloseAction.RequestCancelAndBlock : CloseAction.BlockOnly;
    }

    /// <summary>Whether the "Avbryt" cancel action may be offered right now - only before any file
    /// mutation could possibly have started. Used both to show/hide the Avbryt button and by
    /// <see cref="DecideOnCloseRequest"/> above.</summary>
    public static bool CanCancel(UpdateApplyPhase phase) => phase == UpdateApplyPhase.WaitingForMinecraft;

    /// <summary>
    /// Message to show if the player attempts to close while a run is in flight and cancellation
    /// isn't (or wasn't) an option, so the window makes clear WHY it refused to close rather than
    /// silently ignoring the click/X/Alt+F4.
    /// </summary>
    public const string BlockedCloseMessage = "Uppdateringen installeras.\nVänta tills den är klar.";

    /// <summary>A new <c>RunAsync</c> (fresh attempt or "Försök igen" retry) may only start when no
    /// run is already in flight - prevents two concurrent transactions against the same files.</summary>
    public static bool CanStartNewRun(bool alreadyRunning) => !alreadyRunning;
}
