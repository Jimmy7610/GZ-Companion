package se.jimmyeliasson.gzcompanion.core.feature;

import se.jimmyeliasson.gzcompanion.ui.TabType;

import java.util.EnumMap;
import java.util.Map;

/**
 * Evaluates feature availability and module implementation readiness.
 * Driven by both Milestone implementation state and loaded Rule Pack flags.
 */
public class FeatureManager {
    private final Map<FeatureFlag, Boolean> flagStates = new EnumMap<>(FeatureFlag.class);

    public FeatureManager() {
        resetToDefaults();
    }

    public void resetToDefaults() {
        flagStates.clear();
        for (FeatureFlag flag : FeatureFlag.values()) {
            flagStates.put(flag, flag.isDefaultEnabled());
        }
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
     * In Milestone 1, only HEM is fully implemented and AVAILABLE.
     * Other functional tabs (like Guide, Crafting, Settlements) are COMING_SOON
     * until their respective milestone implementations are complete.
     */
    public ModuleStatus getModuleStatus(TabType tab) {
        if (tab == null) return ModuleStatus.COMING_SOON;
        return switch (tab) {
            case HEM -> ModuleStatus.AVAILABLE;
            case GUIDE -> ModuleStatus.COMING_SOON;
            case CRAFTING -> ModuleStatus.COMING_SOON;
            case KISTOR -> ModuleStatus.COMING_SOON;
            case SETTLEMENT -> ModuleStatus.COMING_SOON;
            case BYGGPLANER -> ModuleStatus.COMING_SOON;
            case MARKETWATCH -> ModuleStatus.COMING_SOON;
            case KOMMANDON -> ModuleStatus.COMING_SOON;
            case INSTALLNINGAR -> ModuleStatus.COMING_SOON;
        };
    }
}