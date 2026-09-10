package se.jimmyeliasson.gzcompanion.settings;

import java.util.Objects;

/**
 * Typed outcome of a {@link SettingsStore#load()} call. Mirrors
 * {@code MarketWatchNotesLoadResult}/{@code BuildingPlanLoadResult}/{@code ChestIndexLoadResult}
 * exactly.
 */
public record SettingsLoadResult(Outcome outcome, CompanionSettings settings) {

    public enum Outcome {
        NOT_FOUND,
        LOADED,
        CORRUPT_RECOVERED,
        INCOMPATIBLE_SCHEMA,
        ERROR
    }

    public SettingsLoadResult {
        Objects.requireNonNull(outcome, "outcome");
        settings = settings != null ? settings : CompanionSettings.defaults();
    }

    public static SettingsLoadResult notFound() {
        return new SettingsLoadResult(Outcome.NOT_FOUND, CompanionSettings.defaults());
    }

    public static SettingsLoadResult loaded(CompanionSettings settings) {
        return new SettingsLoadResult(Outcome.LOADED, settings);
    }

    public static SettingsLoadResult corruptRecovered() {
        return new SettingsLoadResult(Outcome.CORRUPT_RECOVERED, CompanionSettings.defaults());
    }

    public static SettingsLoadResult incompatibleSchema() {
        return new SettingsLoadResult(Outcome.INCOMPATIBLE_SCHEMA, CompanionSettings.defaults());
    }

    public static SettingsLoadResult error() {
        return new SettingsLoadResult(Outcome.ERROR, CompanionSettings.defaults());
    }

    public boolean isUsable() {
        return outcome == Outcome.NOT_FOUND || outcome == Outcome.LOADED || outcome == Outcome.CORRUPT_RECOVERED;
    }
}
