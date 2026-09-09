package se.jimmyeliasson.gzcompanion.knowledge.common;

import java.util.Objects;

/**
 * Typed outcome of loading one bundled M4 knowledge file (commands, crafting overrides, or
 * item overrides). Shared across the three knowledge loaders since the shape is identical.
 *
 * <p>Unlike {@code ChestIndexLoadResult} (which reads a runtime-writable, potentially corrupted
 * user file), these files are read-only bundled resources — there is no "corrupt, back it up and
 * start fresh" scenario to model, only "did this classpath resource parse under a schema version
 * this build understands."
 */
public record KnowledgeLoadResult<T>(Outcome outcome, T data) {

    public enum Outcome {
        /** Parsed successfully under a schema version this build understands. */
        LOADED,
        /** The file declares a schema version newer than this build supports. */
        INCOMPATIBLE_SCHEMA,
        /** The resource is missing, or its root structure could not be parsed at all. */
        ERROR
    }

    public KnowledgeLoadResult {
        Objects.requireNonNull(outcome, "outcome");
    }

    public static <T> KnowledgeLoadResult<T> loaded(T data) {
        return new KnowledgeLoadResult<>(Outcome.LOADED, data);
    }

    public static <T> KnowledgeLoadResult<T> incompatibleSchema(T emptyData) {
        return new KnowledgeLoadResult<>(Outcome.INCOMPATIBLE_SCHEMA, emptyData);
    }

    public static <T> KnowledgeLoadResult<T> error(T emptyData) {
        return new KnowledgeLoadResult<>(Outcome.ERROR, emptyData);
    }

    public boolean isUsable() {
        return outcome == Outcome.LOADED;
    }
}
