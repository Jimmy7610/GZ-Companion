package se.jimmyeliasson.gzcompanion.guide;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import se.jimmyeliasson.gzcompanion.guide.bridge.GuidePlayerSnapshot;
import se.jimmyeliasson.gzcompanion.guide.model.GuideDefinition;
import se.jimmyeliasson.gzcompanion.guide.progress.JsonGuideProgressStore;
import se.jimmyeliasson.gzcompanion.ui.layout.GuideLayout;
import se.jimmyeliasson.gzcompanion.ui.layout.UiRect;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class GuideLayoutTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Should compute 2-pane side-by-side geometry in normal / large mode")
    void testTwoPaneLayout() {
        UiRect bounds = new UiRect(50, 40, 360, 200);
        GuideLayout layout = GuideLayout.calculate(bounds);

        assertFalse(layout.isCompact());
        assertNotNull(layout.headerRect());
        assertNotNull(layout.progressBarRect());
        assertNotNull(layout.leftNavRect());
        assertNotNull(layout.detailRect());
        assertNotNull(layout.markDoneBtnRect());
        assertNotNull(layout.resetBtnRect());

        // Left nav and detail pane should not overlap horizontally
        assertTrue(layout.leftNavRect().right() <= layout.detailRect().x(), "Left navigator and detail pane must not overlap");
        assertTrue(layout.detailRect().right() <= bounds.right(), "Detail pane must fit within bounds");
        assertTrue(layout.markDoneBtnRect().right() <= layout.resetBtnRect().x(), "Action buttons must not overlap");
    }

    @Test
    @DisplayName("Should compute single-pane geometry in compact mode")
    void testCompactLayout() {
        UiRect bounds = new UiRect(10, 10, 240, 180);
        GuideLayout layout = GuideLayout.calculate(bounds);

        assertTrue(layout.isCompact());
        assertEquals(bounds.width(), layout.leftNavRect().width());
        assertEquals(bounds.width(), layout.detailRect().width());
    }

    @Test
    @DisplayName("Should calculate positive max scroll when content exceeds visible height")
    void testCalculateMaxScroll() {
        GuideLoader loader = new GuideLoader();
        GuideLoader.LoadResult result = loader.loadBundled();
        assertTrue(result.isSuccess());
        GuideDefinition guide = result.guides().getFirst();
        assertNotNull(guide);

        JsonGuideProgressStore store = new JsonGuideProgressStore(tempDir.resolve("guide-progress.json"));
        GuideEngine engine = new GuideEngine(loader, store, () -> GuidePlayerSnapshot.EMPTY);
        engine.initialize();

        UiRect tallNav = new UiRect(0, 0, 150, 1000);
        int maxScrollTall = se.jimmyeliasson.gzcompanion.ui.tabs.GuideTabComponent.calculateMaxScroll(tallNav, engine, guide);
        assertEquals(0, maxScrollTall, "Max scroll should be 0 when everything fits");

        UiRect shortNav = new UiRect(0, 0, 150, 100);
        int maxScrollShort = se.jimmyeliasson.gzcompanion.ui.tabs.GuideTabComponent.calculateMaxScroll(shortNav, engine, guide);
        assertTrue(maxScrollShort > 0, "Max scroll should be positive when content exceeds visible height");
    }
}
