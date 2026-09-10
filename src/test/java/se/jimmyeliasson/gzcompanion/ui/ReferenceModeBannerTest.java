package se.jimmyeliasson.gzcompanion.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import se.jimmyeliasson.gzcompanion.ui.layout.UiRect;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Human QA: the Reference Mode banner ("Referensläge — du är inte ansluten till GameZoneMC.")
 * was drawn straight over live content in Byggplaner's compact detail pane, hiding the "Bonus"
 * line - because tab layouts were computed against the full tab bounds while the banner then
 * painted over the last 10px of that same area. {@link ReferenceModeBanner#reserveBottomSpace}
 * lets every tab that shows this banner shrink its own layout bounds first, so content is never
 * laid out underneath the strip the banner later draws.
 */
class ReferenceModeBannerTest {

    @Test
    @DisplayName("Bounds are shrunk by exactly the banner height when the banner will be shown")
    void shrinksBoundsWhenBannerShown() {
        UiRect bounds = new UiRect(10, 20, 200, 150);
        UiRect reserved = ReferenceModeBanner.reserveBottomSpace(bounds, true);

        assertEquals(bounds.x(), reserved.x());
        assertEquals(bounds.y(), reserved.y());
        assertEquals(bounds.width(), reserved.width());
        assertEquals(bounds.height() - ReferenceModeBanner.inlineHeight(), reserved.height());

        // The reserved content area must never extend into the strip the banner actually draws.
        assertTrue(reserved.bottom() <= bounds.bottom() - ReferenceModeBanner.inlineHeight());
    }

    @Test
    @DisplayName("Bounds are unchanged when the banner will not be shown (connected to GameZone)")
    void leavesBoundsUnchangedWhenBannerHidden() {
        UiRect bounds = new UiRect(10, 20, 200, 150);
        UiRect reserved = ReferenceModeBanner.reserveBottomSpace(bounds, false);
        assertEquals(bounds, reserved);
    }

    @Test
    @DisplayName("Never produces a negative height for a pane shorter than the banner itself")
    void neverProducesNegativeHeight() {
        UiRect tiny = new UiRect(0, 0, 50, 4);
        UiRect reserved = ReferenceModeBanner.reserveBottomSpace(tiny, true);
        assertTrue(reserved.height() >= 0);
    }
}
