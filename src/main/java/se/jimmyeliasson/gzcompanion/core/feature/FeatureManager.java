package se.jimmyeliasson.gzcompanion.core.feature;

import se.jimmyeliasson.gzcompanion.chest.model.ChestManagerStatus;
import se.jimmyeliasson.gzcompanion.guide.model.GuideLoadStatus;
import se.jimmyeliasson.gzcompanion.ui.TabType;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Evaluates feature availability and module implementation readiness.
 * Driven by both Milestone implementation state and loaded Rule Pack flags.
 */
public class FeatureManager {
    private final Map<FeatureFlag, Boolean> flagStates = new EnumMap<>(FeatureFlag.class);
    private Supplier<GuideLoadStatus> guideStatusSupplier = () -> GuideLoadStatus.LOADED;
    private Supplier<ChestManagerStatus> chestManagerStatusSupplier = () -> ChestManagerStatus.UNAVAILABLE;

    public FeatureManager() {
        resetToDefaults();
    }

    public void resetToDefaults() {
        flagStates.clear();
        for (FeatureFlag flag : FeatureFlag.values()) {
            flagStates.put(flag, flag.isDefaultEnabled());
        }
    }

    public void setGuideStatusSupplier(Supplier<GuideLoadStatus> supplier) {
        if (supplier != null) {
            this.guideStatusSupplier = supplier;
        }
    }

    public void setGuideStatus(GuideLoadStatus status) {
        if (status != null) {
            this.guideStatusSupplier = () -> status;
        }
    }

    public GuideLoadStatus getGuideLoadStatus() {
        return guideStatusSupplier != null ? guideStatusSupplier.get() : GuideLoadStatus.UNAVAILABLE;
    }

    public void setChestManagerStatusSupplier(Supplier<ChestManagerStatus> supplier) {
        if (supplier != null) {
            this.chestManagerStatusSupplier = supplier;
        }
    }

    public ChestManagerStatus getChestManagerStatus() {
        return chestManagerStatusSupplier != null ? chestManagerStatusSupplier.get() : ChestManagerStatus.UNAVAILABLE;
    }

    public void setFeatureState(FeatureFlag flag, boolean enabled) {
        if (flag != null) {
            flagStates.put(flag, enabled);
        }
    }

    public void setFeatureState(String key, boolean enabled) {
        FeatureFlag flag = FeatureFlag.fromKey(key);
        if (flag != null) {
            flagStates.put(flag, enabled);
        }
    }

    public boolean isEnabled(FeatureFlag flag) {
        if (flag == null) return false;
        return flagStates.getOrDefault(flag, flag.isDefaultEnabled());
    }

    public boolean isEnabled(String key) {
        FeatureFlag flag = FeatureFlag.fromKey(key);
        return flag != null && isEnabled(flag);
    }

    /**
     * Determines the actual runtime module readiness for UI display.
     * Evaluates module implementation readiness and GuideEngine load status.
     */
    public ModuleStatus getModuleStatus(TabType tab) {
        if (tab == null) return ModuleStatus.COMING_SOON;
        return switch (tab) {
            case HEM -> ModuleStatus.AVAILABLE;
            case GUIDE -> (getGuideLoadStatus() == GuideLoadStatus.LOADED) ? ModuleStatus.AVAILABLE : ModuleStatus.COMING_SOON;
            case CRAFTING -> ModuleStatus.COMING_SOON;
            case KISTOR -> getChestManagerStatus().isAvailable() ? ModuleStatus.AVAILABLE : ModuleStatus.COMING_SOON;
            case SETTLEMENT -> ModuleStatus.COMING_SOON;
            case BYGGPLANER -> ModuleStatus.COMING_SOON;
            case MARKETWATCH -> ModuleStatus.COMING_SOON;
            case KOMMANDON -> ModuleStatus.COMING_SOON;
            case INSTALLNINGAR -> ModuleStatus.COMING_SOON;
        };
    }
}