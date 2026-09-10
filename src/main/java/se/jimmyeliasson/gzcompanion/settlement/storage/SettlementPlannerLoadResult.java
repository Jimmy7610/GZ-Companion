package se.jimmyeliasson.gzcompanion.settlement.storage;

import java.util.Objects;

/**
 * Typed outcome of a {@link SettlementPlannerStore#load()} call. Mirrors
 * {@code ChestIndexLoadResult} exactly: an incompatible future schema must never be treated as
 * loaded, to avoid a newer client's planner data being silently overwritten by an older build.
 */
public record SettlementPlannerLoadResult(Outcome outcome, SettlementPlannerData data) {

    public enum Outcome {
        NOT_FOUND,
        LOADED,
        CORRUPT_RECOVERED,
        INCOMPATIBLE_SCHEMA,
        ERROR
    }

    public SettlementPlannerLoadResult {
        Objects.requireNonNull(outcome, "outcome");
        data = data != null ? data : SettlementPlannerData.empty();
    }

    public static SettlementPlannerLoadResult notFound() {
        return new SettlementPlannerLoadResult(Outcome.NOT_FOUND, SettlementPlannerData.empty());
    }

    public static SettlementPlannerLoadResult loaded(SettlementPlannerData data) {
        return new SettlementPlannerLoadResult(Outcome.LOADED, data);
    }

    public static SettlementPlannerLoadResult corruptRecovered() {
        return new SettlementPlannerLoadResult(Outcome.CORRUPT_RECOVERED, SettlementPlannerData.empty());
    }

    public static SettlementPlannerLoadResult incompatibleSchema() {
        return new SettlementPlannerLoadResult(Outcome.INCOMPATIBLE_SCHEMA, SettlementPlannerData.empty());
    }

    public static SettlementPlannerLoadResult error() {
        return new SettlementPlannerLoadResult(Outcome.ERROR, SettlementPlannerData.empty());
    }

    public boolean isUsable() {
        return outcome == Outcome.NOT_FOUND || outcome == Outcome.LOADED || outcome == Outcome.CORRUPT_RECOVERED;
    }
}
