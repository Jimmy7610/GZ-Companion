using System.Net.Http.Headers;
using System.Security.Cryptography;

namespace GZCompanion.Installer.Core;

/// <summary>Thrown when a downloaded file's hash doesn't match what was expected - never silently accepted.</summary>
public sealed class IntegrityException : Exception
{
    public IntegrityException(string message) : base(message) { }
}

/// <summary>
/// Download seam so install orchestration logic is testable without real HTTPS calls. The real
/// implementation (<see cref="HttpsFileDownloader"/>) only ever speaks HTTPS.
/// </summary>
public interface IFileDownloader
{
    Task DownloadToFileAsync(Uri url, string destinationPath, IProgress<double>? progress, CancellationToken ct);
    Task<string> DownloadTextAsync(Uri url, CancellationToken ct);
}

public sealed class HttpsFileDownloader : IFileDownloader, IDisposable
{
    private readonly HttpClient _http;

    public HttpsFileDownloader(string userAgent)
    {
        _http = new HttpClient();
        _http.DefaultRequestHeaders.UserAgent.ParseAdd(userAgent);
    }

    public async Task DownloadToFileAsync(Uri url, string destinationPath, IProgress<double>? progress, CancellationToken ct)
    {
        if (url.Scheme != Uri.UriSchemeHttps)
        {
            throw new InvalidOperationException($"Refusing non-HTTPS download URL: {url}");
        }

        string dir = Path.GetDirectoryName(destinationPath) ?? ".";
        Directory.CreateDirectory(dir);
        string tempPath = Path.Combine(dir, $".{Path.GetFileName(destinationPath)}.download-{Guid.NewGuid():N}");

        using (var response = await _http.GetAsync(url, HttpCompletionOption.ResponseHeadersRead, ct).ConfigureAwait(false))
        {
            response.EnsureSuccessStatusCode();
            long? total = response.Content.Headers.ContentLength;
            await using var httpStream = await response.Content.ReadAsStreamAsync(ct).ConfigureAwait(false);
            await using var fileStream = new FileStream(tempPath, FileMode.Create, FileAccess.Write, FileShare.None);

            var buffer = new byte[81920];
            long readTotal = 0;
            int read;
            while ((read = await httpStream.ReadAsync(buffer, ct).ConfigureAwait(false)) > 0)
            {
                await fileStream.WriteAsync(buffer.AsMemory(0, read), ct).ConfigureAwait(false);
                readTotal += read;
                if (total is > 0)
                {
                    progress?.Report((double)readTotal / total.Value);
                }
            }
        }

        File.Move(tempPath, destinationPath, overwrite: true);
    }

    public async Task<string> DownloadTextAsync(Uri url, CancellationToken ct)
    {
        if (url.Scheme != Uri.UriSchemeHttps)
        {
            throw new InvalidOperationException($"Refusing non-HTTPS download URL: {url}");
        }
        return await _http.GetStringAsync(url, ct).ConfigureAwait(false);
    }

    public void Dispose() => _http.Dispose();
}

public static class Sha256
{
    public static string ComputeFileHashHex(string path)
    {
        using var sha = SHA256.Create();
        using var stream = File.OpenRead(path);
        byte[] hash = sha.ComputeHash(stream);
        return Convert.ToHexStringLower(hash);
    }

    /// <summary>Verifies a file's SHA-256 matches (case-insensitive hex). Throws <see cref="IntegrityException"/> on mismatch - never a silent pass.</summary>
    public static void VerifyOrThrow(string path, string expectedHexSha256, string whatForErrorMessage)
    {
        string actual = ComputeFileHashHex(path);
        if (!string.Equals(actual, expectedHexSha256, StringComparison.OrdinalIgnoreCase))
        {
            throw new IntegrityException($"Checksum mismatch for {whatForErrorMessage}: expected {expectedHexSha256}, got {actual}.");
        }
    }
}
