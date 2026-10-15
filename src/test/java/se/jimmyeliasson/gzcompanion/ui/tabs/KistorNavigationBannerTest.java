package se.jimmyeliasson.gzcompanion.ui.tabs;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import se.jimmyeliasson.gzcompanion.chest.ChestManager;
import se.jimmyeliasson.gzcompanion.chest.KistorRuntime;
import se.jimmyeliasson.gzcompanion.chest.model.ChestSlotEntry;
import se.jimmyeliasson.gzcompanion.chest.model.StorageKind;
import se.jimmyeliasson.gzcompanion.chest.model.StoragePosition;
import se.jimmyeliasson.gzcompanion.chest.model.StorageShape;
import se.jimmyeliasson.gzcompanion.chest.model.StoredContainerId;
import se.jimmyeliasson.gzcompanion.chest.nav.ChestNavigationManager;
import se.jimmyeliasson.gzcompanion.chest.nav.NavigationDestination;
import se.jimmyeliasson.gzcompanion.chest.storage.JsonChestIndexStore;
import se.jimmyeliasson.gzcompanion.marketwatch.MarketWatchNavigation;
import se.jimmyeliasson.gzcompanion.marketwatch.storage.MarketWatchNote;
import se.jimmyeliasson.gzcompanion.marketwatch.storage.MarketWatchSavedLocation;
import se.jimmyeliasson.gzcompanion.ui.layout.KistorLayout;
import se.jimmyeliasson.gzcompanion.ui.layout.UiRect;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The Kistor NAVIGERAR banner's click targets, driven through the tab's real {@code mouseClicked}
 * dispatch: Stoppa has its own hitbox inside the banner and wins over it; the rest of the banner
 * opens the storage detail only for a storage target, never for a saved point (MarketWatch).
 */
class KistorNavigationBannerTest {
    @TempDir
    Path tempDir;

    static final String CTX = "p@@server:play.gamezonemc.se";
    static final String OW = "minecraft:overworld";
    static final StoragePosition CHEST_POS = new StoragePosition(100, 64, 100);
    static final StoredContainerId CHEST = new StoredContainerId(CTX, OW, CHEST_POS, StorageKind.CHEST);

    /** The same window sizes Kistor2LayoutTest covers: compact, small, normal and large content areas. */
    static Stream<UiRect> boundsCases() {
        return Stream.of(new UiRect(0, 0, 200, 170), new UiRect(10, 10, 240, 180), new UiRect(50, 40, 360, 200), new UiRect(0, 0, 620, 320));
    }

    private KistorRuntime runtime() {
        ChestManager manager = new ChestManager(new JsonChestIndexStore(tempDir.resolve("chest-index.json")));
        manager.initialize();
        manager.recordPendingInteraction(CTX, OW, StorageKind.CHEST, CHEST_POS, null, StorageShape.SINGLE, 1L);
        manager.tryBeginCapture(CTX, OW, EnumSet.of(StorageKind.CHEST), 2L);
        manager.updateCaptureSlots(List.of(new ChestSlotEntry(0, "minecraft:stone", 3)), 3L);
        manager.endCapture(4L);
        manager.setLabel(CTX, CHEST, "Materiallager med ett mycket långt namn som aldrig får gå in under Stoppa");
        return new KistorRuntime(manager);
    }

    private static MarketWatchNote shopNote() {
        return new MarketWatchNote("n1", "minecraft:beef", "Raw Beef", null, "Olivres Shop", 1L, false,
                MarketWatchSavedLocation.of(OW, -847, 70, 12, 5L).orElseThrow());
    }

    private static NavigationDestination destination(KistorRuntime runtime) {
        return runtime.navigation().resolveDestination(runtime.chestManager()).orElseThrow();
    }

    private static double[] center(UiRect r) {
        return new double[]{r.x() + r.width() / 2.0, r.y() + r.height() / 2.0};
    }

    /** A point inside the banner but outside Stoppa (line 2, left side). */
    private static double[] bannerOutsideStop(KistorLayout l) {
        UiRect b = l.navBannerRect();
        double[] p = {b.x() + 6, b.bottom() - 3};
        assertTrue(b.contains(p[0], p[1]) && !l.navStopBtnRect().contains(p[0], p[1]));
        return p;
    }

    @ParameterizedTest
    @MethodSource("boundsCases")
    @DisplayName("Chest target: clicking Stoppa stops navigation and does NOT open the storage")
    void stoppaStopsWithoutOpeningStorage(UiRect bounds) {
        KistorRuntime runtime = runtime();
        assertTrue(runtime.navigation().start(runtime.chestManager(), CTX, CHEST, 10L));
        KistorTabComponent tab = new KistorTabComponent();
        tab.prepareNavigationBannerForTesting(bounds, runtime, CTX, destination(runtime), 20L);

        double[] stop = center(tab.getLayout().navStopBtnRect());
        assertTrue(tab.getLayout().navBannerRect().contains(stop[0], stop[1]), "Stoppa lies inside the banner");
        assertTrue(tab.mouseClicked(stop[0], stop[1], 0, bounds, null));

        assertFalse(runtime.navigation().isActive());
        assertEquals(ChestNavigationManager.StopReason.USER, runtime.navigation().lastStopReason().orElseThrow());
        assertNull(tab.stateForTesting().selectedStorage, "Stoppa must not fall through to the banner's open-storage hit");
        assertEquals(KistorLayout.Mode.SAKER, tab.stateForTesting().mode);
    }

    @ParameterizedTest
    @MethodSource("boundsCases")
    @DisplayName("Chest target: clicking the banner outside Stoppa opens that storage's detail and keeps navigating")
    void chestBannerOpensStorage(UiRect bounds) {
        KistorRuntime runtime = runtime();
        runtime.navigation().start(runtime.chestManager(), CTX, CHEST, 10L);
        KistorTabComponent tab = new KistorTabComponent();
        tab.prepareNavigationBannerForTesting(bounds, runtime, CTX, destination(runtime), 20L);

        double[] p = bannerOutsideStop(tab.getLayout());
        assertTrue(tab.mouseClicked(p[0], p[1], 0, bounds, null));

        assertEquals(CHEST, tab.stateForTesting().selectedStorage);
        assertEquals(KistorLayout.Mode.FORVARING, tab.stateForTesting().mode);
        assertTrue(runtime.navigation().isTarget(CHEST), "Opening the detail never stops navigation");
    }

    @ParameterizedTest
    @MethodSource("boundsCases")
    @DisplayName("MarketWatch point target: the banner never tries to open storage, but Stoppa still stops it")
    void pointBannerNeverOpensStorage(UiRect bounds) {
        KistorRuntime runtime = runtime();
        MarketWatchNote note = shopNote();
        assertTrue(MarketWatchNavigation.startNavigation(runtime.navigation(), CTX, note, 10L));
        NavigationDestination dest = destination(runtime);
        assertTrue(dest.container().isEmpty());
        assertEquals("Raw Beef", dest.title());

        KistorTabComponent tab = new KistorTabComponent();
        tab.prepareNavigationBannerForTesting(bounds, runtime, CTX, dest, 20L);

        double[] p = bannerOutsideStop(tab.getLayout());
        assertFalse(tab.mouseClicked(p[0], p[1], 0, bounds, null), "No click target outside Stoppa for a point");
        assertNull(tab.stateForTesting().selectedStorage);
        assertEquals(KistorLayout.Mode.SAKER, tab.stateForTesting().mode);
        assertTrue(MarketWatchNavigation.isNavigatingTo(runtime.navigation(), note));

        double[] stop = center(tab.getLayout().navStopBtnRect());
        assertTrue(tab.mouseClicked(stop[0], stop[1], 0, bounds, null));
        assertFalse(runtime.navigation().isActive());
    }

    @ParameterizedTest
    @MethodSource("boundsCases")
    @DisplayName("Replacing chest -> point -> chest switches the banner's open-storage behaviour with the target")
    void replacementSwitchesBannerBehaviour(UiRect bounds) {
        KistorRuntime runtime = runtime();
        ChestNavigationManager nav = runtime.navigation();
        nav.start(runtime.chestManager(), CTX, CHEST, 10L);
        MarketWatchNavigation.startNavigation(nav, CTX, shopNote(), 11L);
        assertEquals(ChestNavigationManager.StopReason.REPLACED, nav.lastStopReason().orElseThrow());

        KistorTabComponent pointTab = new KistorTabComponent();
        pointTab.prepareNavigationBannerForTesting(bounds, runtime, CTX, destination(runtime), 20L);
        double[] p = bannerOutsideStop(pointTab.getLayout());
        assertFalse(pointTab.mouseClicked(p[0], p[1], 0, bounds, null));

        nav.start(runtime.chestManager(), CTX, CHEST, 12L);
        KistorTabComponent chestTab = new KistorTabComponent();
        chestTab.prepareNavigationBannerForTesting(bounds, runtime, CTX, destination(runtime), 21L);
        assertTrue(chestTab.mouseClicked(p[0], p[1], 0, bounds, null));
        assertEquals(CHEST, chestTab.stateForTesting().selectedStorage);
    }
}
