package se.jimmyeliasson.gzcompanion.knowledge.building;

import se.jimmyeliasson.gzcompanion.knowledge.common.VerificationMetadata;

import java.util.List;
import java.util.Objects;

/**
 * One GameZone Building System 1.0 building. Contains no Minecraft API types - pure Rule Pack
 * knowledge. {@code minWidth}/{@code minDepth} are published for all 19 buildings in the bundled
 * Rule Pack; {@code minHeight} is nullable and only set for the 4 buildings that separately
 * publish a minimum height (Vindhamn, Kyrka, Rådhus, Slott) - see {@link GlobalBuildingRules}'s
 * note for the exact split.
 */
public record SettlementBuilding(
    String id,
    String name,
    int levelRequirement,
    long licenseCost,
    String mainBonus,
    List<BuildingRequirement> specialRequirements,
    Integer minWidth,
    Integer minDepth,
    Integer minHeight,
    VerificationMetadata verification
) {
    public SettlementBuilding {
        Objects.requireNonNull(id, "id");
        name = (name != null && !name.isBlank()) ? name : id;
        specialRequirements = specialRequirements != null ? List.copyOf(specialRequirements) : List.of();
        verification = verification != null ? verification : VerificationMetadata.UNVERIFIED_DEFAULT;
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
}
