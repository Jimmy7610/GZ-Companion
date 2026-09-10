package se.jimmyeliasson.gzcompanion.knowledge.building;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import se.jimmyeliasson.gzcompanion.knowledge.common.KnowledgeLoadResult;
import se.jimmyeliasson.gzcompanion.knowledge.common.VerificationStatus;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class BuildingKnowledgeLoaderTest {
    private static final String BASIC = "/building-fixtures/buildings-basic.json";
    private static final String MALFORMED = "/building-fixtures/buildings-malformed.json";
    private static final String FUTURE_SCHEMA = "/building-fixtures/buildings-future-schema.json";
    private static final String MISSING = "/building-fixtures/does-not-exist.json";

    @Test
    @DisplayName("Loads buildings and global rules from a valid fixture")
    void loadsBasicFixture() {
        KnowledgeLoadResult<BuildingKnowledgeBase> result = new BuildingKnowledgeLoader(BASIC).load();
        assertEquals(KnowledgeLoadResult.Outcome.LOADED, result.outcome());
        BuildingKnowledgeBase base = result.data();
        assertEquals(2, base.size());
        assertEquals(40, base.globalRules().minWallCoveragePercent());
        assertEquals(75, base.globalRules().minRoofCoveragePercent());
        assertTrue(base.byId("bank").isPresent());
        assertTrue(base.byId("bank").get().hasPublishedMinimumFootprint());
        assertFalse(base.byId("stadskarna").get().hasPublishedMinimumFootprint());
    }

    @Test
    @DisplayName("A missing resource is a hard error")
    void missingResourceIsError() {
        KnowledgeLoadResult<BuildingKnowledgeBase> result = new BuildingKnowledgeLoader(MISSING).load();
        assertEquals(KnowledgeLoadResult.Outcome.ERROR, result.outcome());
        assertEquals(0, result.data().size());
    }

    @Test
    @DisplayName("A future schema version is reported as incompatible")
    void futureSchemaIsIncompatible() {
        KnowledgeLoadResult<BuildingKnowledgeBase> result = new BuildingKnowledgeLoader(FUTURE_SCHEMA).load();
        assertEquals(KnowledgeLoadResult.Outcome.INCOMPATIBLE_SCHEMA, result.outcome());
    }

    @Test
    @DisplayName("A malformed building entry (missing id, duplicate id) is skipped without breaking the others")
    void malformedEntriesAreSkipped() {
        KnowledgeLoadResult<BuildingKnowledgeBase> result = new BuildingKnowledgeLoader(MALFORMED).load();
        assertEquals(KnowledgeLoadResult.Outcome.LOADED, result.outcome());
        BuildingKnowledgeBase base = result.data();
        assertEquals(1, base.size());
        assertTrue(base.byId("stadskarna").isPresent());
        assertEquals(1, base.byId("stadskarna").get().levelRequirement());
        assertFalse(base.loadWarnings().isEmpty());
    }

    @Test
    @DisplayName("search() matches by name, id, and bonus text, case-insensitively")
    void searchMatchesAcrossFields() {
        BuildingKnowledgeBase base = new BuildingKnowledgeLoader(BASIC).load().data();
        assertEquals(1, base.search("bank").size());
        assertEquals(1, base.search("RÄNTA").size());
        assertEquals(2, base.search("").size());
        assertEquals(2, base.search(null).size());
    }

    @Test
    @DisplayName("The bundled Rule Pack building data loads with the current Building System 1.0 global rules and at least one building")
    void bundledRulePackLoadsCurrentEngine() {
        KnowledgeLoadResult<BuildingKnowledgeBase> result = new BuildingKnowledgeLoader().load();
        assertEquals(KnowledgeLoadResult.Outcome.LOADED, result.outcome());
        BuildingKnowledgeBase base = result.data();
        assertEquals(19, base.size(), "Bundled building catalog must be the current 19-building Building System 1.0 list.");
        assertEquals(40, base.globalRules().minWallCoveragePercent());
        assertEquals(75, base.globalRules().minRoofCoveragePercent());
        assertTrue(base.globalRules().mustBeFullyInsideTerritory());
    }

    @Test
    @DisplayName("Every bundled building id is unique - no duplicate survives loading")
    void bundledBuildingsHaveUniqueIds() {
        BuildingKnowledgeBase base = new BuildingKnowledgeLoader().load().data();
        Set<String> seen = new HashSet<>();
        for (SettlementBuilding building : base.buildings()) {
            assertTrue(seen.add(building.id()), "Duplicate building id: " + building.id());
        }
        assertEquals(19, seen.size());
    }

    @Test
    @DisplayName("Every bundled building now publishes a non-null minimum width and depth - the per-building footprint gap is closed")
    void everyBundledBuildingHasPublishedFootprint() {
        BuildingKnowledgeBase base = new BuildingKnowledgeLoader().load().data();
        for (SettlementBuilding building : base.buildings()) {
            assertTrue(building.hasPublishedMinimumFootprint(), building.id() + " is missing its published minimum footprint.");
            assertTrue(building.minWidth() > 0, building.id() + " minWidth must be positive.");
            assertTrue(building.minDepth() > 0, building.id() + " minDepth must be positive.");
        }
    }

    @Test
    @DisplayName("Stall's published minimum footprint is exactly 19x19, per its individual Wiki detail page")
    void stallIsExactly19By19() {
        BuildingKnowledgeBase base = new BuildingKnowledgeLoader().load().data();
        SettlementBuilding stall = base.byId("stall").orElseThrow();
        assertEquals(19, stall.minWidth());
        assertEquals(19, stall.minDepth());
        assertEquals(14, stall.levelRequirement());
        assertEquals(500000, stall.licenseCost());
    }

    @Test
    @DisplayName("Exactly 4 of the 19 buildings publish a separate minimum height; the rest correctly have none")
    void exactlyFourBuildingsPublishMinimumHeight() {
        BuildingKnowledgeBase base = new BuildingKnowledgeLoader().load().data();
        long withHeight = base.buildings().stream().filter(b -> b.minHeight() != null).count();
        assertEquals(4, withHeight, "Vindhamn, Kyrka, Rådhus, and Slott are the only buildings with a separately published minimum height.");
        assertEquals(18, base.byId("vindhamn").orElseThrow().minHeight());
        assertEquals(15, base.byId("kyrka").orElseThrow().minHeight());
        assertEquals(16, base.byId("radhus").orElseThrow().minHeight());
        assertEquals(20, base.byId("slott").orElseThrow().minHeight());
    }

    @Test
    @DisplayName("Every building's level requirement is positive and its license cost is non-negative")
    void levelRequirementAndCostAreValid() {
        BuildingKnowledgeBase base = new BuildingKnowledgeLoader().load().data();
        for (SettlementBuilding building : base.buildings()) {
            assertTrue(building.levelRequirement() > 0, building.id() + " levelRequirement must be positive.");
            assertTrue(building.licenseCost() >= 0, building.id() + " licenseCost must be non-negative.");
        }
    }

    @Test
    @DisplayName("Every VERIFIED building entry carries a non-blank sourceReference pointing at its own individual Wiki page")
    void everyVerifiedBuildingHasItsOwnSourceReference() {
        BuildingKnowledgeBase base = new BuildingKnowledgeLoader().load().data();
        for (SettlementBuilding building : base.buildings()) {
            if (building.verification().status() != VerificationStatus.VERIFIED) continue;
            assertNotNull(building.verification().sourceReference(), building.id() + " is VERIFIED but has no sourceReference.");
            assertTrue(building.verification().sourceReference().contains("/wiki/buildings/" + building.id()),
                    building.id() + "'s sourceReference should point at its own individual building page, not a shared summary page.");
        }
    }

    @Test
    @DisplayName("Stadskärna and Handelscentrum's level requirements were corrected against their individual pages, not the overview table's row order")
    void levelRequirementsMatchIndividualPagesNotOverviewOrder() {
        BuildingKnowledgeBase base = new BuildingKnowledgeLoader().load().data();
        // The Fysiska byggnader overview table's "Nivå" column is a row index, not the actual
        // settlement level requirement - confirmed by cross-checking each individual page.
        assertEquals(2, base.byId("stadskarna").orElseThrow().levelRequirement(),
                "Stadskärna's individual page states Settlementnivå 2, not the overview table's row-1 position.");
        assertEquals(4, base.byId("handelscentrum").orElseThrow().levelRequirement(),
                "Handelscentrum's individual page states Settlementnivå 4, not the overview table's row-3 position.");
    }
}
