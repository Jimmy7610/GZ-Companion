package se.jimmyeliasson.gzcompanion.ui.tabs;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Human QA findings: the "Kopiera /marketwatch" reference-card button and the "Ta bort" note
 * delete button were both hardcoded to a fixed pixel width, truncating to "Kopiera /mark..." and
 * "Ta bo..." once real text exceeded it. The fix sizes each button to its actual (widest possible)
 * label instead of a magic-number width - these tests inject a synthetic width measurer so the
 * sizing policy is verifiable without a live Minecraft {@code Font}.
 */
class MarketWatchTabComponentTest {

    /** A stand-in for Font-based measurement: every character is 6px wide. */
    private static int fakeMeasure(String s) {
        return s.length() * 6;
    }

    @Test
    @DisplayName("Button width grows to fit real text instead of staying pinned to a magic-number width")
    void buttonWidthFitsActualText() {
        String longCommand = "Kopiera /marketwatch";
        int width = MarketWatchTabComponent.widestButtonWidth(MarketWatchTabComponentTest::fakeMeasure, 10, longCommand);

        int oldHardcodedWidth = 70;
        assertTrue(width >= fakeMeasure(longCommand) + 10, "Width must actually fit the measured text plus padding.");
        assertTrue(width > oldHardcodedWidth, "The old fixed 70px was too small for this real command and caused the truncation QA reported.");
    }

    @Test
    @DisplayName("Button width picks the widest of several possible labels, so a later label swap (e.g. the delete confirmation) never shrinks it")
    void buttonWidthPicksWidestOfMultipleLabels() {
        int width = MarketWatchTabComponent.widestButtonWidth(MarketWatchTabComponentTest::fakeMeasure, 8, "Ta bort", "Säker?");
        int expected = Math.max(fakeMeasure("Ta bort"), fakeMeasure("Säker?")) + 8;
        assertEquals(expected, width);
    }

    @Test
    @DisplayName("Delete button label reflects the two-step confirmation state")
    void deleteLabelReflectsConfirmationState() {
        assertEquals("Ta bort", MarketWatchTabComponent.deleteButtonLabel(false));
        assertEquals("Säker?", MarketWatchTabComponent.deleteButtonLabel(true));
    }
}
