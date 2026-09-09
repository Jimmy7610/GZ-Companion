package se.jimmyeliasson.gzcompanion;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.jimmyeliasson.gzcompanion.chest.bridge.ChestCaptureController;
import se.jimmyeliasson.gzcompanion.core.CompanionConstants;
import se.jimmyeliasson.gzcompanion.core.CompanionSession;
import se.jimmyeliasson.gzcompanion.guide.GuideScheduler;
import se.jimmyeliasson.gzcompanion.keybind.KeybindHandler;

/**
 * Main Fabric client entrypoint for GZ Companion.
 */
public class GZCompanionClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger(CompanionConstants.MOD_ID);

    private final GuideScheduler guideScheduler = new GuideScheduler();

    @Override
    public void onInitializeClient() {
        LOGGER.info("Initializing {} v{} for Minecraft {}",
                CompanionConstants.MOD_NAME,
                CompanionConstants.getModVersion(),
                CompanionConstants.TARGET_MINECRAFT_VERSION);

        // 1. Initialize core runtime session, storage, and bundled Rule Pack
        CompanionSession session = CompanionSession.getInstance();
        LOGGER.info("Loaded GameZone Rule Pack v{} (status: {})",
                session.getActiveRulePack().manifest().packVersion(),
                session.getCompatibilityResult().overallStatus().getDisplayName());

        // 2. Register keybindings
        KeybindHandler.register();

        // 3. Register background guide evaluation tick scheduler
        ClientTickEvents.END_CLIENT_TICK.register(guideScheduler::onClientTick);

        // 4. Register fair-play opened-storage capture hooks for the Chest Manager
        ChestCaptureController.register(session.getChestManager());
        LOGGER.info(session.getChestManager().getDiagnostics(session.getCurrentStorageContext()).toSafeString());

        // 5. Report the three independently-loaded M4 knowledge modules
        LOGGER.info("Kommandon: {} ({} kommandon, {} kategorier)",
                session.getCommandCatalogStatus().getDisplayName(),
                session.getCommandCatalog().size(),
                session.getCommandCatalog().categoriesInUse().size());
        LOGGER.info("Crafting-overstyrningar: {} ({} recept)",
                session.getCraftingKnowledgeStatus().getDisplayName(),
                session.getCraftingKnowledgeBase().size());
        LOGGER.info("GameZone-föremål: {} ({} föremål)",
                session.getItemKnowledgeStatus().getDisplayName(),
                session.getItemKnowledgeBase().size());

        LOGGER.info("{} initialized successfully.", CompanionConstants.MOD_NAME);
    }
}