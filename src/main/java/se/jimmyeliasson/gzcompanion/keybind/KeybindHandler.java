package se.jimmyeliasson.gzcompanion.keybind;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import se.jimmyeliasson.gzcompanion.ui.GZCompanionMainScreen;

/**
 * Registers and handles keybindings for opening the GZ Companion interface.
 */
public final class KeybindHandler {
    private static KeyMapping openMenuKey;

    private KeybindHandler() {}

    public static void register() {
        KeyMapping.Category category = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath("gzcompanion", "main")
        );

        openMenuKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.gzcompanion.open",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            category
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openMenuKey.consumeClick()) {
                if (client.screen == null) {
                    client.setScreen(new GZCompanionMainScreen());
                }
            }
        });
    }

    public static KeyMapping getOpenMenuKey() {
        return openMenuKey;
    }
}