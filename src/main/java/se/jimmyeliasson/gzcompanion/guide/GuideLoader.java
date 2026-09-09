package se.jimmyeliasson.gzcompanion.guide;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.jimmyeliasson.gzcompanion.guide.model.*;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Loads guide manifests and definitions from bundled classpath resources or custom paths.
 * All JSON parsing is type-safe and fails closed with detailed error messages on malformed inputs.
 */
public class GuideLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(GuideLoader.class);
    private static final String DEFAULT_MANIFEST_PATH = "/assets/gzcompanion/guides/manifest.json";
    private static final String DEFAULT_GUIDES_DIR = "/assets/gzcompanion/guides/";

    public static final int SUPPORTED_SCHEMA_VERSION = 1;

    public record LoadResult(GuideManifest manifest, List<GuideDefinition> guides, List<String> warnings, List<String> errors) {
        public boolean isSuccess() {
            return errors.isEmpty() && !guides.isEmpty();
        }
    }

    public LoadResult loadBundled() {
        return loadFromPath(DEFAULT_MANIFEST_PATH, DEFAULT_GUIDES_DIR);
    }

    public LoadResult loadFromPath(String manifestPath, String guidesDir) {
        List<String> warnings = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        JsonObject manifestJson = loadJsonObject(manifestPath, errors);
        if (manifestJson == null) {
            errors.add("Kunde inte ladda manifest från " + manifestPath);
            return new LoadResult(null, Collections.emptyList(), warnings, errors);
        }

        GuideManifest manifest = parseManifest(manifestJson, warnings, errors);
        if (manifest == null || !errors.isEmpty()) {
            return new LoadResult(manifest, Collections.emptyList(), warnings, errors);
        }

        List<GuideDefinition> guides = new ArrayList<>();

        for (GuideHeader header : manifest.guides()) {
            String guideFile = guidesDir + header.file();
            JsonObject guideJson = loadJsonObject(guideFile, errors);
            if (guideJson == null) {
                errors.add("Kunde inte ladda guidefil: " + guideFile);
                continue;
            }

            GuideDefinition guide = parseGuide(guideJson, header, warnings, errors);
            if (guide != null) {
                GuideValidator.ValidationResult valResult = GuideValidator.validate(guide);
                warnings.addAll(valResult.warnings());
                if (!valResult.isValid()) {
                    errors.addAll(valResult.errors());
                } else {
                    guides.add(guide);
                }
            }
        }

        return new LoadResult(manifest, guides, warnings, errors);
    }

    public GuideManifest parseManifest(JsonObject json, List<String> warnings, List<String> errors) {
        if (json == null) {
            errors.add("Manifest JSON är null");
            return null;
        }

        Integer schemaVersion = getRequiredInt(json, "schemaVersion", errors);
        if (schemaVersion == null) {
            return null;
        }

        if (schemaVersion <= 0) {
            errors.add("Ogiltig manifest schemaVersion: " + schemaVersion + " (måste vara > 0)");
            return null;
        }
        if (schemaVersion > SUPPORTED_SCHEMA_VERSION) {
            errors.add("Stöds ej: manifest schemaVersion " + schemaVersion + " (stödd version: " + SUPPORTED_SCHEMA_VERSION + ")");
            return null;
        }

        String contentVersion = getOptionalString(json, "contentVersion", "2026.09.09.1", errors);
        String locale = getOptionalString(json, "locale", "sv-SE", errors);

        List<String> testedVersions = getOptionalStringList(json, "testedMinecraftVersions", errors);
        if (testedVersions == null) {
            return null;
        }

        JsonArray guidesArr = getRequiredArray(json, "guides", errors);
        if (guidesArr == null) {
            return null;
        }

        List<GuideHeader> headers = new ArrayList<>();
        for (JsonElement el : guidesArr) {
            if (!el.isJsonObject()) {
                errors.add("Element i 'guides' måste vara JSON-objekt");
                return null;
            }
            JsonObject obj = el.getAsJsonObject();
            String id = getRequiredString(obj, "id", errors);
            String file = getRequiredString(obj, "file", errors);
            String title = getRequiredString(obj, "title", errors);
            String desc = getOptionalString(obj, "description", "", errors);
            if (id == null || file == null || title == null || desc == null) {
                return null;
            }
            headers.add(new GuideHeader(id, file, title, desc));
        }

        return new GuideManifest(schemaVersion, contentVersion, locale, testedVersions, headers);
    }

    public GuideDefinition parseGuide(JsonObject json, GuideHeader header, List<String> warnings, List<String> errors) {
        if (json == null) {
            errors.add("Guide JSON är null");
            return null;
        }

        Integer schemaVersion = getRequiredInt(json, "schemaVersion", errors);
        if (schemaVersion == null) {
            return null;
        }

        if (schemaVersion <= 0) {
            errors.add("Ogiltig guide schemaVersion: " + schemaVersion + " (måste vara > 0)");
            return null;
        }
        if (schemaVersion > SUPPORTED_SCHEMA_VERSION) {
            errors.add("Stöds ej: guide schemaVersion " + schemaVersion + " (stödd version: " + SUPPORTED_SCHEMA_VERSION + ")");
            return null;
        }

        String fallbackId = header != null ? header.id() : "";
        String fallbackTitle = header != null ? header.title() : "";
        String fallbackDesc = header != null ? header.description() : "";

        String id = getOptionalString(json, "id", fallbackId, errors);
        String title = getOptionalString(json, "title", fallbackTitle, errors);
        String desc = getOptionalString(json, "description", fallbackDesc, errors);
        if (id == null || title == null || desc == null) {
            return null;
        }

        JsonArray chaptersArr = getRequiredArray(json, "chapters", errors);
        if (chaptersArr == null) {
            return null;
        }

        List<GuideChapter> chapters = new ArrayList<>();
        for (JsonElement chEl : chaptersArr) {
            if (!chEl.isJsonObject()) {
                errors.add("Element i 'chapters' måste vara JSON-objekt");
                return null;
            }
            JsonObject chObj = chEl.getAsJsonObject();
            String chId = getRequiredString(chObj, "id", errors);
            String chTitle = getRequiredString(chObj, "title", errors);
            Integer chOrder = getOptionalInt(chObj, "order", chapters.size() + 1, errors);
            if (chId == null || chTitle == null || chOrder == null) {
                return null;
            }
            chapters.add(new GuideChapter(chId, chTitle, chOrder));
        }

        JsonArray stepsArr = getRequiredArray(json, "steps", errors);
        if (stepsArr == null) {
            return null;
        }

        List<GuideStep> steps = new ArrayList<>();
        for (JsonElement stEl : stepsArr) {
            if (!stEl.isJsonObject()) {
                errors.add("Element i 'steps' måste vara JSON-objekt");
                return null;
            }
            GuideStep parsedStep = parseStep(stEl.getAsJsonObject(), steps.size() + 1, warnings, errors);
            if (parsedStep == null) {
                return null;
            }
            steps.add(parsedStep);
        }

        return new GuideDefinition(schemaVersion, id, title, desc, chapters, steps);
    }

    private GuideStep parseStep(JsonObject obj, int defaultOrder, List<String> warnings, List<String> errors) {
        String id = getRequiredString(obj, "id", errors);
        String chapterId = getRequiredString(obj, "chapterId", errors);
        Integer order = getOptionalInt(obj, "order", defaultOrder, errors);
        String title = getRequiredString(obj, "title", errors);
        String summary = getOptionalString(obj, "summary", "", errors);
        String desc = getOptionalString(obj, "description", summary != null ? summary : "", errors);
        String why = getOptionalString(obj, "why", null, errors);
        String tip = getOptionalString(obj, "tip", null, errors);
        String warning = getOptionalString(obj, "warning", null, errors);
        Boolean optional = getOptionalBoolean(obj, "optional", false, errors);
        Boolean manualAllowed = getOptionalBoolean(obj, "manualCompletionAllowed", true, errors);

        if (id == null || chapterId == null || order == null || title == null || summary == null || desc == null || optional == null || manualAllowed == null) {
            return null;
        }

        List<String> prerequisites = getOptionalStringList(obj, "prerequisites", errors);
        if (prerequisites == null) {
            return null;
        }

        List<String> supersededBy = getOptionalStringList(obj, "supersededBy", errors);
        if (supersededBy == null) {
            return null;
        }

        List<GuideCondition> conditions = new ArrayList<>();
        if (obj.has("conditions")) {
            JsonArray condArr = getOptionalArray(obj, "conditions", errors);
            if (condArr == null) {
                return null;
            }
            for (JsonElement el : condArr) {
                if (!el.isJsonObject()) {
                    errors.add("Element i 'conditions' måste vara JSON-objekt");
                    return null;
                }
                GuideCondition cond = parseCondition(el.getAsJsonObject(), errors);
                if (cond == null) {
                    return null;
                }
                conditions.add(cond);
            }
        } else if (obj.has("condition")) {
            if (!obj.get("condition").isJsonObject()) {
                errors.add("'condition' måste vara ett JSON-objekt");
                return null;
            }
            GuideCondition cond = parseCondition(obj.getAsJsonObject("condition"), errors);
            if (cond == null) {
                return null;
            }
            conditions.add(cond);
        }

        if (conditions.isEmpty() && (obj.has("conditions") || obj.has("condition"))) {
            return null;
        }

        if (conditions.isEmpty()) {
            conditions.add(GuideCondition.manual());
        }

        return new GuideStep(id, chapterId, order, title, summary, desc, why, tip, warning, prerequisites, optional, manualAllowed, conditions, supersededBy);
    }

    private GuideCondition parseCondition(JsonObject obj, List<String> errors) {
        String typeStr = getRequiredString(obj, "type", errors);
        if (typeStr == null || typeStr.isBlank()) {
            errors.add("Villkor saknar typ (type)");
            return null;
        }

        GuideConditionType type;
        try {
            type = GuideConditionType.valueOf(typeStr.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            errors.add("Okänd eller ogiltig villkorstyp: '" + typeStr + "'");
            return null;
        }

        String itemId = getOptionalString(obj, "itemId", null, errors);
        List<String> itemIds = getOptionalStringList(obj, "itemIds", errors);
        if (itemIds == null) {
            return null;
        }

        String itemTag = getOptionalString(obj, "itemTag", null, errors);
        String tag = getOptionalString(obj, "tag", itemTag, errors);
        Integer count = getOptionalInt(obj, "count", 1, errors);
        if (count == null) {
            return null;
        }
        String description = getOptionalString(obj, "description", null, errors);

        List<GuideCondition> subConditions = new ArrayList<>();
        if (obj.has("subConditions")) {
            JsonArray subArr = getOptionalArray(obj, "subConditions", errors);
            if (subArr == null) {
                return null;
            }
            for (JsonElement el : subArr) {
                if (!el.isJsonObject()) {
                    errors.add("Element i 'subConditions' måste vara JSON-objekt");
                    return null;
                }
                GuideCondition sub = parseCondition(el.getAsJsonObject(), errors);
                if (sub == null) {
                    return null;
                }
                subConditions.add(sub);
            }
        }

        return new GuideCondition(type, itemId, itemIds, tag, count, description, subConditions);
    }

    // Type-safe JSON extraction helpers

    public static Integer getOptionalInt(JsonObject obj, String key, Integer fallback, List<String> errors) {
        if (obj == null || !obj.has(key)) return fallback;
        JsonElement el = obj.get(key);
        if (el.isJsonNull()) return fallback;
        if (!el.isJsonPrimitive()) {
            errors.add("Fältet '" + key + "' måste vara ett heltal (hittade icke-primitivt värde)");
            return null;
        }
        JsonPrimitive prim = el.getAsJsonPrimitive();
        if (prim.isNumber()) {
            try {
                return prim.getAsInt();
            } catch (Exception e) {
                errors.add("Fältet '" + key + "' har ogiltigt talformat: " + prim);
                return null;
            }
        }
        errors.add("Fältet '" + key + "' måste vara ett heltal (hittade: " + prim + ")");
        return null;
    }

    public static Integer getRequiredInt(JsonObject obj, String key, List<String> errors) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            errors.add("Obligatoriskt fält '" + key + "' saknas");
            return null;
        }
        return getOptionalInt(obj, key, null, errors);
    }

    public static Boolean getOptionalBoolean(JsonObject obj, String key, Boolean fallback, List<String> errors) {
        if (obj == null || !obj.has(key)) return fallback;
        JsonElement el = obj.get(key);
        if (el.isJsonNull()) return fallback;
        if (!el.isJsonPrimitive()) {
            errors.add("Fältet '" + key + "' måste vara en boolean (hittade icke-primitivt värde)");
            return null;
        }
        JsonPrimitive prim = el.getAsJsonPrimitive();
        if (prim.isBoolean()) {
            return prim.getAsBoolean();
        }
        errors.add("Fältet '" + key + "' måste vara en boolean (hittade: " + prim + ")");
        return null;
    }

    public static Boolean getRequiredBoolean(JsonObject obj, String key, List<String> errors) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            errors.add("Obligatoriskt fält '" + key + "' saknas");
            return null;
        }
        return getOptionalBoolean(obj, key, null, errors);
    }

    public static String getOptionalString(JsonObject obj, String key, String fallback, List<String> errors) {
        if (obj == null || !obj.has(key)) return fallback;
        JsonElement el = obj.get(key);
        if (el.isJsonNull()) return fallback;
        if (!el.isJsonPrimitive() || !el.getAsJsonPrimitive().isString()) {
            errors.add("Fältet '" + key + "' måste vara en textsträng");
            return null;
        }
        return el.getAsString();
    }

    public static String getRequiredString(JsonObject obj, String key, List<String> errors) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            errors.add("Obligatoriskt fält '" + key + "' saknas");
            return null;
        }
        return getOptionalString(obj, key, null, errors);
    }

    public static JsonArray getOptionalArray(JsonObject obj, String key, List<String> errors) {
        if (obj == null || !obj.has(key)) return null;
        JsonElement el = obj.get(key);
        if (el.isJsonNull()) return null;
        if (!el.isJsonArray()) {
            errors.add("Fältet '" + key + "' måste vara en lista (JSON array)");
            return null;
        }
        return el.getAsJsonArray();
    }

    public static JsonArray getRequiredArray(JsonObject obj, String key, List<String> errors) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            errors.add("Obligatoriskt fält '" + key + "' saknas");
            return null;
        }
        return getOptionalArray(obj, key, errors);
    }

    public static List<String> getOptionalStringList(JsonObject obj, String key, List<String> errors) {
        if (obj == null || !obj.has(key)) return Collections.emptyList();
        JsonElement el = obj.get(key);
        if (el.isJsonNull()) return Collections.emptyList();
        if (!el.isJsonArray()) {
            errors.add("Fältet '" + key + "' måste vara en lista (JSON array)");
            return null;
        }
        List<String> list = new ArrayList<>();
        for (JsonElement item : el.getAsJsonArray()) {
            if (!item.isJsonPrimitive() || !item.getAsJsonPrimitive().isString()) {
                errors.add("Element i listan '" + key + "' måste vara strängar");
                return null;
            }
            list.add(item.getAsString());
        }
        return list;
    }

    private JsonObject loadJsonObject(String resourcePath, List<String> errors) {
        try {
            InputStream stream = getClass().getResourceAsStream(resourcePath);
            if (stream == null) {
                stream = GuideLoader.class.getClassLoader().getResourceAsStream(
                        resourcePath.startsWith("/") ? resourcePath.substring(1) : resourcePath
                );
            }
            if (stream == null) {
                errors.add("Guide-resurs saknas: " + resourcePath);
                return null;
            }
            try (InputStream is = stream; InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                JsonElement element = JsonParser.parseReader(reader);
                if (element != null && element.isJsonObject()) {
                    return element.getAsJsonObject();
                }
            }
            errors.add("Felaktig JSON i guide-resurs: " + resourcePath);
        } catch (Exception e) {
            LOGGER.warn("Kunde inte läsa guidefil {}: {}", resourcePath, e.getMessage());
            errors.add("Undantag vid inläsning av " + resourcePath + ": " + e.getMessage());
        }
        return null;
    }
}
