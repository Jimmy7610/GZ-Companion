package se.jimmyeliasson.gzcompanion.core;

import se.jimmyeliasson.gzcompanion.core.feature.FeatureManager;
import se.jimmyeliasson.gzcompanion.diagnostics.CompatibilityResult;
import se.jimmyeliasson.gzcompanion.diagnostics.CompatibilityService;
import se.jimmyeliasson.gzcompanion.gamezone.RulePack;
import se.jimmyeliasson.gzcompanion.gamezone.RulePackLoader;
import se.jimmyeliasson.gzcompanion.minecraft.MinecraftBridge;
import se.jimmyeliasson.gzcompanion.minecraft.VanillaMinecraftBridge;
import se.jimmyeliasson.gzcompanion.profile.ServerProfile;
import se.jimmyeliasson.gzcompanion.storage.StorageManager;

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
    private RulePack activeRulePack;
    private CompatibilityResult compatibilityResult;

    private CompanionSession() {
        this.bridge = new VanillaMinecraftBridge();
        this.featureManager = new FeatureManager();
        this.storageManager = new StorageManager(Paths.get("."));
        this.compatibilityService = new CompatibilityService();
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

    public ServerProfile getCurrentServerProfile() {
        if (bridge.isConnectedToGameZone()) {
            return ServerProfile.GAMEZONE;
        }
        return ServerProfile.GENERIC;
    }
}
