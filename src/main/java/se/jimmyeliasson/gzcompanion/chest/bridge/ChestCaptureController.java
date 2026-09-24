package se.jimmyeliasson.gzcompanion.chest.bridge;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
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
import se.jimmyeliasson.gzcompanion.chest.KistorRuntime;
import se.jimmyeliasson.gzcompanion.chest.model.ChestCaptureEvent;
import se.jimmyeliasson.gzcompanion.chest.model.StorageKind;
import se.jimmyeliasson.gzcompanion.chest.model.StoragePosition;
import se.jimmyeliasson.gzcompanion.chest.model.StorageShape;
import se.jimmyeliasson.gzcompanion.core.CompanionSession;
import se.jimmyeliasson.gzcompanion.gamezone.toast.GameZoneToastManager;

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
 *       menu, we try to correlate it with the pending interaction via {@link ChestManager}. On
 *       success, an immediate snapshot is taken right away (the screen is already legitimately
 *       open, so this is legitimate — waiting for the first tick would race a player who closes
 *       instantly).</li>
 *   <li>While correlated, {@link ScreenEvents#afterTick} re-reads only the storage portion of the
 *       menu once per client tick and forwards it to the manager, which itself decides whether
 *       anything actually changed.</li>
 *   <li>{@link ScreenEvents#remove} takes one true final snapshot while the menu is still valid,
 *       forwards it, then finalizes and persists exactly once.</li>
 * </ol>
 */
public final class ChestCaptureController {

    private ChestCaptureController() {}

    public static void register(ChestManager manager, KistorRuntime kistor, GameZoneToastManager toastManager) {
        UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
            onUseBlock(manager, player, level, hitResult);
            return InteractionResult.PASS;
        });

        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof AbstractContainerScreen<?> containerScreen) {
                onContainerScreenOpened(manager, kistor, toastManager, client, containerScreen);
            }
        });

        // Leaving a world/server (disconnecting, quitting to title, or joining a different world)
        // always tears down the client play connection first. Clearing transient capture state
        // here means a pending interaction or an active capture can never survive into another
        // world/server context - without ever persisting a guessed or fake final snapshot.
        ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> {
            manager.clearTransientCaptureState();
            // Kistor 2.0: an active navigation target (and any Hämtningslista) belongs to the
            // world/server being left - stop it so it can never be shown in another context.
            kistor.onDisconnect();
        });
        // Defense in depth: when a new play connection is established, any still-active target
        // that does not belong to the newly joined context is stopped.
        ClientPlayConnectionEvents.JOIN.register((listener, sender, client) -> {
            try {
                kistor.navigation().onContextObserved(CompanionSession.getInstance().getCurrentStorageContext());
            } catch (Exception ignored) {
                kistor.navigation().onDisconnect();
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

            StorageShape shape = StorageShape.NOT_APPLICABLE;
            StoragePosition partner = null;
            if (kindOpt.get().isChestFamily()) {
                MinecraftChestCaptureAdapter.ChestShapeReading reading = MinecraftChestCaptureAdapter.resolveChestShape(state, pos);
                shape = reading.shape();
                partner = reading.partner();
            }

            manager.recordPendingInteraction(contextKey, dimensionKey, kindOpt.get(), clicked, partner, shape, System.currentTimeMillis());
        } catch (Exception ignored) {
            // Never let capture bookkeeping affect the player's actual interaction.
        }
    }

    private static void onContainerScreenOpened(ChestManager manager, KistorRuntime kistor, GameZoneToastManager toastManager,
                                                Minecraft client, AbstractContainerScreen<?> screen) {
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

            // Take an immediate snapshot now - the storage screen is already legitimately open,
            // so this is a legitimate read. Waiting for the next tick would leave a race where a
            // player who closes the screen before the first tick leaves no snapshot at all.
            var initialSlots = MinecraftChestCaptureAdapter.extractStorageSlots(menu, playerInventory);
            manager.updateCaptureSlots(initialSlots, System.currentTimeMillis());

            ScreenEvents.afterTick(screen).register((Screen s) -> {
                var slots = MinecraftChestCaptureAdapter.extractStorageSlots(menu, playerInventory);
                manager.updateCaptureSlots(slots, System.currentTimeMillis());
            });

            ScreenEvents.remove(screen).register((Screen s) -> {
                // Read the menu one true final time while it is still valid, so the persisted
                // "senast känt innehåll" reflects the last state the player actually saw.
                var finalSlots = MinecraftChestCaptureAdapter.extractStorageSlots(menu, playerInventory);
                long now = System.currentTimeMillis();
                manager.updateCaptureSlots(finalSlots, now);
                // Kistor 2.0 feedback happens only AFTER the one legitimate finalization/persist -
                // never while the open menu is being read.
                manager.endCapture(now).ifPresent(event -> onCaptureFinalized(kistor, toastManager, event, now));
            });
        } catch (Exception ignored) {
            // Defensive: a capture bookkeeping failure must never break the player's screen.
        }
    }

    private static void onCaptureFinalized(KistorRuntime kistor, GameZoneToastManager toastManager, ChestCaptureEvent event, long now) {
        try {
            kistor.onCaptureFinalized(event).ifPresent(message ->
                    toastManager.offerCompanion(message.dedupeKey(), message.title(), message.body(), now));
        } catch (Exception ignored) {
            // Feedback is a convenience - it must never affect capture or the player's screen.
        }
    }
}
