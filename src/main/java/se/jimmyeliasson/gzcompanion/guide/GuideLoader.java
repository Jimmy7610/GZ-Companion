package se.jimmyeliasson.gzcompanion.guide;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
 */
public class GuideLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(GuideLoader.class);
    private static final String DEFAULT_MANIFEST_PATH = "/assets/gzcompanion/guides/manifest.json";
    private static final String DEFAULT_GUIDES_DIR = "/assets/gzcompanion/guides/";

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

        GuideManifest manifest = parseManifest(manifestJson, warnings);
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

    public GuideManifest parseManifest(JsonObject json, List<String> warnings) {
        int schemaVersion = json.has("schemaVersion") ? json.get("schemaVersion").getAsInt() : 1;
        String contentVersion = getString(json, "contentVersion", "2026.09.09.1");
        String locale = getString(json, "locale", "sv-SE");

        List<String> testedVersions = new ArrayList<>();
        if (json.has("testedMinecraftVersions") && json.get("testedMinecraftVersions").isJsonArray()) {
            for (JsonElement el : json.getAsJsonArray("testedMinecraftVersions")) {
                if (el.isJsonPrimitive()) testedVersions.add(el.getAsString());
            }
        }

        List<GuideHeader> headers = new ArrayList<>();
        if (json.has("guides") && json.get("guides").isJsonArray()) {
            for (JsonElement el : json.getAsJsonArray("guides")) {
                if (el.isJsonObject()) {
                    JsonObject obj = el.getAsJsonObject();
                    String id = getString(obj, "id", "");
                    String file = getString(obj, "file", "");
                    String title = getString(obj, "title", "");
                    String desc = getString(obj, "description", "");
                    headers.add(new GuideHeader(id, file, title, desc));
                }
            }
        }

        return new GuideManifest(schemaVersion, contentVersion, locale, testedVersions, headers);
    }

    public GuideDefinition parseGuide(JsonObject json, GuideHeader header, List<String> warnings, List<String> errors) {
        int schemaVersion = json.has("schemaVersion") ? json.get("schemaVersion").getAsInt() : 1;
        String id = getString(json, "id", header != null ? header.id() : "");
        String title = getString(json, "title", header != null ? header.title() : "");
        String desc = getString(json, "description", header != null ? header.description() : "");

        List<GuideChapter> chapters = new ArrayList<>();
        if (json.has("chapters") && json.get("chapters").isJsonArray()) {
            for (JsonElement chEl : json.getAsJsonArray("chapters")) {
                if (chEl.isJsonObject()) {
                    JsonObject chObj = chEl.getAsJsonObject();
                    String chId = getString(chObj, "id", "");
                    String chTitle = getString(chObj, "title", "");
                    int chOrder = chObj.has("order") ? chObj.get("order").getAsInt() : chapters.size() + 1;
                    chapters.add(new GuideChapter(chId, chTitle, chOrder));
                }
            }
        }

        List<GuideStep> steps = new ArrayList<>();
        if (json.has("steps") && json.get("steps").isJsonArray()) {
            for (JsonElement stEl : json.getAsJsonArray("steps")) {
                if (stEl.isJsonObject()) {
                    steps.add(parseStep(stEl.getAsJsonObject(), steps.size() + 1, warnings));
                }
            }
        }

        return new GuideDefinition(schemaVersion, id, title, desc, chapters, steps);
    }

    private GuideStep parseStep(JsonObject obj, int defaultOrder, List<String> warnings) {
        String id = getString(obj, "id", "");
        String chapterId = getString(obj, "chapterId", "");
        int order = obj.has("order") ? obj.get("order").getAsInt() : defaultOrder;
        String title = getString(obj, "title", "");
        String summary = getString(obj, "summary", "");
        String desc = getString(obj, "description", summary);
        String why = getString(obj, "why", null);
        String tip = getString(obj, "tip", null);
        String warning = getString(obj, "warning", null);
        boolean optional = obj.has("optional") && obj.get("optional").getAsBoolean();
        boolean manualAllowed = !obj.has("manualCompletionAllowed") || obj.get("manualCompletionAllowed").getAsBoolean();

        List<String> prerequisites = new ArrayList<>();
        if (obj.has("prerequisites") && obj.get("prerequisites").isJsonArray()) {
            for (JsonElement el : obj.getAsJsonArray("prerequisites")) {
                if (el.isJsonPrimitive()) prerequisites.add(el.getAsString());
            }
        }

        List<String> supersededBy = new ArrayList<>();
        if (obj.has("supersededBy") && obj.get("supersededBy").isJsonArray()) {
            for (JsonElement el : obj.getAsJsonArray("supersededBy")) {
                if (el.isJsonPrimitive()) supersededBy.add(el.getAsString());
            }
        }

        List<GuideCondition> conditions = new ArrayList<>();
        if (obj.has("conditions") && obj.get("conditions").isJsonArray()) {
            for (JsonElement el : obj.getAsJsonArray("conditions")) {
                if (el.isJsonObject()) {
                    conditions.add(parseCondition(el.getAsJsonObject()));
                }
            }
        } else if (obj.has("condition") && obj.get("condition").isJsonObject()) {
            conditions.add(parseCondition(obj.getAsJsonObject("condition")));
        }

        if (conditions.isEmpty()) {
            conditions.add(GuideCondition.manual());
        }

        return new GuideStep(id, chapterId, order, title, summary, desc, why, tip, warning, prerequisites, optional, manualAllowed, conditions, supersededBy);
    }

    private GuideCondition parseCondition(JsonObject obj) {
        String typeStr = getString(obj, "type", "MANUAL");
        GuideConditionType type;
        try {
            type = GuideConditionType.valueOf(typeStr.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            type = GuideConditionType.MANUAL;
        }

        String itemId = getString(obj, "itemId", null);
        List<String> itemIds = new ArrayList<>();
        if (obj.has("itemIds") && obj.get("itemIds").isJsonArray()) {
            for (JsonElement el : obj.getAsJsonArray("itemIds")) {
                if (el.isJsonPrimitive()) itemIds.add(el.getAsString());
            }
        }

        String tag = getString(obj, "tag", getString(obj, "itemTag", null));
        int count = obj.has("count") ? obj.get("count").getAsInt() : 1;
        String description = getString(obj, "description", null);

        List<GuideCondition> subConditions = new ArrayList<>();
        if (obj.has("subConditions") && obj.get("subConditions").isJsonArray()) {
            for (JsonElement el : obj.getAsJsonArray("subConditions")) {
                if (el.isJsonObject()) {
                    subConditions.add(parseCondition(el.getAsJsonObject()));
                }
            }
        }

        return new GuideCondition(type, itemId, itemIds, tag, count, description, subConditions);
    }

    private JsonObject loadJsonObject(String resourcePath, List<String> errors) {
        try (InputStream stream = getClass().getResourceAsStream(resourcePath)) {
            if (stream == null) {
                InputStream clStream = GuideLoader.class.getClassLoader().getResourceAsStream(
                        resourcePath.startsWith("/") ? resourcePath.substring(1) : resourcePath
                );
                if (clStream == null) {
                    errors.add("Guide-resurs saknas: " + resourcePath);
                    return null;
                }
                try (InputStreamReader reader = new InputStreamReader(clStream, StandardCharsets.UTF_8)) {
                    JsonElement element = JsonParser.parseReader(reader);
                    if (element != null && element.isJsonObject()) {
                        return element.getAsJsonObject();
                    }
                }
            } else {
                try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                    JsonElement element = JsonParser.parseReader(reader);
                    if (element != null && element.isJsonObject()) {
                        return element.getAsJsonObject();
                    }
                }
            }
            errors.add("Felaktig JSON i guide-resurs: " + resourcePath);
        } catch (Exception e) {
            LOGGER.warn("Kunde inte läsa guidefil {}: {}", resourcePath, e.getMessage());
            errors.add("Undantag vid inläsning av " + resourcePath + ": " + e.getMessage());
        }
        return null;
    }

    private static String getString(JsonObject obj, String key, String fallback) {
        if (obj != null && obj.has(key) && obj.get(key).isJsonPrimitive()) {
            return obj.get(key).getAsString();
        }
        return fallback;
    }
}
