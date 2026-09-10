package se.jimmyeliasson.gzcompanion.knowledge.economy;

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
import java.util.List;

/**
 * Loads the bundled verified MarketWatch system facts from {@code gamezone-pack/marketwatch.json}
 * (a read-only classpath resource). The 7 production categories MarketWatch demand is grouped by
 * are intentionally NOT duplicated here - they are read from the already-loaded
 * {@code SettlementCatalog.productionCategories()} (same Wiki source) to avoid two files ever
 * drifting apart; see {@code CompanionSession}/{@code MarketWatchTabComponent}.
 */
public final class MarketWatchKnowledgeLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(MarketWatchKnowledgeLoader.class);
    private static final String DEFAULT_RESOURCE_PATH = "/gamezone-pack/marketwatch.json";
    private static final int CURRENT_SCHEMA = 1;

    private final String resourcePath;

    public MarketWatchKnowledgeLoader() {
        this(DEFAULT_RESOURCE_PATH);
    }

    public MarketWatchKnowledgeLoader(String resourcePath) {
        this.resourcePath = resourcePath;
    }

    public KnowledgeLoadResult<MarketWatchInfo> load() {
        JsonObject root;
        try (InputStream stream = MarketWatchKnowledgeLoader.class.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                LOGGER.error("MarketWatch resource not found: {}", resourcePath);
                return KnowledgeLoadResult.error(MarketWatchInfo.empty());
            }
            try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                JsonElement element = JsonParser.parseReader(reader);
                if (element == null || !element.isJsonObject()) {
                    LOGGER.error("MarketWatch resource {} did not contain a JSON object.", resourcePath);
                    return KnowledgeLoadResult.error(MarketWatchInfo.empty());
                }
                root = element.getAsJsonObject();
            }
        } catch (Exception e) {
            LOGGER.error("Failed to read MarketWatch resource {}: {}", resourcePath, e.getMessage());
            return KnowledgeLoadResult.error(MarketWatchInfo.empty());
        }

        int schemaVersion = getInt(root, "schemaVersion", 1);
        if (schemaVersion < 1) {
            LOGGER.error("MarketWatch resource declares invalid schemaVersion {}.", schemaVersion);
            return KnowledgeLoadResult.error(MarketWatchInfo.empty());
        }
        if (schemaVersion > CURRENT_SCHEMA) {
            LOGGER.warn("MarketWatch resource schemaVersion {} is newer than this build supports ({}).", schemaVersion, CURRENT_SCHEMA);
            return KnowledgeLoadResult.incompatibleSchema(MarketWatchInfo.empty());
        }

        try {
            String command = getString(root, "command", null);
            int categoryCount = getInt(root, "categoryCount", 0);
            String purpose = getString(root, "purpose", null);
            List<String> usageSteps = readStringArray(root, "usageSteps");
            VerificationMetadata verification = parseVerification(root);
            return KnowledgeLoadResult.loaded(new MarketWatchInfo(command, categoryCount, purpose, usageSteps, verification));
        } catch (Exception ex) {
            LOGGER.error("Failed to parse MarketWatch resource {}: {}", resourcePath, ex.getMessage());
            return KnowledgeLoadResult.error(MarketWatchInfo.empty());
        }
    }

    private List<String> readStringArray(JsonObject obj, String key) {
        List<String> result = new ArrayList<>();
        if (obj.has(key) && obj.get(key).isJsonArray()) {
            JsonArray arr = obj.getAsJsonArray(key);
            for (JsonElement el : arr) {
                if (el.isJsonPrimitive()) result.add(el.getAsString());
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

    private static int getInt(JsonObject obj, String key, int fallback) {
        return (obj.has(key) && !obj.get(key).isJsonNull() && obj.get(key).isJsonPrimitive()) ? obj.get(key).getAsInt() : fallback;
    }

    private static String getString(JsonObject obj, String key, String fallback) {
        if (obj.has(key) && !obj.get(key).isJsonNull() && obj.get(key).isJsonPrimitive()) {
            return obj.get(key).getAsString();
        }
        return fallback;
    }
}
