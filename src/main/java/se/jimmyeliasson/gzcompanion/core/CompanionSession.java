package se.jimmyeliasson.gzcompanion.core;

import se.jimmyeliasson.gzcompanion.chest.ChestManager;
import se.jimmyeliasson.gzcompanion.chest.bridge.MinecraftChestCaptureAdapter;
import se.jimmyeliasson.gzcompanion.chest.storage.JsonChestIndexStore;
import se.jimmyeliasson.gzcompanion.core.feature.FeatureManager;
import se.jimmyeliasson.gzcompanion.diagnostics.CompatibilityResult;
import se.jimmyeliasson.gzcompanion.diagnostics.CompatibilityService;
import se.jimmyeliasson.gzcompanion.gamezone.RulePack;
import se.jimmyeliasson.gzcompanion.gamezone.RulePackLoader;
import se.jimmyeliasson.gzcompanion.gamezone.bridge.GameZoneChatObserver;
import se.jimmyeliasson.gzcompanion.gamezone.parsing.GameZoneParserCatalog;
import se.jimmyeliasson.gzcompanion.gamezone.parsing.GameZoneParserLoader;
import se.jimmyeliasson.gzcompanion.gamezone.toast.GameZoneToastManager;
import se.jimmyeliasson.gzcompanion.guide.GuideEngine;
import se.jimmyeliasson.gzcompanion.guide.GuideLoader;
import se.jimmyeliasson.gzcompanion.guide.bridge.MinecraftGuideSnapshotProvider;
import se.jimmyeliasson.gzcompanion.guide.progress.GuideContext;
import se.jimmyeliasson.gzcompanion.guide.progress.JsonGuideProgressStore;
import se.jimmyeliasson.gzcompanion.knowledge.commands.CommandCatalog;
import se.jimmyeliasson.gzcompanion.knowledge.commands.CommandKnowledgeLoader;
import se.jimmyeliasson.gzcompanion.knowledge.common.KnowledgeLoadResult;
import se.jimmyeliasson.gzcompanion.knowledge.common.KnowledgeModuleStatus;
import se.jimmyeliasson.gzcompanion.knowledge.crafting.CraftingKnowledgeBase;
import se.jimmyeliasson.gzcompanion.knowledge.crafting.CraftingKnowledgeLoader;
import se.jimmyeliasson.gzcompanion.knowledge.items.ItemKnowledgeBase;
import se.jimmyeliasson.gzcompanion.knowledge.items.ItemKnowledgeLoader;
import se.jimmyeliasson.gzcompanion.knowledge.settlement.SettlementCatalog;
import se.jimmyeliasson.gzcompanion.knowledge.settlement.SettlementKnowledgeLoader;
import se.jimmyeliasson.gzcompanion.minecraft.MinecraftBridge;
import se.jimmyeliasson.gzcompanion.minecraft.VanillaMinecraftBridge;
import se.jimmyeliasson.gzcompanion.profile.ServerProfile;
import se.jimmyeliasson.gzcompanion.settlement.SettlementPlannerManager;
import se.jimmyeliasson.gzcompanion.settlement.storage.JsonSettlementPlannerStore;
import se.jimmyeliasson.gzcompanion.storage.StorageManager;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Runtime coordinator for GZ Companion session state.
 */
public class CompanionSession {
    private static final CompanionSession INSTANCE = new CompanionSession();

    private final MinecraftBridge bridge;
    private final FeatureManager featureManager;
    private final StorageManager storageManager;
    private final CompatibilityService compatibilityService;
    private final GuideEngine guideEngine;
    private final ChestManager chestManager;
    private RulePack activeRulePack;
    private CompatibilityResult compatibilityResult;

    private CommandCatalog commandCatalog = CommandCatalog.empty();
    private KnowledgeModuleStatus commandCatalogStatus = KnowledgeModuleStatus.UNAVAILABLE;
    private CraftingKnowledgeBase craftingKnowledgeBase = CraftingKnowledgeBase.empty();
    private KnowledgeModuleStatus craftingKnowledgeStatus = KnowledgeModuleStatus.UNAVAILABLE;
    private ItemKnowledgeBase itemKnowledgeBase = ItemKnowledgeBase.empty();
    private KnowledgeModuleStatus itemKnowledgeStatus = KnowledgeModuleStatus.UNAVAILABLE;
    private SettlementCatalog settlementCatalog = SettlementCatalog.empty();
    private KnowledgeModuleStatus settlementCatalogStatus = KnowledgeModuleStatus.UNAVAILABLE;
    private final SettlementPlannerManager settlementPlannerManager;

    private GameZoneParserCatalog parserCatalog = GameZoneParserCatalog.empty();
    private KnowledgeModuleStatus parserCatalogStatus = KnowledgeModuleStatus.UNAVAILABLE;
    private final GameZoneToastManager toastManager = new GameZoneToastManager();
    private final GameZoneChatObserver chatObserver = new GameZoneChatObserver(() -> parserCatalog, toastManager);

    private CompanionSession() {
        this.bridge = new VanillaMinecraftBridge();
        this.featureManager = new FeatureManager();
        this.storageManager = new StorageManager(Paths.get("."));
        this.compatibilityService = new CompatibilityService();

        Path configDir = Paths.get(".").resolve("config").resolve("gzcompanion");
        JsonGuideProgressStore progressStore = new JsonGuideProgressStore(configDir.resolve("guide-progress.json"));
        this.guideEngine = new GuideEngine(new GuideLoader(), progressStore, new MinecraftGuideSnapshotProvider());

        this.chestManager = new ChestManager(new JsonChestIndexStore(configDir.resolve("chest-index.json")));
        this.chestManager.setDisplayNameResolver(MinecraftChestCaptureAdapter::resolveItemDisplayName);

        this.settlementPlannerManager = new SettlementPlannerManager(new JsonSettlementPlannerStore(configDir.resolve("settlement-planner.json")));

        init();
    }

    public static CompanionSession getInstance() {
        return INSTANCE;
    }

    public void init() {
        RulePackLoader loader = new RulePackLoader();
        this.activeRulePack = loader.loadBundled();
        
        // Sync feature flags
        if (activeRulePack != null && activeRulePack.featureFlags() != null) {
            activeRulePack.featureFlags().forEach(featureManager::setFeatureState);
        }
        
        guideEngine.initialize();
        featureManager.setGuideStatusSupplier(guideEngine::getLoadStatus);

        chestManager.initialize();
        featureManager.setChestManagerStatusSupplier(chestManager::getStatus);

        loadKnowledgeModules();
        featureManager.setCommandCatalogStatusSupplier(() -> commandCatalogStatus);
        featureManager.setCraftingKnowledgeStatusSupplier(() -> craftingKnowledgeStatus);
        featureManager.setItemKnowledgeStatusSupplier(() -> itemKnowledgeStatus);
        featureManager.setSettlementKnowledgeStatusSupplier(() -> settlementCatalogStatus);

        settlementPlannerManager.initialize();

        loadParserCatalog();

        refreshCompatibility();
    }

    private void loadParserCatalog() {
        try {
            KnowledgeLoadResult<GameZoneParserCatalog> result = new GameZoneParserLoader().load();
            this.parserCatalog = result.data() != null ? result.data() : GameZoneParserCatalog.empty();
            this.parserCatalogStatus = toModuleStatus(result.outcome());
        } catch (Exception e) {
            this.parserCatalog = GameZoneParserCatalog.empty();
            this.parserCatalogStatus = KnowledgeModuleStatus.ERROR;
        }
    }

    /**
     * Loads the three M4 knowledge modules independently - a failure in one (missing resource,
     * malformed JSON, unsupported schema) must never prevent the other two from loading, and
     * must never affect Guide/Kistor. Each loader already fails closed internally; this wraps
     * that in an extra defensive try/catch so a truly unexpected exception here still can't take
     * the whole session down.
     */
    private void loadKnowledgeModules() {
        try {
            KnowledgeLoadResult<CommandCatalog> result = new CommandKnowledgeLoader().load();
            this.commandCatalog = result.data() != null ? result.data() : CommandCatalog.empty();
            this.commandCatalogStatus = toModuleStatus(result.outcome());
        } catch (Exception e) {
            this.commandCatalog = CommandCatalog.empty();
            this.commandCatalogStatus = KnowledgeModuleStatus.ERROR;
        }

        try {
            KnowledgeLoadResult<CraftingKnowledgeBase> result = new CraftingKnowledgeLoader().load();
            this.craftingKnowledgeBase = result.data() != null ? result.data() : CraftingKnowledgeBase.empty();
            this.craftingKnowledgeStatus = toModuleStatus(result.outcome());
        } catch (Exception e) {
            this.craftingKnowledgeBase = CraftingKnowledgeBase.empty();
            this.craftingKnowledgeStatus = KnowledgeModuleStatus.ERROR;
        }

        try {
            KnowledgeLoadResult<ItemKnowledgeBase> result = new ItemKnowledgeLoader().load();
            this.itemKnowledgeBase = result.data() != null ? result.data() : ItemKnowledgeBase.empty();
            this.itemKnowledgeStatus = toModuleStatus(result.outcome());
        } catch (Exception e) {
            this.itemKnowledgeBase = ItemKnowledgeBase.empty();
            this.itemKnowledgeStatus = KnowledgeModuleStatus.ERROR;
        }

        try {
            KnowledgeLoadResult<SettlementCatalog> result = new SettlementKnowledgeLoader().load();
            this.settlementCatalog = result.data() != null ? result.data() : SettlementCatalog.empty();
            this.settlementCatalogStatus = toModuleStatus(result.outcome());
        } catch (Exception e) {
            this.settlementCatalog = SettlementCatalog.empty();
            this.settlementCatalogStatus = KnowledgeModuleStatus.ERROR;
        }
    }

    private static KnowledgeModuleStatus toModuleStatus(KnowledgeLoadResult.Outcome outcome) {
        return switch (outcome) {
            case LOADED -> KnowledgeModuleStatus.LOADED;
            case INCOMPATIBLE_SCHEMA -> KnowledgeModuleStatus.INCOMPATIBLE;
            case ERROR -> KnowledgeModuleStatus.ERROR;
        };
    }

    public void refreshCompatibility() {
        this.compatibilityResult = compatibilityService.evaluate(activeRulePack, bridge.getMinecraftVersion());
    }

    public MinecraftBridge getBridge() {
        return bridge;
    }

    public FeatureManager getFeatureManager() {
        return featureManager;
    }

    public StorageManager getStorageManager() {
        return storageManager;
    }

    public RulePack getActiveRulePack() {
        return activeRulePack;
    }

    public CompatibilityResult getCompatibilityResult() {
        if (compatibilityResult == null) {
            refreshCompatibility();
        }
        return compatibilityResult;
    }

    public GuideEngine getGuideEngine() {
        return guideEngine;
    }

    public GuideContext getCurrentGuideContext() {
        return bridge.getGuideContext();
    }

    public ChestManager getChestManager() {
        return chestManager;
    }

    public CommandCatalog getCommandCatalog() {
        return commandCatalog;
    }

    public KnowledgeModuleStatus getCommandCatalogStatus() {
        return commandCatalogStatus;
    }

    public CraftingKnowledgeBase getCraftingKnowledgeBase() {
        return craftingKnowledgeBase;
    }

    public KnowledgeModuleStatus getCraftingKnowledgeStatus() {
        return craftingKnowledgeStatus;
    }

    public ItemKnowledgeBase getItemKnowledgeBase() {
        return itemKnowledgeBase;
    }

    public KnowledgeModuleStatus getItemKnowledgeStatus() {
        return itemKnowledgeStatus;
    }

    public SettlementCatalog getSettlementCatalog() {
        return settlementCatalog;
    }

    public KnowledgeModuleStatus getSettlementCatalogStatus() {
        return settlementCatalogStatus;
    }

    public SettlementPlannerManager getSettlementPlannerManager() {
        return settlementPlannerManager;
    }

    public GameZoneParserCatalog getParserCatalog() {
        return parserCatalog;
    }

    public KnowledgeModuleStatus getParserCatalogStatus() {
        return parserCatalogStatus;
    }

    public GameZoneToastManager getToastManager() {
        return toastManager;
    }

    public GameZoneChatObserver getChatObserver() {
        return chatObserver;
    }

    /**
     * The isolated storage-index context key for the player's current world/server, reusing the
     * same profile/world/server identity semantics as {@link GuideContext}.
     */
    public String getCurrentStorageContext() {
        return getCurrentGuideContext().getStorageKey();
    }

    public void evaluateGuide() {
        if (guideEngine != null) {
            guideEngine.evaluate(getCurrentGuideContext());
        }
    }

    public ServerProfile getCurrentServerProfile() {
        if (bridge.isConnectedToGameZone()) {
            return ServerProfile.GAMEZONE;
        }
        return ServerProfile.GENERIC;
    }
}

