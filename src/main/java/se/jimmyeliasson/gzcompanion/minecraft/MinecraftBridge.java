package se.jimmyeliasson.gzcompanion.minecraft;

import se.jimmyeliasson.gzcompanion.guide.progress.GuideContext;
import java.util.List;
import java.util.Optional;

/**
 * Clean bridge abstraction between GZ Companion core and the Minecraft runtime.
 */
public interface MinecraftBridge {
    String getPlayerName();
    Optional<String> getCurrentServerAddress();
    boolean isConnectedToGameZone();
    String getMinecraftVersion();
    GuideContext getGuideContext();
    void openScreen(Object screen);

    /**
     * Every player the vanilla client's own player list currently knows about - the exact same
     * data that backs the normal in-game player list, nothing more. Empty (never null) when not
     * connected to a server, or the connection genuinely reports nobody. No GameZone-specific
     * assumptions belong here - see {@code se.jimmyeliasson.gzcompanion.online} for anything
     * GameZone-aware built on top of this.
     */
    List<OnlinePlayerSnapshot> getOnlinePlayers();

    /**
     * The vanilla TAB list header text currently shown above the player list - the exact same
     * {@code Component} text every vanilla client already receives and renders when a player opens
     * the player list, flattened to plain text. Empty when not connected, or the server hasn't set
     * one. No GameZone-specific parsing belongs here - see
     * {@code se.jimmyeliasson.gzcompanion.gamezone.settlement} for anything GameZone-aware built on
     * top of this.
     */
    Optional<String> getTabHeaderText();
}
