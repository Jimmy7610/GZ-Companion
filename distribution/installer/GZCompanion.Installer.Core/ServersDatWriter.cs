using System.Text;

namespace GZCompanion.Installer.Core;

/// <summary>
/// Minimal, deterministic reader/writer for Minecraft's <c>servers.dat</c> NBT format - an
/// UNCOMPRESSED NBT file (unlike the gzip-compressed <c>level.dat</c>) whose root is an unnamed
/// TAG_Compound holding one "servers" TAG_List of TAG_Compound entries, each with at least a
/// "name" and an "ip" TAG_String. This is not a general-purpose NBT library - it only understands
/// the exact narrow structure GZ Companion itself writes, which is all it ever needs to read back
/// (an existing isolated servers.dat is never merged into, only preserved untouched - see
/// <see cref="InstallEngine"/>).
/// </summary>
public static class ServersDatWriter
{
    private const byte TagEnd = 0;
    private const byte TagString = 8;
    private const byte TagList = 9;
    private const byte TagCompound = 10;

    /// <summary>Builds a servers.dat containing exactly one server entry with "name" and "ip".</summary>
    public static byte[] BuildSingleServerServersDat(string serverName, string serverIp)
    {
        using var stream = new MemoryStream();
        using var writer = new BinaryWriter(stream);

        writer.Write(TagCompound);
        WriteNbtString(writer, string.Empty); // unnamed root compound

        writer.Write(TagList);
        WriteNbtString(writer, "servers");
        writer.Write(TagCompound); // element type of the list
        WriteBigEndianInt(writer, 1); // one entry

        // The single server compound entry - list elements carry no name of their own.
        writer.Write(TagString);
        WriteNbtString(writer, "name");
        WriteNbtString(writer, serverName);

        writer.Write(TagString);
        WriteNbtString(writer, "ip");
        WriteNbtString(writer, serverIp);

        writer.Write(TagEnd); // end of the server compound
        writer.Write(TagEnd); // end of the root compound

        return stream.ToArray();
    }

    /// <summary>
    /// Writes the servers.dat to <paramref name="path"/> only if it does not already exist yet -
    /// never overwrites an existing file, not even byte-for-byte identical content, so a reinstall
    /// or update can never disturb a player's own existing server list.
    /// </summary>
    public static bool WriteIfAbsent(string path, string serverName, string serverIp)
    {
        if (File.Exists(path)) return false;
        Directory.CreateDirectory(Path.GetDirectoryName(path)!);
        File.WriteAllBytes(path, BuildSingleServerServersDat(serverName, serverIp));
        return true;
    }

    /// <summary>Parses back the (name, ip) pairs of every entry in the "servers" list, for validating what was actually written.</summary>
    public static List<(string Name, string Ip)> ReadServerEntries(byte[] data)
    {
        using var stream = new MemoryStream(data);
        using var reader = new BinaryReader(stream);

        byte rootTag = reader.ReadByte();
        if (rootTag != TagCompound) throw new FormatException("Expected a root TAG_Compound.");
        ReadNbtString(reader); // root name, ignored

        var result = new List<(string, string)>();
        while (true)
        {
            byte tagId = reader.ReadByte();
            if (tagId == TagEnd) break;
            string tagName = ReadNbtString(reader);

            if (tagId != TagList || tagName != "servers")
            {
                throw new FormatException($"Unexpected tag \"{tagName}\" (id {tagId}) at root - this reader only understands the exact structure BuildSingleServerServersDat writes.");
            }

            byte elementType = reader.ReadByte();
            if (elementType != TagCompound) throw new FormatException("Expected \"servers\" to be a list of TAG_Compound.");
            int count = ReadBigEndianInt(reader);

            for (int i = 0; i < count; i++)
            {
                string? name = null;
                string? ip = null;
                while (true)
                {
                    byte entryTagId = reader.ReadByte();
                    if (entryTagId == TagEnd) break;
                    string entryName = ReadNbtString(reader);
                    if (entryTagId != TagString) throw new FormatException("Unexpected non-string field in a server entry.");
                    string value = ReadNbtString(reader);
                    if (entryName == "name") name = value;
                    else if (entryName == "ip") ip = value;
                }
                if (name == null || ip == null) throw new FormatException("Server entry missing name and/or ip.");
                result.Add((name, ip));
            }
        }
        return result;
    }

    private static void WriteNbtString(BinaryWriter writer, string value)
    {
        byte[] bytes = Encoding.UTF8.GetBytes(value);
        WriteBigEndianShort(writer, (short)bytes.Length);
        writer.Write(bytes);
    }

    private static void WriteBigEndianShort(BinaryWriter writer, short value)
    {
        writer.Write((byte)((value >> 8) & 0xFF));
        writer.Write((byte)(value & 0xFF));
    }

    private static void WriteBigEndianInt(BinaryWriter writer, int value)
    {
        writer.Write((byte)((value >> 24) & 0xFF));
        writer.Write((byte)((value >> 16) & 0xFF));
        writer.Write((byte)((value >> 8) & 0xFF));
        writer.Write((byte)(value & 0xFF));
    }

    private static string ReadNbtString(BinaryReader reader)
    {
        short length = ReadBigEndianShort(reader);
        byte[] bytes = reader.ReadBytes(length);
        return Encoding.UTF8.GetString(bytes);
    }

    private static short ReadBigEndianShort(BinaryReader reader)
    {
        int high = reader.ReadByte();
        int low = reader.ReadByte();
        return (short)((high << 8) | low);
    }

    private static int ReadBigEndianInt(BinaryReader reader)
    {
        int b1 = reader.ReadByte();
        int b2 = reader.ReadByte();
        int b3 = reader.ReadByte();
        int b4 = reader.ReadByte();
        return (b1 << 24) | (b2 << 16) | (b3 << 8) | b4;
    }
}
