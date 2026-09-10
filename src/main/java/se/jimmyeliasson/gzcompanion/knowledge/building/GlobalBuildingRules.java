package se.jimmyeliasson.gzcompanion.knowledge.building;

import se.jimmyeliasson.gzcompanion.knowledge.common.VerificationMetadata;

/**
 * Global, building-agnostic GameZone Building System 1.0 rules: minimum wall/roof coverage
 * percentages, the requirement that a building sit fully inside settlement territory, and the
 * license -> build -> approve process. Per-building minimum footprint dimensions are
 * deliberately NOT modeled here (see {@link SettlementBuilding#minWidth()} and {@code note} on
 * this record) - the summary source used for this pass does not publish them, and this project's
 * verification policy forbids inventing plausible-sounding numbers.
 */
public record GlobalBuildingRules(
    int minWallCoveragePercent,
    int minRoofCoveragePercent,
    boolean mustBeFullyInsideTerritory,
    String process,
    String note,
    VerificationMetadata verification
) {
    public GlobalBuildingRules {
        verification = verification != null ? verification : VerificationMetadata.UNVERIFIED_DEFAULT;
    }

    public static GlobalBuildingRules empty() {
        return new GlobalBuildingRules(0, 0, false, null, null, VerificationMetadata.UNVERIFIED_DEFAULT);
    }
}
