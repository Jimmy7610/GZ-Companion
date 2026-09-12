using GZCompanion.Installer.Core;

namespace GZCompanion.Installer.App;

/// <summary>
/// The small, dedicated window shown for a REAL <c>--apply-update</c> run - the whole point of
/// this feature is a one-click update for a non-technical player, and a console window that
/// vanishes the instant Minecraft closes is not that. This form contains NO filesystem or process
/// logic itself - it only ever renders whatever <see cref="UpdateApplyCoordinator"/> reports and
/// invokes actions ("Försök igen", "Avbryt") back into it, per the project's "keep orchestration
/// testable" rule; the actual close/cancel rules it enforces live in the pure, unit-tested
/// <see cref="UpdateApplyClosePolicy"/>, not here. See <see cref="Program"/> for the headless
/// (<c>--test-root</c>) equivalent used in automated smoke tests.
///
/// <para><see cref="UpdateApplyCoordinator.RunAsync"/> is genuinely asynchronous (see
/// <see cref="MinecraftExitGuard.WaitUntilSafeToMutateAsync"/>), so this window's message loop stays
/// responsive - repaints, moves, and reacts to Avbryt/Stäng - for the entire "waiting for Minecraft
/// to close" phase, which can take minutes at the real poll budgets.</para>
/// </summary>
internal sealed class UpdateApplyForm : Form
{
    private readonly UpdateApplyRequest _request;
    private readonly Label _titleLabel;
    private readonly Label _statusLabel;
    private readonly ProgressBar _progressBar;
    private readonly Button _retryButton;
    private readonly Button _cancelButton;
    private readonly Button _closeButton;
    private readonly Action? _onSuccess;

    private bool _running;
    private UpdateApplyPhase _currentPhase = UpdateApplyPhase.WaitingForMinecraft;
    private CancellationTokenSource? _cts;

    public UpdateApplyForm(UpdateApplyRequest request, Action? onSuccess = null)
    {
        _request = request;
        _onSuccess = onSuccess;

        Text = "GZ Companion Update";
        ClientSize = new Size(420, 220);
        FormBorderStyle = FormBorderStyle.FixedDialog;
        MaximizeBox = false;
        MinimizeBox = false;
        StartPosition = FormStartPosition.CenterScreen;
        BackColor = Theme.PanelBg;

        _titleLabel = new Label
        {
            Text = "GZ COMPANION UPPDATERING",
            Font = new Font("Segoe UI", 13f, FontStyle.Bold),
            ForeColor = Theme.Mint,
            AutoSize = false,
            Location = new Point(20, 16),
            Size = new Size(380, 28),
        };

        _statusLabel = new Label
        {
            Text = "Förbereder...",
            Font = Theme.BodyFont,
            ForeColor = Theme.TextSecondary,
            AutoSize = false,
            Location = new Point(20, 56),
            Size = new Size(380, 70),
        };

        _progressBar = new ProgressBar
        {
            Style = ProgressBarStyle.Marquee,
            MarqueeAnimationSpeed = 30,
            Location = new Point(20, 130),
            Size = new Size(380, 8),
        };

        _retryButton = Theme.PrimaryButton("Försök igen");
        _retryButton.Location = new Point(20, 156);
        _retryButton.Size = new Size(180, 36);
        _retryButton.Visible = false;
        _retryButton.Click += (_, _) => _ = RunAsync();

        _cancelButton = Theme.SecondaryLinkButton("Avbryt");
        _cancelButton.Location = new Point(20, 164);
        _cancelButton.Size = new Size(100, 24);
        _cancelButton.Visible = false;
        _cancelButton.Click += (_, _) => _cts?.Cancel();

        _closeButton = Theme.SecondaryLinkButton("Stäng");
        _closeButton.Location = new Point(210, 164);
        _closeButton.Size = new Size(100, 24);
        _closeButton.Click += (_, _) => Close();

        Controls.Add(_titleLabel);
        Controls.Add(_statusLabel);
        Controls.Add(_progressBar);
        Controls.Add(_retryButton);
        Controls.Add(_cancelButton);
        Controls.Add(_closeButton);

        // Covers all three ways a player could try to close this window (Stäng, the title bar X,
        // Alt+F4) with ONE check - WinForms reports every one of them as FormClosing.
        FormClosing += OnFormClosing;

        Load += (_, _) => _ = RunAsync();
    }

    private void OnFormClosing(object? sender, FormClosingEventArgs e)
    {
        // Gated on _running, NOT _currentPhase - see UpdateApplyClosePolicy's doc comment for why a
        // phase-based check would reopen the exact race this method exists to close (a delayed
        // Progress<T> notification could leave _currentPhase stale at WaitingForMinecraft while the
        // coordinator has already moved on to actually mutating files on its background thread).
        switch (UpdateApplyClosePolicy.DecideOnCloseRequest(_running, _currentPhase))
        {
            case UpdateApplyClosePolicy.CloseAction.AllowClose:
                return;

            case UpdateApplyClosePolicy.CloseAction.RequestCancelAndBlock:
                // Best-effort only: if the guard has already succeeded, this cancellation request
                // has no effect on InstallEngine (see UpdateApplyCoordinator's cancellation-boundary
                // doc comment) - it is never relied upon for safety, only offered when it's likely
                // to still be useful.
                _cts?.Cancel();
                e.Cancel = true;
                break;

            case UpdateApplyClosePolicy.CloseAction.BlockOnly:
                e.Cancel = true;
                _statusLabel.Text = UpdateApplyClosePolicy.BlockedCloseMessage;
                _statusLabel.ForeColor = Theme.StatusRed;
                break;
        }
    }

    private async Task RunAsync()
    {
        if (!UpdateApplyClosePolicy.CanStartNewRun(_running))
        {
            return; // a run is already in flight (e.g. a stray double-click on Försök igen) - never overlap two transactions
        }
        _running = true;

        _retryButton.Visible = false;
        _progressBar.Visible = true;
        _statusLabel.ForeColor = Theme.TextSecondary;
        SetPhase(UpdateApplyPhase.WaitingForMinecraft);

        _cts = new CancellationTokenSource();
        try
        {
            // Constructed on the UI thread, so Progress<T>'s callback is automatically marshaled
            // back to it via the captured SynchronizationContext - safe to touch controls directly
            // here. The coordinator's own async wait (MinecraftExitGuard.WaitUntilSafeToMutateAsync)
            // never blocks this thread, so the message loop keeps pumping (repaints, Avbryt/Stäng)
            // for the whole WaitingForMinecraft phase.
            var progress = new Progress<UpdateApplyProgress>(OnProgress);
            var coordinator = new UpdateApplyCoordinator();
            UpdateApplyOutcome outcome = await coordinator.RunAsync(_request, progress, _cts.Token).ConfigureAwait(true);
            OnFinished(outcome);
        }
        finally
        {
            _running = false;
            _cts.Dispose();
            _cts = null;
        }
    }

    private void SetPhase(UpdateApplyPhase phase)
    {
        _currentPhase = phase;
        // Purely cosmetic (shows/hides the Avbryt button) - actual close safety is enforced by
        // OnFormClosing based on _running alone, so a stale _currentPhase here can never allow an
        // unsafe close; at worst a Cancel() request lands too late and is harmlessly ignored (see
        // UpdateApplyCoordinator's cancellation-boundary doc comment). The Stäng button itself is
        // always left enabled - it also routes through OnFormClosing, which is the sole authority.
        _cancelButton.Visible = _running && UpdateApplyClosePolicy.CanCancel(phase);
    }

    private void OnProgress(UpdateApplyProgress progress)
    {
        SetPhase(progress.Phase);
        _statusLabel.Text = progress.Message;
        _statusLabel.ForeColor = progress.Phase switch
        {
            UpdateApplyPhase.OtherMinecraftRunning or UpdateApplyPhase.LauncherMustClose or UpdateApplyPhase.Failed => Theme.StatusRed,
            UpdateApplyPhase.Succeeded => Theme.StatusGreen,
            _ => Theme.TextSecondary,
        };
    }

    private void OnFinished(UpdateApplyOutcome outcome)
    {
        _progressBar.Visible = false;
        SetPhase(outcome.Phase);

        switch (outcome.Phase)
        {
            case UpdateApplyPhase.Succeeded:
                _titleLabel.Text = "✓ GZ COMPANION HAR UPPDATERATS";
                _titleLabel.ForeColor = Theme.StatusGreen;
                _statusLabel.Text = $"{outcome.Message}\n\nMinecraft Launcher är redo.";
                _statusLabel.ForeColor = Theme.StatusGreen;
                _onSuccess?.Invoke();
                break;

            case UpdateApplyPhase.OtherMinecraftRunning:
                _statusLabel.Text = "Minecraft kör fortfarande.\nStäng Minecraft och försök igen.";
                _statusLabel.ForeColor = Theme.StatusRed;
                _retryButton.Visible = true;
                break;

            case UpdateApplyPhase.Cancelled:
                _statusLabel.Text = "Uppdateringen avbröts.\nIngenting har ändrats.";
                _statusLabel.ForeColor = Theme.TextSecondary;
                _retryButton.Visible = true;
                break;

            case UpdateApplyPhase.LauncherMustClose:
                _statusLabel.Text = "Minecraft Launcher måste stängas\nför den här uppdateringen.\n\nStäng Launcher och klicka Försök igen.";
                _statusLabel.ForeColor = Theme.StatusRed;
                _retryButton.Visible = true;
                break;

            case UpdateApplyPhase.Failed:
            default:
                _statusLabel.Text = $"Uppdateringen kunde inte installeras.\nDin tidigare version är kvar.\n\n{outcome.Message}";
                _statusLabel.ForeColor = Theme.StatusRed;
                _retryButton.Visible = true;
                break;
        }
    }
}
