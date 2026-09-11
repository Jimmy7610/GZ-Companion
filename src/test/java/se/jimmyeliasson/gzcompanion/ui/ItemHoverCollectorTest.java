package se.jimmyeliasson.gzcompanion.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import se.jimmyeliasson.gzcompanion.ui.layout.UiRect;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure hit-testing/selection logic for item hover tooltips - no live Minecraft Font/ItemStack
 * needed (the collector is generic over its payload), so this exercises exactly what
 * {@link ItemHoverTooltips} relies on without any MC-touching glue.
 */
class ItemHoverCollectorTest {

    @Test
    @DisplayName("Pointer inside a registered item's rect resolves that item as hovered")
    void pointerInsideItemRect_becomesHovered() {
        ItemHoverCollector<String> collector = new ItemHoverCollector<>();
        collector.register(new UiRect(10, 10, 16, 16), "iron_ingot");

        assertEquals("iron_ingot", collector.hovered(15, 15).orElse(null));
    }

    @Test
    @DisplayName("Pointer outside every registered rect resolves nothing - no tooltip")
    void pointerOutsideEveryRect_resolvesNothing() {
        ItemHoverCollector<String> collector = new ItemHoverCollector<>();
        collector.register(new UiRect(10, 10, 16, 16), "iron_ingot");

        assertTrue(collector.hovered(100, 100).isEmpty());
    }

    @Test
    @DisplayName("With multiple overlapping targets, only the most recently registered (topmost/last-drawn) one is selected")
    void multipleOverlappingTargets_onlyTopmostSelected() {
        ItemHoverCollector<String> collector = new ItemHoverCollector<>();
        collector.register(new UiRect(0, 0, 20, 20), "background_item");
        collector.register(new UiRect(5, 5, 10, 10), "foreground_item"); // drawn after, so visually on top at the overlap

        assertEquals("foreground_item", collector.hovered(8, 8).orElse(null),
                "The point is inside both rects - the later-registered (topmost) target must win, not the first or both.");
        assertEquals("background_item", collector.hovered(1, 1).orElse(null),
                "A point only inside the first target's rect must still resolve to it.");
    }

    @Test
    @DisplayName("A target whose rect falls entirely outside a scissored/visible area is dropped and never hovers again")
    void offscreenTarget_isRemovedAndNeverHovered() {
        ItemHoverCollector<String> collector = new ItemHoverCollector<>();
        UiRect visibleArea = new UiRect(0, 100, 200, 50); // visible y-range: 100-150
        UiRect offscreenAboveRect = new UiRect(10, 10, 16, 16); // entirely above the visible area
        UiRect onscreenRect = new UiRect(10, 110, 16, 16); // inside the visible area

        collector.register(offscreenAboveRect, "scrolled_off_item");
        collector.register(onscreenRect, "visible_item");

        collector.removeIf(t -> ItemHoverCollector.isOutsideVisibleArea(t.rect(), visibleArea));

        assertTrue(collector.hovered(15, 15).isEmpty(), "The offscreen target's rect must no longer register a hover, even at its own former position.");
        assertEquals("visible_item", collector.hovered(15, 115).orElse(null), "A genuinely visible target must survive the same filtering pass.");
    }

    @Test
    @DisplayName("isOutsideVisibleArea is a pure vertical-bounds check matching how scrollable lists clip rows")
    void isOutsideVisibleArea_checksVerticalBoundsOnly() {
        UiRect visibleArea = new UiRect(0, 100, 200, 50); // y: 100-150

        assertTrue(ItemHoverCollector.isOutsideVisibleArea(new UiRect(0, 50, 10, 10), visibleArea), "Fully above the visible area.");
        assertTrue(ItemHoverCollector.isOutsideVisibleArea(new UiRect(0, 160, 10, 10), visibleArea), "Fully below the visible area.");
        assertFalse(ItemHoverCollector.isOutsideVisibleArea(new UiRect(0, 120, 10, 10), visibleArea), "Fully inside the visible area.");
    }

    @Test
    @DisplayName("clear() drops every target - a stale target from a previous frame never survives into the next one")
    void clear_dropsStaleTargetsFromPreviousFrame() {
        ItemHoverCollector<String> collector = new ItemHoverCollector<>();
        collector.register(new UiRect(10, 10, 16, 16), "iron_ingot");
        assertTrue(collector.hovered(15, 15).isPresent());

        collector.clear();

        assertTrue(collector.hovered(15, 15).isEmpty(), "After clear(), no target should be hoverable even at the exact same screen position.");
        assertEquals(0, collector.size());
    }
}
