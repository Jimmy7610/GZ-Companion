package se.jimmyeliasson.gzcompanion.chest.bridge;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * Minecraft-specific adapter that turns a stored Kistor item id (chest snapshots store plain ids,
 * never ItemStacks) into a real vanilla item icon. Keeps every Minecraft type out of the pure
 * chest domain classes.
 *
 * <p>Resolution goes through the running client's own item registry via
 * {@code BuiltInRegistries.ITEM.getOptional} - an unknown or future id resolves to "no icon"
 * instead of silently becoming Air. Rendering uses the real 26.1.2
 * {@link GuiGraphicsExtractor#fakeItem(ItemStack, int, int)} (the same call the Crafting,
 * Settlement and Byggplaner tabs already use); smaller-than-16px icons are drawn by scaling the
 * extractor's own 2D pose, so text alignment around them stays on the normal pixel grid.
 */
public final class ChestItemIcons {
    /** Vanilla item icons are authored at 16×16. */
    public static final int NATIVE_SIZE = 16;
    /** Bounded memo: comfortably above the vanilla item registry size. */
    private static final int MAX_CACHE = 4096;

    private static final Map<String, ItemStack> CACHE = new HashMap<>();

    private ChestItemIcons() {}

    /** Resolves a stored item id to a renderable stack, or {@link ItemStack#EMPTY} if unknown. */
    public static ItemStack resolve(String itemId) {
        if (itemId == null || itemId.isBlank()) return ItemStack.EMPTY;
        ItemStack cached = CACHE.get(itemId);
        if (cached != null) return cached;
        ItemStack resolved = ItemStack.EMPTY;
        try {
            Identifier id = Identifier.tryParse(itemId);
            if (id != null) {
                Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
                if (item != null) {
                    ItemStack stack = new ItemStack(item);
                    if (!stack.isEmpty()) resolved = stack;
                }
            }
        } catch (Exception ignored) {
            resolved = ItemStack.EMPTY;
        }
        if (CACHE.size() >= MAX_CACHE) CACHE.clear();
        CACHE.put(itemId, resolved);
        return resolved;
    }

    /**
     * Draws the item's real icon at (x, y) with the given square size. An unknown id draws a small
     * neutral placeholder instead, so rows keep their alignment. Never throws.
     *
     * @return true if a real item icon was drawn.
     */
    public static boolean draw(GuiGraphicsExtractor extractor, String itemId, int x, int y, int size) {
        ItemStack stack = resolve(itemId);
        try {
            if (stack.isEmpty()) {
                drawPlaceholder(extractor, x, y, size);
                return false;
            }
            if (size == NATIVE_SIZE) {
                extractor.fakeItem(stack, x, y);
            } else {
                float scale = size / (float) NATIVE_SIZE;
                extractor.pose().pushMatrix();
                extractor.pose().translate(x, y);
                extractor.pose().scale(scale, scale);
                extractor.fakeItem(stack, 0, 0);
                extractor.pose().popMatrix();
            }
            return true;
        } catch (Exception ignored) {
            // A single unbakeable icon must never take down the Kistor tab.
            return false;
        }
    }

    private static void drawPlaceholder(GuiGraphicsExtractor extractor, int x, int y, int size) {
        int inset = Math.max(1, size / 6);
        extractor.fill(x + inset, y + inset, x + size - inset, y + size - inset, 0x40475569);
        extractor.fill(x + inset, y + inset, x + size - inset, y + inset + 1, 0x80475569);
        extractor.fill(x + inset, y + size - inset - 1, x + size - inset, y + size - inset, 0x80475569);
    }
}
