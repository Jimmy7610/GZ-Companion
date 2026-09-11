namespace GZCompanion.Installer.Core;

/// <summary>
/// Writes a file by first writing to a temp file in the same directory, then renaming it over the
/// destination. On the same volume, <see cref="File.Replace(string, string, string?)"/>/<see cref="File.Move(string, string, bool)"/>
/// are effectively atomic - a crash mid-install can never leave a half-written launcher_profiles.json
/// or a half-written mod jar in place.
/// </summary>
public static class AtomicFileWriter
{
    public static void WriteAllTextAtomically(string destinationPath, string content)
    {
        string dir = Path.GetDirectoryName(destinationPath) ?? ".";
        Directory.CreateDirectory(dir);
        string tempPath = Path.Combine(dir, $".{Path.GetFileName(destinationPath)}.tmp-{Guid.NewGuid():N}");
        File.WriteAllText(tempPath, content);
        try
        {
            File.Move(tempPath, destinationPath, overwrite: true);
        }
        catch
        {
            TryDelete(tempPath);
            throw;
        }
    }

    public static void CopyFileAtomically(string sourcePath, string destinationPath)
    {
        string dir = Path.GetDirectoryName(destinationPath) ?? ".";
        Directory.CreateDirectory(dir);
        string tempPath = Path.Combine(dir, $".{Path.GetFileName(destinationPath)}.tmp-{Guid.NewGuid():N}");
        File.Copy(sourcePath, tempPath, overwrite: true);
        try
        {
            File.Move(tempPath, destinationPath, overwrite: true);
        }
        catch
        {
            TryDelete(tempPath);
            throw;
        }
    }

    private static void TryDelete(string path)
    {
        try { File.Delete(path); } catch { /* best effort cleanup only */ }
    }
}
