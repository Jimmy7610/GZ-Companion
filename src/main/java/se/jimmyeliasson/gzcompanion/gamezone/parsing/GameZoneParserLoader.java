package se.jimmyeliasson.gzcompanion.gamezone.parsing;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.jimmyeliasson.gzcompanion.gamezone.events.GameZoneEventType;
import se.jimmyeliasson.gzcompanion.knowledge.common.KnowledgeLoadResult;
import se.jimmyeliasson.gzcompanion.knowledge.common.VerificationMetadata;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Loads the bundled GameZone chat/event parser rules from {@code gamezone-pack/parsers.json}.
 * A parser with an unknown {@code eventType}/{@code matchType}, a blank/duplicate id, or (for
 * {@code REGEX}) an invalid pattern is skipped with a warning rather than failing the whole file -
 * one malformed parser can never break the others.
 */
public final class GameZoneParserLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(GameZoneParserLoader.class);
    private static final String DEFAULT_RESOURCE_PATH = "/gamezone-pack/parsers.json";
    private static final int CURRENT_SCHEMA = 1;

    private final String resourcePath;

    public GameZoneParserLoader() {
        this(DEFAULT_RESOURCE_PATH);
    }

    public GameZoneParserLoader(String resourcePath) {
        this.resourcePath = resourcePath;
    }

    public KnowledgeLoadResult<GameZoneParserCatalog> load() {
        JsonObject root;
        try (InputStream stream = GameZoneParserLoader.class.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                LOGGER.error("Parser catalog resource not found: {}", resourcePath);
                return KnowledgeLoadResult.error(GameZoneParserCatalog.empty());
            }
            try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                JsonElement element = JsonParser.parseReader(reader);
                if (element == null || !element.isJsonObject()) {
                    LOGGER.error("Parser catalog {} did not contain a JSON object.", resourcePath);
                    return KnowledgeLoadResult.error(GameZoneParserCatalog.empty());
                }
                root = element.getAsJsonObject();
            }
        } catch (Exception e) {
            LOGGER.error("Failed to read parser catalog {}: {}", resourcePath, e.getMessage());
            return KnowledgeLoadResult.error(GameZoneParserCatalog.empty());
        }

        int schemaVersion = (root.has("schemaVersion") && !root.get("schemaVersion").isJsonNull())
                ? root.get("schemaVersion").getAsInt() : 1;
        if (schemaVersion < 1) {
            LOGGER.error("Parser catalog declares invalid schemaVersion {}.", schemaVersion);
            return KnowledgeLoadResult.error(GameZoneParserCatalog.empty());
        }
        if (schemaVersion > CURRENT_SCHEMA) {
            LOGGER.warn("Parser catalog schemaVersion {} is newer than this build supports ({}).", schemaVersion, CURRENT_SCHEMA);
            return KnowledgeLoadResult.incompatibleSchema(GameZoneParserCatalog.empty());
        }

        List<String> warnings = new ArrayList<>();
        List<GameZoneParserDefinition> parsers = parseParsers(root, warnings);
        return KnowledgeLoadResult.loaded(new GameZoneParserCatalog(parsers, warnings));
    }

    private List<GameZoneParserDefinition> parseParsers(JsonObject root, List<String> warnings) {
        List<GameZoneParserDefinition> result = new ArrayList<>();
        if (!root.has("parsers") || !root.get("parsers").isJsonArray()) return result;

        Set<String> seenIds = new HashSet<>();
        for (JsonElement el : root.getAsJsonArray("parsers")) {
            if (!el.isJsonObject()) continue;
            try {
                JsonObject obj = el.getAsJsonObject();

                String id = getString(obj, "id", null);
                if (id == null || id.isBlank()) {
                    warnings.add("Hoppar över parser utan giltigt id.");
                    continue;
                }
                if (!seenIds.add(id)) {
                    warnings.add("Dubblerat parser-id ignorerat: " + id);
                    continue;
                }

                GameZoneEventType eventType = parseEventType(getString(obj, "eventType", null));
                if (eventType == null) {
                    warnings.add("Hoppar över parser '" + id + "' med okänd/saknad eventType.");
                    continue;
                }

                ParserMatchType matchType = parseMatchType(getString(obj, "matchType", null));
                if (matchType == null) {
                    warnings.add("Hoppar över parser '" + id + "' med okänd/saknad matchType.");
                    continue;
                }

                String pattern = getString(obj, "pattern", null);
                if (pattern == null || pattern.isBlank()) {
                    warnings.add("Hoppar över parser '" + id + "' utan pattern.");
                    continue;
                }

                List<String> captureGroupNames = readStringArray(obj, "captureGroups");
                boolean enabled = obj.has("enabled") && obj.get("enabled").isJsonPrimitive() && obj.get("enabled").getAsBoolean();
                VerificationMetadata verification = parseVerification(obj);

                GameZoneParserDefinition parser = new GameZoneParserDefinition(id, eventType, matchType, pattern, captureGroupNames, enabled, verification);
                if (!parser.hasValidRegexSyntax()) {
                    warnings.add("Hoppar över parser '" + id + "': ogiltigt reguljärt uttryck.");
                    continue;
                }

                result.add(parser);
            } catch (Exception ex) {
                warnings.add("Hoppar över felformad parserpost: " + ex.getMessage());
            }
        }
        return result;
    }

    private VerificationMetadata parseVerification(JsonObject obj) {
        if (!obj.has("verification") || !obj.get("verification").isJsonObject()) {
            return VerificationMetadata.UNVERIFIED_DEFAULT;
        }
        JsonObject v = obj.getAsJsonObject("verification");
        return VerificationMetadata.of(
                getString(v, "status", null),
                getString(v, "sourceName", null),
                getString(v, "sourceReference", null),
                getString(v, "lastVerified", null));
    }

    private static GameZoneEventType parseEventType(String raw) {
        if (raw == null) return null;
        try {
            return GameZoneEventType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static ParserMatchType parseMatchType(String raw) {
        if (raw == null) return null;
        try {
            return ParserMatchType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
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

    private static String getString(JsonObject obj, String key, String fallback) {
        if (obj.has(key) && !obj.get(key).isJsonNull() && obj.get(key).isJsonPrimitive()) {
            return obj.get(key).getAsString();
        }
        return fallback;
    }
}
