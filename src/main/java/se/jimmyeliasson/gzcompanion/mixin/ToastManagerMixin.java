package se.jimmyeliasson.gzcompanion.mixin;

import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import se.jimmyeliasson.gzcompanion.core.CompanionSession;
import se.jimmyeliasson.gzcompanion.minecraft.VanillaToastFilter;

/**
 * Optional, visual-only: skips queuing Minecraft's "Chat messages can't be verified" toast when
 * the player enabled "Dölj varningen ..." in Inställningar. Every other toast - and this one too
 * while the setting is off (the default) - is passed through untouched. See
 * {@link VanillaToastFilter} for why the toast id identifies this one warning precisely.
 */
@Mixin(ToastManager.class)
public abstract class ToastManagerMixin {
    @Inject(method = "addToast", at = @At("HEAD"), cancellable = true)
    private void gzcompanion$hideUnverifiedChatWarning(Toast toast, CallbackInfo ci) {
        if (!(toast instanceof SystemToast systemToast)) return; // cheap early-out for every other toast type
        boolean hide;
        try {
            hide = CompanionSession.getInstance().getSettingsManager().getSettings().hideUnverifiedChatWarning();
        } catch (Throwable ignored) {
            return; // settings unavailable -> behave exactly like vanilla
        }
        if (VanillaToastFilter.shouldSuppress(systemToast.getToken(), hide)) {
            ci.cancel();
        }
    }
}
