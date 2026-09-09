package se.jimmyeliasson.gzcompanion.guide.bridge;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable snapshot of legitimate client-visible player state required by the Guide Engine.
 */
public record GuidePlayerSnapshot(
    Map<String, Integer> itemCounts,
    Map<String, Integer> tagCounts,
    boolean hasEdibleItem,
    String inventoryFingerprint,
    Map<String, String> keyTokens
) {
    public static final GuidePlayerSnapshot EMPTY = new GuidePlayerSnapshot(
        Map.of(), Map.of(), false, "empty", Map.of()
    );

    public GuidePlayerSnapshot {
        itemCounts = itemCounts != null ? Collections.unmodifiableMap(itemCounts) : Map.of();
        tagCounts = tagCounts != null ? Collections.unmodifiableMap(tagCounts) : Map.of();
        inventoryFingerprint = Objects.requireNonNullElse(inventoryFingerprint, "");
        keyTokens = keyTokens != null ? Collections.unmodifiableMap(keyTokens) : Map.of();
    }

    public int getItemCount(String itemId) {
        if (itemId == null) return 0;
        return itemCounts.getOrDefault(itemId, 0);
    }

    public int getTagCount(String tag) {
        if (tag == null) return 0;
        return tagCounts.getOrDefault(tag, 0);
    }

    public String resolveKeyToken(String token) {
        if (token == null) return "";
        return keyTokens.getOrDefault(token, token);
    }
}