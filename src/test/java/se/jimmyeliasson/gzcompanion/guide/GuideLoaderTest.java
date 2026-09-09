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

    @Test
    @DisplayName("Should reject unsupported future manifest schema version (> 1)")
    void testRejectFutureManifestSchema() {
        GuideLoader loader = new GuideLoader();
        com.google.gson.JsonObject json = new com.google.gson.JsonObject();
        json.addProperty("schemaVersion", 999);
        json.addProperty("contentVersion", "2026.09.09.1");
        json.addProperty("locale", "sv-SE");

        java.util.List<String> warnings = new java.util.ArrayList<>();
        java.util.List<String> errors = new java.util.ArrayList<>();
        GuideManifest manifest = loader.parseManifest(json, warnings, errors);

        assertNull(manifest);
        assertFalse(errors.isEmpty());
        assertTrue(errors.get(0).contains("schemaVersion 999"));
    }

    @Test
    @DisplayName("Should reject manifest schema version <= 0 or missing")
    void testRejectInvalidManifestSchema() {
        GuideLoader loader = new GuideLoader();
        com.google.gson.JsonObject json = new com.google.gson.JsonObject();
        json.addProperty("schemaVersion", 0);

        java.util.List<String> warnings = new java.util.ArrayList<>();
        java.util.List<String> errors = new java.util.ArrayList<>();
        GuideManifest manifest = loader.parseManifest(json, warnings, errors);

        assertNull(manifest);
        assertFalse(errors.isEmpty());
    }

    @Test
    @DisplayName("Should reject unsupported future guide schema version (> 1)")
    void testRejectFutureGuideSchema() {
        GuideLoader loader = new GuideLoader();
        com.google.gson.JsonObject json = new com.google.gson.JsonObject();
        json.addProperty("schemaVersion", 999);
        json.addProperty("id", "future-guide");

        java.util.List<String> warnings = new java.util.ArrayList<>();
        java.util.List<String> errors = new java.util.ArrayList<>();
        GuideDefinition guide = loader.parseGuide(json, null, warnings, errors);

        assertNull(guide);
        assertFalse(errors.isEmpty());
        assertTrue(errors.get(0).contains("schemaVersion 999"));
    }

    @Test
    @DisplayName("Should reject unknown condition types without converting to MANUAL")
    void testRejectUnknownConditionType() {
        GuideLoader loader = new GuideLoader();
        com.google.gson.JsonObject json = new com.google.gson.JsonObject();
        json.addProperty("schemaVersion", 1);
        json.addProperty("id", "test-guide");
        json.addProperty("title", "Test Guide");

        com.google.gson.JsonArray chapters = new com.google.gson.JsonArray();
        com.google.gson.JsonObject ch = new com.google.gson.JsonObject();
        ch.addProperty("id", "ch1");
        ch.addProperty("title", "Chapter 1");
        chapters.add(ch);
        json.add("chapters", chapters);

        com.google.gson.JsonArray steps = new com.google.gson.JsonArray();
        com.google.gson.JsonObject step = new com.google.gson.JsonObject();
        step.addProperty("id", "step_typo");
        step.addProperty("chapterId", "ch1");
        step.addProperty("title", "Step Typo");

        com.google.gson.JsonArray conditions = new com.google.gson.JsonArray();
        com.google.gson.JsonObject cond = new com.google.gson.JsonObject();
        cond.addProperty("type", "HAS_ITME"); // Typo
        conditions.add(cond);
        step.add("conditions", conditions);
        steps.add(step);
        json.add("steps", steps);

        java.util.List<String> warnings = new java.util.ArrayList<>();
        java.util.List<String> errors = new java.util.ArrayList<>();
        GuideDefinition guide = loader.parseGuide(json, null, warnings, errors);

        assertNotNull(guide);
        assertTrue(guide.steps().isEmpty(), "Step with invalid condition type must not be loaded");
        assertFalse(errors.isEmpty(), "Error must be recorded for unknown condition type");
        assertTrue(errors.stream().anyMatch(e -> e.contains("HAS_ITME")));
    }
}
