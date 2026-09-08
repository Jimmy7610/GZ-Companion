package se.jimmyeliasson.gzcompanion.minecraft;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import se.jimmyeliasson.gzcompanion.core.CompanionConstants;
import se.jimmyeliasson.gzcompanion.profile.ServerDetection;

import java.util.Optional;

/**
 * Minecraft runtime bridge using vanilla Minecraft client APIs.
 */
public class VanillaMinecraftBridge implements MinecraftBridge {

    @Override
    public String getPlayerName() {
        try {
            Minecraft client = Minecraft.getInstance();
            if (client != null && client.getUser() != null) {
                String name = client.getUser().getName();
                if (name != null && !name.isBlank()) {
                    return name;
                }
            }
        } catch (Exception ignored) {}
        return "Spelare";
    }

    @Override
    public Optional<String> getCurrentServerAddress() {
        try {
            Minecraft client = Minecraft.getInstance();
            if (client != null) {
                ServerData serverData = client.getCurrentServer();
                if (serverData != null && serverData.ip != null) {
                    return Optional.of(serverData.ip);
                }
                if (client.isLocalServer()) {
                    return Optional.of("Lokal v\u00E4rld");
                }
            }
        } catch (Exception ignored) {}
        return Optional.empty();
    }

    @Override
    public boolean isConnectedToGameZone() {
        Optional<String> addr = getCurrentServerAddress();
        return addr.isPresent() && ServerDetection.isGameZone(addr.get());
    }

    @Override
    public String getMinecraftVersion() {
        return CompanionConstants.TARGET_MINECRAFT_VERSION;
    }

    @Override
    public void openScreen(Object screen) {
        if (screen instanceof Screen mcScreen) {
            Minecraft client = Minecraft.getInstance();
            if (client != null) {
                client.setScreen(mcScreen);
            }
        }
    }
}