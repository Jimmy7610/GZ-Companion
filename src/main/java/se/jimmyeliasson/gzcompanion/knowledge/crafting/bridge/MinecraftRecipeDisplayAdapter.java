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
import se.jimmyeliasson.gzcompanion.knowledge.crafting.IngredientOption;
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
 *
 * <p>Callers MUST NOT invoke {@link #readClientRecipeBook()} on every render frame — it iterates
 * every unlocked recipe collection and resolves every ingredient's {@code ItemStack}. Throttle
 * calls via {@code se.jimmyeliasson.gzcompanion.knowledge.crafting.ClientRecipeCachePolicy}.
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

    /**
     * Resolves a raw Minecraft item id (e.g. {@code minecraft:oak_planks}) to a renderable,
     * displayable {@link ItemStack} for the Crafting tab's UI layer. Returns
     * {@link ItemStack#EMPTY} for any unresolvable/invalid id rather than throwing - a bad or
     * unknown id must degrade to "no icon," never crash the render pass.
     */
    public static ItemStack resolveDisplayStack(String itemId) {
        try {
            if (itemId == null || itemId.isBlank()) {
                return ItemStack.EMPTY;
            }
            Identifier id = Identifier.tryParse(itemId);
            if (id == null) {
                return ItemStack.EMPTY;
            }
            Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
            if (item == null) {
                return ItemStack.EMPTY;
            }
            return new ItemStack(item);
        } catch (Exception ignored) {
            return ItemStack.EMPTY;
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
            String outputDisplayName = resolveDisplayName(resultStack);

            if (display instanceof ShapedCraftingRecipeDisplay shaped) {
                List<List<IngredientOption>> slots = new ArrayList<>();
                for (SlotDisplay slot : shaped.ingredients()) {
                    slots.add(resolveAlternatives(slot, context));
                }
                return new ClientRecipeSnapshot(outputId, outputDisplayName, resultStack.getCount(), RecipeKind.SHAPED,
                        shaped.width(), shaped.height(), slots);
            }
            if (display instanceof ShapelessCraftingRecipeDisplay shapeless) {
                List<List<IngredientOption>> slots = new ArrayList<>();
                for (SlotDisplay slot : shapeless.ingredients()) {
                    slots.add(resolveAlternatives(slot, context));
                }
                return new ClientRecipeSnapshot(outputId, outputDisplayName, resultStack.getCount(), RecipeKind.SHAPELESS, 0, 0, slots);
            }
            // Furnace/smithing/stonecutter/other display kinds are out of scope for the
            // crafting-table-focused Crafting tab in this milestone.
            return null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static List<IngredientOption> resolveAlternatives(SlotDisplay slot, ContextMap context) {
        List<IngredientOption> options = new ArrayList<>();
        if (slot == null) return options;
        try {
            List<String> seenIds = new ArrayList<>();
            for (ItemStack stack : slot.resolveForStacks(context)) {
                if (stack == null || stack.isEmpty()) continue;
                String id = resolveItemId(stack.getItem());
                if (id.isEmpty() || seenIds.contains(id)) continue;
                seenIds.add(id);
                options.add(new IngredientOption(id, resolveDisplayName(stack)));
            }
        } catch (Exception ignored) {
            // Leave as an empty-slot representation rather than propagate a resolution failure.
        }
        return options;
    }

    private static String resolveItemId(Item item) {
        if (item == null) return "";
        Identifier key = BuiltInRegistries.ITEM.getKey(item);
        return key != null ? key.toString() : "";
    }

    /** The player's actual translated item name (e.g. "Oak Planks"), resolved once here. */
    private static String resolveDisplayName(ItemStack stack) {
        try {
            return stack.getHoverName().getString();
        } catch (Exception ignored) {
            return "";
        }
    }
}
