package se.jimmyeliasson.gzcompanion.knowledge.commands;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import se.jimmyeliasson.gzcompanion.knowledge.common.VerificationMetadata;
import se.jimmyeliasson.gzcompanion.knowledge.common.VerificationStatus;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CommandCatalogTest {

    private static CommandDefinition cmd(String id, String primary, String categoryId, VerificationStatus status) {
        VerificationMetadata verification = status == VerificationStatus.VERIFIED
                ? new VerificationMetadata(status, "Test Source", "https://example.com", "2026-09-10")
                : new VerificationMetadata(status, null, null, null);
        return new CommandDefinition(id, primary, List.of(), primary, "Description of " + primary,
                categoryId, List.of("keyword-" + id), List.of(), null, verification);
    }

    @Test
    @DisplayName("empty() catalog has no commands, categories, or warnings")
    void testEmpty() {
        CommandCatalog catalog = CommandCatalog.empty();
        assertEquals(0, catalog.size());
        assertTrue(catalog.categoriesInUse().isEmpty());
        assertTrue(catalog.loadWarnings().isEmpty());
    }

    @Test
    @DisplayName("categoriesInUse only returns categories that actually have commands, sorted by sortOrder")
    void testCategoriesInUseFiltersAndSorts() {
        List<CommandCategory> categories = List.of(
                new CommandCategory("b", "B", 20),
                new CommandCategory("a", "A", 10),
                new CommandCategory("unused", "Unused", 5)
        );
        List<CommandDefinition> commands = List.of(
                cmd("c1", "/one", "a", VerificationStatus.VERIFIED),
                cmd("c2", "/two", "b", VerificationStatus.VERIFIED)
        );
        CommandCatalog catalog = new CommandCatalog(categories, commands, List.of());

        List<CommandCategory> inUse = catalog.categoriesInUse();
        assertEquals(2, inUse.size());
        assertEquals("a", inUse.get(0).id());
        assertEquals("b", inUse.get(1).id());
    }

    @Test
    @DisplayName("resolveCategory falls back to the shared FALLBACK category for an unknown id")
    void testResolveCategoryFallback() {
        CommandCatalog catalog = new CommandCatalog(List.of(), List.of(), List.of());
        CommandCategory resolved = catalog.resolveCategory("does-not-exist");
        assertEquals(CommandCategory.FALLBACK_ID, resolved.id());

        assertEquals(CommandCategory.FALLBACK_ID, catalog.resolveCategory(null).id());
    }

    @Test
    @DisplayName("countByStatus counts only commands with the exact matching status")
    void testCountByStatus() {
        List<CommandDefinition> commands = List.of(
                cmd("c1", "/one", "a", VerificationStatus.VERIFIED),
                cmd("c2", "/two", "a", VerificationStatus.VERIFIED),
                cmd("c3", "/three", "a", VerificationStatus.UNVERIFIED)
        );
        CommandCatalog catalog = new CommandCatalog(List.of(), commands, List.of());
        assertEquals(2, catalog.countByStatus(VerificationStatus.VERIFIED));
        assertEquals(1, catalog.countByStatus(VerificationStatus.UNVERIFIED));
        assertEquals(0, catalog.countByStatus(VerificationStatus.STALE));
    }

    @Test
    @DisplayName("search matches command text, description, and keywords case-insensitively")
    void testSearchMatchesAcrossFields() {
        List<CommandDefinition> commands = List.of(
                cmd("c1", "/settlement info", "settlement", VerificationStatus.VERIFIED),
                cmd("c2", "/company create", "foretag", VerificationStatus.VERIFIED)
        );
        CommandCatalog catalog = new CommandCatalog(List.of(), commands, List.of());

        assertEquals(1, catalog.search("SETTLEMENT", null).size());
        assertEquals(1, catalog.search("keyword-c2", null).size());
        assertEquals(0, catalog.search("nonexistent-term", null).size());
        assertEquals(2, catalog.search(null, null).size());
        assertEquals(2, catalog.search("", null).size());
    }

    @Test
    @DisplayName("search with a categoryIdFilter only returns commands in that category")
    void testSearchWithCategoryFilter() {
        List<CommandDefinition> commands = List.of(
                cmd("c1", "/settlement info", "settlement", VerificationStatus.VERIFIED),
                cmd("c2", "/company create", "foretag", VerificationStatus.VERIFIED)
        );
        CommandCatalog catalog = new CommandCatalog(List.of(), commands, List.of());

        List<CommandDefinition> filtered = catalog.search(null, "settlement");
        assertEquals(1, filtered.size());
        assertEquals("c1", filtered.get(0).id());
    }

    @Test
    @DisplayName("search combines a text query and a category filter with AND semantics")
    void testSearchCombinesQueryAndCategory() {
        List<CommandDefinition> commands = List.of(
                cmd("c1", "/settlement info", "settlement", VerificationStatus.VERIFIED),
                cmd("c2", "/settlement create", "settlement", VerificationStatus.VERIFIED)
        );
        CommandCatalog catalog = new CommandCatalog(List.of(), commands, List.of());

        List<CommandDefinition> filtered = catalog.search("create", "settlement");
        assertEquals(1, filtered.size());
        assertEquals("c2", filtered.get(0).id());
    }
}
