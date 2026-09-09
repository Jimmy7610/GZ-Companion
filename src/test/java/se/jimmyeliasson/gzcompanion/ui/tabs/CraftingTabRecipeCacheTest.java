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
 * Proves the Crafting tab's recipe-book cache actually throttles: repeated render-adjacent calls
 * inside the refresh window must not trigger repeated (expensive) recipe-book reads. Uses the
 * package-private test injection seam ({@code setClientRecipeSupplierForTesting}) so this runs
 * without a live Minecraft client.
 */
class CraftingTabRecipeCacheTest {

    private static ClientRecipeSnapshot dummyRecipe() {
        return new ClientRecipeSnapshot("minecraft:stick", "Stick", 4, RecipeKind.SHAPELESS, 0, 0, List.of());
    }

    @Test
    @DisplayName("Repeated refresh calls within the throttle window only read the recipe book once")
    void testRepeatedCallsWithinWindowDoNotRereadRecipeBook() {
        CraftingTabComponent tab = new CraftingTabComponent();
        AtomicInteger readCount = new AtomicInteger();
        tab.setClientRecipeSupplierForTesting(() -> {
            readCount.incrementAndGet();
            return List.of(dummyRecipe());
        });

        long baseTime = 10_000_000L;
        assertTrue(tab.refreshClientRecipesIfNeeded(baseTime, "ctx-1"), "First call must always refresh");
        assertEquals(1, readCount.get());

        // Several more "render frames" within the throttle window - none should re-read.
        assertFalse(tab.refreshClientRecipesIfNeeded(baseTime + 10, "ctx-1"));
        assertFalse(tab.refreshClientRecipesIfNeeded(baseTime + 500, "ctx-1"));
        assertFalse(tab.refreshClientRecipesIfNeeded(baseTime + 1499, "ctx-1"));
        assertEquals(1, readCount.get(), "No read should happen again inside the throttle window");
    }

    @Test
    @DisplayName("A refresh happens again once the throttle interval has elapsed")
    void testRefreshHappensAfterIntervalElapses() {
        CraftingTabComponent tab = new CraftingTabComponent();
        AtomicInteger readCount = new AtomicInteger();
        tab.setClientRecipeSupplierForTesting(() -> {
            readCount.incrementAndGet();
            return List.of(dummyRecipe());
        });

        long baseTime = 10_000_000L;
        tab.refreshClientRecipesIfNeeded(baseTime, "ctx-1");
        assertEquals(1, readCount.get());

        boolean refreshed = tab.refreshClientRecipesIfNeeded(baseTime + ClientRecipeCachePolicy.MIN_REFRESH_INTERVAL_MS, "ctx-1");
        assertTrue(refreshed);
        assertEquals(2, readCount.get());
    }

    @Test
    @DisplayName("A context change (world/server/profile switch) forces an immediate refresh even inside the window")
    void testContextChangeForcesImmediateRefresh() {
        CraftingTabComponent tab = new CraftingTabComponent();
        AtomicInteger readCount = new AtomicInteger();
        tab.setClientRecipeSupplierForTesting(() -> {
            readCount.incrementAndGet();
            return List.of(dummyRecipe());
        });

        long baseTime = 10_000_000L;
        tab.refreshClientRecipesIfNeeded(baseTime, "ctx-1");
        assertEquals(1, readCount.get());

        boolean refreshed = tab.refreshClientRecipesIfNeeded(baseTime + 10, "ctx-2");
        assertTrue(refreshed, "A different context key must force a refresh regardless of elapsed time");
        assertEquals(2, readCount.get());
    }

    @Test
    @DisplayName("A null supplier result is treated as an empty list, never a crash")
    void testNullSupplierResultTreatedAsEmpty() {
        CraftingTabComponent tab = new CraftingTabComponent();
        tab.setClientRecipeSupplierForTesting(() -> null);

        assertTrue(tab.refreshClientRecipesIfNeeded(1L, "ctx"));
        assertTrue(tab.getCachedClientRecipesForTesting().isEmpty());
    }
}
