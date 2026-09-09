package se.jimmyeliasson.gzcompanion.knowledge.crafting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Immutable, loaded-once GameZone crafting override knowledge (additions/replacements/disabled
 * vanilla recipes). Search runs purely in memory against data parsed once at load time.
 */
public final class CraftingKnowledgeBase {
    private final List<GameZoneCraftingEntry> entries;
    private final List<String> loadWarnings;
    private final Map<String, String> searchTextByEntryId;

    public CraftingKnowledgeBase(List<GameZoneCraftingEntry> entries, List<String> loadWarnings) {
        this.entries = entries != null ? List.copyOf(entries) : List.of();
        this.loadWarnings = loadWarnings != null ? List.copyOf(loadWarnings) : List.of();

        Map<String, String> searchable = new HashMap<>();
        for (GameZoneCraftingEntry entry : this.entries) {
            searchable.put(entry.id(), buildSearchableText(entry));
        }
        this.searchTextByEntryId = Map.copyOf(searchable);
    }

    public static CraftingKnowledgeBase empty() {
        return new CraftingKnowledgeBase(List.of(), List.of());
    }

    public List<GameZoneCraftingEntry> entries() {
        return entries;
    }

    public List<String> loadWarnings() {
        return loadWarnings;
    }

    public int size() {
        return entries.size();
    }

    public List<GameZoneCraftingEntry> search(String query) {
        String q = (query != null && !query.isBlank()) ? query.trim().toLowerCase(Locale.ROOT) : null;
        if (q == null) return entries;

        List<GameZoneCraftingEntry> result = new ArrayList<>();
        for (GameZoneCraftingEntry entry : entries) {
            if (searchTextByEntryId.getOrDefault(entry.id(), "").contains(q)) {
                result.add(entry);
            }
        }
        return result;
    }

    private static String buildSearchableText(GameZoneCraftingEntry entry) {
        StringBuilder sb = new StringBuilder();
        sb.append(entry.outputItemId()).append(' ');
        if (entry.notes() != null) sb.append(entry.notes()).append(' ');
        for (IngredientRef ref : entry.grid()) appendIngredient(sb, ref);
        for (IngredientRef ref : entry.ingredients()) appendIngredient(sb, ref);
        return sb.toString().toLowerCase(Locale.ROOT);
    }

    private static void appendIngredient(StringBuilder sb, IngredientRef ref) {
        if (ref == null) return;
        for (String itemId : ref.itemIds()) sb.append(itemId).append(' ');
        if (ref.tag() != null) sb.append(ref.tag()).append(' ');
    }
}
