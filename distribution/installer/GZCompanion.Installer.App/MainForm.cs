using System.Diagnostics;
using System.Reflection;
using GZCompanion.Installer.Core;

namespace GZCompanion.Installer.App;

/// <summary>One "Minecraft 26.1.2  ✓ Hittad" row: a fixed label plus a check mark and a mutable status suffix.</summary>
internal sealed class StatusRow
{
    private readonly Label _check;
    private readonly Label _text;
    private readonly string _label;

    public StatusRow(Panel parent, int y, string label)
    {
        _label = label;
        _check = new Label { Text = "…", ForeColor = Theme.TextMuted, Location = new Point(10, y), AutoSize = true, Font = Theme.BodyFont };
        _text = new Label { Text = label, ForeColor = Theme.TextSecondary, Location = new Point(32, y), AutoSize = true, Font = Theme.BodyFont };
        parent.Controls.Add(_check);
        parent.Controls.Add(_text);
    }

    public void SetPending()
    {
        _check.Text = "…";
        _check.ForeColor = Theme.TextMuted;
        _text.Text = _label;
    }

    public void SetStatus(StatusIcon icon, string suffix)
    {
        _check.Text = icon switch { StatusIcon.Ok => "✓", StatusIcon.Fail => "✕", _ => "!" };
        _check.ForeColor = icon switch { StatusIcon.Ok => Theme.StatusGreen, StatusIcon.Fail => Theme.StatusRed, _ => Theme.StatusYellow };
        _text.Text = string.IsNullOrEmpty(suffix) ? _label : $"{_label}  {suffix}";
    }
}

internal enum StatusIcon { Ok, Fail, Warn }

public sealed class MainForm : Form
{
    private readonly AppOptions _options;
    private readonly InstallPaths _paths = InstallPaths.FromEnvironment();
    private CompatibilityManifest? _manifest;
    private SupportedEntry? _target;

    private Label _subLabel = null!;
    private Panel _statusCard = null!;
    private StatusRow _rowWindows = null!;
    private StatusRow _rowMcVersion = null!;
    private StatusRow _rowLoader = null!;
    private StatusRow _rowApi = null!;
    private StatusRow _rowCompanion = null!;
    private Label _warningLabel = null!;
    private Button _actionButton = null!;
    private Button _retryButton = null!;
    private Button _advancedLink = null!;
    private Button _diagnosticsButton = null!;
    private TextBox _logBox = null!;
    private Panel _completionPanel = null!;
    private bool _advancedVisible;
    private string _lastStep = "start";
    private string _lastStatus = "ok";
    private string? _lastErrorMessage;

    public MainForm(AppOptions options)
    {
        _options = options;
        Text = _options.Uninstall ? "Avinstallera GZ Companion" : "GZ Companion Setup";
        ClientSize = new Size(480, 520);
        FormBorderStyle = FormBorderStyle.FixedDialog;
        MaximizeBox = false;
        StartPosition = FormStartPosition.CenterScreen;
        BackColor = Theme.PanelBg;
        Font = Theme.BodyFont;

        BuildLayout();
        Load += async (_, _) => await DetectAsync();
    }

    private void BuildLayout()
    {
        var title = new Label
        {
            Text = "GZ COMPANION",
            Font = Theme.HeadingFont,
            ForeColor = Theme.Mint,
            AutoSize = true,
            Location = new Point(24, 20),
        };
        Controls.Add(title);

        var testBadge = new Label
        {
            Text = $"v{Program.InstallerVersion} · Testversion",
            Font = Theme.SmallFont,
            ForeColor = Theme.TextMuted,
            AutoSize = true,
            Location = new Point(26, 54),
        };
        Controls.Add(testBadge);

        _subLabel = new Label
        {
            Text = "Kontrollerar din dator...",
            Font = Theme.BodyFont,
            ForeColor = Theme.TextSecondary,
            AutoSize = true,
            Location = new Point(24, 78),
        };
        Controls.Add(_subLabel);

        _statusCard = new Panel
        {
            BackColor = Theme.CardBg,
            Location = new Point(24, 106),
            Size = new Size(ClientSize.Width - 48, 150),
            Anchor = AnchorStyles.Top | AnchorStyles.Left | AnchorStyles.Right,
        };
        Controls.Add(_statusCard);

        _rowWindows = new StatusRow(_statusCard, 8, "Minecraft Java-utgåvan");
        _rowMcVersion = new StatusRow(_statusCard, 36, "Minecraft 26.1.2");
        _rowLoader = new StatusRow(_statusCard, 64, "Fabric Loader 0.19.5");
        _rowApi = new StatusRow(_statusCard, 92, "Fabric API 0.155.3+26.1.2");
        _rowCompanion = new StatusRow(_statusCard, 120, $"GZ Companion v{Program.InstallerVersion}");

        _warningLabel = new Label
        {
            Text = string.Empty,
            Font = Theme.SmallFont,
            ForeColor = Theme.StatusYellow,
            Location = new Point(24, 266),
            Size = new Size(ClientSize.Width - 48, 60),
            Anchor = AnchorStyles.Top | AnchorStyles.Left | AnchorStyles.Right,
            Visible = false,
        };
        Controls.Add(_warningLabel);

        _actionButton = Theme.PrimaryButton(_options.Uninstall ? "AVINSTALLERA" : "INSTALLERA");
        _actionButton.Location = new Point(24, 330);
        _actionButton.Size = new Size(ClientSize.Width - 48, 40);
        _actionButton.Anchor = AnchorStyles.Top | AnchorStyles.Left | AnchorStyles.Right;
        _actionButton.Enabled = false;
        _actionButton.Click += async (_, _) => await RunActionAsync();
        Controls.Add(_actionButton);

        _retryButton = Theme.SecondaryLinkButton("Försök igen");
        _retryButton.Location = new Point(24, 330);
        _retryButton.Size = new Size(ClientSize.Width - 48, 32);
        _retryButton.Anchor = AnchorStyles.Top | AnchorStyles.Left | AnchorStyles.Right;
        _retryButton.Visible = false;
        _retryButton.Click += async (_, _) => await DetectAsync();
        Controls.Add(_retryButton);

        _advancedLink = Theme.SecondaryLinkButton("Avancerat ▾");
        _advancedLink.Location = new Point(24, 380);
        _advancedLink.AutoSize = true;
        _advancedLink.Click += (_, _) => ToggleAdvanced();
        Controls.Add(_advancedLink);

        _diagnosticsButton = Theme.SecondaryLinkButton("Kopiera diagnostik");
        _diagnosticsButton.Location = new Point(200, 380);
        _diagnosticsButton.AutoSize = true;
        _diagnosticsButton.Click += (_, _) => CopyDiagnostics();
        Controls.Add(_diagnosticsButton);

        _logBox = new TextBox
        {
            Multiline = true,
            ReadOnly = true,
            ScrollBars = ScrollBars.Vertical,
            Font = Theme.MonoFont,
            BackColor = Theme.CardInner,
            ForeColor = Theme.TextSecondary,
            BorderStyle = BorderStyle.FixedSingle,
            Location = new Point(24, 412),
            Size = new Size(ClientSize.Width - 48, 96),
            Anchor = AnchorStyles.Top | AnchorStyles.Left | AnchorStyles.Right,
            Visible = false,
        };
        Controls.Add(_logBox);

        _completionPanel = new Panel
        {
            Location = new Point(24, 106),
            Size = new Size(ClientSize.Width - 48, 300),
            Anchor = AnchorStyles.Top | AnchorStyles.Left | AnchorStyles.Right,
            Visible = false,
        };
        Controls.Add(_completionPanel);

        var disclaimer = new Label
        {
            Text = "GZ Companion är ett inofficiellt community-projekt för GameZoneMC.\nDet är inte anslutet till eller godkänt av GameZoneMC.",
            Font = Theme.SmallFont,
            ForeColor = Theme.TextMuted,
            AutoSize = false,
            TextAlign = ContentAlignment.MiddleCenter,
            Location = new Point(24, ClientSize.Height - 40),
            Size = new Size(ClientSize.Width - 48, 34),
            Anchor = AnchorStyles.Bottom | AnchorStyles.Left | AnchorStyles.Right,
        };
        Controls.Add(disclaimer);
    }

    // ------------------------------------------------------------------
    // Detection
    // ------------------------------------------------------------------

    private async Task DetectAsync()
    {
        _retryButton.Visible = false;
        _actionButton.Visible = true;
        _actionButton.Enabled = false;
        _warningLabel.Visible = false;
        _subLabel.Text = "Kontrollerar din dator...";
        foreach (var row in new[] { _rowWindows, _rowMcVersion, _rowLoader, _rowApi, _rowCompanion }) row.SetPending();

        await Task.Run(() =>
        {
            _manifest = LoadManifest();
            _target = _manifest.FindByMinecraftVersion("26.1.2");
        });

        var windows = EnvironmentDetection.CheckWindowsVersion(Environment.OSVersion.Version);
        var launcher = EnvironmentDetection.CheckLauncher(_paths);
        var processLister = new RealProcessLister();
        bool minecraftRunning = EnvironmentDetection.IsMinecraftLikelyRunning(processLister);
        bool launcherAppRunning = EnvironmentDetection.IsMinecraftLauncherRunning(processLister);

        _rowWindows.SetStatus(
            windows.Level == WindowsSupportLevel.Unsupported ? StatusIcon.Fail : StatusIcon.Ok,
            windows.Level == WindowsSupportLevel.Unsupported ? $"{windows.DisplayVersion} - stöds ej" : $"{windows.DisplayVersion} Hittad");
        _rowMcVersion.SetStatus(_target is not null ? StatusIcon.Ok : StatusIcon.Fail, _target is not null ? "Stöds" : "Ingen kompatibel version hittades");
        _rowLoader.SetStatus(StatusIcon.Ok, "Installeras automatiskt");
        _rowApi.SetStatus(StatusIcon.Ok, "Installeras automatiskt");
        _rowCompanion.SetStatus(StatusIcon.Ok, string.Empty);

        if (_options.Uninstall)
        {
            if (launcherAppRunning)
            {
                ShowBlocked("Minecraft Launcher är öppen.\nStäng Minecraft Launcher innan installationen fortsätter.");
                return;
            }
            bool existing = Directory.Exists(_paths.GzCompanionGameDir);
            _subLabel.Text = existing ? "Redo att avinstallera." : "GZ Companion verkar inte vara installerat.";
            _actionButton.Enabled = existing && windows.Level != WindowsSupportLevel.Unsupported;
            return;
        }

        if (windows.Level == WindowsSupportLevel.Unsupported)
        {
            ShowBlocked("Den här installeraren stöder endast Windows 10/11.");
            return;
        }
        if (!launcher.Found)
        {
            ShowBlocked("Minecraft Launcher hittades inte.\nInstallera/starta den officiella Minecraft Launcher först.");
            return;
        }
        if (launcherAppRunning)
        {
            // Official Fabric installation guidance requires the launcher itself to be closed
            // before launcher_profiles.json is edited - distinct from "the game is running" below.
            ShowBlocked("Minecraft Launcher är öppen.\nStäng Minecraft Launcher innan installationen fortsätter.");
            return;
        }
        if (minecraftRunning)
        {
            ShowBlocked("Minecraft körs.\nStäng Minecraft innan installationen fortsätter.");
            return;
        }
        if (_target is null)
        {
            ShowBlocked("Ingen kompatibel version hittades i den här utgåvan av installeraren.");
            return;
        }

        _subLabel.Text = "Redo att installera.";
        _actionButton.Enabled = true;
    }

    private void ShowBlocked(string message)
    {
        _subLabel.Text = "Kan inte fortsätta just nu.";
        _warningLabel.Text = message;
        _warningLabel.Visible = true;
        _actionButton.Visible = false;
        _retryButton.Visible = true;
    }

    private static CompatibilityManifest LoadManifest()
    {
        using var stream = Assembly.GetExecutingAssembly().GetManifestResourceStream("compatibility.json")
            ?? throw new InvalidOperationException("compatibility.json was not embedded in this build.");
        using var reader = new StreamReader(stream);
        return CompatibilityManifest.Parse(reader.ReadToEnd());
    }

    private static byte[] LoadEmbeddedCompanionJar()
    {
        using var stream = Assembly.GetExecutingAssembly().GetManifestResourceStream("gzcompanion.jar")
            ?? throw new InvalidOperationException("gzcompanion.jar was not embedded in this build. Run build-installer.ps1, which copies it in before publishing.");
        using var mem = new MemoryStream();
        stream.CopyTo(mem);
        return mem.ToArray();
    }

    // ------------------------------------------------------------------
    // Install / uninstall
    // ------------------------------------------------------------------

    private async Task RunActionAsync()
    {
        _actionButton.Enabled = false;
        _advancedVisible = true;
        _logBox.Visible = true;
        _advancedLink.Text = "Avancerat ▴";
        _logBox.Clear();

        using var downloader = new HttpsFileDownloader($"GZCompanionInstaller/{Program.InstallerVersion}");
        var deps = new InstallEngineDependencies
        {
            Paths = _paths,
            Downloader = downloader,
            LoadEmbeddedCompanionJar = LoadEmbeddedCompanionJar,
            Clock = () => DateTimeOffset.Now,
            // The GUI already gated "Installera"/"Avinstallera" on this in DetectAsync, but the
            // engine re-checks it right before touching launcher_profiles.json to close the race
            // where the player opens the launcher between detection and clicking the button.
            IsLauncherRunning = () => EnvironmentDetection.IsMinecraftLauncherRunning(new RealProcessLister()),
        };
        var engine = new InstallEngine(deps);
        var progress = new Progress<string>(AppendLog);

        InstallOutcome outcome;
        using var cts = new CancellationTokenSource();
        try
        {
            outcome = _options.Uninstall
                ? await engine.UninstallAsync(keepUserData: true, _options.DryRun, progress, cts.Token)
                : await engine.RunAsync(_target!, _options.DryRun, progress, cts.Token);
        }
        catch (Exception ex)
        {
            outcome = new InstallOutcome(false, _options.DryRun, Array.Empty<InstallStepResult>(), ex.Message);
        }

        _lastStep = outcome.Steps.Count > 0 ? outcome.Steps[^1].Step : "unknown";
        _lastStatus = outcome.Success ? "ok" : "fel";
        _lastErrorMessage = outcome.ErrorMessage;

        if (outcome.Success)
        {
            ShowCompletion(dryRun: _options.DryRun);
        }
        else
        {
            AppendLog($"FEL: {outcome.ErrorMessage}");
            MessageBox.Show(this,
                $"Installationen kunde inte slutföras.\n\n{outcome.ErrorMessage}\n\nInga befintliga Minecraft-profiler har skadats.",
                "GZ Companion Setup", MessageBoxButtons.OK, MessageBoxIcon.Error);
            _actionButton.Enabled = true;
        }
    }

    private void AppendLog(string line) => _logBox.AppendText(line + Environment.NewLine);

    private void ShowCompletion(bool dryRun)
    {
        _statusCard.Visible = false;
        _actionButton.Visible = false;
        _advancedLink.Visible = false;
        _diagnosticsButton.Visible = true;
        _completionPanel.Visible = true;
        _completionPanel.Controls.Clear();

        string headline = _options.Uninstall
            ? "✓ GZ Companion har avinstallerats"
            : dryRun ? "✓ Torrkörning klar (inget ändrades)" : "✓ GZ Companion är installerat";

        var head = new Label { Text = headline, ForeColor = Theme.StatusGreen, Font = new Font("Segoe UI", 12f, FontStyle.Bold), AutoSize = true, Location = new Point(0, 0) };
        _completionPanel.Controls.Add(head);

        if (!_options.Uninstall)
        {
            var body = new Label
            {
                Text = "Öppna Minecraft Launcher och välj:\n\"GZ Companion - GameZone\"",
                ForeColor = Theme.TextSecondary,
                Font = Theme.BodyFont,
                AutoSize = true,
                Location = new Point(0, 40),
            };
            _completionPanel.Controls.Add(body);

            var openLauncherBtn = Theme.PrimaryButton("ÖPPNA MINECRAFT LAUNCHER");
            openLauncherBtn.Location = new Point(0, 90);
            openLauncherBtn.Size = new Size(_completionPanel.Width, 40);
            openLauncherBtn.Anchor = AnchorStyles.Top | AnchorStyles.Left | AnchorStyles.Right;
            openLauncherBtn.Click += (_, _) => OpenMinecraftLauncher();
            _completionPanel.Controls.Add(openLauncherBtn);
        }
    }

    private void OpenMinecraftLauncher()
    {
        try
        {
            Process.Start(new ProcessStartInfo("minecraft://") { UseShellExecute = true });
        }
        catch
        {
            MessageBox.Show(this, "Kunde inte öppna Minecraft Launcher automatiskt. Öppna den manuellt från Start-menyn.",
                "GZ Companion Setup", MessageBoxButtons.OK, MessageBoxIcon.Information);
        }
    }

    private void ToggleAdvanced()
    {
        _advancedVisible = !_advancedVisible;
        _logBox.Visible = _advancedVisible;
        _advancedLink.Text = _advancedVisible ? "Avancerat ▴" : "Avancerat ▾";
    }

    private void CopyDiagnostics()
    {
        var info = new DiagnosticsInfo(
            Program.InstallerVersion,
            EnvironmentDetection.CheckWindowsVersion(Environment.OSVersion.Version).DisplayVersion,
            _paths.DotMinecraftDir,
            _paths.GzCompanionGameDir,
            _target?.MinecraftVersion ?? "okänd",
            _target?.FabricLoaderVersion ?? "okänd",
            _target?.FabricApiVersion ?? "okänd",
            Program.InstallerVersion,
            _lastStep,
            _lastStatus,
            null,
            _lastErrorMessage);
        string text = DiagnosticsBuilder.Build(info);
        try
        {
            Clipboard.SetText(text);
            MessageBox.Show(this, "Diagnostik kopierad till urklipp.", "GZ Companion Setup", MessageBoxButtons.OK, MessageBoxIcon.Information);
        }
        catch
        {
            MessageBox.Show(this, text, "Diagnostik (kunde inte kopiera automatiskt)", MessageBoxButtons.OK, MessageBoxIcon.Information);
        }
    }
}
