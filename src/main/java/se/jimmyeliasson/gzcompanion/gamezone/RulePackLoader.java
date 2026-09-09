package se.jimmyeliasson.gzcompanion.gamezone;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.jimmyeliasson.gzcompanion.diagnostics.CompatibilityStatus;
import se.jimmyeliasson.gzcompanion.gamezone.model.*;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Loads and parses GameZone Rule Pack with strict graceful degradation.
 */
public class RulePackLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(RulePackLoader.class);
    private static final String BUNDLED_PATH = "/gamezone-pack/";

    public RulePack loadBundled() {
        return loadFromPrefix(BUNDLED_PATH);
    }

    public RulePack loadFromPrefix(String prefix) {
        List<String> warnings = new ArrayList<>();

        JsonObject manifestJson = loadJsonObject(prefix + "manifest.json", warnings);
        if (manifestJson == null) {
            LOGGER.error("Failed to load GameZone Rule Pack manifest from {}", prefix);
            return RulePack.empty("Kunde inte ladda Rule Pack manifest fr\u00E5n " + prefix);
        }

        RulePackManifest manifest = parseManifest(manifestJson, warnings);
        Map<String, Boolean> featureFlags = parseFeatureFlags(prefix + "feature-flags.json", warnings);
        List<CommandDefinition> commands = parseCommands(prefix + "commands.json", warnings);
        List<GuideDefinition> guides = parseGuides(prefix + "guides.json", warnings);
        List<WorldRule> worldRules = parseWorldRules(prefix + "world-rules.json", warnings);
        List<ParserRule> parsers = parseParsers(prefix + "parsers.json", warnings);

        return new RulePack(manifest, featureFlags, commands, guides, worldRules, parsers, warnings);
    }

    public RulePackManifest parseManifest(JsonObject json, List<String> warnings) {
        String schemaVersion = getString(json, "schemaVersion", "1.0.0");
        String profile = getString(json, "profile", "gamezone");
        String packVersion = getString(json, "packVersion", "1.0.0");
        String name = getString(json, "name", "GameZone Rule Pack");
        String author = getString(json, "author", "Jimmy Eliasson");
        String compatibleCompanionVersions = getString(json, "compatibleCompanionVersions", ">=0.1.0-alpha");
        String targetHost = getString(json, "targetHost", "play.gamezonemc.se");

        List<String> testedMinecraft = new ArrayList<>();
        if (json.has("testedMinecraftVersions") && json.get("testedMinecraftVersions").isJsonArray()) {
            for (JsonElement el : json.getAsJsonArray("testedMinecraftVersions")) {
                if (el.isJsonPrimitive()) testedMinecraft.add(el.getAsString());
            }
        }

        RulePackManifest.VerificationInfo verification = null;
        if (json.has("verification") && json.get("verification").isJsonObject()) {
            JsonObject vObj = json.getAsJsonObject("verification");
            String src = getString(vObj, "source", "Unknown");
            String lastV = getString(vObj, "lastVerified", "");
            String statStr = getString(vObj, "status", "UNKNOWN");
            CompatibilityStatus status = parseStatus(statStr);
            verification = new RulePackManifest.VerificationInfo(src, lastV, status);
        }

        Map<String, RulePackManifest.ModuleInfo> modules = new HashMap<>();
        if (json.has("modules") && json.get("modules").isJsonObject()) {
            JsonObject mObj = json.getAsJsonObject("modules");
            for (Map.Entry<String, JsonElement> entry : mObj.entrySet()) {
                if (entry.getValue().isJsonObject()) {
                    JsonObject modObj = entry.getValue().getAsJsonObject();
                    String file = getString(modObj, "file", "");
                    String stat = getString(modObj, "status", "UNKNOWN");
                    modules.put(entry.getKey(), new RulePackManifest.ModuleInfo(file, parseStatus(stat)));
                }
            }
        }

        return new RulePackManifest(
            schemaVersion, profile, packVersion, name, author,
            compatibleCompanionVersions, testedMinecraft, targetHost,
            verification, modules
        );
    }

    private Map<String, Boolean> parseFeatureFlags(String path, List<String> warnings) {
        Map<String, Boolean> result = new HashMap<>();
        JsonObject obj = loadJsonObject(path, warnings);
        if (obj != null && obj.has("flags") && obj.get("flags").isJsonObject()) {
            JsonObject flags = obj.getAsJsonObject("flags");
            for (Map.Entry<String, JsonElement> entry : flags.entrySet()) {
                if (entry.getValue().isJsonObject()) {
                    JsonObject f = entry.getValue().getAsJsonObject();
                    boolean enabled = f.has("enabled") && f.get("enabled").getAsBoolean();
                    result.put(entry.getKey(), enabled);
                }
            }
        }
        return result;
    }

    private List<CommandDefinition> parseCommands(String path, List<String> warnings) {
        List<CommandDefinition> list = new ArrayList<>();
        JsonObject obj = loadJsonObject(path, warnings);
        if (obj != null && obj.has("categories") && obj.get("categories").isJsonArray()) {
            for (JsonElement catEl : obj.getAsJsonArray("categories")) {
                if (catEl.isJsonObject() && catEl.getAsJsonObject().has("commands")) {
                    for (JsonElement cmdEl : catEl.getAsJsonObject().getAsJsonArray("commands")) {
                        if (cmdEl.isJsonObject()) {
                            JsonObject cmd = cmdEl.getAsJsonObject();
                            String c = getString(cmd, "command", "");
                            String d = getString(cmd, "description", "");
                            String p = getString(cmd, "permission", "ALL");
                            // A missing/malformed status must never silently become VERIFIED - that
                            // would let an edited data file assert a fact as confirmed without anyone
                            // actually confirming it. Default to UNVERIFIED, matching parseStatus's own
                            // UNKNOWN fallback for truly unparseable values.
                            CompatibilityStatus s = parseStatus(getString(cmd, "status", "UNVERIFIED"));
                            list.add(new CommandDefinition(c, d, p, s));
                        }
                    }
                }
            }
        }
        return list;
    }

    private List<GuideDefinition> parseGuides(String path, List<String> warnings) {
        List<GuideDefinition> list = new ArrayList<>();
        JsonObject obj = loadJsonObject(path, warnings);
        if (obj != null && obj.has("guides") && obj.get("guides").isJsonArray()) {
            for (JsonElement el : obj.getAsJsonArray("guides")) {
                if (el.isJsonObject()) {
                    JsonObject g = el.getAsJsonObject();
                    String id = getString(g, "id", "");
                    String title = getString(g, "title", "");
                    String cat = getString(g, "category", "Allm\u00E4nt");
                    List<GuideDefinition.GuideStep> steps = new ArrayList<>();
                    if (g.has("steps") && g.get("steps").isJsonArray()) {
                        for (JsonElement sEl : g.getAsJsonArray("steps")) {
                            if (sEl.isJsonObject()) {
                                JsonObject s = sEl.getAsJsonObject();
                                int num = s.has("stepNumber") ? s.get("stepNumber").getAsInt() : steps.size() + 1;
                                String sTitle = getString(s, "title", "");
                                String sBody = getString(s, "body", "");
                                CompatibilityStatus stat = parseStatus(getString(s, "status", "VERIFIED"));
                                steps.add(new GuideDefinition.GuideStep(num, sTitle, sBody, stat));
                            }
                        }
                    }
                    list.add(new GuideDefinition(id, title, cat, steps));
                }
            }
        }
        return list;
    }

    private List<WorldRule> parseWorldRules(String path, List<String> warnings) {
        List<WorldRule> list = new ArrayList<>();
        JsonObject obj = loadJsonObject(path, warnings);
        if (obj != null && obj.has("rules") && obj.get("rules").isJsonArray()) {
            for (JsonElement el : obj.getAsJsonArray("rules")) {
                if (el.isJsonObject()) {
                    JsonObject r = el.getAsJsonObject();
                    String id = getString(r, "id", "");
                    String name = getString(r, "name", "");
                    String desc = getString(r, "description", "");
                    CompatibilityStatus stat = parseStatus(getString(r, "status", "VERIFIED"));
                    list.add(new WorldRule(id, name, desc, stat));
                }
            }
        }
        return list;
    }

    private List<ParserRule> parseParsers(String path, List<String> warnings) {
        List<ParserRule> list = new ArrayList<>();
        JsonObject obj = loadJsonObject(path, warnings);
        if (obj != null && obj.has("parsers") && obj.get("parsers").isJsonArray()) {
            for (JsonElement el : obj.getAsJsonArray("parsers")) {
                if (el.isJsonObject()) {
                    JsonObject p = el.getAsJsonObject();
                    String id = getString(p, "id", "");
                    String pat = getString(p, "pattern", "");
                    String target = getString(p, "target", "");
                    CompatibilityStatus stat = parseStatus(getString(p, "status", "VERIFIED"));
                    list.add(new ParserRule(id, pat, target, stat));
                }
            }
        }
        return list;
    }

    private JsonObject loadJsonObject(String resourcePath, List<String> warnings) {
        try (InputStream stream = RulePackLoader.class.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                warnings.add("Resursfil saknas: " + resourcePath);
                return null;
            }
            try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                JsonElement element = JsonParser.parseReader(reader);
                if (element != null && element.isJsonObject()) {
                    return element.getAsJsonObject();
                } else {
                    warnings.add("Felaktig JSON-struktur i " + resourcePath);
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Kunde inte l\u00E4sa Rule Pack-fil {}: {}", resourcePath, e.getMessage());
            warnings.add("Undantag vid l\u00E4sning av " + resourcePath + ": " + e.getMessage());
        }
        return null;
    }

    private static String getString(JsonObject obj, String key, String fallback) {
        if (obj.has(key) && obj.get(key).isJsonPrimitive()) {
            return obj.get(key).getAsString();
        }
        return fallback;
    }

    private static CompatibilityStatus parseStatus(String status) {
        if (status == null) return CompatibilityStatus.UNKNOWN;
        try {
            return CompatibilityStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return CompatibilityStatus.UNKNOWN;
        }
    }
}