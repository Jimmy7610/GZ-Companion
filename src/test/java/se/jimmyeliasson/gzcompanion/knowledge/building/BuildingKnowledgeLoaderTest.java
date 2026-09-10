package se.jimmyeliasson.gzcompanion.knowledge.building;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import se.jimmyeliasson.gzcompanion.knowledge.common.KnowledgeLoadResult;

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
}
