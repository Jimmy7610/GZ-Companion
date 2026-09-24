package se.jimmyeliasson.gzcompanion.chest.storage;

import java.util.HashMap;
import java.util.Map;

/**
 * Versioned root schema for {@code config/gzcompanion/chest-index.json}.
 */
public record ChestIndexData(int schemaVersion, Map<String, ContextContainers> contexts) {
    /**
     * v1: M3 chest index. v2 (Kistor 2.0): adds optional per-container {@code favorite}, {@code group},
     * {@code locationNote} and a single {@code previous} snapshot. v1 files load losslessly with safe
     * defaults for every new field; see {@code JsonChestIndexStore}.
     */
    public static final int CURRENT_SCHEMA = 2;

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
