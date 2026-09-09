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

        assertNull(guide);
        assertFalse(errors.isEmpty(), "Error must be recorded for unknown condition type");
        assertTrue(errors.stream().anyMatch(e -> e.contains("HAS_ITME")));
    }

    @Test
    @DisplayName("Should fail safely when schemaVersion is a non-numeric string or null")
    void testMalformedNumericSchemaVersionFailsSafely() {
        GuideLoader loader = new GuideLoader();
        List<String> warnings = new java.util.ArrayList<>();
        List<String> errors = new java.util.ArrayList<>();

        // String "banana" instead of integer
        com.google.gson.JsonObject jsonBanana = new com.google.gson.JsonObject();
        jsonBanana.addProperty("schemaVersion", "banana");
        GuideManifest manifest1 = loader.parseManifest(jsonBanana, warnings, errors);
        assertNull(manifest1);
        assertFalse(errors.isEmpty());

        // Null schemaVersion
        errors.clear();
        com.google.gson.JsonObject jsonNull = new com.google.gson.JsonObject();
        jsonNull.add("schemaVersion", com.google.gson.JsonNull.INSTANCE);
        GuideManifest manifest2 = loader.parseManifest(jsonNull, warnings, errors);
        assertNull(manifest2);
        assertFalse(errors.isEmpty());
    }

    @Test
    @DisplayName("Should fail safely when order or count are malformed strings")
    void testMalformedNumericFieldsFailSafely() {
        GuideLoader loader = new GuideLoader();
        List<String> warnings = new java.util.ArrayList<>();
        List<String> errors = new java.util.ArrayList<>();

        // Malformed order in chapter
        com.google.gson.JsonObject json = new com.google.gson.JsonObject();
        json.addProperty("schemaVersion", 1);
        json.addProperty("id", "g1");
        json.addProperty("title", "G1");

        com.google.gson.JsonArray chapters = new com.google.gson.JsonArray();
        com.google.gson.JsonObject ch = new com.google.gson.JsonObject();
        ch.addProperty("id", "ch1");
        ch.addProperty("title", "Chapter 1");
        ch.addProperty("order", "first"); // Malformed string instead of int
        chapters.add(ch);
        json.add("chapters", chapters);

        com.google.gson.JsonArray steps = new com.google.gson.JsonArray();
        json.add("steps", steps);

        GuideDefinition guide = loader.parseGuide(json, null, warnings, errors);
        assertNull(guide);
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.contains("order")));
    }

    @Test
    @DisplayName("Should fail safely when count in condition is a string")
    void testMalformedCountFailsSafely() {
        GuideLoader loader = new GuideLoader();
        List<String> warnings = new java.util.ArrayList<>();
        List<String> errors = new java.util.ArrayList<>();

        com.google.gson.JsonObject json = new com.google.gson.JsonObject();
        json.addProperty("schemaVersion", 1);
        json.addProperty("id", "g1");
        json.addProperty("title", "G1");

        com.google.gson.JsonArray chapters = new com.google.gson.JsonArray();
        com.google.gson.JsonObject ch = new com.google.gson.JsonObject();
        ch.addProperty("id", "ch1");
        ch.addProperty("title", "Chapter 1");
        chapters.add(ch);
        json.add("chapters", chapters);

        com.google.gson.JsonArray steps = new com.google.gson.JsonArray();
        com.google.gson.JsonObject step = new com.google.gson.JsonObject();
        step.addProperty("id", "s1");
        step.addProperty("chapterId", "ch1");
        step.addProperty("title", "Step 1");

        com.google.gson.JsonObject cond = new com.google.gson.JsonObject();
        cond.addProperty("type", "HAS_ITEM");
        cond.addProperty("itemId", "minecraft:stick");
        cond.addProperty("count", "four"); // Malformed string
        step.add("condition", cond);
        steps.add(step);
        json.add("steps", steps);

        GuideDefinition guide = loader.parseGuide(json, null, warnings, errors);
        assertNull(guide);
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.contains("count")));
    }

    @Test
    @DisplayName("Should fail safely when boolean fields are malformed types")
    void testMalformedBooleanFailsSafely() {
        GuideLoader loader = new GuideLoader();
        List<String> warnings = new java.util.ArrayList<>();
        List<String> errors = new java.util.ArrayList<>();

        com.google.gson.JsonObject json = new com.google.gson.JsonObject();
        json.addProperty("schemaVersion", 1);
        json.addProperty("id", "g1");
        json.addProperty("title", "G1");

        com.google.gson.JsonArray chapters = new com.google.gson.JsonArray();
        com.google.gson.JsonObject ch = new com.google.gson.JsonObject();
        ch.addProperty("id", "ch1");
        ch.addProperty("title", "Chapter 1");
        chapters.add(ch);
        json.add("chapters", chapters);

        com.google.gson.JsonArray steps = new com.google.gson.JsonArray();
        com.google.gson.JsonObject step = new com.google.gson.JsonObject();
        step.addProperty("id", "s1");
        step.addProperty("chapterId", "ch1");
        step.addProperty("title", "Step 1");
        step.addProperty("optional", "yes"); // "yes" instead of boolean true/false
        steps.add(step);
        json.add("steps", steps);

        GuideDefinition guide = loader.parseGuide(json, null, warnings, errors);
        assertNull(guide);
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.contains("optional")));
    }

    @Test
    @DisplayName("Should fail safely when array fields are objects or strings")
    void testMalformedArraysFailSafely() {
        GuideLoader loader = new GuideLoader();
        List<String> warnings = new java.util.ArrayList<>();
        List<String> errors = new java.util.ArrayList<>();

        // testedMinecraftVersions is a string instead of array
        com.google.gson.JsonObject manifestJson = new com.google.gson.JsonObject();
        manifestJson.addProperty("schemaVersion", 1);
        manifestJson.addProperty("testedMinecraftVersions", "26.1.2");
        com.google.gson.JsonArray guides = new com.google.gson.JsonArray();
        manifestJson.add("guides", guides);

        GuideManifest manifest = loader.parseManifest(manifestJson, warnings, errors);
        assertNull(manifest);
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.contains("testedMinecraftVersions")));

        // chapters is a string "wrong"
        errors.clear();
        com.google.gson.JsonObject guideJson = new com.google.gson.JsonObject();
        guideJson.addProperty("schemaVersion", 1);
        guideJson.addProperty("chapters", "wrong");
        GuideDefinition guide1 = loader.parseGuide(guideJson, null, warnings, errors);
        assertNull(guide1);
        assertFalse(errors.isEmpty());

        // steps is an object {}
        errors.clear();
        com.google.gson.JsonObject guideJson2 = new com.google.gson.JsonObject();
        guideJson2.addProperty("schemaVersion", 1);
        guideJson2.add("chapters", new com.google.gson.JsonArray());
        guideJson2.add("steps", new com.google.gson.JsonObject());
        GuideDefinition guide2 = loader.parseGuide(guideJson2, null, warnings, errors);
        assertNull(guide2);
        assertFalse(errors.isEmpty());
    }

    @Test
    @DisplayName("Should fail safely when nested condition subConditions is not an array")
    void testMalformedNestedConditionFailsSafely() {
        GuideLoader loader = new GuideLoader();
        List<String> warnings = new java.util.ArrayList<>();
        List<String> errors = new java.util.ArrayList<>();

        com.google.gson.JsonObject json = new com.google.gson.JsonObject();
        json.addProperty("schemaVersion", 1);
        json.addProperty("id", "g1");
        json.addProperty("title", "G1");

        com.google.gson.JsonArray chapters = new com.google.gson.JsonArray();
        com.google.gson.JsonObject ch = new com.google.gson.JsonObject();
        ch.addProperty("id", "ch1");
        ch.addProperty("title", "Chapter 1");
        chapters.add(ch);
        json.add("chapters", chapters);

        com.google.gson.JsonArray steps = new com.google.gson.JsonArray();
        com.google.gson.JsonObject step = new com.google.gson.JsonObject();
        step.addProperty("id", "s1");
        step.addProperty("chapterId", "ch1");
        step.addProperty("title", "Step 1");

        com.google.gson.JsonObject cond = new com.google.gson.JsonObject();
        cond.addProperty("type", "ALL_OF");
        cond.addProperty("subConditions", "wrong"); // String instead of array
        step.add("condition", cond);
        steps.add(step);
        json.add("steps", steps);

        GuideDefinition guide = loader.parseGuide(json, null, warnings, errors);
        assertNull(guide);
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.contains("subConditions")));
    }
}
