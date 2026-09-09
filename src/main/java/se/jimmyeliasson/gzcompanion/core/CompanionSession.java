package se.jimmyeliasson.gzcompanion.core;

import se.jimmyeliasson.gzcompanion.chest.ChestManager;
import se.jimmyeliasson.gzcompanion.chest.bridge.MinecraftChestCaptureAdapter;
import se.jimmyeliasson.gzcompanion.chest.storage.JsonChestIndexStore;
import se.jimmyeliasson.gzcompanion.core.feature.FeatureManager;
import se.jimmyeliasson.gzcompanion.diagnostics.CompatibilityResult;
import se.jimmyeliasson.gzcompanion.diagnostics.CompatibilityService;
import se.jimmyeliasson.gzcompanion.gamezone.RulePack;
import se.jimmyeliasson.gzcompanion.gamezone.RulePackLoader;
import se.jimmyeliasson.gzcompanion.guide.GuideEngine;
import se.jimmyeliasson.gzcompanion.guide.GuideLoader;
import se.jimmyeliasson.gzcompanion.guide.bridge.MinecraftGuideSnapshotProvider;
import se.jimmyeliasson.gzcompanion.guide.progress.GuideContext;
import se.jimmyeliasson.gzcompanion.guide.progress.JsonGuideProgressStore;
import se.jimmyeliasson.gzcompanion.minecraft.MinecraftBridge;
import se.jimmyeliasson.gzcompanion.minecraft.VanillaMinecraftBridge;
import se.jimmyeliasson.gzcompanion.profile.ServerProfile;
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

        refreshCompatibility();
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

