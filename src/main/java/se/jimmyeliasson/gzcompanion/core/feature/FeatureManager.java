package se.jimmyeliasson.gzcompanion.core.feature;

import java.util.EnumMap;
import java.util.Map;

/**
 * Evaluates feature availability driven by the loaded GameZone Rule Pack.
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
}