package se.jimmyeliasson.gzcompanion.ui.tabs;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import se.jimmyeliasson.gzcompanion.knowledge.crafting.ClientRecipeCachePolicy;
import se.jimmyeliasson.gzcompanion.knowledge.crafting.ClientRecipeSnapshot;
import se.jimmyeliasson.gzcompanion.knowledge.crafting.RecipeKind;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers the context-key robustness fix: a persistently-null context key (the real
 * player/world/server context being legitimately unavailable) must not defeat the throttle by
 * looking "changed" on every single render call.
 */
class CraftingTabCacheContextTest {

    private static ClientRecipeSnapshot dummyRecipe() {
        return new ClientRecipeSnapshot("minecraft:stick", "Stick", 1, RecipeKind.SHAPELESS, 0, 0, List.of(), 1);
    }

    private static CraftingTabComponent tabCountingReads(AtomicInteger readCount) {
        CraftingTabComponent tab = new CraftingTabComponent();
        tab.setClientRecipeSupplierForTesting(() -> {
            readCount.incrementAndGet();
            return List.of(dummyRecipe());
        });
        return tab;
    }

    @Test
    @DisplayName("The same null context on every call does not force a refresh every frame")
    void testRepeatedNullContextDoesNotForceRefreshEveryFrame() {
        AtomicInteger readCount = new AtomicInteger();
        CraftingTabComponent tab = tabCountingReads(readCount);

        long baseTime = 1_000_000L;
        assertTrue(tab.refreshClientRecipesIfNeeded(baseTime, null), "First call always refreshes");
        assertEquals(1, readCount.get());

        // Still null, still within the throttle window - must NOT re-read.
        assertFalse(tab.refreshClientRecipesIfNeeded(baseTime + 10, null));
        assertFalse(tab.refreshClientRecipesIfNeeded(baseTime + 500, null));
        assertEquals(1, readCount.get(), "A persistently-null context must not defeat the throttle");
    }

    @Test
    @DisplayName("A transition from null to a real context forces a refresh")
    void testNullToRealContextForcesRefresh() {
        AtomicInteger readCount = new AtomicInteger();
        CraftingTabComponent tab = tabCountingReads(readCount);

        long baseTime = 1_000_000L;
        tab.refreshClientRecipesIfNeeded(baseTime, null);
        assertEquals(1, readCount.get());

        boolean refreshed = tab.refreshClientRecipesIfNeeded(baseTime + 10, "world-1");
        assertTrue(refreshed, "null -> a real context is a genuine change and must refresh immediately");
        assertEquals(2, readCount.get());
    }

    @Test
    @DisplayName("A transition between two different real contexts (A -> B) forces a refresh")
    void testContextAToBForcesRefresh() {
        AtomicInteger readCount = new AtomicInteger();
        CraftingTabComponent tab = tabCountingReads(readCount);

        long baseTime = 1_000_000L;
        tab.refreshClientRecipesIfNeeded(baseTime, "world-a");
        assertEquals(1, readCount.get());

        boolean refreshed = tab.refreshClientRecipesIfNeeded(baseTime + 10, "world-b");
        assertTrue(refreshed);
        assertEquals(2, readCount.get());
    }

    @Test
    @DisplayName("The same real context repeated still only refreshes on the normal interval")
    void testSameRealContextRespectsInterval() {
        AtomicInteger readCount = new AtomicInteger();
        CraftingTabComponent tab = tabCountingReads(readCount);

        long baseTime = 1_000_000L;
        tab.refreshClientRecipesIfNeeded(baseTime, "world-a");
        assertEquals(1, readCount.get());

        assertFalse(tab.refreshClientRecipesIfNeeded(baseTime + 100, "world-a"));
        assertEquals(1, readCount.get());

        boolean refreshedAfterInterval = tab.refreshClientRecipesIfNeeded(baseTime + ClientRecipeCachePolicy.MIN_REFRESH_INTERVAL_MS, "world-a");
        assertTrue(refreshedAfterInterval);
        assertEquals(2, readCount.get());
    }

    @Test
    @DisplayName("A transition from a real context back to null also counts as a change and refreshes")
    void testRealContextToNullForcesRefresh() {
        AtomicInteger readCount = new AtomicInteger();
        CraftingTabComponent tab = tabCountingReads(readCount);

        long baseTime = 1_000_000L;
        tab.refreshClientRecipesIfNeeded(baseTime, "world-a");
        assertEquals(1, readCount.get());

        boolean refreshed = tab.refreshClientRecipesIfNeeded(baseTime + 10, null);
        assertTrue(refreshed, "Losing context (world-a -> null) is a real change and must refresh");
        assertEquals(2, readCount.get());
    }
}
