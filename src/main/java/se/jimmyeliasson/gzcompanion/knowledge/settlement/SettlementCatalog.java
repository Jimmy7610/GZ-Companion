package se.jimmyeliasson.gzcompanion.knowledge.settlement;

import se.jimmyeliasson.gzcompanion.knowledge.common.VerificationMetadata;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Immutable, loaded-once GameZone settlement knowledge: the current "Settlement Levels 1.0"
 * progression plus the foundation/production-category facts from {@code settlements.json}.
 *
 * <p>This is current-engine data ONLY. There is deliberately no code path anywhere in this class
 * that can produce a 15-level result - the obsolete pre-migration building/level mapping is never
 * modeled here at all.
 */
public final class SettlementCatalog {
    private final List<SettlementLevel> levels;
    private final SettlementFoundation foundation;
    private final List<ProductionCategory> productionCategories;
    private final VerificationMetadata productionCategoriesVerification;
    private final List<String> loadWarnings;
    private final Map<Integer, SettlementLevel> levelsByNumber;

    public SettlementCatalog(List<SettlementLevel> levels, SettlementFoundation foundation,
                              List<ProductionCategory> productionCategories,
                              VerificationMetadata productionCategoriesVerification,
                              List<String> loadWarnings) {
        this.levels = levels != null ? List.copyOf(levels) : List.of();
        this.foundation = foundation;
        this.productionCategories = productionCategories != null ? List.copyOf(productionCategories) : List.of();
        this.productionCategoriesVerification = productionCategoriesVerification != null
                ? productionCategoriesVerification : VerificationMetadata.UNVERIFIED_DEFAULT;
        this.loadWarnings = loadWarnings != null ? List.copyOf(loadWarnings) : List.of();

        Map<Integer, SettlementLevel> byNumber = new LinkedHashMap<>();
        for (SettlementLevel level : this.levels) {
            byNumber.put(level.level(), level);
        }
        this.levelsByNumber = Map.copyOf(byNumber);
    }

    public static SettlementCatalog empty() {
        return new SettlementCatalog(List.of(), null, List.of(), VerificationMetadata.UNVERIFIED_DEFAULT, List.of());
    }

    public List<SettlementLevel> levels() {
        return levels;
    }

    public SettlementFoundation foundation() {
        return foundation;
    }

    public List<ProductionCategory> productionCategories() {
        return productionCategories;
    }

    public VerificationMetadata productionCategoriesVerification() {
        return productionCategoriesVerification;
    }

    public List<String> loadWarnings() {
        return loadWarnings;
    }

    public int size() {
        return levels.size();
    }

    public Optional<SettlementLevel> byLevel(int level) {
        return Optional.ofNullable(levelsByNumber.get(level));
    }

    public int minLevel() {
        return levels.stream().mapToInt(SettlementLevel::level).min().orElse(0);
    }

    public int maxLevel() {
        return levels.stream().mapToInt(SettlementLevel::level).max().orElse(0);
    }

    /**
     * Aggregates every level strictly greater than {@code fromLevel} and up to and including
     * {@code targetLevel}: total coin cost, merged material requirements (summed by
     * {@link ItemRequirement#mergeKey()}), distinct required-building names in level order, and
     * the number of upgrades that represents. Levels not present in this catalog (a data gap) are
     * silently skipped rather than invented. Returns {@link LevelRangeSummary#empty} when
     * {@code targetLevel <= fromLevel} or the catalog has no levels in range.
     */
    public LevelRangeSummary levelRange(int fromLevel, int targetLevel) {
        if (targetLevel <= fromLevel) {
            return LevelRangeSummary.empty(fromLevel, targetLevel);
        }

        long totalCoins = 0L;
        Map<String, ItemRequirement> merged = new LinkedHashMap<>();
        List<String> buildingNames = new ArrayList<>();
        int upgradeCount = 0;

        for (int lvl = fromLevel + 1; lvl <= targetLevel; lvl++) {
            SettlementLevel level = levelsByNumber.get(lvl);
            if (level == null) continue;
            upgradeCount++;
            totalCoins += level.coinCost();

            for (ItemRequirement req : level.items()) {
                String key = req.mergeKey();
                ItemRequirement existing = merged.get(key);
                if (existing == null) {
                    merged.put(key, req);
                } else {
                    // Deliberately NOT a ternary: mixing a primitive int (from Math.max) with an
                    // Integer branch forces Java to unbox the Integer branch for type unification
                    // even when it isn't taken, which NPEs the moment either value is legitimately
                    // null (e.g. a plain, non-distinct-variant requirement).
                    Integer distinct;
                    if (existing.distinctVariantsRequired() != null) {
                        int otherVariants = req.distinctVariantsRequired() != null ? req.distinctVariantsRequired() : 0;
                        distinct = Math.max(existing.distinctVariantsRequired(), otherVariants);
                    } else {
                        distinct = req.distinctVariantsRequired();
                    }
                    merged.put(key, new ItemRequirement(existing.itemId(), existing.displayName(),
                            existing.count() + req.count(), distinct));
                }
            }

            if (level.requiredBuildingName() != null && !buildingNames.contains(level.requiredBuildingName())) {
                buildingNames.add(level.requiredBuildingName());
            }
        }

        return new LevelRangeSummary(fromLevel, targetLevel, totalCoins, List.copyOf(merged.values()),
                List.copyOf(buildingNames), upgradeCount);
    }
}
