package se.jimmyeliasson.gzcompanion.marketwatch.storage;

import java.util.Objects;

/**
 * Typed outcome of a {@link MarketWatchNotesStore#load()} call. Mirrors
 * {@code ChestIndexLoadResult}/{@code SettlementPlannerLoadResult}/{@code BuildingPlanLoadResult}
 * exactly.
 */
public record MarketWatchNotesLoadResult(Outcome outcome, MarketWatchNotesData data) {

    public enum Outcome {
        NOT_FOUND,
        LOADED,
        CORRUPT_RECOVERED,
        INCOMPATIBLE_SCHEMA,
        ERROR
    }

    public MarketWatchNotesLoadResult {
        Objects.requireNonNull(outcome, "outcome");
        data = data != null ? data : MarketWatchNotesData.empty();
    }

    public static MarketWatchNotesLoadResult notFound() {
        return new MarketWatchNotesLoadResult(Outcome.NOT_FOUND, MarketWatchNotesData.empty());
    }

    public static MarketWatchNotesLoadResult loaded(MarketWatchNotesData data) {
        return new MarketWatchNotesLoadResult(Outcome.LOADED, data);
    }

    public static MarketWatchNotesLoadResult corruptRecovered() {
        return new MarketWatchNotesLoadResult(Outcome.CORRUPT_RECOVERED, MarketWatchNotesData.empty());
    }

    public static MarketWatchNotesLoadResult incompatibleSchema() {
        return new MarketWatchNotesLoadResult(Outcome.INCOMPATIBLE_SCHEMA, MarketWatchNotesData.empty());
    }

    public static MarketWatchNotesLoadResult error() {
        return new MarketWatchNotesLoadResult(Outcome.ERROR, MarketWatchNotesData.empty());
    }

    public boolean isUsable() {
        return outcome == Outcome.NOT_FOUND || outcome == Outcome.LOADED || outcome == Outcome.CORRUPT_RECOVERED;
    }
}
