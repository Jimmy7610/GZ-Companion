using GZCompanion.Installer.Core;
using Xunit;

namespace GZCompanion.Installer.Tests;

public class ServersDatWriterTests : IDisposable
{
    private readonly string _tempRoot = Directory.CreateTempSubdirectory("gzc-serversdat-test-").FullName;
    public void Dispose() { try { Directory.Delete(_tempRoot, recursive: true); } catch { } }

    [Fact]
    public void BuildSingleServerServersDat_RootIsAnUnnamedCompoundTag()
    {
        byte[] data = ServersDatWriter.BuildSingleServerServersDat("GameZoneMC", "play.gamezonemc.se");

        Assert.Equal(0x0A, data[0]); // TAG_Compound
        Assert.Equal(0x00, data[1]); // name length high byte
        Assert.Equal(0x00, data[2]); // name length low byte - empty root name
    }

    [Fact]
    public void BuildSingleServerServersDat_EndsWithTheRootCompoundsClosingTagEnd()
    {
        byte[] data = ServersDatWriter.BuildSingleServerServersDat("GameZoneMC", "play.gamezonemc.se");
        Assert.Equal(0x00, data[^1]);
    }

    [Fact]
    public void BuildSingleServerServersDat_RoundTripsToExactlyOneEntry()
    {
        byte[] data = ServersDatWriter.BuildSingleServerServersDat("GameZoneMC", "play.gamezonemc.se");

        var entries = ServersDatWriter.ReadServerEntries(data);

        Assert.Single(entries);
        Assert.Equal("GameZoneMC", entries[0].Name);
        Assert.Equal("play.gamezonemc.se", entries[0].Ip);
    }

    [Fact]
    public void BuildSingleServerServersDat_RoundTripsNonAsciiNamesCorrectly()
    {
        // UTF-8 string length is a byte count, not a char count - a real regression risk if the
        // writer ever used .Length (char count) instead of the UTF-8 byte count for the NBT string.
        byte[] data = ServersDatWriter.BuildSingleServerServersDat("Servern på GameZone", "play.gamezonemc.se");

        var entries = ServersDatWriter.ReadServerEntries(data);

        Assert.Equal("Servern på GameZone", entries[0].Name);
    }

    [Fact]
    public void BuildSingleServerServersDat_IsFullyDeterministic()
    {
        byte[] first = ServersDatWriter.BuildSingleServerServersDat("GameZoneMC", "play.gamezonemc.se");
        byte[] second = ServersDatWriter.BuildSingleServerServersDat("GameZoneMC", "play.gamezonemc.se");
        Assert.Equal(first, second);
    }

    [Fact]
    public void WriteIfAbsent_CreatesTheFileAndParentDirectoryOnAFreshInstall()
    {
        string path = Path.Combine(_tempRoot, "nested", "minecraft", "servers.dat");

        bool created = ServersDatWriter.WriteIfAbsent(path, "GameZoneMC", "play.gamezonemc.se");

        Assert.True(created);
        Assert.True(File.Exists(path));
        var entries = ServersDatWriter.ReadServerEntries(File.ReadAllBytes(path));
        Assert.Equal("GameZoneMC", entries[0].Name);
        Assert.Equal("play.gamezonemc.se", entries[0].Ip);
    }

    [Fact]
    public void WriteIfAbsent_NeverOverwritesAnExistingFileByteForByte()
    {
        string path = Path.Combine(_tempRoot, "servers.dat");
        byte[] existing = { 0xDE, 0xAD, 0xBE, 0xEF }; // deliberately not valid NBT
        File.WriteAllBytes(path, existing);

        bool created = ServersDatWriter.WriteIfAbsent(path, "GameZoneMC", "play.gamezonemc.se");

        Assert.False(created);
        Assert.Equal(existing, File.ReadAllBytes(path));
    }

    [Fact]
    public void ReadServerEntries_RejectsAnUnexpectedRootTag()
    {
        byte[] notNbt = { 0x01, 0x02, 0x03 };
        Assert.Throws<FormatException>(() => ServersDatWriter.ReadServerEntries(notNbt));
    }
}
