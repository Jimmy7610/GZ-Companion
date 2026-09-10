package se.jimmyeliasson.gzcompanion.gamezone.parsing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import se.jimmyeliasson.gzcompanion.gamezone.events.GameZoneEventType;
import se.jimmyeliasson.gzcompanion.gamezone.events.GameZoneObservedEvent;
import se.jimmyeliasson.gzcompanion.knowledge.common.VerificationMetadata;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class GameZoneParserEngineTest {

    private static final VerificationMetadata TEST_ONLY_VERIFICATION =
            new VerificationMetadata(se.jimmyeliasson.gzcompanion.knowledge.common.VerificationStatus.VERIFIED, "TEST ONLY fixture", "test://fixture", "2026-09-10");

    @Test
    @DisplayName("A verified, active EXACT parser matches an identical message")
    void testExactMatch() {
        GameZoneParserDefinition parser = new GameZoneParserDefinition("p1", GameZoneEventType.SYSTEM_MESSAGE, ParserMatchType.EXACT,
                "TEST ONLY hello", List.of(), true, TEST_ONLY_VERIFICATION);

        Optional<GameZoneObservedEvent> result = GameZoneParserEngine.match(List.of(parser), "TEST ONLY hello", 1000L);
        assertTrue(result.isPresent());
        assertEquals(GameZoneEventType.SYSTEM_MESSAGE, result.get().eventType());
        assertEquals("p1", result.get().parserId());
    }

    @Test
    @DisplayName("An unrecognized message does not match any parser")
    void testUnknownMessageNoMatch() {
        GameZoneParserDefinition parser = new GameZoneParserDefinition("p1", GameZoneEventType.SYSTEM_MESSAGE, ParserMatchType.EXACT,
                "TEST ONLY hello", List.of(), true, TEST_ONLY_VERIFICATION);

        Optional<GameZoneObservedEvent> result = GameZoneParserEngine.match(List.of(parser), "completely unrelated message", 1000L);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("A CONTAINS parser matches a substring anywhere in the message")
    void testContainsMatch() {
        GameZoneParserDefinition parser = new GameZoneParserDefinition("p1", GameZoneEventType.WHISPER, ParserMatchType.CONTAINS,
                "TEST_ONLY_MARKER", List.of(), true, TEST_ONLY_VERIFICATION);

        Optional<GameZoneObservedEvent> result = GameZoneParserEngine.match(List.of(parser), "prefix TEST_ONLY_MARKER suffix", 1000L);
        assertTrue(result.isPresent());
    }

    @Test
    @DisplayName("A REGEX parser captures declared groups by name, in order")
    void testRegexCapturesNamedGroups() {
        GameZoneParserDefinition parser = new GameZoneParserDefinition("p1", GameZoneEventType.BALANCE_CHANGE, ParserMatchType.REGEX,
                "^TEST_ONLY balance is (\\d+) for (\\w+)$", List.of("amount", "player"), true, TEST_ONLY_VERIFICATION);

        Optional<GameZoneObservedEvent> result = GameZoneParserEngine.match(List.of(parser), "TEST_ONLY balance is 500 for Alice", 1000L);
        assertTrue(result.isPresent());
        assertEquals("500", result.get().capturedValues().get("amount"));
        assertEquals("Alice", result.get().capturedValues().get("player"));
    }

    @Test
    @DisplayName("One parser with a runtime match failure cannot prevent a later parser in the list from matching")
    void testOneParserFailureDoesNotBreakOthers() {
        // A REGEX parser whose pattern would already have failed load-time validation, simulating
        // a defensive runtime failure - the engine must still evaluate the next parser in the list.
        GameZoneParserDefinition brokenFirst = new GameZoneParserDefinition("broken", GameZoneEventType.UNKNOWN, ParserMatchType.REGEX,
                "[", List.of(), true, TEST_ONLY_VERIFICATION);
        GameZoneParserDefinition workingSecond = new GameZoneParserDefinition("working", GameZoneEventType.WHISPER, ParserMatchType.EXACT,
                "TEST ONLY message", List.of(), true, TEST_ONLY_VERIFICATION);

        Optional<GameZoneObservedEvent> result = GameZoneParserEngine.match(List.of(brokenFirst, workingSecond), "TEST ONLY message", 1000L);
        assertTrue(result.isPresent());
        assertEquals("working", result.get().parserId());
    }

    @Test
    @DisplayName("An empty or null parser list never matches, never throws")
    void testEmptyOrNullParserList() {
        assertTrue(GameZoneParserEngine.match(List.of(), "any message", 1000L).isEmpty());
        assertTrue(GameZoneParserEngine.match(null, "any message", 1000L).isEmpty());
    }

    @Test
    @DisplayName("A null message never matches, never throws")
    void testNullMessage() {
        GameZoneParserDefinition parser = new GameZoneParserDefinition("p1", GameZoneEventType.SYSTEM_MESSAGE, ParserMatchType.CONTAINS,
                "x", List.of(), true, TEST_ONLY_VERIFICATION);
        assertTrue(GameZoneParserEngine.match(List.of(parser), null, 1000L).isEmpty());
    }
}
