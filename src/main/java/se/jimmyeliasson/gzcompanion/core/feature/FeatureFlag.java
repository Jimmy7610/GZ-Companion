package se.jimmyeliasson.gzcompanion.core.feature;

/**
 * Feature flag identifiers supported by GZ Companion.
 *
 * In accordance with "Java understands Minecraft. Data understands GameZone",
 * Java defines stable feature keys with safe defaults (disabled/false for optional
 * server-specific features). Active states are driven dynamically by the loaded
 * Rule Pack (feature-flags.json).
 */
public enum FeatureFlag {
    BEGINNER_GUIDE("beginnerGuide", "Interaktiv nybörjarguide", false),
    CHEST_MANAGER("chestManager", "Kist- och containeröversikt", false),
    SETTLEMENT_TOOLS("settlementTools", "Settlement-hjälpmedel", false),
    BUILDING_PLANNER("buildingPlanner", "Byggplanerare", false),
    MARKET_WATCH("marketWatch", "Marknadsbevakare", false),
    DEATH_RISK_ADVISOR("deathRiskAdvisor", "Överlevnads- och riskrådgivare", false);

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