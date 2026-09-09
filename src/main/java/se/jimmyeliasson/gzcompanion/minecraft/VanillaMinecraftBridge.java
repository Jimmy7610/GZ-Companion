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
    public se.jimmyeliasson.gzcompanion.guide.progress.GuideContext getGuideContext() {
        String profileId = "offline_profile";
        try {
            Minecraft client = Minecraft.getInstance();
            if (client != null && client.getUser() != null && client.getUser().getProfileId() != null) {
                profileId = client.getUser().getProfileId().toString();
            } else if (client != null && client.player != null) {
                profileId = client.player.getStringUUID();
            }
        } catch (Exception ignored) {}

        String contextKey = "singleplayer:default";
        try {
            Minecraft client = Minecraft.getInstance();
            if (client != null) {
                if (client.getCurrentServer() != null && client.getCurrentServer().ip != null) {
                    contextKey = "server:" + client.getCurrentServer().ip.toLowerCase().trim();
                } else if (client.isLocalServer()) {
                    contextKey = "singleplayer:local";
                }
            }
        } catch (Exception ignored) {}

        return new se.jimmyeliasson.gzcompanion.guide.progress.GuideContext(profileId, contextKey);
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