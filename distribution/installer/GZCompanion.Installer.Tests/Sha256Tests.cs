using GZCompanion.Installer.Core;
using Xunit;

namespace GZCompanion.Installer.Tests;

public class Sha256Tests : IDisposable
{
    private readonly string _tempRoot = Directory.CreateTempSubdirectory("gzc-sha256-test-").FullName;
    public void Dispose() { try { Directory.Delete(_tempRoot, recursive: true); } catch { } }

    [Fact]
    public void ComputesKnownHashForKnownContent()
    {
        string path = Path.Combine(_tempRoot, "empty.txt");
        File.WriteAllBytes(path, Array.Empty<byte>());
        // SHA-256 of zero-length input is a well-known, widely published constant.
        Assert.Equal("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", Sha256.ComputeFileHashHex(path));
    }

    [Fact]
    public void VerifyOrThrow_PassesForMatchingHash()
    {
        string path = Path.Combine(_tempRoot, "a.bin");
        File.WriteAllBytes(path, new byte[] { 1, 2, 3 });
        string hash = Sha256.ComputeFileHashHex(path);
        Sha256.VerifyOrThrow(path, hash, "test file"); // must not throw
    }

    [Fact]
    public void VerifyOrThrow_ThrowsIntegrityExceptionOnMismatch()
    {
        string path = Path.Combine(_tempRoot, "a.bin");
        File.WriteAllBytes(path, new byte[] { 1, 2, 3 });
        var ex = Assert.Throws<IntegrityException>(() => Sha256.VerifyOrThrow(path, new string('0', 64), "test file"));
        Assert.Contains("test file", ex.Message);
    }

    [Fact]
    public void VerifyOrThrow_IsCaseInsensitive()
    {
        string path = Path.Combine(_tempRoot, "a.bin");
        File.WriteAllBytes(path, new byte[] { 9, 9, 9 });
        string hash = Sha256.ComputeFileHashHex(path).ToUpperInvariant();
        Sha256.VerifyOrThrow(path, hash, "test file"); // must not throw despite case difference
    }
}
