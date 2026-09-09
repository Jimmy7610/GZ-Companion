package se.jimmyeliasson.gzcompanion.knowledge.items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Immutable, loaded-once GameZone custom-item/lore knowledge. Search runs purely in memory
 * against data parsed once at load time. Deliberately does NOT assume a base Minecraft item ID
 * is unique — several GameZone items may legitimately share one base item (see
 * {@code docs/KNOWLEDGE-BASE.md} for the identity limitations this reflects).
 */
public final class ItemKnowledgeBase {
    private final List<CustomItemKnowledge> items;
    private final List<String> loadWarnings;
    private final Map<String, String> searchTextByItemId;

    public ItemKnowledgeBase(List<CustomItemKnowledge> items, List<String> loadWarnings) {
        this.items = items != null ? List.copyOf(items) : List.of();
        this.loadWarnings = loadWarnings != null ? List.copyOf(loadWarnings) : List.of();

        Map<String, String> searchable = new HashMap<>();
        for (CustomItemKnowledge item : this.items) {
            searchable.put(item.id(), buildSearchableText(item));
        }
        this.searchTextByItemId = Map.copyOf(searchable);
    }

    public static ItemKnowledgeBase empty() {
        return new ItemKnowledgeBase(List.of(), List.of());
    }

    public List<CustomItemKnowledge> items() {
        return items;
    }

    public List<String> loadWarnings() {
        return loadWarnings;
    }

    public int size() {
        return items.size();
    }

    /** All entries sharing the given base Minecraft item ID. Never assumes uniqueness. */
    public List<CustomItemKnowledge> byBaseItemId(String baseMinecraftItemId) {
        if (baseMinecraftItemId == null) return List.of();
        List<CustomItemKnowledge> result = new ArrayList<>();
        for (CustomItemKnowledge item : items) {
            if (baseMinecraftItemId.equals(item.baseMinecraftItemId())) result.add(item);
        }
        return result;
    }

    public List<CustomItemKnowledge> search(String query) {
        String q = (query != null && !query.isBlank()) ? query.trim().toLowerCase(Locale.ROOT) : null;
        if (q == null) return items;

        List<CustomItemKnowledge> result = new ArrayList<>();
        for (CustomItemKnowledge item : items) {
            if (searchTextByItemId.getOrDefault(item.id(), "").contains(q)) {
                result.add(item);
            }
        }
        return result;
    }

    private static String buildSearchableText(CustomItemKnowledge item) {
        StringBuilder sb = new StringBuilder();
        sb.append(item.displayName()).append(' ');
        if (item.baseMinecraftItemId() != null) sb.append(item.baseMinecraftItemId()).append(' ');
        if (item.category() != null) sb.append(item.category()).append(' ');
        if (item.tier() != null) sb.append(item.tier()).append(' ');
        if (item.culture() != null) sb.append(item.culture()).append(' ');
        if (item.serial() != null) sb.append(item.serial()).append(' ');
        if (item.description() != null) sb.append(item.description()).append(' ');
        return sb.toString().toLowerCase(Locale.ROOT);
    }
}
