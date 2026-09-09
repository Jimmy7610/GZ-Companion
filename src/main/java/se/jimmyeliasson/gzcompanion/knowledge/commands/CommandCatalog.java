package se.jimmyeliasson.gzcompanion.knowledge.commands;

import se.jimmyeliasson.gzcompanion.knowledge.common.VerificationStatus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Immutable, loaded-once GameZone command knowledge. All search/filter/lookup operations run
 * purely in memory against data parsed once at load time — never against a file, never against
 * a server, never re-parsed per keystroke or per render frame.
 */
public final class CommandCatalog {
    private final List<CommandCategory> categories;
    private final List<CommandDefinition> commands;
    private final List<String> loadWarnings;
    private final Map<String, CommandCategory> categoriesById;
    private final Map<String, String> searchTextByCommandId;

    public CommandCatalog(List<CommandCategory> categories, List<CommandDefinition> commands, List<String> loadWarnings) {
        this.categories = categories != null ? List.copyOf(categories) : List.of();
        this.commands = commands != null ? List.copyOf(commands) : List.of();
        this.loadWarnings = loadWarnings != null ? List.copyOf(loadWarnings) : List.of();

        Map<String, CommandCategory> byId = new HashMap<>();
        for (CommandCategory category : this.categories) {
            byId.put(category.id(), category);
        }
        this.categoriesById = Map.copyOf(byId);

        Map<String, String> searchable = new HashMap<>();
        for (CommandDefinition command : this.commands) {
            CommandCategory category = categoriesById.getOrDefault(command.categoryId(), CommandCategory.FALLBACK);
            searchable.put(command.id(), buildSearchableText(command, category));
        }
        this.searchTextByCommandId = Map.copyOf(searchable);
    }

    public static CommandCatalog empty() {
        return new CommandCatalog(List.of(), List.of(), List.of());
    }

    /** Categories that actually have at least one command, sorted by sortOrder. */
    public List<CommandCategory> categoriesInUse() {
        java.util.Set<String> usedIds = new java.util.HashSet<>();
        for (CommandDefinition command : commands) {
            usedIds.add(command.categoryId());
        }
        List<CommandCategory> result = new ArrayList<>();
        for (CommandCategory category : categories) {
            if (usedIds.contains(category.id())) {
                result.add(category);
            }
        }
        result.sort(Comparator.comparingInt(CommandCategory::sortOrder));
        return result;
    }

    public List<CommandDefinition> commands() {
        return commands;
    }

    public List<String> loadWarnings() {
        return loadWarnings;
    }

    public int size() {
        return commands.size();
    }

    public CommandCategory resolveCategory(String categoryId) {
        if (categoryId == null) return CommandCategory.FALLBACK;
        return categoriesById.getOrDefault(categoryId, CommandCategory.FALLBACK);
    }

    public int countByStatus(VerificationStatus status) {
        int count = 0;
        for (CommandDefinition command : commands) {
            if (command.verification().status() == status) count++;
        }
        return count;
    }

    /** Local-only search + optional category filter. Never touches disk or network. */
    public List<CommandDefinition> search(String query, String categoryIdFilter) {
        String q = (query != null && !query.isBlank()) ? query.trim().toLowerCase(Locale.ROOT) : null;
        List<CommandDefinition> result = new ArrayList<>();
        for (CommandDefinition command : commands) {
            if (categoryIdFilter != null && !categoryIdFilter.isBlank() && !categoryIdFilter.equals(command.categoryId())) {
                continue;
            }
            if (q != null) {
                String haystack = searchTextByCommandId.getOrDefault(command.id(), "");
                if (!haystack.contains(q)) continue;
            }
            result.add(command);
        }
        return result;
    }

    /**
     * Includes both the category's raw id (e.g. {@code foretag}) and its resolved, human-facing
     * displayName (e.g. {@code Företag & Handel}) so a Swedish search term like "handel" finds
     * every command in that category even when the word appears nowhere else on the command.
     * Also appends an underscore-to-space normalized copy of the whole text so a Minecraft-id-like
     * example such as {@code /market oak_log} is still found by searching "oak log".
     */
    private static String buildSearchableText(CommandDefinition command, CommandCategory category) {
        StringBuilder sb = new StringBuilder();
        sb.append(command.primaryCommand()).append(' ');
        sb.append(command.syntax()).append(' ');
        sb.append(command.description()).append(' ');
        sb.append(command.categoryId()).append(' ');
        sb.append(category.displayName()).append(' ');
        for (String alias : command.aliases()) sb.append(alias).append(' ');
        for (String keyword : command.keywords()) sb.append(keyword).append(' ');
        for (String example : command.examples()) sb.append(example).append(' ');
        String base = sb.toString().toLowerCase(Locale.ROOT);
        String spaced = base.replace('_', ' ');
        return spaced.equals(base) ? base : base + ' ' + spaced;
    }
}
