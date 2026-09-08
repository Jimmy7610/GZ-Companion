package se.jimmyeliasson.gzcompanion;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.jimmyeliasson.gzcompanion.core.CompanionConstants;
import se.jimmyeliasson.gzcompanion.core.CompanionSession;
import se.jimmyeliasson.gzcompanion.keybind.KeybindHandler;

/**
 * Main Fabric client entrypoint for GZ Companion.
 */
public class GZCompanionClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger(CompanionConstants.MOD_ID);

    @Override
    public void onInitializeClient() {
        LOGGER.info("Initializing {} v{} for Minecraft {}",
                CompanionConstants.MOD_NAME,
                CompanionConstants.MOD_VERSION,
                CompanionConstants.TARGET_MINECRAFT_VERSION);

        // 1. Initialize core runtime session, storage, and bundled Rule Pack
        CompanionSession session = CompanionSession.getInstance();
        LOGGER.info("Loaded GameZone Rule Pack v{} (status: {})",
                session.getActiveRulePack().manifest().packVersion(),
                session.getCompatibilityResult().overallStatus().getDisplayName());

        // 2. Register keybindings
        KeybindHandler.register();

        LOGGER.info("{} initialized successfully.", CompanionConstants.MOD_NAME);
    }
}
