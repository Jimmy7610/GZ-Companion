package se.jimmyeliasson.gzcompanion;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.jimmyeliasson.gzcompanion.chest.bridge.ChestCaptureController;
import se.jimmyeliasson.gzcompanion.chest.bridge.KistorNavigationHudElement;
import se.jimmyeliasson.gzcompanion.core.CompanionConstants;
import se.jimmyeliasson.gzcompanion.core.CompanionSession;
import se.jimmyeliasson.gzcompanion.gamezone.bridge.GameZoneToastHudElement;
import se.jimmyeliasson.gzcompanion.guide.GuideScheduler;
import se.jimmyeliasson.gzcompanion.keybind.KeybindHandler;

import java.time.Duration;

/**
 * Main Fabric client entrypoint for GZ Companion.
 */
public class GZCompanionClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger(CompanionConstants.MOD_ID);

    /** Bounded wait for CompanionSession#flushBeforeShutdown - long enough for a normal small JSON
     * write, short enough to never make a game exit feel stuck. */
    private static final Duration SHUTDOWN_FLUSH_TIMEOUT = Duration.ofSeconds(2);

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
        ChestCaptureController.register(session.getChestManager(), session.getKistorRuntime(), session.getToastManager());
        LOGGER.info(session.getChestManager().getDiagnostics(session.getCurrentStorageContext()).toSafeString());

        // 5. Report every independently-loaded knowledge/local-state module
        LOGGER.info("Guide: {}", session.getGuideEngine().getLoadStatus().getDisplayName());
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
        LOGGER.info("Settlement: {} ({} nivåer)",
                session.getSettlementCatalogStatus().getDisplayName(),
                session.getSettlementCatalog().size());
        LOGGER.info("Byggplaner: {} ({} byggnader)",
                session.getBuildingKnowledgeStatus().getDisplayName(),
                session.getBuildingKnowledgeBase().size());
        LOGGER.info("MarketWatch: {} (kommando: {})",
                session.getMarketWatchInfoStatus().getDisplayName(),
                session.getMarketWatchInfo().command());
        LOGGER.info("Inställningar: {}", session.getSettingsManager().getStatus().getDisplayName());

        // 6. Register the read-only GameZone chat/event observer and its local toast HUD element.
        // Only ever listens via the non-cancellable ClientReceiveMessageEvents.GAME/CHAT - never
        // ALLOW_GAME/ALLOW_CHAT - so it can observe but never cancel, rewrite, or hide a message.
        session.getChatObserver().register();
        new GameZoneToastHudElement(session.getToastManager()).register();
        // Kistor 2.0 "HITTA" navigation HUD - draws nothing unless the player explicitly started
        // navigation toward one already-known storage location.
        new KistorNavigationHudElement(session.getKistorRuntime(), session.getToastManager()).register();
        LOGGER.info("GameZone-händelsemotor: {} ({} verifierade parsrar)",
                session.getParserCatalogStatus().getDisplayName(),
                session.getParserCatalog().activeCount());

        // 7. Bounded best-effort final flush of any not-yet-persisted guide progress on normal
        // game exit - see CompanionSession#flushBeforeShutdown and
        // docs/PERFORMANCE-AUDIT-ALPHA4.md's "shutdown / final flush" section. Never blocks longer
        // than SHUTDOWN_FLUSH_TIMEOUT, so a stuck/slow disk can never hang game shutdown.
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> session.flushBeforeShutdown(SHUTDOWN_FLUSH_TIMEOUT));

        LOGGER.info("{} initialized successfully.", CompanionConstants.MOD_NAME);
    }
}