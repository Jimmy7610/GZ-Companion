package se.jimmyeliasson.gzcompanion.guide;

import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Shared canonical registry of Minecraft item tags observable by the Guide snapshot engine.
 * Ensures guide definitions and condition evaluators can only declare and evaluate tags
 * that the snapshot system actually tracks.
 */
public final class GuideSupportedTags {

    public static final String LOGS = "minecraft:logs";
    public static final String PLANKS = "minecraft:planks";
    public static final String BEDS = "minecraft:beds";
    public static final String COALS = "minecraft:coals";
    public static final String WOOL = "minecraft:wool";

    private static final Map<String, TagKey<Item>> TAG_MAP;

    static {
        Map<String, TagKey<Item>> map = new LinkedHashMap<>();
        map.put(LOGS, ItemTags.LOGS);
        map.put(PLANKS, ItemTags.PLANKS);
        map.put(BEDS, ItemTags.BEDS);
        map.put(COALS, ItemTags.COALS);
        map.put(WOOL, ItemTags.WOOL);
        TAG_MAP = Collections.unmodifiableMap(map);
    }

    private GuideSupportedTags() {
    }

    /**
     * Checks if the specified tag identifier is supported and observable by the guide snapshot provider.
     */
    public static boolean isSupported(String tag) {
        return tag != null && TAG_MAP.containsKey(tag);
    }

    /**
     * Returns the set of all supported tag names (e.g. "minecraft:logs").
     */
    public static Set<String> getSupportedTagNames() {
        return TAG_MAP.keySet();
    }

    /**
     * Returns the mapping of tag names to Minecraft TagKey<Item> instances.
     */
    public static Map<String, TagKey<Item>> getTagMap() {
        return TAG_MAP;
    }
}
