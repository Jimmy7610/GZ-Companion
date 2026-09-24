package se.jimmyeliasson.gzcompanion.chest.index;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import se.jimmyeliasson.gzcompanion.chest.ChestManager;
import se.jimmyeliasson.gzcompanion.chest.model.ChestGroupFilter;
import se.jimmyeliasson.gzcompanion.chest.model.ChestSlotEntry;
import se.jimmyeliasson.gzcompanion.chest.model.ChestTypeFilter;
import se.jimmyeliasson.gzcompanion.chest.model.StorageKind;
import se.jimmyeliasson.gzcompanion.chest.model.StoragePosition;
import se.jimmyeliasson.gzcompanion.chest.model.StorageShape;
import se.jimmyeliasson.gzcompanion.chest.storage.JsonChestIndexStore;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChestItemIndexCacheTest {
    @TempDir
    Path tempDir;

    @Test
    @DisplayName("The cached index is reused while nothing changes and rebuilt deterministically after a Chest Manager mutation")
    void cacheInvalidatesOnMutation() {
        ChestManager m = new ChestManager(new JsonChestIndexStore(tempDir.resolve("chest-index.json")));
        m.initialize();
        String ctx = "p@@server:gz";
        StoragePosition pos = new StoragePosition(1, 64, 1);
        m.recordPendingInteraction(ctx, "minecraft:overworld", StorageKind.CHEST, pos, null, StorageShape.SINGLE, 1000L);
        m.tryBeginCapture(ctx, "minecraft:overworld", EnumSet.of(StorageKind.CHEST), 1010L);
        m.updateCaptureSlots(List.of(new ChestSlotEntry(0, "minecraft:stone", 5)), 1020L);
        m.endCapture(1030L);

        ChestItemIndexCache cache = new ChestItemIndexCache();
        ChestItemIndex first = cache.get(m, ctx, ChestTypeFilter.ALL, ChestGroupFilter.ALL);
        ChestItemIndex again = cache.get(m, ctx, ChestTypeFilter.ALL, ChestGroupFilter.ALL);
        assertSame(first, again, "No rebuild while the revision is unchanged (e.g. every render frame)");
        assertEquals(5, first.totalOf("minecraft:stone"));

        assertTrue(cache.get(m, ctx, ChestTypeFilter.BARREL, ChestGroupFilter.ALL).isEmpty(), "A different filter is a different cache key");
        assertEquals(5, cache.get(m, ctx, ChestTypeFilter.ALL, ChestGroupFilter.ALL).totalOf("minecraft:stone"));

        m.clearContext(ctx);
        ChestItemIndex afterClear = cache.get(m, ctx, ChestTypeFilter.ALL, ChestGroupFilter.ALL);
        assertNotSame(first, afterClear);
        assertTrue(afterClear.isEmpty(), "A reset must never keep serving the old totals");
    }
}
