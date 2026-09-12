package se.jimmyeliasson.gzcompanion.minecraft;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import se.jimmyeliasson.gzcompanion.core.CompanionConstants;
import se.jimmyeliasson.gzcompanion.mixin.PlayerTabOverlayAccessor;
import se.jimmyeliasson.gzcompanion.profile.ServerDetection;

import java.util.ArrayList;
import java.util.List;
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
                    contextKey = se.jimmyeliasson.gzcompanion.guide.progress.GuideContextResolver.resolveServerContext(client.getCurrentServer().ip);
                } else if (client.isLocalServer() || client.getSingleplayerServer() != null) {
                    String worldName = null;
                    if (client.getSingleplayerServer() != null) {
                        try {
                            worldName = client.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).getFileName().toString();
                        } catch (Exception ignored) {}
                        if (worldName == null || worldName.isBlank()) {
                            try {
                                worldName = client.getSingleplayerServer().getWorldData().getLevelName();
                            } catch (Exception ignored) {}
                        }
                    }
                    contextKey = se.jimmyeliasson.gzcompanion.guide.progress.GuideContextResolver.resolveSingleplayerContext(worldName != null ? worldName : "local");
                }
            }
        } catch (Exception ignored) {}

        return se.jimmyeliasson.gzcompanion.guide.progress.GuideContextResolver.create(profileId, contextKey);
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

    /**
     * Reads the exact same player list the vanilla in-game player list (tab list) shows -
     * {@code ClientPacketListener.getListedOnlinePlayers()} only ever contains entries the server
     * itself marked "listed", so this can never reveal a player the server/vanilla client hides
     * from the ordinary player list. Never queries anything beyond that: no coordinates, no
     * inventory, no entity scanning.
     */
    @Override
    public List<OnlinePlayerSnapshot> getOnlinePlayers() {
        List<OnlinePlayerSnapshot> result = new ArrayList<>();
        try {
            Minecraft client = Minecraft.getInstance();
            if (client != null && client.getConnection() != null) {
                PlayerTabOverlay tabOverlay = client.gui != null ? client.gui.getTabList() : null;
                for (PlayerInfo info : client.getConnection().getListedOnlinePlayers()) {
                    GameProfile profile = info.getProfile();
                    if (profile == null || profile.name() == null || profile.name().isBlank()) continue;
                    boolean isLocal = client.isLocalPlayer(profile.id());
                    String tabDisplayText = null;
                    if (tabOverlay != null) {
                        try {
                            Component displayName = tabOverlay.getNameForDisplay(info);
                            tabDisplayText = displayName != null ? displayName.getString() : null;
                        } catch (Exception ignored) {}
                    }
                    result.add(new OnlinePlayerSnapshot(profile.name(), isLocal, info.getLatency(), tabDisplayText));
                }
            }
        } catch (Exception ignored) {
            // Best-effort - an empty list is always a safe fallback, never a crash.
        }
        return result;
    }

    /**
     * Reads the same TAB header {@code Component} vanilla already received from the server and
     * shows above the player list - via {@link PlayerTabOverlayAccessor}, the smallest possible
     * Mixin accessor exposing a private vanilla field vanilla itself only offers a setter for
     * (see that class's javadoc). No GameZone parsing happens here - just flattening the already-
     * received Component to plain text.
     */
    @Override
    public Optional<String> getTabHeaderText() {
        try {
            Minecraft client = Minecraft.getInstance();
            if (client != null && client.gui != null) {
                PlayerTabOverlay tabOverlay = client.gui.getTabList();
                if (tabOverlay instanceof PlayerTabOverlayAccessor accessor) {
                    Component header = accessor.gzcompanion$getHeader();
                    if (header != null) {
                        String text = header.getString();
                        if (text != null && !text.isBlank()) {
                            return Optional.of(text);
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // Best-effort - absent header is always a safe fallback, never a crash.
        }
        return Optional.empty();
    }
}