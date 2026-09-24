package se.jimmyeliasson.gzcompanion.minecraft;

import net.minecraft.client.gui.components.toasts.SystemToast;

/**
 * Decides whether ONE specific vanilla toast is hidden client-side: Minecraft's
 * "Chat messages can't be verified" warning, and only while the player turned on the optional
 * "Dölj varningen ..." setting (default off).
 *
 * <p>In Minecraft 26.1.2 that warning is exactly one {@link SystemToast} whose token is
 * {@link SystemToast.SystemToastId#UNSECURE_SERVER_WARNING}. That id is referenced nowhere else
 * in the client - it is created only in {@code ClientPacketListener.handleLogin} - so matching on
 * the id alone identifies this warning precisely: no text matching, no heuristics, and every other
 * toast (including every other {@code SystemToast}) is untouched. Hiding it changes nothing but
 * what is drawn: chat content, signing, reporting, commands and networking are unaffected, and
 * vanilla still records that the warning was "seen" exactly as before.
 */
public final class VanillaToastFilter {
    private VanillaToastFilter() {}

    /**
     * @param toastToken     the toast's {@code getToken()} value.
     * @param hideSettingOn  the player's "hide unverified chat warning" setting.
     */
    public static boolean shouldSuppress(Object toastToken, boolean hideSettingOn) {
        return hideSettingOn && toastToken == SystemToast.SystemToastId.UNSECURE_SERVER_WARNING;
    }
}
