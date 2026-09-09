package se.jimmyeliasson.gzcompanion.knowledge.commands;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.jimmyeliasson.gzcompanion.knowledge.common.KnowledgeLoadResult;
import se.jimmyeliasson.gzcompanion.knowledge.common.VerificationMetadata;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Loads the bundled GameZone command catalog from {@code gamezone-pack/commands.json} (a
 * read-only classpath resource, packaged by the same {@code processResources} step that already
 * bundles the rest of {@code gamezone-pack/}). Loaded exactly once at startup; the resulting
 * {@link CommandCatalog} is then searched purely in memory.
 */
public final class CommandKnowledgeLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(CommandKnowledgeLoader.class);
    private static final String DEFAULT_RESOURCE_PATH = "/gamezone-pack/commands.json";
    private static final int CURRENT_SCHEMA = 1;

    private final String resourcePath;

    public CommandKnowledgeLoader() {
        this(DEFAULT_RESOURCE_PATH);
    }

    public CommandKnowledgeLoader(String resourcePath) {
        this.resourcePath = resourcePath;
    }

    public KnowledgeLoadResult<CommandCatalog> load() {
        JsonObject root;
        try (InputStream stream = CommandKnowledgeLoader.class.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                LOGGER.error("Command catalog resource not found: {}", resourcePath);
                return KnowledgeLoadResult.error(CommandCatalog.empty());
            }
            try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                JsonElement element = JsonParser.parseReader(reader);
                if (element == null || !element.isJsonObject()) {
                    LOGGER.error("Command catalog {} did not contain a JSON object.", resourcePath);
                    return KnowledgeLoadResult.error(CommandCatalog.empty());
                }
                root = element.getAsJsonObject();
            }
        } catch (Exception e) {
            LOGGER.error("Failed to read command catalog {}: {}", resourcePath, e.getMessage());
            return KnowledgeLoadResult.error(CommandCatalog.empty());
        }

        int schemaVersion = (root.has("schemaVersion") && !root.get("schemaVersion").isJsonNull())
                ? root.get("schemaVersion").getAsInt() : 1;
        if (schemaVersion < 1) {
            LOGGER.error("Command catalog declares invalid schemaVersion {}.", schemaVersion);
            return KnowledgeLoadResult.error(CommandCatalog.empty());
        }
        if (schemaVersion > CURRENT_SCHEMA) {
            LOGGER.warn("Command catalog schemaVersion {} is newer than this build supports ({}).", schemaVersion, CURRENT_SCHEMA);
            return KnowledgeLoadResult.incompatibleSchema(CommandCatalog.empty());
        }

        List<String> warnings = new ArrayList<>();
        List<CommandCategory> categories = parseCategories(root, warnings);
        List<CommandDefinition> commands = parseCommands(root, warnings);

        return KnowledgeLoadResult.loaded(new CommandCatalog(categories, commands, warnings));
    }

    private List<CommandCategory> parseCategories(JsonObject root, List<String> warnings) {
        List<CommandCategory> result = new ArrayList<>();
        if (!root.has("categories") || !root.get("categories").isJsonArray()) return result;

        Set<String> seenIds = new HashSet<>();
        for (JsonElement el : root.getAsJsonArray("categories")) {
            if (!el.isJsonObject()) continue;
            try {
                JsonObject obj = el.getAsJsonObject();
                String id = getString(obj, "id", null);
                if (id == null || id.isBlank()) {
                    warnings.add("Hoppar över kategori utan giltigt id.");
                    continue;
                }
                if (!seenIds.add(id)) {
                    warnings.add("Dubblerat kategori-id ignorerat: " + id);
                    continue;
                }
                String displayName = getString(obj, "displayName", id);
                int sortOrder = obj.has("sortOrder") && obj.get("sortOrder").isJsonPrimitive() ? obj.get("sortOrder").getAsInt() : 999;
                result.add(new CommandCategory(id, displayName, sortOrder));
            } catch (Exception ex) {
                warnings.add("Hoppar över felformad kategori: " + ex.getMessage());
            }
        }
        return result;
    }

    private List<CommandDefinition> parseCommands(JsonObject root, List<String> warnings) {
        List<CommandDefinition> result = new ArrayList<>();
        if (!root.has("commands") || !root.get("commands").isJsonArray()) return result;

        Set<String> seenIds = new HashSet<>();
        // Tracks every command string (primary or alias) already claimed, normalized, so a
        // later entry can never silently shadow an earlier one - first occurrence in the file
        // always wins, deterministically.
        Set<String> claimedCommandStrings = new HashSet<>();

        for (JsonElement el : root.getAsJsonArray("commands")) {
            if (!el.isJsonObject()) continue;
            try {
                JsonObject obj = el.getAsJsonObject();

                String id = getString(obj, "id", null);
                if (id == null || id.isBlank()) {
                    warnings.add("Hoppar över kommando utan giltigt id.");
                    continue;
                }
                if (!seenIds.add(id)) {
                    warnings.add("Dubblerat kommando-id ignorerat: " + id);
                    continue;
                }

                String primaryCommand = getString(obj, "primaryCommand", null);
                if (primaryCommand == null || primaryCommand.isBlank()) {
                    warnings.add("Hoppar över kommando '" + id + "' utan primaryCommand.");
                    continue;
                }

                String syntax = getString(obj, "syntax", null);
                if (syntax == null || syntax.isBlank()) {
                    warnings.add("Hoppar över kommando '" + id + "' utan syntax.");
                    continue;
                }

                String normalizedPrimary = normalize(primaryCommand);
                if (!claimedCommandStrings.add(normalizedPrimary)) {
                    warnings.add("Dubblerat primärkommando ignorerat: " + primaryCommand + " (id: " + id + ")");
                    continue;
                }

                List<String> aliases = new ArrayList<>();
                Set<String> dedupedAliases = new LinkedHashSet<>();
                if (obj.has("aliases") && obj.get("aliases").isJsonArray()) {
                    for (JsonElement aliasEl : obj.getAsJsonArray("aliases")) {
                        if (!aliasEl.isJsonPrimitive()) continue;
                        String alias = aliasEl.getAsString();
                        if (alias == null || alias.isBlank()) continue;
                        String normalizedAlias = normalize(alias);
                        if (!dedupedAliases.add(normalizedAlias)) continue; // duplicate within this entry
                        if (!claimedCommandStrings.add(normalizedAlias)) {
                            warnings.add("Alias '" + alias + "' för '" + id + "' krockar med ett tidigare kommando/alias och ignoreras.");
                            continue;
                        }
                        aliases.add(alias);
                    }
                }

                String description = getString(obj, "description", "");
                String categoryId = getString(obj, "categoryId", CommandCategory.FALLBACK_ID);

                List<String> keywords = readStringArray(obj, "keywords");
                List<String> examples = readStringArray(obj, "examples");
                String requirements = getString(obj, "requirements", null);

                VerificationMetadata verification = parseVerification(obj);

                result.add(new CommandDefinition(id, primaryCommand, aliases, syntax, description,
                        categoryId, keywords, examples, requirements, verification));
            } catch (Exception ex) {
                warnings.add("Hoppar över felformad kommandopost: " + ex.getMessage());
            }
        }
        return result;
    }

    private VerificationMetadata parseVerification(JsonObject obj) {
        if (!obj.has("verification") || !obj.get("verification").isJsonObject()) {
            return VerificationMetadata.UNVERIFIED_DEFAULT;
        }
        JsonObject v = obj.getAsJsonObject("verification");
        String status = getString(v, "status", null);
        String sourceName = getString(v, "sourceName", null);
        String sourceReference = getString(v, "sourceReference", null);
        String lastVerified = getString(v, "lastVerified", null);
        return VerificationMetadata.of(status, sourceName, sourceReference, lastVerified);
    }

    private List<String> readStringArray(JsonObject obj, String key) {
        List<String> result = new ArrayList<>();
        if (obj.has(key) && obj.get(key).isJsonArray()) {
            for (JsonElement el : obj.getAsJsonArray(key)) {
                if (el.isJsonPrimitive()) result.add(el.getAsString());
            }
        }
        return result;
    }

    private static String normalize(String s) {
        return s.trim().toLowerCase(Locale.ROOT);
    }

    private static String getString(JsonObject obj, String key, String fallback) {
        if (obj.has(key) && !obj.get(key).isJsonNull() && obj.get(key).isJsonPrimitive()) {
            return obj.get(key).getAsString();
        }
        return fallback;
    }
}
