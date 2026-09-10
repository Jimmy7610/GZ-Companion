package se.jimmyeliasson.gzcompanion.building.storage;

import java.util.Objects;

/**
 * Typed outcome of a {@link BuildingPlanStore#load()} call. Mirrors {@code ChestIndexLoadResult}
 * and {@code SettlementPlannerLoadResult} exactly.
 */
public record BuildingPlanLoadResult(Outcome outcome, BuildingPlanData data) {

    public enum Outcome {
        NOT_FOUND,
        LOADED,
        CORRUPT_RECOVERED,
        INCOMPATIBLE_SCHEMA,
        ERROR
    }

    public BuildingPlanLoadResult {
        Objects.requireNonNull(outcome, "outcome");
        data = data != null ? data : BuildingPlanData.empty();
    }

    public static BuildingPlanLoadResult notFound() {
        return new BuildingPlanLoadResult(Outcome.NOT_FOUND, BuildingPlanData.empty());
    }

    public static BuildingPlanLoadResult loaded(BuildingPlanData data) {
        return new BuildingPlanLoadResult(Outcome.LOADED, data);
    }

    public static BuildingPlanLoadResult corruptRecovered() {
        return new BuildingPlanLoadResult(Outcome.CORRUPT_RECOVERED, BuildingPlanData.empty());
    }

    public static BuildingPlanLoadResult incompatibleSchema() {
        return new BuildingPlanLoadResult(Outcome.INCOMPATIBLE_SCHEMA, BuildingPlanData.empty());
    }

    public static BuildingPlanLoadResult error() {
        return new BuildingPlanLoadResult(Outcome.ERROR, BuildingPlanData.empty());
    }

    public boolean isUsable() {
        return outcome == Outcome.NOT_FOUND || outcome == Outcome.LOADED || outcome == Outcome.CORRUPT_RECOVERED;
    }
}
