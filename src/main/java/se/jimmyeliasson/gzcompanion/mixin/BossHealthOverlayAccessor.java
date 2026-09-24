package se.jimmyeliasson.gzcompanion.mixin;

import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.components.LerpingBossEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;
import java.util.UUID;

/**
 * Exposes {@link BossHealthOverlay}'s private {@code events} map so the Kistor navigation HUD can
 * count how many boss bars the client is ALREADY drawing at the top of the screen and sit below
 * them instead of covering them. Read-only: only the map's size is ever used - the same boss bars
 * the player is already looking at.
 */
@Mixin(BossHealthOverlay.class)
public interface BossHealthOverlayAccessor {
    @Accessor("events")
    Map<UUID, LerpingBossEvent> gzcompanion$getEvents();
}
