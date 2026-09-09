package se.jimmyeliasson.gzcompanion.knowledge.commands;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import se.jimmyeliasson.gzcompanion.knowledge.common.KnowledgeLoadResult;
import se.jimmyeliasson.gzcompanion.knowledge.common.VerificationStatus;

import static org.junit.jupiter.api.Assertions.*;

class CommandKnowledgeLoaderTest {

    @Test
    @DisplayName("Bundled commands.json loads successfully with only VERIFIED GameZone Wiki-sourced entries")
    void testLoadBundled() {
        KnowledgeLoadResult<CommandCatalog> result = new CommandKnowledgeLoader().load();
        assertTrue(result.isUsable(), "Bundled commands.json must load under the current schema");

        CommandCatalog catalog = result.data();
        assertNotNull(catalog);
        assertTrue(catalog.size() > 50, "Bundled catalog should contain the full GameZone command list");
        assertTrue(catalog.loadWarnings().isEmpty(), "Bundled data should not trigger any load warnings: " + catalog.loadWarnings());

        // Every bundled command must be VERIFIED with a real source - nothing invented.
        for (CommandDefinition command : catalog.commands()) {
            assertEquals(VerificationStatus.VERIFIED, command.verification().status(),
                    "Bundled command " + command.id() + " must be VERIFIED");
            assertTrue(command.verification().hasSource(), "Bundled command " + command.id() + " must carry a source");
            assertNotNull(command.verification().lastVerified());
        }

        assertFalse(catalog.categoriesInUse().isEmpty());
    }

    @Test
    @DisplayName("Missing resource fails closed with ERROR outcome and an empty usable catalog")
    void testMissingResourceFailsClosed() {
        KnowledgeLoadResult<CommandCatalog> result = new CommandKnowledgeLoader("/does/not/exist.json").load();
        assertFalse(result.isUsable());
        assertEquals(KnowledgeLoadResult.Outcome.ERROR, result.outcome());
        assertNotNull(result.data());
        assertEquals(0, result.data().size());
    }

    @Test
    @DisplayName("A schemaVersion newer than supported is rejected as INCOMPATIBLE_SCHEMA, not silently accepted")
    void testFutureSchemaVersionRejected() {
        KnowledgeLoadResult<CommandCatalog> result = new CommandKnowledgeLoader("/knowledge-fixtures/commands-future-schema.json").load();
        assertFalse(result.isUsable());
        assertEquals(KnowledgeLoadResult.Outcome.INCOMPATIBLE_SCHEMA, result.outcome());
    }

    @Test
    @DisplayName("A blank/missing command id is skipped with a warning, not silently added")
    void testBlankIdSkipped() {
        KnowledgeLoadResult<CommandCatalog> result = new CommandKnowledgeLoader("/knowledge-fixtures/commands-blank-id.json").load();
        assertTrue(result.isUsable());
        assertEquals(0, result.data().size());
        assertFalse(result.data().loadWarnings().isEmpty());
    }

    @Test
    @DisplayName("A duplicate command id is skipped - first occurrence wins")
    void testDuplicateIdSkipped() {
        KnowledgeLoadResult<CommandCatalog> result = new CommandKnowledgeLoader("/knowledge-fixtures/commands-duplicate-id.json").load();
        assertTrue(result.isUsable());
        assertEquals(1, result.data().size());
        assertEquals("/first", result.data().commands().get(0).primaryCommand());
        assertFalse(result.data().loadWarnings().isEmpty());
    }

    @Test
    @DisplayName("An alias colliding with another command's primaryCommand is dropped, not silently merged - the command itself still loads")
    void testAliasCollisionDropsOnlyTheAlias() {
        KnowledgeLoadResult<CommandCatalog> result = new CommandKnowledgeLoader("/knowledge-fixtures/commands-alias-collision.json").load();
        assertTrue(result.isUsable());
        assertEquals(2, result.data().size(), "Both commands still load - only the colliding alias is dropped");
        CommandDefinition second = result.data().commands().stream().filter(c -> c.id().equals("cmd2")).findFirst().orElseThrow();
        assertTrue(second.aliases().isEmpty(), "The colliding alias must not silently attach to the second command");
        assertFalse(result.data().loadWarnings().isEmpty());
    }

    @Test
    @DisplayName("A command referencing an unknown categoryId falls back gracefully via resolveCategory")
    void testUnknownCategoryFallsBackGracefully() {
        KnowledgeLoadResult<CommandCatalog> result = new CommandKnowledgeLoader("/knowledge-fixtures/commands-unknown-category.json").load();
        assertTrue(result.isUsable());
        CommandCatalog catalog = result.data();
        assertEquals(1, catalog.size());
        CommandCategory resolved = catalog.resolveCategory(catalog.commands().get(0).categoryId());
        assertEquals(CommandCategory.FALLBACK_ID, resolved.id());
    }

    @Test
    @DisplayName("A command missing verification entirely defaults to UNVERIFIED, never VERIFIED")
    void testMissingVerificationDefaultsToUnverified() {
        KnowledgeLoadResult<CommandCatalog> result = new CommandKnowledgeLoader("/knowledge-fixtures/commands-no-verification.json").load();
        assertTrue(result.isUsable());
        assertEquals(1, result.data().size());
        assertEquals(VerificationStatus.UNVERIFIED, result.data().commands().get(0).verification().status());
    }
}
