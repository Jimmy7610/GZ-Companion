package se.jimmyeliasson.gzcompanion.core.feature;

/**
 * Rule Pack-driven feature flags for GZ Companion.
 */
public enum FeatureFlag {
    BEGINNER_GUIDE("beginnerGuide", "Interaktiv nyb\u00F6rjarguide", true),
    CHEST_MANAGER("chestManager", "Kist- och container\u00F6versikt", true),
    SETTLEMENT_TOOLS("settlementTools", "Settlement-hj\u00E4lpmedel", false),
    BUILDING_PLANNER("buildingPlanner", "Byggplanerare", false),
    MARKET_WATCH("marketWatch", "Marknadsbevakare", false),
    DEATH_RISK_ADVISOR("deathRiskAdvisor", "\u00D6verlevnads- och riskr\u00E5dgivare", true);

    private final String key;
    private final String displayName;
    private final boolean defaultEnabled;

    FeatureFlag(String key, String displayName, boolean defaultEnabled) {
        this.key = key;
        this.displayName = displayName;
        this.defaultEnabled = defaultEnabled;
    }

    public String getKey() {
        return key;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isDefaultEnabled() {
        return defaultEnabled;
    }

    public static FeatureFlag fromKey(String key) {
        if (key == null) return null;
        for (FeatureFlag flag : values()) {
            if (flag.key.equalsIgnoreCase(key)) {
                return flag;
            }
        }
        return null;
    }
}