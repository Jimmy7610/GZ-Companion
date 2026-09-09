package se.jimmyeliasson.gzcompanion.knowledge.crafting.bridge;

import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import se.jimmyeliasson.gzcompanion.knowledge.crafting.ClientRecipeSnapshot;
import se.jimmyeliasson.gzcompanion.knowledge.crafting.RecipeKind;

import java.util.ArrayList;
import java.util.List;

/**
 * The ONLY place in the knowledge/crafting module that touches Minecraft recipe/item classes.
 * Reads the local client's OWN recipe book ({@code LocalPlayer.getRecipeBook()}) — the exact
 * same legitimately-unlocked recipe collection the vanilla Recipe Book UI itself uses. This is
 * client/server-synced data (recipe unlocks arrive over the network via
 * {@code ClientboundRecipeBookAddPacket}), never a raw dump of "every possible recipe" — only
 * recipes the player has legitimately discovered are ever returned. See
 * {@link ClientRecipeSnapshot} for why this is never labeled "Vanilla."
 *
 * <p>Read-only. Never registers, replaces, or intercepts any recipe; never sends a packet.
 */
public final class MinecraftRecipeDisplayAdapter {

    private MinecraftRecipeDisplayAdapter() {}

    /**
     * Reads every legitimately-unlocked shaped/shapeless crafting recipe currently in the
     * local client's own recipe book. Other display kinds (furnace, smithing, stonecutter,
     * etc.) are intentionally not returned by this crafting-table-focused reader.
     */
    public static List<ClientRecipeSnapshot> readClientRecipeBook() {
        try {
            Minecraft client = Minecraft.getInstance();
            if (client == null || client.player == null || client.level == null) {
                return List.of();
            }

            ClientRecipeBook book = client.player.getRecipeBook();
            if (book == null) {
                return List.of();
            }

            ContextMap context = SlotDisplayContext.fromLevel(client.level);

            List<ClientRecipeSnapshot> result = new ArrayList<>();
            for (RecipeCollection collection : book.getCollections()) {
                for (RecipeDisplayEntry entry : collection.getRecipes()) {
                    ClientRecipeSnapshot snapshot = toSnapshot(entry, context);
                    if (snapshot != null) {
                        result.add(snapshot);
                    }
                }
            }
            return result;
        } catch (Exception ignored) {
            // Defensive: a recipe-book read failure must never break the Companion UI.
            return List.of();
        }
    }

    private static ClientRecipeSnapshot toSnapshot(RecipeDisplayEntry entry, ContextMap context) {
        try {
            RecipeDisplay display = entry.display();
            ItemStack resultStack = display.result().resolveForFirstStack(context);
            if (resultStack == null || resultStack.isEmpty()) {
                return null;
            }
            String outputId = resolveItemId(resultStack.getItem());
            if (outputId.isEmpty()) {
                return null;
            }

            if (display instanceof ShapedCraftingRecipeDisplay shaped) {
                List<List<String>> slots = new ArrayList<>();
                for (SlotDisplay slot : shaped.ingredients()) {
                    slots.add(resolveAlternativeIds(slot, context));
                }
                return new ClientRecipeSnapshot(outputId, resultStack.getCount(), RecipeKind.SHAPED,
                        shaped.width(), shaped.height(), slots);
            }
            if (display instanceof ShapelessCraftingRecipeDisplay shapeless) {
                List<List<String>> slots = new ArrayList<>();
                for (SlotDisplay slot : shapeless.ingredients()) {
                    slots.add(resolveAlternativeIds(slot, context));
                }
                return new ClientRecipeSnapshot(outputId, resultStack.getCount(), RecipeKind.SHAPELESS, 0, 0, slots);
            }
            // Furnace/smithing/stonecutter/other display kinds are out of scope for the
            // crafting-table-focused Crafting tab in this milestone.
            return null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static List<String> resolveAlternativeIds(SlotDisplay slot, ContextMap context) {
        List<String> ids = new ArrayList<>();
        if (slot == null) return ids;
        try {
            for (ItemStack stack : slot.resolveForStacks(context)) {
                if (stack == null || stack.isEmpty()) continue;
                String id = resolveItemId(stack.getItem());
                if (!id.isEmpty() && !ids.contains(id)) {
                    ids.add(id);
                }
            }
        } catch (Exception ignored) {
            // Leave as an empty-slot representation rather than propagate a resolution failure.
        }
        return ids;
    }

    private static String resolveItemId(Item item) {
        if (item == null) return "";
        Identifier key = BuiltInRegistries.ITEM.getKey(item);
        return key != null ? key.toString() : "";
    }
}
