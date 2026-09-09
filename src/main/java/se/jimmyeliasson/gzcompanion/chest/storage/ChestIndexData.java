package se.jimmyeliasson.gzcompanion.chest.storage;

import java.util.HashMap;
import java.util.Map;

/**
 * Versioned root schema for {@code config/gzcompanion/chest-index.json}.
 */
public record ChestIndexData(int schemaVersion, Map<String, ContextContainers> contexts) {
    public static final int CURRENT_SCHEMA = 1;

    public ChestIndexData {
        contexts = contexts != null ? new HashMap<>(contexts) : new HashMap<>();
    }

    public static ChestIndexData empty() {
        return new ChestIndexData(CURRENT_SCHEMA, new HashMap<>());
    }

    public ContextContainers getContext(String contextStorageKey) {
        return contexts.getOrDefault(contextStorageKey, ContextContainers.empty());
    }

    public ChestIndexData withContext(String contextStorageKey, ContextContainers updated) {
        Map<String, ContextContainers> copy = new HashMap<>(contexts);
        copy.put(contextStorageKey, updated);
        return new ChestIndexData(schemaVersion, copy);
    }
}
