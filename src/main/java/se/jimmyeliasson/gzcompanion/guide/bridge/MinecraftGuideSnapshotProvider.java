package se.jimmyeliasson.gzcompanion.guide.bridge;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

/**
 * Real Minecraft 26.1.2 client-side snapshot extractor.
 * Only reads legitimate local player inventory and current key mappings.
 */
public class MinecraftGuideSnapshotProvider implements GuideSnapshotProvider {

    private static final TagKey<Item>[] TRACKED_TAGS = new TagKey[] {
        ItemTags.LOGS,
        ItemTags.PLANKS,
        ItemTags.BEDS,
        ItemTags.COALS,
        ItemTags.WOOL
    };

    @Override
    public GuidePlayerSnapshot createSnapshot() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null) {
            return GuidePlayerSnapshot.EMPTY;
        }

        Player player = client.player;
        Inventory inv = player.getInventory();
        if (inv == null) {
            return GuidePlayerSnapshot.EMPTY;
        }

        Map<String, Integer> itemCounts = new HashMap<>();
        Map<String, Integer> tagCounts = new HashMap<>();
        boolean hasEdibleItem = false;
        StringBuilder fingerprintBuilder = new StringBuilder();

        int size = inv.getContainerSize();
        for (int slot = 0; slot < size; slot++) {
            ItemStack stack = inv.getItem(slot);
            if (stack.isEmpty()) continue;

            Item item = stack.getItem();
            Identifier key = BuiltInRegistries.ITEM.getKey(item);
            String itemId = key != null ? key.toString() : "";
            int count = stack.getCount();

            if (!itemId.isEmpty()) {
                itemCounts.put(itemId, itemCounts.getOrDefault(itemId, 0) + count);
                fingerprintBuilder.append(itemId).append(':').append(count).append(';');
            }

            if (!hasEdibleItem && stack.has(DataComponents.FOOD)) {
                hasEdibleItem = true;
            }

            for (TagKey<Item> tagKey : TRACKED_TAGS) {
                if (stack.is(tagKey)) {
                    String tagStr = tagKey.location().toString();
                    tagCounts.put(tagStr, tagCounts.getOrDefault(tagStr, 0) + count);
                }
            }
        }

        Map<String, String> keyTokens = extractKeyTokens(client.options);
        String fingerprint = hashString(fingerprintBuilder.toString());

        return new GuidePlayerSnapshot(itemCounts, tagCounts, hasEdibleItem, fingerprint, keyTokens);
    }

    public static Map<String, String> extractKeyTokens(Options options) {
        if (options == null) return Map.of();
        Map<String, String> tokens = new HashMap<>();
        if (options.keyUp != null) tokens.put("key.forward", options.keyUp.getTranslatedKeyMessage().getString());
        if (options.keyDown != null) tokens.put("key.back", options.keyDown.getTranslatedKeyMessage().getString());
        if (options.keyLeft != null) tokens.put("key.left", options.keyLeft.getTranslatedKeyMessage().getString());
        if (options.keyRight != null) tokens.put("key.right", options.keyRight.getTranslatedKeyMessage().getString());
        if (options.keyJump != null) tokens.put("key.jump", options.keyJump.getTranslatedKeyMessage().getString());
        if (options.keyInventory != null) tokens.put("key.inventory", options.keyInventory.getTranslatedKeyMessage().getString());
        if (options.keyAttack != null) tokens.put("key.attack", options.keyAttack.getTranslatedKeyMessage().getString());
        if (options.keyUse != null) tokens.put("key.use", options.keyUse.getTranslatedKeyMessage().getString());
        if (options.keyShift != null) tokens.put("key.sneak", options.keyShift.getTranslatedKeyMessage().getString());
        if (options.keySprint != null) tokens.put("key.sprint", options.keySprint.getTranslatedKeyMessage().getString());
        if (options.keyDrop != null) tokens.put("key.drop", options.keyDrop.getTranslatedKeyMessage().getString());
        if (options.keySwapOffhand != null) tokens.put("key.swapOffhand", options.keySwapOffhand.getTranslatedKeyMessage().getString());
        return tokens;
    }

    private String hashString(String input) {
        if (input.isEmpty()) return "empty";
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return String.valueOf(input.hashCode());
        }
    }
}