package se.jimmyeliasson.gzcompanion.guide;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import se.jimmyeliasson.gzcompanion.guide.model.GuideChapter;
import se.jimmyeliasson.gzcompanion.guide.model.GuideDefinition;
import se.jimmyeliasson.gzcompanion.guide.model.GuideManifest;
import se.jimmyeliasson.gzcompanion.guide.model.GuideStep;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GuideLoaderTest {

    @Test
    @DisplayName("Should successfully load bundled manifest and beginner guide")
    void testLoadBundled() {
        GuideLoader loader = new GuideLoader();
        GuideLoader.LoadResult result = loader.loadBundled();

        assertTrue(result.isSuccess(), "Guide loading should succeed with bundled resources: " + result.errors());
        assertTrue(result.errors().isEmpty(), "No errors should occur during bundled load");

        GuideManifest manifest = result.manifest();
        assertNotNull(manifest, "Manifest must not be null");
        assertEquals("2026.09.09.1", manifest.contentVersion());
        assertEquals("sv-SE", manifest.locale());
        assertTrue(manifest.testedMinecraftVersions().contains("26.1.2"));
        assertFalse(manifest.guides().isEmpty(), "Manifest should contain at least 1 guide");

        List<GuideDefinition> guides = result.guides();
        assertEquals(1, guides.size());

        GuideDefinition beginnerGuide = guides.get(0);
        assertEquals("minecraft-beginner", beginnerGuide.id());
        assertEquals("Nybörjarguiden", beginnerGuide.title());
        assertEquals(5, beginnerGuide.chapters().size(), "Should have exactly 5 chapters");
        assertEquals(22, beginnerGuide.steps().size(), "Should have exactly 22 steps");

        // Verify chapter IDs and ordering
        assertEquals("ch1_kom_igang", beginnerGuide.chapters().get(0).id());
        assertEquals("ch2_forsta_verktygen", beginnerGuide.chapters().get(1).id());
        assertEquals("ch3_overlev_natten", beginnerGuide.chapters().get(2).id());
        assertEquals("ch4_trygg_bas", beginnerGuide.chapters().get(3).id());
        assertEquals("ch5_jarnaldern", beginnerGuide.chapters().get(4).id());

        // Verify first and last step IDs
        assertEquals("movement_controls", beginnerGuide.steps().get(0).id());
        assertEquals("craft_iron_armor", beginnerGuide.steps().get(21).id());
    }

    @Test
    @DisplayName("Should gracefully handle non-existent resource paths")
    void testLoadNonExistentPath() {
        GuideLoader loader = new GuideLoader();
        GuideLoader.LoadResult result = loader.loadFromPath("/non/existent/manifest.json", "/non/existent/");

        assertFalse(result.isSuccess());
        assertNull(result.manifest());
        assertTrue(result.guides().isEmpty());
        assertFalse(result.errors().isEmpty());
    }
}
