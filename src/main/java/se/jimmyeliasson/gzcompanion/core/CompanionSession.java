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
import se.jimmyeliasson.gzcompanion.gamezone.settlement.GameZoneSettlementTracker;
import se.jimmyeliasson.gzcompanion.gamezone.status.GameZoneLiveStatusTracker;
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
import se.jimmyeliasson.gzcompanion.building.BuildingPlanManager;
import se.jimmyeliasson.gzcompanion.building.storage.JsonBuildingPlanStore;
import se.jimmyeliasson.gzcompanion.knowledge.building.BuildingKnowledgeBase;
import se.jimmyeliasson.gzcompanion.knowledge.building.BuildingKnowledgeLoader;
import se.jimmyeliasson.gzcompanion.knowledge.economy.MarketWatchInfo;
import se.jimmyeliasson.gzcompanion.knowledge.economy.MarketWatchKnowledgeLoader;
import se.jimmyeliasson.gzcompanion.knowledge.items.ItemKnowledgeBase;
import se.jimmyeliasson.gzcompanion.knowledge.items.ItemKnowledgeLoader;
import se.jimmyeliasson.gzcompanion.knowledge.settlement.SettlementCatalog;
import se.jimmyeliasson.gzcompanion.knowledge.settlement.SettlementKnowledgeLoader;
import se.jimmyeliasson.gzcompanion.leaderboard.LeaderboardManager;
import se.jimmyeliasson.gzcompanion.marketwatch.MarketWatchNotesManager;
import se.jimmyeliasson.gzcompanion.marketwatch.storage.JsonMarketWatchNotesStore;
import se.jimmyeliasson.gzcompanion.minecraft.MinecraftBridge;
import se.jimmyeliasson.gzcompanion.minecraft.VanillaMinecraftBridge;
import se.jimmyeliasson.gzcompanion.profile.ServerProfile;
import se.jimmyeliasson.gzcompanion.settings.JsonSettingsStore;
import se.jimmyeliasson.gzcompanion.settings.SettingsManager;
import se.jimmyeliasson.gzcompanion.settlement.SettlementPlannerManager;
import se.jimmyeliasson.gzcompanion.settlement.storage.JsonSettlementPlannerStore;
import se.jimmyeliasson.gzcompanion.storage.StorageManager;
import se.jimmyeliasson.gzcompanion.update.GitHubReleaseSource;
import se.jimmyeliasson.gzcompanion.update.HttpUpdateByteSource;
import se.jimmyeliasson.gzcompanion.update.SemanticVersion;
import se.jimmyeliasson.gzcompanion.update.UpdateChannel;
import se.jimmyeliasson.gzcompanion.update.UpdateDownloader;
import se.jimmyeliasson.gzcompanion.update.UpdateManager;
import se.jimmyeliasson.gzcompanion.update.UpdateReleaseSource;

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
    private BuildingKnowledgeBase buildingKnowledgeBase = BuildingKnowledgeBase.empty();
    private KnowledgeModuleStatus buildingKnowledgeStatus = KnowledgeModuleStatus.UNAVAILABLE;
    private final BuildingPlanManager buildingPlanManager;
    private MarketWatchInfo marketWatchInfo = MarketWatchInfo.empty();
    private KnowledgeModuleStatus marketWatchInfoStatus = KnowledgeModuleStatus.UNAVAILABLE;
    private final MarketWatchNotesManager marketWatchNotesManager;
    private final SettingsManager settingsManager;

    private GameZoneParserCatalog parserCatalog = GameZoneParserCatalog.empty();
    private KnowledgeModuleStatus parserCatalogStatus = KnowledgeModuleStatus.UNAVAILABLE;
    private final GameZoneToastManager toastManager = new GameZoneToastManager();
    private final GameZoneChatObserver chatObserver = new GameZoneChatObserver(() -> parserCatalog, toastManager, this::isConnectedToGameZone);
    private final GameZoneSettlementTracker settlementTracker = new GameZoneSettlementTracker();
    private final GameZoneLiveStatusTracker liveStatusTracker = new GameZoneLiveStatusTracker();
    private final UpdateManager updateManager = createUpdateManager();
    private final LeaderboardManager leaderboardManager =
            new LeaderboardManager(new se.jimmyeliasson.gzcompanion.leaderboard.GameZoneLeaderboardSource(CompanionConstants.getModVersion()));

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
        this.buildingPlanManager = new BuildingPlanManager(new JsonBuildingPlanStore(configDir.resolve("building-plans.json")));
        this.marketWatchNotesManager = new MarketWatchNotesManager(new JsonMarketWatchNotesStore(configDir.resolve("marketwatch-notes.json")));
        this.settingsManager = new SettingsManager(new JsonSettingsStore(configDir.resolve("settings.json")));

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
        featureManager.setBuildingKnowledgeStatusSupplier(() -> buildingKnowledgeStatus);
        featureManager.setMarketWatchKnowledgeStatusSupplier(() -> marketWatchInfoStatus);

        settlementPlannerManager.initialize();
        buildingPlanManager.initialize();
        marketWatchNotesManager.initialize();
        settingsManager.initialize();
        featureManager.setSettingsStatusSupplier(() -> settingsManager.getStatus().isAvailable());
        toastManager.setNotificationsEnabledSupplier(() -> settingsManager.getSettings().companionNotificationsEnabled());
        toastManager.setGameZoneToastsEnabledSupplier(() -> settingsManager.getSettings().gameZoneToastsEnabled());

        loadParserCatalog();

        refreshCompatibility();

        updateManager.startBackgroundChecks();
    }

    private static UpdateManager createUpdateManager() {
        String versionString = CompanionConstants.getModVersion();
        SemanticVersion currentVersion = SemanticVersion.parse(versionString)
                .orElseGet(() -> SemanticVersion.parse("0.0.0").orElseThrow());
        UpdateChannel currentChannel = UpdateChannel.classify(currentVersion);
        UpdateReleaseSource releaseSource = new GitHubReleaseSource(versionString);
        UpdateDownloader downloader = new UpdateDownloader(new HttpUpdateByteSource(versionString));
        return new UpdateManager(currentVersion, currentChannel, releaseSource, downloader);
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

        try {
            KnowledgeLoadResult<BuildingKnowledgeBase> result = new BuildingKnowledgeLoader().load();
            this.buildingKnowledgeBase = result.data() != null ? result.data() : BuildingKnowledgeBase.empty();
            this.buildingKnowledgeStatus = toModuleStatus(result.outcome());
        } catch (Exception e) {
            this.buildingKnowledgeBase = BuildingKnowledgeBase.empty();
            this.buildingKnowledgeStatus = KnowledgeModuleStatus.ERROR;
        }

        try {
            KnowledgeLoadResult<MarketWatchInfo> result = new MarketWatchKnowledgeLoader().load();
            this.marketWatchInfo = result.data() != null ? result.data() : MarketWatchInfo.empty();
            this.marketWatchInfoStatus = toModuleStatus(result.outcome());
        } catch (Exception e) {
            this.marketWatchInfo = MarketWatchInfo.empty();
            this.marketWatchInfoStatus = KnowledgeModuleStatus.ERROR;
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

    public BuildingKnowledgeBase getBuildingKnowledgeBase() {
        return buildingKnowledgeBase;
    }

    public KnowledgeModuleStatus getBuildingKnowledgeStatus() {
        return buildingKnowledgeStatus;
    }

    public BuildingPlanManager getBuildingPlanManager() {
        return buildingPlanManager;
    }

    public MarketWatchInfo getMarketWatchInfo() {
        return marketWatchInfo;
    }

    public KnowledgeModuleStatus getMarketWatchInfoStatus() {
        return marketWatchInfoStatus;
    }

    public MarketWatchNotesManager getMarketWatchNotesManager() {
        return marketWatchNotesManager;
    }

    public SettingsManager getSettingsManager() {
        return settingsManager;
    }

    public GameZoneSettlementTracker getSettlementTracker() {
        return settlementTracker;
    }

    public GameZoneLiveStatusTracker getLiveStatusTracker() {
        return liveStatusTracker;
    }

    /** THE single authoritative updater instance - Home and Inställningar both read this same manager. */
    public UpdateManager getUpdateManager() {
        return updateManager;
    }

    /** THE single authoritative Leaderboards cache/fetch manager - see {@link LeaderboardManager}. */
    public LeaderboardManager getLeaderboardManager() {
        return leaderboardManager;
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

    /**
     * Whether the client is currently connected to a server {@link
     * se.jimmyeliasson.gzcompanion.profile.ServerDetection} recognizes as GameZoneMC - never true
     * for singleplayer or any other server. This is the sole authoritative profile check used to
     * gate {@link GameZoneChatObserver}'s parsing; no ping probes or additional server scanning
     * are ever performed here.
     */
    public boolean isConnectedToGameZone() {
        return bridge.isConnectedToGameZone();
    }
}

