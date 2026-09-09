package se.jimmyeliasson.gzcompanion.chest.storage;

import java.util.Objects;

/**
 * Typed outcome of a {@link ChestIndexStore#load()} call. Distinguishes "safe to use and treat
 * as LOADED" outcomes from an incompatible-future-schema outcome, which must NEVER be treated as
 * loaded — doing so risks a future-version index file being silently overwritten by an older
 * client that only understood an empty/default index.
 */
public record ChestIndexLoadResult(Outcome outcome, ChestIndexData data) {

    public enum Outcome {
        /** No index file existed yet. A fresh, empty, current-schema index is safe to use. */
        NOT_FOUND,
        /** The file parsed successfully under a schema version this build understands. */
        LOADED,
        /** The file was unreadable/malformed; it was backed up and an empty index is safe to use. */
        CORRUPT_RECOVERED,
        /** The file declares a schema version newer than this build supports. Left untouched on disk. */
        INCOMPATIBLE_SCHEMA,
        /** An unexpected failure occurred outside normal parsing. */
        ERROR
    }

    public ChestIndexLoadResult {
        Objects.requireNonNull(outcome, "outcome");
        data = data != null ? data : ChestIndexData.empty();
    }

    public static ChestIndexLoadResult notFound() {
        return new ChestIndexLoadResult(Outcome.NOT_FOUND, ChestIndexData.empty());
    }

    public static ChestIndexLoadResult loaded(ChestIndexData data) {
        return new ChestIndexLoadResult(Outcome.LOADED, data);
    }

    public static ChestIndexLoadResult corruptRecovered() {
        return new ChestIndexLoadResult(Outcome.CORRUPT_RECOVERED, ChestIndexData.empty());
    }

    public static ChestIndexLoadResult incompatibleSchema() {
        return new ChestIndexLoadResult(Outcome.INCOMPATIBLE_SCHEMA, ChestIndexData.empty());
    }

    public static ChestIndexLoadResult error() {
        return new ChestIndexLoadResult(Outcome.ERROR, ChestIndexData.empty());
    }

    /**
     * Whether this outcome is safe to treat as a usable, writable index (i.e. the Chest Manager
     * may become LOADED). {@link Outcome#INCOMPATIBLE_SCHEMA} and {@link Outcome#ERROR} are
     * deliberately excluded.
     */
    public boolean isUsable() {
        return outcome == Outcome.NOT_FOUND || outcome == Outcome.LOADED || outcome == Outcome.CORRUPT_RECOVERED;
    }
}
