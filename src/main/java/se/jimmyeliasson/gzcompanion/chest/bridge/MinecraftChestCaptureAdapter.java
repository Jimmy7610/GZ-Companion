package se.jimmyeliasson.gzcompanion.chest.bridge;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.inventory.HopperMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.DropperBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.TrappedChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import se.jimmyeliasson.gzcompanion.chest.model.ChestSlotEntry;
import se.jimmyeliasson.gzcompanion.chest.model.StorageKind;
import se.jimmyeliasson.gzcompanion.chest.model.StoragePosition;
import se.jimmyeliasson.gzcompanion.chest.model.StorageShape;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The ONLY place in the Chest Manager module that touches Minecraft menu/block/screen classes.
 * Translates real client-visible Minecraft state into plain domain values consumed by
 * {@link se.jimmyeliasson.gzcompanion.chest.ChestManager}. No Minecraft types leak past this
 * class into the domain/storage layers.
 */
public final class MinecraftChestCaptureAdapter {

    private MinecraftChestCaptureAdapter() {}

    /**
     * Classifies a block the player just physically interacted with against the explicit
     * fair-play storage allow-list. Returns empty for every other block, including furnaces,
     * crafting tables, anvils, enchanting tables, and any non-storage block.
     */
    public static Optional<StorageKind> classifyBlock(BlockState state) {
        if (state == null) return Optional.empty();
        Block block = state.getBlock();

        // Order matters: subclasses must be checked before their vanilla parent block.
        if (block instanceof TrappedChestBlock) return Optional.of(StorageKind.TRAPPED_CHEST);
        if (block instanceof ChestBlock) return Optional.of(StorageKind.CHEST);
        if (block instanceof BarrelBlock) return Optional.of(StorageKind.BARREL);
        if (block instanceof ShulkerBoxBlock) return Optional.of(StorageKind.SHULKER_BOX);
        if (block instanceof HopperBlock) return Optional.of(StorageKind.HOPPER);
        if (block instanceof DropperBlock) return Optional.of(StorageKind.DROPPER);
        if (block instanceof DispenserBlock) return Optional.of(StorageKind.DISPENSER);
        return Optional.empty();
    }

    /**
     * Returns every {@link StorageKind} whose block(s) legitimately open this exact menu Java
     * class. Vanilla menu classes are shared across several block kinds (a Barrel opens the same
     * {@code ChestMenu} as a Chest), so this is a structural set, not a 1:1 class mapping. An
     * empty set means the menu is not a supported storage menu at all (crafting, furnace, anvil,
     * enchanting table, merchant, beacon, etc.) and must never be correlated or indexed.
     */
    public static Set<StorageKind> compatibleKindsForMenu(AbstractContainerMenu menu) {
        if (menu instanceof ChestMenu) return EnumSet.of(StorageKind.CHEST, StorageKind.TRAPPED_CHEST, StorageKind.BARREL);
        if (menu instanceof ShulkerBoxMenu) return EnumSet.of(StorageKind.SHULKER_BOX);
        if (menu instanceof HopperMenu) return EnumSet.of(StorageKind.HOPPER);
        if (menu instanceof DispenserMenu) return EnumSet.of(StorageKind.DISPENSER, StorageKind.DROPPER);
        return EnumSet.noneOf(StorageKind.class);
    }

    /**
     * Determines the chest-family physical shape (single / double / unknown) using ONLY the
     * already client-visible {@link BlockState} of the block the player clicked (the
     * {@code ChestBlock.TYPE} property and {@code ChestBlock.getConnectedBlockPos}). Never scans
     * surrounding block entities.
     *
     * <p>Critically distinguishes "Minecraft explicitly proved this is a {@code SINGLE} chest"
     * (returns {@link StorageShape#SINGLE}, no partner) from "this is a double-chest half whose
     * partner could not be safely resolved" (returns {@link StorageShape#UNKNOWN}, no partner) —
     * these previously collapsed into the same ambiguous state.
     *
     * <p>Only meaningful for chest-family blocks; callers must not invoke this for other storage
     * kinds (Barrel, Shulker Box, Hopper, Dispenser, Dropper), which are always
     * {@link StorageShape#NOT_APPLICABLE}.
     */
    public static ChestShapeReading resolveChestShape(BlockState state, BlockPos clickedPos) {
        if (state == null || clickedPos == null || !(state.getBlock() instanceof ChestBlock)) {
            return ChestShapeReading.NOT_APPLICABLE;
        }

        try {
            ChestType type = state.getValue(ChestBlock.TYPE);
            if (type == null) {
                return ChestShapeReading.UNKNOWN;
            }
            if (type == ChestType.SINGLE) {
                return ChestShapeReading.SINGLE;
            }

            // LEFT or RIGHT - a double chest half.
            BlockPos partnerPos = ChestBlock.getConnectedBlockPos(clickedPos, state);
            if (partnerPos == null || partnerPos.equals(clickedPos)) {
                return ChestShapeReading.UNKNOWN;
            }
            return new ChestShapeReading(StorageShape.DOUBLE, new StoragePosition(partnerPos.getX(), partnerPos.getY(), partnerPos.getZ()));
        } catch (Exception ignored) {
            // Defensive: never guess a shape or partner position if the state shape is unexpected.
            return ChestShapeReading.UNKNOWN;
        }
    }

    /** Plain result pairing a resolved {@link StorageShape} with its partner position, if any. */
    public record ChestShapeReading(StorageShape shape, StoragePosition partner) {
        public static final ChestShapeReading SINGLE = new ChestShapeReading(StorageShape.SINGLE, null);
        public static final ChestShapeReading UNKNOWN = new ChestShapeReading(StorageShape.UNKNOWN, null);
        public static final ChestShapeReading NOT_APPLICABLE = new ChestShapeReading(StorageShape.NOT_APPLICABLE, null);
    }

    /**
     * Extracts only the storage portion of an opened menu's slots, explicitly excluding every
     * slot backed by the player's own inventory/hotbar container.
     */
    public static List<ChestSlotEntry> extractStorageSlots(AbstractContainerMenu menu, Container playerInventory) {
        List<ChestSlotEntry> result = new ArrayList<>();
        if (menu == null) return result;

        int storageIndex = 0;
        for (Slot slot : menu.slots) {
            if (slot.container == playerInventory) {
                continue;
            }
            int currentIndex = storageIndex++;
            ItemStack stack = slot.getItem();
            if (stack == null || stack.isEmpty()) continue;

            String itemId = resolveItemId(stack.getItem());
            if (itemId.isEmpty()) continue;
            result.add(new ChestSlotEntry(currentIndex, itemId, stack.getCount()));
        }
        return result;
    }

    public static String resolveItemId(Item item) {
        if (item == null) return "";
        Identifier key = BuiltInRegistries.ITEM.getKey(item);
        return key != null ? key.toString() : "";
    }

    /**
     * Resolves a real vanilla translated item display name for search/display purposes.
     * Safe to call off-thread only from client render/tick context.
     */
    public static String resolveItemDisplayName(String itemId) {
        if (itemId == null || itemId.isEmpty()) return "";
        try {
            Identifier id = Identifier.tryParse(itemId);
            if (id == null) return "";
            Item item = BuiltInRegistries.ITEM.getValue(id);
            if (item == null) return "";
            return new ItemStack(item).getHoverName().getString();
        } catch (Exception ignored) {
            return "";
        }
    }

    /**
     * Convenience guard combining physical-click safety concerns for callers: is this player
     * currently a valid source of a legitimate interaction (not spectator).
     */
    public static boolean isLegitimateInteractionSource(Player player) {
        return player != null && !player.isSpectator();
    }
}
