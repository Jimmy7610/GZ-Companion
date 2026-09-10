package se.jimmyeliasson.gzcompanion.knowledge.settlement;

import java.util.List;

/**
 * Aggregated local planning result for upgrading a settlement from one level to a higher target
 * level: the sum of every level's coin cost, every level's material requirements merged by
 * {@link ItemRequirement#mergeKey()} (so the same concrete item or ambiguous category required at
 * multiple levels along the path is shown once with a combined count), the distinct building
 * prerequisites encountered along the way (in level order), and how many upgrades that represents.
 *
 * <p>Purely a local calculation over already-loaded Rule Pack data - never a claim about the
 * player's actual, server-observed settlement state.
 */
public record LevelRangeSummary(
    int fromLevel,
    int toLevel,
    long totalCoinCost,
    List<ItemRequirement> mergedItems,
    List<String> requiredBuildingNames,
    int upgradeCount
) {
    public static LevelRangeSummary empty(int fromLevel, int toLevel) {
        return new LevelRangeSummary(fromLevel, toLevel, 0L, List.of(), List.of(), 0);
    }
}
