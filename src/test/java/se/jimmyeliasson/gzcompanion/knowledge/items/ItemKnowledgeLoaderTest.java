package se.jimmyeliasson.gzcompanion.knowledge.items;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import se.jimmyeliasson.gzcompanion.knowledge.common.KnowledgeLoadResult;
import se.jimmyeliasson.gzcompanion.knowledge.common.VerificationStatus;

import static org.junit.jupiter.api.Assertions.*;

class ItemKnowledgeLoaderTest {

    @Test
    @DisplayName("Bundled item-overrides.json loads all 50 relics, each VERIFIED with a real GameZone Wiki source")
    void testLoadBundled() {
        KnowledgeLoadResult<ItemKnowledgeBase> result = new ItemKnowledgeLoader().load();
        assertTrue(result.isUsable());

        ItemKnowledgeBase base = result.data();
        assertEquals(50, base.size(), "The registry documents exactly 50 relics");
        assertTrue(base.loadWarnings().isEmpty(), "Bundled data should not trigger any load warnings: " + base.loadWarnings());

        for (CustomItemKnowledge item : base.items()) {
            assertEquals(VerificationStatus.VERIFIED, item.verification().status(), "Relic " + item.id() + " must be VERIFIED");
            assertTrue(item.verification().hasSource(), "Relic " + item.id() + " must carry a source");
            assertNull(item.releaseStatus(), "No per-relic release status is published on the wiki - must not be invented");
        }
    }

    @Test
    @DisplayName("Several relics may legitimately share the same base Minecraft item id - byBaseItemId never assumes uniqueness")
    void testSharedBaseItemIdIsNotAssumedUnique() {
        ItemKnowledgeBase base = new ItemKnowledgeLoader().load().data();
        // Several relics use minecraft:iron_pickaxe as their base item (Miner's Companion, Ruinsplitter, ...).
        assertTrue(base.byBaseItemId("minecraft:iron_pickaxe").size() > 1);
    }

    @Test
    @DisplayName("Missing resource fails closed with ERROR outcome")
    void testMissingResourceFailsClosed() {
        KnowledgeLoadResult<ItemKnowledgeBase> result = new ItemKnowledgeLoader("/does/not/exist.json").load();
        assertFalse(result.isUsable());
        assertEquals(KnowledgeLoadResult.Outcome.ERROR, result.outcome());
    }

    @Test
    @DisplayName("A future schemaVersion is rejected as INCOMPATIBLE_SCHEMA")
    void testFutureSchemaRejected() {
        KnowledgeLoadResult<ItemKnowledgeBase> result = new ItemKnowledgeLoader("/knowledge-fixtures/items-future-schema.json").load();
        assertEquals(KnowledgeLoadResult.Outcome.INCOMPATIBLE_SCHEMA, result.outcome());
    }

    @Test
    @DisplayName("An item missing displayName is skipped, even though it has a valid id")
    void testMissingDisplayNameSkipped() {
        KnowledgeLoadResult<ItemKnowledgeBase> result = new ItemKnowledgeLoader("/knowledge-fixtures/items-no-displayname.json").load();
        assertTrue(result.isUsable());
        assertEquals(0, result.data().size());
        assertFalse(result.data().loadWarnings().isEmpty());
    }

    @Test
    @DisplayName("A duplicate item id is skipped - first occurrence wins")
    void testDuplicateIdSkipped() {
        KnowledgeLoadResult<ItemKnowledgeBase> result = new ItemKnowledgeLoader("/knowledge-fixtures/items-duplicate-id.json").load();
        assertTrue(result.isUsable());
        assertEquals(1, result.data().size());
        assertEquals("First", result.data().items().get(0).displayName());
    }

    @Test
    @DisplayName("An item with only id and displayName loads fine - every other field is optional by design")
    void testMinimalItemLoadsWithAllOptionalFieldsNull() {
        KnowledgeLoadResult<ItemKnowledgeBase> result = new ItemKnowledgeLoader("/knowledge-fixtures/items-minimal.json").load();
        assertTrue(result.isUsable());
        assertEquals(1, result.data().size());
        CustomItemKnowledge item = result.data().items().get(0);
        assertNull(item.baseMinecraftItemId());
        assertNull(item.description());
        assertTrue(item.lore().isEmpty());
        assertNull(item.tier());
        assertNull(item.culture());
        assertEquals(se.jimmyeliasson.gzcompanion.knowledge.common.VerificationStatus.UNVERIFIED, item.verification().status());
    }

    @Test
    @DisplayName("A malformed lore array entry (non-string element) is safely ignored rather than crashing the load")
    void testMalformedLoreEntrySafelyIgnored() {
        KnowledgeLoadResult<ItemKnowledgeBase> result = new ItemKnowledgeLoader("/knowledge-fixtures/items-malformed-lore.json").load();
        assertTrue(result.isUsable());
        assertEquals(1, result.data().size());
        // Fixture has 3 raw "lore" array entries, one of which is a nested object - only the 2
        // valid string lines must survive.
        assertEquals(2, result.data().items().get(0).lore().size());
    }
}
