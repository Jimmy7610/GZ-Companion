package se.jimmyeliasson.gzcompanion.knowledge.settlement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import se.jimmyeliasson.gzcompanion.knowledge.common.VerificationMetadata;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SettlementCatalogTest {

    private static final VerificationMetadata TEST_ONLY_VERIFICATION = VerificationMetadata.of(
            "VERIFIED", "TEST ONLY fixture", "test://fixture", "2026-09-10");

    private SettlementLevel level(int level, long coinCost, List<ItemRequirement> items, String requiredBuilding) {
        return new SettlementLevel(level, "Nivå " + level, coinCost, items, requiredBuilding, null, null, TEST_ONLY_VERIFICATION);
    }

    private SettlementCatalog buildTestCatalog() {
        List<SettlementLevel> levels = List.of(
                level(1, 0, List.of(), null),
                level(2, 100, List.of(new ItemRequirement("minecraft:oak_log", "Ekstock", 10, null)), null),
                level(3, 250, List.of(
                        new ItemRequirement("minecraft:oak_log", "Ekstock", 5, null),
                        new ItemRequirement(null, "Ullfärg", 2, 2)
                ), "Stadskärna"),
                level(4, 500, List.of(new ItemRequirement("minecraft:iron_ingot", "Järntacka", 20, null)), "Bank")
        );
        return new SettlementCatalog(levels, null, List.of(), VerificationMetadata.UNVERIFIED_DEFAULT, List.of());
    }

    @Test
    @DisplayName("byLevel resolves an existing level and is empty for a level not in the catalog")
    void byLevelResolution() {
        SettlementCatalog catalog = buildTestCatalog();
        Optional<SettlementLevel> found = catalog.byLevel(2);
        assertTrue(found.isPresent());
        assertEquals("Nivå 2", found.get().name());
        assertTrue(catalog.byLevel(999).isEmpty());
    }

    @Test
    @DisplayName("levelRange sums coin cost and merges identical item requirements across levels")
    void levelRangeAggregatesCoinsAndItems() {
        SettlementCatalog catalog = buildTestCatalog();
        LevelRangeSummary summary = catalog.levelRange(1, 3);

        assertEquals(1, summary.fromLevel());
        assertEquals(3, summary.toLevel());
        assertEquals(2, summary.upgradeCount());
        assertEquals(100L + 250L, summary.totalCoinCost());

        ItemRequirement oakLog = summary.mergedItems().stream()
                .filter(i -> "minecraft:oak_log".equals(i.itemId())).findFirst().orElseThrow();
        assertEquals(15, oakLog.count());

        assertEquals(List.of("Stadskärna"), summary.requiredBuildingNames());
    }

    @Test
    @DisplayName("A distinct-variant requirement (e.g. wool colors) merges its variant count using the max seen")
    void distinctVariantRequirementIsPreserved() {
        SettlementCatalog catalog = buildTestCatalog();
        LevelRangeSummary summary = catalog.levelRange(1, 3);

        ItemRequirement wool = summary.mergedItems().stream()
                .filter(i -> i.itemId() == null).findFirst().orElseThrow();
        assertEquals("Ullfärg", wool.displayName());
        assertEquals(2, wool.count());
        assertEquals(2, wool.distinctVariantsRequired());
    }

    @Test
    @DisplayName("Requesting a target level not higher than the current level returns an empty summary")
    void invalidRangeReturnsEmpty() {
        SettlementCatalog catalog = buildTestCatalog();
        assertEquals(LevelRangeSummary.empty(3, 3), catalog.levelRange(3, 3));
        assertEquals(LevelRangeSummary.empty(3, 1), catalog.levelRange(3, 1));
    }

    @Test
    @DisplayName("levelRange spanning the full catalog (level 1 to the max level) includes every building prerequisite in order")
    void fullRangeIncludesAllBuildingPrerequisitesInOrder() {
        SettlementCatalog catalog = buildTestCatalog();
        LevelRangeSummary summary = catalog.levelRange(catalog.minLevel(), catalog.maxLevel());
        assertEquals(List.of("Stadskärna", "Bank"), summary.requiredBuildingNames());
        assertEquals(3, summary.upgradeCount());
    }

    @Test
    @DisplayName("empty() catalog has no levels and safe defaults everywhere")
    void emptyCatalogIsSafe() {
        SettlementCatalog empty = SettlementCatalog.empty();
        assertEquals(0, empty.size());
        assertTrue(empty.byLevel(1).isEmpty());
        assertNull(empty.foundation());
        assertEquals(LevelRangeSummary.empty(0, 0), empty.levelRange(0, 0));
    }
}
