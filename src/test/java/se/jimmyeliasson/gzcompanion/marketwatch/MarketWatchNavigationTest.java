package se.jimmyeliasson.gzcompanion.marketwatch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import se.jimmyeliasson.gzcompanion.chest.ChestManager;
import se.jimmyeliasson.gzcompanion.chest.model.ChestSlotEntry;
import se.jimmyeliasson.gzcompanion.chest.model.StorageKind;
import se.jimmyeliasson.gzcompanion.chest.model.StoragePosition;
import se.jimmyeliasson.gzcompanion.chest.model.StorageShape;
import se.jimmyeliasson.gzcompanion.chest.model.StoredContainerId;
import se.jimmyeliasson.gzcompanion.chest.nav.ChestNavigationManager;
import se.jimmyeliasson.gzcompanion.chest.nav.NavigationDestination;
import se.jimmyeliasson.gzcompanion.chest.nav.NavigationPoint;
import se.jimmyeliasson.gzcompanion.chest.nav.PlayerPose;
import se.jimmyeliasson.gzcompanion.chest.storage.JsonChestIndexStore;
import se.jimmyeliasson.gzcompanion.marketwatch.storage.MarketWatchNote;
import se.jimmyeliasson.gzcompanion.marketwatch.storage.MarketWatchSavedLocation;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class MarketWatchNavigationTest {
    @TempDir
    Path tempDir;

    static final String CTX = "p@@server:play.gamezonemc.se";
    static final String OW = "minecraft:overworld";

    private static MarketWatchNote located(String id) {
        MarketWatchSavedLocation loc = MarketWatchSavedLocation.of(OW, -847, 70, 12, 5000L).orElseThrow();
        return new MarketWatchNote(id, "minecraft:beef", "Raw Beef", null, "Olivres Shop, 20 st", 1L, false, loc);
    }

    private static MarketWatchNote plain(String id) {
        return new MarketWatchNote(id, "minecraft:beef", "Raw Beef", null, "20 st", 1L, false);
    }

    @Test
    @DisplayName("A note with a saved location starts navigation with exactly its coordinates, dimension and name")
    void validLocationStartsNavigation() {
        ChestNavigationManager nav = new ChestNavigationManager();
        MarketWatchNote note = located("n1");

        assertTrue(MarketWatchNavigation.canNavigate(CTX, note));
        assertTrue(MarketWatchNavigation.startNavigation(nav, CTX, note, 10L));
        assertTrue(MarketWatchNavigation.isNavigatingTo(nav, note));

        NavigationPoint point = nav.pointTarget().orElseThrow();
        assertEquals("marketwatch:n1", point.sourceKey());
        assertEquals(CTX, point.contextStorageKey());
        assertEquals(OW, point.dimensionKey());
        assertEquals(new StoragePosition(-847, 70, 12), point.position());
        assertEquals("Raw Beef", point.label());

        NavigationDestination dest = nav.resolveDestination(null).orElseThrow();
        assertEquals("Raw Beef", dest.title());
    }

    @Test
    @DisplayName("A note without a saved location can never start navigation")
    void noLocationCannotNavigate() {
        ChestNavigationManager nav = new ChestNavigationManager();
        MarketWatchNote note = plain("n1");
        assertFalse(MarketWatchNavigation.canNavigate(CTX, note));
        assertTrue(MarketWatchNavigation.toNavigationPoint(CTX, note).isEmpty());
        assertFalse(MarketWatchNavigation.startNavigation(nav, CTX, note, 10L));
        assertFalse(nav.isActive());
        assertFalse(MarketWatchNavigation.startNavigation(nav, null, located("n2"), 10L), "No context, no navigation");
        assertFalse(MarketWatchNavigation.startNavigation(null, CTX, located("n2"), 10L));
        assertFalse(MarketWatchNavigation.canNavigate(CTX, null));
    }

    @Test
    @DisplayName("Only the note that started navigation reports itself as the target")
    void targetIdentityIsPerNote() {
        ChestNavigationManager nav = new ChestNavigationManager();
        MarketWatchNote a = located("a");
        MarketWatchNote b = located("b");
        MarketWatchNavigation.startNavigation(nav, CTX, a, 1L);
        assertTrue(MarketWatchNavigation.isNavigatingTo(nav, a));
        assertFalse(MarketWatchNavigation.isNavigatingTo(nav, b));

        MarketWatchNavigation.startNavigation(nav, CTX, b, 2L);
        assertFalse(MarketWatchNavigation.isNavigatingTo(nav, a));
        assertTrue(MarketWatchNavigation.isNavigatingTo(nav, b));
        assertEquals(ChestNavigationManager.StopReason.REPLACED, nav.lastStopReason().orElseThrow());
    }

    @Test
    @DisplayName("MarketWatch replaces an active Kistor chest target, and Kistor HITTA still replaces it back")
    void replacesAndIsReplacedByChestNavigation() {
        ChestManager manager = new ChestManager(new JsonChestIndexStore(tempDir.resolve("chest-index.json")));
        manager.initialize();
        StoragePosition pos = new StoragePosition(5, 64, 5);
        manager.recordPendingInteraction(CTX, OW, StorageKind.CHEST, pos, null, StorageShape.SINGLE, 1L);
        manager.tryBeginCapture(CTX, OW, EnumSet.of(StorageKind.CHEST), 2L);
        manager.updateCaptureSlots(List.of(new ChestSlotEntry(0, "minecraft:stone", 1)), 3L);
        manager.endCapture(4L);
        StoredContainerId chest = new StoredContainerId(CTX, OW, pos, StorageKind.CHEST);

        ChestNavigationManager nav = new ChestNavigationManager();
        assertTrue(nav.start(manager, CTX, chest, 5L));
        MarketWatchNote note = located("n1");
        assertTrue(MarketWatchNavigation.startNavigation(nav, CTX, note, 6L));
        assertFalse(nav.isTarget(chest));
        assertTrue(MarketWatchNavigation.isNavigatingTo(nav, note));

        assertTrue(nav.start(manager, CTX, chest, 7L));
        assertTrue(nav.isTarget(chest));
        assertFalse(MarketWatchNavigation.isNavigatingTo(nav, note));
    }

    @Test
    @DisplayName("Spara plats records the player's own block position, floored, with dimension and time")
    void locationFromPoseFloorsCoordinates() {
        MarketWatchSavedLocation loc = MarketWatchNavigation.locationFromPose(
                Optional.of(new PlayerPose("minecraft:the_nether", -12.3, 64.99, 7.8, 90f)), 1234L).orElseThrow();
        assertEquals("minecraft:the_nether", loc.dimensionKey());
        assertEquals(-13, loc.x());
        assertEquals(64, loc.y());
        assertEquals(7, loc.z());
        assertEquals(1234L, loc.savedAtMs());
    }

    @Test
    @DisplayName("Without a usable live position nothing is recorded - never a fallback 0,0")
    void locationFromPoseRejectsUnusablePose() {
        assertTrue(MarketWatchNavigation.locationFromPose(Optional.empty(), 1L).isEmpty());
        assertTrue(MarketWatchNavigation.locationFromPose(null, 1L).isEmpty());
        assertTrue(MarketWatchNavigation.locationFromPose(Optional.of(new PlayerPose(OW, Double.NaN, 64, 0, 0f)), 1L).isEmpty());
        assertTrue(MarketWatchNavigation.locationFromPose(Optional.of(new PlayerPose(OW, 0, Double.POSITIVE_INFINITY, 0, 0f)), 1L).isEmpty());
        assertTrue(MarketWatchNavigation.locationFromPose(Optional.of(new PlayerPose(null, 1, 64, 1, 0f)), 1L).isEmpty());
        assertTrue(MarketWatchNavigation.locationFromPose(Optional.of(new PlayerPose(OW, 4.0e7, 64, 1, 0f)), 1L).isEmpty());
        assertTrue(MarketWatchNavigation.locationFromPose(Optional.of(new PlayerPose(OW, 1, 9999, 1, 0f)), 1L).isEmpty());
    }

    @Test
    @DisplayName("Spara plats works in any dimension Minecraft can name, and Hitta navigates there")
    void anyDimensionCanBeSavedAndNavigated() {
        for (String dim : List.of("minecraft:the_end", "gamezone:resource_world", "my_mod:deep/caves")) {
            MarketWatchSavedLocation loc = MarketWatchNavigation.locationFromPose(
                    Optional.of(new PlayerPose(dim, 1.2, 70, 2.8, 0f)), 1L).orElseThrow(() -> new AssertionError(dim));
            MarketWatchNote note = plain("n").withLocation(loc);
            ChestNavigationManager nav = new ChestNavigationManager();
            assertTrue(MarketWatchNavigation.startNavigation(nav, CTX, note, 1L), dim);
            assertEquals(dim, nav.resolveDestination(null).orElseThrow().dimensionKey());
        }
    }

    @Test
    @DisplayName("stopIfNavigatingTo only stops the note's own navigation")
    void stopIfNavigatingToIsTargeted() {
        ChestNavigationManager nav = new ChestNavigationManager();
        MarketWatchNavigation.startNavigation(nav, CTX, located("a"), 1L);
        MarketWatchNavigation.stopIfNavigatingTo(nav, located("b"));
        assertTrue(nav.isActive());
        MarketWatchNavigation.stopIfNavigatingTo(nav, located("a"));
        assertFalse(nav.isActive());
        assertEquals(ChestNavigationManager.StopReason.TARGET_MISSING, nav.lastStopReason().orElseThrow());
    }
}
