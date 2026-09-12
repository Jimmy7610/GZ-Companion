package se.jimmyeliasson.gzcompanion.mixin;

import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes {@link PlayerTabOverlay}'s private {@code header} field, which vanilla only offers a
 * setter for. This is the already-received TAB header {@link Component} every vanilla client gets
 * from the server it's connected to - reading it is not different in kind from what the client
 * already renders on screen every time the player list is open; this accessor just lets Companion
 * read the same Component instead of re-parsing rendered pixels.
 */
@Mixin(PlayerTabOverlay.class)
public interface PlayerTabOverlayAccessor {
    @Accessor("header")
    Component gzcompanion$getHeader();
}
