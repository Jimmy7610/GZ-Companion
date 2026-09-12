package se.jimmyeliasson.gzcompanion.update;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The small, strict, versioned {@code update-manifest.json} schema published alongside
 * {@code GZ-Companion-Setup.exe} in every GitHub Release. Parsing is entirely fail-safe: anything
 * malformed, missing, or from an unsupported future schema version returns {@link Optional#empty()}
 * rather than guessing or throwing into caller code - an update whose manifest can't be confidently
 * understood must never be offered.
 *
 * @param installerFileName expected asset file name (e.g. {@code "GZ-Companion-Setup.exe"}).
 * @param installerSha256   lowercase hex SHA-256 the downloaded installer bytes must match exactly.
 * @param installerSizeBytes exact expected byte count of the downloaded installer.
 * @param notes             short, human-readable Swedish release-note lines shown in the update panel.
 */
public record UpdateManifest(
        int schemaVersion,
        String version,
        String channel,
        String minecraftVersion,
        String fabricLoaderVersion,
        String fabricApiVersion,
        String installerFileName,
        String installerSha256,
        long installerSizeBytes,
        List<String> notes
) {
    public static final int SUPPORTED_SCHEMA_VERSION = 1;
    /** The ONLY installer file name this mod will ever accept - not merely "a safe name". */
    public static final String EXPECTED_INSTALLER_FILE_NAME = "GZ-Companion-Setup.exe";
    private static final Pattern SHA256_HEX = Pattern.compile("^[0-9a-fA-F]{64}$");

    public UpdateManifest {
        notes = notes != null ? List.copyOf(notes) : List.of();
    }

    public static Optional<UpdateManifest> parse(String json) {
        if (json == null || json.isBlank()) return Optional.empty();
        try {
            JsonElement root = JsonParser.parseString(json);
            if (root == null || !root.isJsonObject()) return Optional.empty();
            JsonObject obj = root.getAsJsonObject();

            if (!obj.has("schemaVersion") || !obj.get("schemaVersion").isJsonPrimitive()) return Optional.empty();
            int schemaVersion = obj.get("schemaVersion").getAsInt();
            if (schemaVersion != SUPPORTED_SCHEMA_VERSION) return Optional.empty(); // unknown future schema - fail safe

            String version = requiredString(obj, "version");
            String channel = requiredString(obj, "channel");
            String minecraftVersion = requiredString(obj, "minecraftVersion");
            String fabricLoaderVersion = requiredString(obj, "fabricLoaderVersion");
            String fabricApiVersion = requiredString(obj, "fabricApiVersion");
            if (version == null || channel == null || minecraftVersion == null
                    || fabricLoaderVersion == null || fabricApiVersion == null) return Optional.empty();

            if (!obj.has("installer") || !obj.get("installer").isJsonObject()) return Optional.empty();
            JsonObject installer = obj.getAsJsonObject("installer");
            String fileName = requiredString(installer, "fileName");
            String sha256 = requiredString(installer, "sha256");
            if (fileName == null || !isSafeFileName(fileName)
                    || !fileName.equals(EXPECTED_INSTALLER_FILE_NAME)
                    || sha256 == null || !SHA256_HEX.matcher(sha256).matches()) {
                return Optional.empty();
            }
            if (!installer.has("sizeBytes") || !installer.get("sizeBytes").isJsonPrimitive()) return Optional.empty();
            long sizeBytes = installer.get("sizeBytes").getAsLong();
            if (sizeBytes <= 0) return Optional.empty();

            List<String> notes = new ArrayList<>();
            if (obj.has("notes") && obj.get("notes").isJsonArray()) {
                for (JsonElement el : obj.getAsJsonArray("notes")) {
                    if (el != null && el.isJsonPrimitive()) notes.add(el.getAsString());
                }
            }

            return Optional.of(new UpdateManifest(
                    schemaVersion, version, channel.toLowerCase(java.util.Locale.ROOT),
                    minecraftVersion, fabricLoaderVersion, fabricApiVersion,
                    fileName, sha256.toLowerCase(java.util.Locale.ROOT), sizeBytes, notes));
        } catch (Exception e) {
            // Any malformed/unexpected JSON shape must fail safe, never throw into caller code.
            return Optional.empty();
        }
    }

    /**
     * A manifest-declared file name must be a single bare file name - no path separators, no
     * {@code ..} traversal, no drive letters. This is untrusted data ultimately used to build a
     * local file path (see {@code UpdatePaths}), so a manifest that doesn't look like a plain
     * file name is rejected outright rather than sanitized-and-proceeded-with.
     */
    private static boolean isSafeFileName(String name) {
        if (name.isBlank()) return false;
        if (name.contains("/") || name.contains("\\") || name.contains("..") || name.contains(":")) return false;
        return !name.equals(".") && !name.equals("..");
    }

    private static String requiredString(JsonObject obj, String key) {
        if (!obj.has(key) || !obj.get(key).isJsonPrimitive()) return null;
        String value = obj.get(key).getAsString();
        return value.isBlank() ? null : value;
    }
}
