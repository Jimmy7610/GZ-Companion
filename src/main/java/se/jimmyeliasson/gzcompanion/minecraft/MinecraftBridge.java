package se.jimmyeliasson.gzcompanion.minecraft;

import java.util.Optional;

/**
 * Clean bridge abstraction between GZ Companion core and the Minecraft runtime.
 */
public interface MinecraftBridge {
    String getPlayerName();
    Optional<String> getCurrentServerAddress();
    boolean isConnectedToGameZone();
    String getMinecraftVersion();
    void openScreen(Object screen);
}
