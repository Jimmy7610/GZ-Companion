package se.jimmyeliasson.gzcompanion.chest.bridge;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import se.jimmyeliasson.gzcompanion.chest.ChestManager;
import se.jimmyeliasson.gzcompanion.chest.model.StorageKind;
import se.jimmyeliasson.gzcompanion.chest.model.StoragePosition;
import se.jimmyeliasson.gzcompanion.core.CompanionSession;

import java.util.Optional;
import java.util.Set;

/**
 * The only place that registers Fabric client hooks for the Chest Manager. Purely observational:
 * the {@link UseBlockCallback} listener always returns {@link InteractionResult#PASS}, so it
 * never alters vanilla interaction behavior or sends any packet on its own.
 *
 * <p>Capture lifecycle:
 * <ol>
 *   <li>{@link UseBlockCallback} fires client-side (works identically in singleplayer and on any
 *       remote server, including GameZoneMC, because it hooks the client's own interaction
 *       handling before any packet is considered) when the player right-clicks a block. If it is
 *       on the storage allow-list, a pending interaction is recorded.</li>
 *   <li>{@link ScreenEvents#AFTER_INIT} fires when a screen opens. If it is a supported storage
 *       menu, we try to correlate it with the pending interaction via {@link ChestManager}.</li>
 *   <li>While correlated, {@link ScreenEvents#afterTick} re-reads only the storage portion of the
 *       menu once per client tick and forwards it to the manager, which itself decides whether
 *       anything actually changed.</li>
 *   <li>{@link ScreenEvents#remove} finalizes and persists (if changed) exactly once.</li>
 * </ol>
 */
public final class ChestCaptureController {

    private ChestCaptureController() {}

    public static void register(ChestManager manager) {
        UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
            onUseBlock(manager, player, level, hitResult);
            return InteractionResult.PASS;
        });

        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof AbstractContainerScreen<?> containerScreen) {
                onContainerScreenOpened(manager, client, containerScreen);
            }
        });
    }

    private static void onUseBlock(ChestManager manager, Player player, Level level, BlockHitResult hitResult) {
        try {
            if (!MinecraftChestCaptureAdapter.isLegitimateInteractionSource(player) || level == null || hitResult == null) {
                return;
            }

            BlockPos pos = hitResult.getBlockPos();
            BlockState state = level.getBlockState(pos);
            Optional<StorageKind> kindOpt = MinecraftChestCaptureAdapter.classifyBlock(state);
            if (kindOpt.isEmpty()) {
                return;
            }

            String contextKey = CompanionSession.getInstance().getCurrentGuideContext().getStorageKey();
            String dimensionKey = level.dimension().identifier().toString();
            StoragePosition clicked = new StoragePosition(pos.getX(), pos.getY(), pos.getZ());

            StoragePosition partner = null;
            boolean partnerKnown = false;
            if (kindOpt.get().isChestFamily()) {
                Optional<StoragePosition> partnerOpt = MinecraftChestCaptureAdapter.findChestPartner(state, pos);
                if (partnerOpt.isPresent()) {
                    partner = partnerOpt.get();
                    partnerKnown = true;
                }
            }

            manager.recordPendingInteraction(contextKey, dimensionKey, kindOpt.get(), clicked, partner, partnerKnown, System.currentTimeMillis());
        } catch (Exception ignored) {
            // Never let capture bookkeeping affect the player's actual interaction.
        }
    }

    private static void onContainerScreenOpened(ChestManager manager, Minecraft client, AbstractContainerScreen<?> screen) {
        try {
            AbstractContainerMenu menu = screen.getMenu();
            if (client == null || client.player == null || menu == null) {
                return;
            }

            Set<StorageKind> compatibleKinds = MinecraftChestCaptureAdapter.compatibleKindsForMenu(menu);
            if (compatibleKinds.isEmpty()) {
                // Not a supported storage menu at all (crafting, furnace, anvil, etc.) - ignore.
                return;
            }

            Level level = client.player.level();
            if (level == null) return;

            String contextKey = CompanionSession.getInstance().getCurrentGuideContext().getStorageKey();
            String dimensionKey = level.dimension().identifier().toString();

            boolean began = manager.tryBeginCapture(contextKey, dimensionKey, compatibleKinds, System.currentTimeMillis());
            if (!began) {
                return;
            }

            var playerInventory = client.player.getInventory();

            ScreenEvents.afterTick(screen).register((Screen s) -> {
                var slots = MinecraftChestCaptureAdapter.extractStorageSlots(menu, playerInventory);
                manager.updateCaptureSlots(slots, System.currentTimeMillis());
            });

            ScreenEvents.remove(screen).register((Screen s) -> manager.endCapture(System.currentTimeMillis()));
        } catch (Exception ignored) {
            // Defensive: a capture bookkeeping failure must never break the player's screen.
        }
    }
}
