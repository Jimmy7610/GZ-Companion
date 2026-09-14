package se.jimmyeliasson.gzcompanion.settlement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import se.jimmyeliasson.gzcompanion.knowledge.settlement.SettlementCatalog;
import se.jimmyeliasson.gzcompanion.knowledge.settlement.SettlementLevel;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** {@link LiveSettlementLevel#evaluate} - Rule Pack alignment between a raw live GameZone level/name and the bundled {@link SettlementCatalog}. */
class LiveSettlementLevelTest {

    private static SettlementLevel level(int number, String name) {
        return new SettlementLevel(number, name, 1000, List.of(), null, null, null, null);
    }

    private static SettlementCatalog catalogWith(SettlementLevel... levels) {
        return new SettlementCatalog(List.of(levels), null, List.of(), null, List.of());
    }

    @Test
    @DisplayName("D: a live level whose name matches the catalog is ALIGNED and trusted")
    void matchingLevelNameIsAlignedAndTrusted() {
        SettlementCatalog catalog = catalogWith(level(10, "Småstad"));
        LiveSettlementLevel result = LiveSettlementLevel.evaluate(10, "Småstad", catalog);

        assertEquals(LiveLevelAlignment.ALIGNED, result.alignment());
        assertTrue(result.trusted());
        assertEquals(10, result.level());
    }

    @Test
    @DisplayName("D: name comparison normalizes case and surrounding whitespace only - not a fuzzy match")
    void nameComparisonNormalizesCaseAndWhitespaceOnly() {
        SettlementCatalog catalog = catalogWith(level(10, "Småstad"));
        LiveSettlementLevel result = LiveSettlementLevel.evaluate(10, "  småSTAD  ", catalog);

        assertEquals(LiveLevelAlignment.ALIGNED, result.alignment());
    }

    @Test
    @DisplayName("E: a live level number the catalog doesn't have at all is a MISMATCH, never trusted, raw value preserved")
    void unknownLevelNumberIsMismatch() {
        SettlementCatalog catalog = catalogWith(level(10, "Småstad"));
        LiveSettlementLevel result = LiveSettlementLevel.evaluate(99, "Påhittad", catalog);

        assertEquals(LiveLevelAlignment.MISMATCH, result.alignment());
        assertFalse(result.trusted());
        assertEquals(99, result.level(), "the raw live level must still be preserved, never hidden");
        assertEquals("Påhittad", result.levelName());
    }

    @Test
    @DisplayName("E: a live level number the catalog has, but under a DIFFERENT name, is a MISMATCH")
    void knownLevelWithDisagreeingNameIsMismatch() {
        SettlementCatalog catalog = catalogWith(level(10, "Småstad"));
        LiveSettlementLevel result = LiveSettlementLevel.evaluate(10, "Metropol", catalog);

        assertEquals(LiveLevelAlignment.MISMATCH, result.alignment());
        assertFalse(result.trusted());
        assertEquals(10, result.level());
        assertEquals("Metropol", result.levelName(), "GameZone's own reported name must never be overwritten by the catalog's name");
    }

    @Test
    @DisplayName("F: no live level at all is UNKNOWN, never guessed as aligned or mismatched")
    void missingLevelIsUnknown() {
        SettlementCatalog catalog = catalogWith(level(10, "Småstad"));
        LiveSettlementLevel result = LiveSettlementLevel.evaluate(null, null, catalog);

        assertEquals(LiveLevelAlignment.UNKNOWN, result.alignment());
        assertFalse(result.trusted());
        assertNull(result.level());
    }

    @Test
    @DisplayName("F: a live level number with no accompanying name is UNKNOWN - insufficient info to confirm or deny alignment")
    void levelWithoutNameIsUnknown() {
        SettlementCatalog catalog = catalogWith(level(10, "Småstad"));
        LiveSettlementLevel result = LiveSettlementLevel.evaluate(10, null, catalog);

        assertEquals(LiveLevelAlignment.UNKNOWN, result.alignment());
        assertFalse(result.trusted());
        assertEquals(10, result.level(), "the raw level is still preserved even when alignment can't be confirmed");
    }

    @Test
    @DisplayName("Boundary: level 1 (minimum) and level 50 (maximum) both align correctly - no off-by-one at either edge")
    void boundaryLevelsOneAndFiftyAlignCorrectly() {
        SettlementCatalog catalog = catalogWith(level(1, "Boplats"), level(50, "Imperium"));

        assertEquals(LiveLevelAlignment.ALIGNED, LiveSettlementLevel.evaluate(1, "Boplats", catalog).alignment());
        assertEquals(LiveLevelAlignment.ALIGNED, LiveSettlementLevel.evaluate(50, "Imperium", catalog).alignment());
    }

    @Test
    @DisplayName("NONE constant is unknown/untrusted with no level")
    void noneConstantIsUnknown() {
        assertEquals(LiveLevelAlignment.UNKNOWN, LiveSettlementLevel.NONE.alignment());
        assertFalse(LiveSettlementLevel.NONE.trusted());
        assertNull(LiveSettlementLevel.NONE.level());
    }
}
