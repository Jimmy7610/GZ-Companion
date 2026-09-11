namespace GZCompanion.Installer.Core;

/// <summary>
/// Jimmy's own public support/feedback contact for the GZ Companion project - not GameZoneMC's,
/// and never used to collect, transmit, or store anything about the person running the installer.
/// </summary>
public static class SupportContact
{
    public const string Email = "jbl_76@hotmail.com";
    public const string MailtoUri = "mailto:" + Email;
}

/// <summary>Starts a process for a shell-openable target (a file, URL, or mailto: link) - real implementation in <see cref="WindowsShellProcessStarter"/>.</summary>
public interface IProcessStarter
{
    void Start(string target);
}

public sealed class WindowsShellProcessStarter : IProcessStarter
{
    public void Start(string target) =>
        System.Diagnostics.Process.Start(new System.Diagnostics.ProcessStartInfo(target) { UseShellExecute = true });
}

/// <summary>
/// Opens the user's default mail client on <see cref="SupportContact.MailtoUri"/>. Never throws -
/// a missing/misconfigured mail client (or no mail client at all) must never crash the installer;
/// the support email is always visible/copyable in the UI regardless of whether this succeeds.
/// </summary>
public sealed class MailClientOpener
{
    private readonly IProcessStarter _starter;

    public MailClientOpener(IProcessStarter starter) => _starter = starter;

    public bool TryOpen()
    {
        try
        {
            _starter.Start(SupportContact.MailtoUri);
            return true;
        }
        catch
        {
            return false;
        }
    }
}
