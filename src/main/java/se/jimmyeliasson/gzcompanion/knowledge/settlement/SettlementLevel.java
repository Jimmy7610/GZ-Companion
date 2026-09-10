package se.jimmyeliasson.gzcompanion.knowledge.settlement;

import se.jimmyeliasson.gzcompanion.knowledge.common.VerificationMetadata;

import java.util.List;
import java.util.Objects;

/**
 * One level of the current "Settlement Levels 1.0" progression (50 levels, 49 upgrades, max
 * level Imperium). Contains no Minecraft API types - pure Rule Pack knowledge.
 */
public record SettlementLevel(
    int level,
    String name,
    long coinCost,
    List<ItemRequirement> items,
    String requiredBuildingName,
    String unlockedBuildingName,
    String unlockedBuildingBonus,
    VerificationMetadata verification
) {
    public SettlementLevel {
        Objects.requireNonNull(name, "name");
        items = items != null ? List.copyOf(items) : List.of();
        verification = verification != null ? verification : VerificationMetadata.UNVERIFIED_DEFAULT;
        if (coinCost < 0) coinCost = 0;
    }
}
