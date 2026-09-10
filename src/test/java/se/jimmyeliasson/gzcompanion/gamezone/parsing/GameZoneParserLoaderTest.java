package se.jimmyeliasson.gzcompanion.gamezone.parsing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import se.jimmyeliasson.gzcompanion.knowledge.common.KnowledgeLoadResult;

import static org.junit.jupiter.api.Assertions.*;

class GameZoneParserLoaderTest {

    @Test
    @DisplayName("Bundled parsers.json loads successfully with zero parsers - no canonical chat text is published for any event yet")
    void testLoadBundled() {
        KnowledgeLoadResult<GameZoneParserCatalog> result = new GameZoneParserLoader().load();
        assertTrue(result.isUsable());
        assertEquals(0, result.data().size());
        assertEquals(0, result.data().activeCount());
        assertTrue(result.data().loadWarnings().isEmpty());
    }

    @Test
    @DisplayName("Missing resource fails closed with ERROR outcome")
    void testMissingResourceFailsClosed() {
        KnowledgeLoadResult<GameZoneParserCatalog> result = new GameZoneParserLoader("/does/not/exist.json").load();
        assertFalse(result.isUsable());
        assertEquals(KnowledgeLoadResult.Outcome.ERROR, result.outcome());
    }

    @Test
    @DisplayName("A future schemaVersion is rejected as INCOMPATIBLE_SCHEMA")
    void testFutureSchemaRejected() {
        KnowledgeLoadResult<GameZoneParserCatalog> result = new GameZoneParserLoader("/gamezone-fixtures/parsers-future-schema.json").load();
        assertEquals(KnowledgeLoadResult.Outcome.INCOMPATIBLE_SCHEMA, result.outcome());
    }

    @Test
    @DisplayName("An unverified parser is loaded but never appears in activeParsers(), even if enabled=true in the data")
    void testUnverifiedParserDisabledDespiteEnabledFlag() {
        KnowledgeLoadResult<GameZoneParserCatalog> result = new GameZoneParserLoader("/gamezone-fixtures/parsers-unverified-enabled.json").load();
        assertTrue(result.isUsable());
        assertEquals(1, result.data().size());
        assertEquals(0, result.data().activeCount(), "An enabled-but-unverified parser must never be active");
    }

    @Test
    @DisplayName("A verified and enabled parser is loaded and appears in activeParsers()")
    void testVerifiedEnabledParserIsActive() {
        KnowledgeLoadResult<GameZoneParserCatalog> result = new GameZoneParserLoader("/gamezone-fixtures/parsers-verified-enabled.json").load();
        assertTrue(result.isUsable());
        assertEquals(1, result.data().size());
        assertEquals(1, result.data().activeCount());
    }

    @Test
    @DisplayName("A parser with an invalid regex pattern is skipped with a warning, not partially loaded")
    void testMalformedRegexSkippedSafely() {
        KnowledgeLoadResult<GameZoneParserCatalog> result = new GameZoneParserLoader("/gamezone-fixtures/parsers-malformed-regex.json").load();
        assertTrue(result.isUsable());
        assertEquals(0, result.data().size());
        assertFalse(result.data().loadWarnings().isEmpty());
    }

    @Test
    @DisplayName("A parser with an unknown eventType or matchType is skipped, other valid parsers still load")
    void testUnknownEnumValuesSkippedButOthersSurvive() {
        KnowledgeLoadResult<GameZoneParserCatalog> result = new GameZoneParserLoader("/gamezone-fixtures/parsers-unknown-enum.json").load();
        assertTrue(result.isUsable());
        assertEquals(1, result.data().size(), "Only the one valid parser should survive - the two with bad enums are skipped");
        assertFalse(result.data().loadWarnings().isEmpty());
    }

    @Test
    @DisplayName("A duplicate parser id is skipped - first occurrence wins")
    void testDuplicateIdSkipped() {
        KnowledgeLoadResult<GameZoneParserCatalog> result = new GameZoneParserLoader("/gamezone-fixtures/parsers-duplicate-id.json").load();
        assertTrue(result.isUsable());
        assertEquals(1, result.data().size());
    }
}
