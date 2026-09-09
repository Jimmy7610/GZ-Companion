package se.jimmyeliasson.gzcompanion.guide.progress;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Root data model serialized to guide-progress.json.
 */
public record GuideProgressData(
    int schemaVersion,
    Map<String, ContextProgress> contexts
) {
    public static final int CURRENT_SCHEMA = 1;

    public GuideProgressData {
        contexts = contexts != null ? Collections.unmodifiableMap(new HashMap<>(contexts)) : Map.of();
    }

    public static GuideProgressData empty() {
        return new GuideProgressData(CURRENT_SCHEMA, Map.of());
    }

    public ContextProgress getContext(String storageKey) {
        return contexts.getOrDefault(storageKey, new ContextProgress(null, Map.of()));
    }
}