package se.jimmyeliasson.gzcompanion.core.feature;

import se.jimmyeliasson.gzcompanion.chest.model.ChestManagerStatus;
import se.jimmyeliasson.gzcompanion.guide.model.GuideLoadStatus;
import se.jimmyeliasson.gzcompanion.knowledge.common.KnowledgeModuleStatus;
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
    private Supplier<KnowledgeModuleStatus> commandCatalogStatusSupplier = () -> KnowledgeModuleStatus.UNAVAILABLE;
    private Supplier<KnowledgeModuleStatus> craftingKnowledgeStatusSupplier = () -> KnowledgeModuleStatus.UNAVAILABLE;
    private Supplier<KnowledgeModuleStatus> itemKnowledgeStatusSupplier = () -> KnowledgeModuleStatus.UNAVAILABLE;
    private Supplier<KnowledgeModuleStatus> settlementKnowledgeStatusSupplier = () -> KnowledgeModuleStatus.UNAVAILABLE;
    private Supplier<KnowledgeModuleStatus> buildingKnowledgeStatusSupplier = () -> KnowledgeModuleStatus.UNAVAILABLE;
    private Supplier<KnowledgeModuleStatus> marketWatchKnowledgeStatusSupplier = () -> KnowledgeModuleStatus.UNAVAILABLE;
    private Supplier<Boolean> settingsStatusSupplier = () -> Boolean.FALSE;

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

    public void setCommandCatalogStatusSupplier(Supplier<KnowledgeModuleStatus> supplier) {
        if (supplier != null) {
            this.commandCatalogStatusSupplier = supplier;
        }
    }

    public KnowledgeModuleStatus getCommandCatalogStatus() {
        return commandCatalogStatusSupplier != null ? commandCatalogStatusSupplier.get() : KnowledgeModuleStatus.UNAVAILABLE;
    }

    public void setCraftingKnowledgeStatusSupplier(Supplier<KnowledgeModuleStatus> supplier) {
        if (supplier != null) {
            this.craftingKnowledgeStatusSupplier = supplier;
        }
    }

    public KnowledgeModuleStatus getCraftingKnowledgeStatus() {
        return craftingKnowledgeStatusSupplier != null ? craftingKnowledgeStatusSupplier.get() : KnowledgeModuleStatus.UNAVAILABLE;
    }

    public void setItemKnowledgeStatusSupplier(Supplier<KnowledgeModuleStatus> supplier) {
        if (supplier != null) {
            this.itemKnowledgeStatusSupplier = supplier;
        }
    }

    public KnowledgeModuleStatus getItemKnowledgeStatus() {
        return itemKnowledgeStatusSupplier != null ? itemKnowledgeStatusSupplier.get() : KnowledgeModuleStatus.UNAVAILABLE;
    }

    public void setSettlementKnowledgeStatusSupplier(Supplier<KnowledgeModuleStatus> supplier) {
        if (supplier != null) {
            this.settlementKnowledgeStatusSupplier = supplier;
        }
    }

    public KnowledgeModuleStatus getSettlementKnowledgeStatus() {
        return settlementKnowledgeStatusSupplier != null ? settlementKnowledgeStatusSupplier.get() : KnowledgeModuleStatus.UNAVAILABLE;
    }

    public void setBuildingKnowledgeStatusSupplier(Supplier<KnowledgeModuleStatus> supplier) {
        if (supplier != null) {
            this.buildingKnowledgeStatusSupplier = supplier;
        }
    }

    public KnowledgeModuleStatus getBuildingKnowledgeStatus() {
        return buildingKnowledgeStatusSupplier != null ? buildingKnowledgeStatusSupplier.get() : KnowledgeModuleStatus.UNAVAILABLE;
    }

    public void setMarketWatchKnowledgeStatusSupplier(Supplier<KnowledgeModuleStatus> supplier) {
        if (supplier != null) {
            this.marketWatchKnowledgeStatusSupplier = supplier;
        }
    }

    public KnowledgeModuleStatus getMarketWatchKnowledgeStatus() {
        return marketWatchKnowledgeStatusSupplier != null ? marketWatchKnowledgeStatusSupplier.get() : KnowledgeModuleStatus.UNAVAILABLE;
    }

    public void setSettingsStatusSupplier(Supplier<Boolean> supplier) {
        if (supplier != null) {
            this.settingsStatusSupplier = supplier;
        }
    }

    public boolean getSettingsStatus() {
        return settingsStatusSupplier != null && Boolean.TRUE.equals(settingsStatusSupplier.get());
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
            case CRAFTING -> (getCraftingKnowledgeStatus().isAvailable() || getItemKnowledgeStatus().isAvailable())
                    ? ModuleStatus.AVAILABLE : ModuleStatus.COMING_SOON;
            case KISTOR -> getChestManagerStatus().isAvailable() ? ModuleStatus.AVAILABLE : ModuleStatus.COMING_SOON;
            case SETTLEMENT -> getSettlementKnowledgeStatus().isAvailable() ? ModuleStatus.AVAILABLE : ModuleStatus.COMING_SOON;
            case BYGGPLANER -> getBuildingKnowledgeStatus().isAvailable() ? ModuleStatus.AVAILABLE : ModuleStatus.COMING_SOON;
            case MARKETWATCH -> getMarketWatchKnowledgeStatus().isAvailable() ? ModuleStatus.AVAILABLE : ModuleStatus.COMING_SOON;
            case KOMMANDON -> getCommandCatalogStatus().isAvailable() ? ModuleStatus.AVAILABLE : ModuleStatus.COMING_SOON;
            case INSTALLNINGAR -> getSettingsStatus() ? ModuleStatus.AVAILABLE : ModuleStatus.COMING_SOON;
        };
    }
}