package se.jimmyeliasson.gzcompanion.minecraft;

import se.jimmyeliasson.gzcompanion.guide.progress.GuideContext;
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
}
