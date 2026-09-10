package se.jimmyeliasson.gzcompanion.knowledge.building;

import se.jimmyeliasson.gzcompanion.knowledge.common.VerificationMetadata;

import java.util.List;
import java.util.Objects;

/**
 * One GameZone Building System 1.0 building. Contains no Minecraft API types - pure Rule Pack
 * knowledge. {@code minWidth}/{@code minDepth}/{@code minHeight} are nullable and, in the
 * bundled Rule Pack today, always null - see {@link GlobalBuildingRules}'s note for why.
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
}
