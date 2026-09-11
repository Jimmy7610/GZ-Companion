using GZCompanion.Installer.Core;
using Xunit;

namespace GZCompanion.Installer.Tests;

public class AtomicFileWriterTests : IDisposable
{
    private readonly string _tempRoot = Directory.CreateTempSubdirectory("gzc-atomic-test-").FullName;
    public void Dispose() { try { Directory.Delete(_tempRoot, recursive: true); } catch { } }

    [Fact]
    public void WriteAllTextAtomically_CreatesFileAndCleansUpTempFile()
    {
        string dest = Path.Combine(_tempRoot, "sub", "file.json");
        AtomicFileWriter.WriteAllTextAtomically(dest, "{\"a\":1}");

        Assert.True(File.Exists(dest));
        Assert.Equal("{\"a\":1}", File.ReadAllText(dest));
        Assert.Empty(Directory.GetFiles(Path.Combine(_tempRoot, "sub"), "*.tmp-*"));
    }

    [Fact]
    public void WriteAllTextAtomically_OverwritesExistingFile()
    {
        string dest = Path.Combine(_tempRoot, "file.json");
        File.WriteAllText(dest, "old");
        AtomicFileWriter.WriteAllTextAtomically(dest, "new");
        Assert.Equal("new", File.ReadAllText(dest));
    }

    [Fact]
    public void CopyFileAtomically_CopiesContentAndCleansUpTempFile()
    {
        string src = Path.Combine(_tempRoot, "src.jar");
        File.WriteAllBytes(src, new byte[] { 1, 2, 3, 4 });
        string dest = Path.Combine(_tempRoot, "mods", "dest.jar");

        AtomicFileWriter.CopyFileAtomically(src, dest);

        Assert.Equal(new byte[] { 1, 2, 3, 4 }, File.ReadAllBytes(dest));
        Assert.Empty(Directory.GetFiles(Path.Combine(_tempRoot, "mods"), "*.tmp-*"));
    }
}
