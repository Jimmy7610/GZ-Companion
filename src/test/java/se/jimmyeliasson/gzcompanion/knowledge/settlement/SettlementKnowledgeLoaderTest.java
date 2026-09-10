package se.jimmyeliasson.gzcompanion.knowledge.settlement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import se.jimmyeliasson.gzcompanion.knowledge.common.KnowledgeLoadResult;
import se.jimmyeliasson.gzcompanion.knowledge.common.VerificationStatus;

import static org.junit.jupiter.api.Assertions.*;

class SettlementKnowledgeLoaderTest {
    private static final String LEVELS_BASIC = "/settlement-fixtures/levels-basic.json";
    private static final String LEVELS_MALFORMED = "/settlement-fixtures/levels-malformed-entry.json";
    private static final String LEVELS_FUTURE_SCHEMA = "/settlement-fixtures/levels-future-schema.json";
    private static final String LEVELS_INVALID_SCHEMA = "/settlement-fixtures/levels-invalid-schema.json";
    private static final String FOUNDATION_BASIC = "/settlement-fixtures/foundation-basic.json";
    private static final String MISSING = "/settlement-fixtures/does-not-exist.json";

    @Test
    @DisplayName("Loads levels and foundation/categories together when both files are valid")
    void loadsCombinedCatalog() {
        KnowledgeLoadResult<SettlementCatalog> result = new SettlementKnowledgeLoader(LEVELS_BASIC, FOUNDATION_BASIC).load();
        assertEquals(KnowledgeLoadResult.Outcome.LOADED, result.outcome());
        SettlementCatalog catalog = result.data();
        assertEquals(3, catalog.size());
        assertNotNull(catalog.foundation());
        assertEquals("/settlement create <namn>", catalog.foundation().creationCommand());
        assertEquals(2, catalog.productionCategories().size());
        assertEquals(VerificationStatus.VERIFIED, catalog.productionCategoriesVerification().status());
    }

    @Test
    @DisplayName("A missing settlements.json still leaves the level progression usable")
    void missingFoundationFileDoesNotHideLevels() {
        KnowledgeLoadResult<SettlementCatalog> result = new SettlementKnowledgeLoader(LEVELS_BASIC, MISSING).load();
        assertEquals(KnowledgeLoadResult.Outcome.LOADED, result.outcome());
        SettlementCatalog catalog = result.data();
        assertEquals(3, catalog.size());
        assertNull(catalog.foundation());
        assertTrue(catalog.productionCategories().isEmpty());
        assertFalse(catalog.loadWarnings().isEmpty());
    }

    @Test
    @DisplayName("A missing levels resource is a hard error for the whole module")
    void missingLevelsResourceIsError() {
        KnowledgeLoadResult<SettlementCatalog> result = new SettlementKnowledgeLoader(MISSING, FOUNDATION_BASIC).load();
        assertEquals(KnowledgeLoadResult.Outcome.ERROR, result.outcome());
        assertEquals(0, result.data().size());
    }

    @Test
    @DisplayName("A future levels schema version is reported as incompatible, not silently ignored")
    void futureSchemaIsIncompatible() {
        KnowledgeLoadResult<SettlementCatalog> result = new SettlementKnowledgeLoader(LEVELS_FUTURE_SCHEMA, FOUNDATION_BASIC).load();
        assertEquals(KnowledgeLoadResult.Outcome.INCOMPATIBLE_SCHEMA, result.outcome());
    }

    @Test
    @DisplayName("An invalid (zero/negative) schema version is treated as an error")
    void invalidSchemaIsError() {
        KnowledgeLoadResult<SettlementCatalog> result = new SettlementKnowledgeLoader(LEVELS_INVALID_SCHEMA, FOUNDATION_BASIC).load();
        assertEquals(KnowledgeLoadResult.Outcome.ERROR, result.outcome());
    }

    @Test
    @DisplayName("A malformed level entry (missing level number, duplicate id) is skipped without breaking the other entries")
    void malformedEntriesAreSkippedIndividually() {
        KnowledgeLoadResult<SettlementCatalog> result = new SettlementKnowledgeLoader(LEVELS_MALFORMED, MISSING).load();
        assertEquals(KnowledgeLoadResult.Outcome.LOADED, result.outcome());
        SettlementCatalog catalog = result.data();
        assertEquals(2, catalog.size());
        assertTrue(catalog.byLevel(1).isPresent());
        assertEquals("Enstöring", catalog.byLevel(1).get().name());
        assertTrue(catalog.byLevel(2).isPresent());
        assertFalse(catalog.loadWarnings().isEmpty());
    }

    @Test
    @DisplayName("The bundled Rule Pack settlement data loads as exactly the current 50-level / 49-upgrade Settlement Levels 1.0 progression")
    void bundledRulePackIsCurrentFiftyLevelEngine() {
        KnowledgeLoadResult<SettlementCatalog> result = new SettlementKnowledgeLoader().load();
        assertEquals(KnowledgeLoadResult.Outcome.LOADED, result.outcome());
        SettlementCatalog catalog = result.data();

        // Anti-regression guard: the GameZone Wiki migrated away from an obsolete 15-level
        // settlement/building model. This pass must ALWAYS reflect the current "Settlement
        // Levels 1.0" engine (50 levels, level 1 through level 50) - never the old 15-level one.
        assertEquals(50, catalog.size(), "Bundled settlement progression must be the current 50-level engine, not the obsolete 15-level one.");
        assertEquals(1, catalog.minLevel());
        assertEquals(50, catalog.maxLevel());
        for (int lvl = 1; lvl <= 50; lvl++) {
            assertTrue(catalog.byLevel(lvl).isPresent(), "Level " + lvl + " must be present in the current 50-level progression.");
        }
        assertTrue(catalog.byLevel(15).isPresent(), "Level 15 existing is not itself proof of the old engine, but must still resolve under the new one.");
        assertNotNull(catalog.foundation());
        assertEquals(7, catalog.productionCategories().size());
    }
}
