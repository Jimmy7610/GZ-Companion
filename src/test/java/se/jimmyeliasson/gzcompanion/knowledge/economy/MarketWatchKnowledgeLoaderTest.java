package se.jimmyeliasson.gzcompanion.knowledge.economy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import se.jimmyeliasson.gzcompanion.knowledge.common.KnowledgeLoadResult;
import se.jimmyeliasson.gzcompanion.knowledge.common.VerificationStatus;

import static org.junit.jupiter.api.Assertions.*;

class MarketWatchKnowledgeLoaderTest {
    private static final String BASIC = "/marketwatch-fixtures/marketwatch-basic.json";
    private static final String FUTURE_SCHEMA = "/marketwatch-fixtures/marketwatch-future-schema.json";
    private static final String INVALID_SCHEMA = "/marketwatch-fixtures/marketwatch-invalid-schema.json";
    private static final String MISSING = "/marketwatch-fixtures/does-not-exist.json";

    @Test
    @DisplayName("Loads MarketWatch info from a valid fixture")
    void loadsBasicFixture() {
        KnowledgeLoadResult<MarketWatchInfo> result = new MarketWatchKnowledgeLoader(BASIC).load();
        assertEquals(KnowledgeLoadResult.Outcome.LOADED, result.outcome());
        MarketWatchInfo info = result.data();
        assertEquals("/marketwatch", info.command());
        assertEquals(7, info.categoryCount());
        assertEquals(2, info.usageSteps().size());
        assertEquals(VerificationStatus.VERIFIED, info.verification().status());
    }

    @Test
    @DisplayName("A missing resource is a hard error")
    void missingResourceIsError() {
        KnowledgeLoadResult<MarketWatchInfo> result = new MarketWatchKnowledgeLoader(MISSING).load();
        assertEquals(KnowledgeLoadResult.Outcome.ERROR, result.outcome());
        assertNull(result.data().command());
    }

    @Test
    @DisplayName("A future schema version is reported as incompatible")
    void futureSchemaIsIncompatible() {
        KnowledgeLoadResult<MarketWatchInfo> result = new MarketWatchKnowledgeLoader(FUTURE_SCHEMA).load();
        assertEquals(KnowledgeLoadResult.Outcome.INCOMPATIBLE_SCHEMA, result.outcome());
    }

    @Test
    @DisplayName("An invalid schema version is treated as an error")
    void invalidSchemaIsError() {
        KnowledgeLoadResult<MarketWatchInfo> result = new MarketWatchKnowledgeLoader(INVALID_SCHEMA).load();
        assertEquals(KnowledgeLoadResult.Outcome.ERROR, result.outcome());
    }

    @Test
    @DisplayName("The bundled Rule Pack MarketWatch data loads the verified command and category count")
    void bundledRulePackLoadsVerifiedFacts() {
        KnowledgeLoadResult<MarketWatchInfo> result = new MarketWatchKnowledgeLoader().load();
        assertEquals(KnowledgeLoadResult.Outcome.LOADED, result.outcome());
        MarketWatchInfo info = result.data();
        assertEquals("/marketwatch", info.command());
        assertEquals(7, info.categoryCount());
        assertEquals(VerificationStatus.VERIFIED, info.verification().status());
        assertNotNull(info.purpose());
        assertFalse(info.usageSteps().isEmpty());
    }
}
