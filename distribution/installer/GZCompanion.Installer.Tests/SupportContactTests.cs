using GZCompanion.Installer.Core;
using Xunit;

namespace GZCompanion.Installer.Tests;

internal sealed class FakeProcessStarter : IProcessStarter
{
    public List<string> StartedTargets { get; } = new();
    public Exception? ThrowOnStart { get; set; }

    public void Start(string target)
    {
        if (ThrowOnStart is not null) throw ThrowOnStart;
        StartedTargets.Add(target);
    }
}

public class SupportContactTests
{
    [Fact]
    public void SupportEmail_IsExactlyTheSuppliedAddress()
    {
        Assert.Equal("jbl_76@hotmail.com", SupportContact.Email);
    }

    [Fact]
    public void MailtoUri_IsExactlyMailtoPlusTheSupportEmail()
    {
        Assert.Equal("mailto:jbl_76@hotmail.com", SupportContact.MailtoUri);
    }

    [Fact]
    public void TryOpen_StartsExactlyTheMailtoUriAndReportsSuccess()
    {
        var starter = new FakeProcessStarter();
        var opener = new MailClientOpener(starter);

        bool result = opener.TryOpen();

        Assert.True(result);
        Assert.Single(starter.StartedTargets);
        Assert.Equal("mailto:jbl_76@hotmail.com", starter.StartedTargets[0]);
    }

    [Fact]
    public void TryOpen_NeverThrowsWhenNoMailClientIsRegistered()
    {
        var starter = new FakeProcessStarter { ThrowOnStart = new InvalidOperationException("no application is associated") };
        var opener = new MailClientOpener(starter);

        bool result = opener.TryOpen();

        Assert.False(result);
    }
}
