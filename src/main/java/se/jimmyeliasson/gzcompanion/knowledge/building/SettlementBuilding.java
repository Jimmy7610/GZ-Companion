package se.jimmyeliasson.gzcompanion.knowledge.building;

import se.jimmyeliasson.gzcompanion.knowledge.common.VerificationMetadata;
import se.jimmyeliasson.gzcompanion.knowledge.common.VerificationStatus;

import java.util.List;
import java.util.Objects;

/**
 * One GameZone Building System 1.0 building. Contains no Minecraft API types - pure Rule Pack
 * knowledge. {@code minWidth}/{@code minDepth} are published for all 19 buildings in the bundled
 * Rule Pack; {@code minHeight} is nullable and only set for the 4 buildings that separately
 * publish a minimum height (Vindhamn, Kyrka, Rådhus, Slott) - see {@link GlobalBuildingRules}'s
 * note for the exact split.
 *
 * <p><b>Two independent verification trails.</b> {@code verification} covers this record's
 * footprint/cost/special-requirements/bonus - facts that were cross-checked between the
 * building's own individual Wiki page and the Settlement Upgrade progression page and found
 * consistent for all 19 buildings. {@code levelRequirement} and {@code
 * progressionRequiredForUpgradeToLevel} get their OWN separate {@code levelRequirementVerification}
 * trail, because for exactly 2 of the 19 buildings (Stadskärna, Handelscentrum) those two sources
 * make directly contradictory claims about when the building is actually available - see
 * {@link #hasLevelRequirementConflict()}. Downgrading the whole building to unverified over a
 * disputed level number would incorrectly throw away genuinely confirmed footprint/cost data;
 * keeping one shared verification trail would incorrectly present the disputed level number as
 * settled truth. Splitting the two trails lets each be exactly as trustworthy as it actually is.
 */
public record SettlementBuilding(
    String id,
    String name,
    int levelRequirement,
    Integer progressionRequiredForUpgradeToLevel,
    long licenseCost,
    String mainBonus,
    List<BuildingRequirement> specialRequirements,
    Integer minWidth,
    Integer minDepth,
    Integer minHeight,
    VerificationMetadata verification,
    VerificationMetadata levelRequirementVerification
) {
    public SettlementBuilding {
        Objects.requireNonNull(id, "id");
        name = (name != null && !name.isBlank()) ? name : id;
        specialRequirements = specialRequirements != null ? List.copyOf(specialRequirements) : List.of();
        verification = verification != null ? verification : VerificationMetadata.UNVERIFIED_DEFAULT;
        levelRequirementVerification = levelRequirementVerification != null ? levelRequirementVerification : VerificationMetadata.UNVERIFIED_DEFAULT;
        if (licenseCost < 0) licenseCost = 0;
    }

    public boolean hasPublishedMinimumFootprint() {
        return minWidth != null && minDepth != null;
    }

    /**
     * Whether a local planning structure of the given dimensions meets this building's published
     * minimum footprint. Returns {@code true} when no minimum footprint is published at all - there
     * is nothing to compare against, so this must never manufacture a false failure. A published
     * minimum height is only checked when this building actually has one; buildings without a
     * separately-published height requirement (the majority) never fail on height.
     */
    public boolean fitsFootprint(int width, int depth, int height) {
        if (!hasPublishedMinimumFootprint()) return true;
        if (width < minWidth || depth < minDepth) return false;
        return minHeight == null || height >= minHeight;
    }

    /**
     * Detects a circular/impossible progression claim: a building cannot simultaneously be
     * "only available once the settlement reaches level {@code levelRequirement}" (per its own
     * individual Wiki page) AND "must already be completed before the settlement can upgrade to
     * level {@code progressionRequiredForUpgradeToLevel}" (per the Settlement Upgrade progression
     * page) when {@code levelRequirement >= progressionRequiredForUpgradeToLevel} - that would
     * require the building to both precede and follow the same upgrade. This is a general,
     * data-driven rule (not a hardcoded building-id check): scanning all 19 bundled buildings with
     * it finds exactly the 2 known-conflicting ones and no others. When it is null (some buildings,
     * e.g. Laboratorium, are never presented as a general upgrade gate at all - only conditionally
     * for one settlement category), there is nothing to compare and this returns {@code false}.
     */
    public boolean hasLevelRequirementConflict() {
        return progressionRequiredForUpgradeToLevel != null && levelRequirement >= progressionRequiredForUpgradeToLevel;
    }

    /** Convenience: true only when the level fact is confirmed consistent AND not disputed. */
    public boolean isLevelRequirementVerified() {
        return levelRequirementVerification.status() == VerificationStatus.VERIFIED && !hasLevelRequirementConflict();
    }
}
