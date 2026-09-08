package se.jimmyeliasson.gzcompanion.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import se.jimmyeliasson.gzcompanion.core.feature.FeatureFlag;
import se.jimmyeliasson.gzcompanion.core.feature.FeatureManager;
import se.jimmyeliasson.gzcompanion.core.feature.ModuleStatus;
import se.jimmyeliasson.gzcompanion.ui.TabType;

import static org.junit.jupiter.api.Assertions.*;

class FeatureFlagTest {

    @Test
    @DisplayName("Should evaluate default feature flag values as safely disabled")
    void testDefaultValues() {
        FeatureManager manager = new FeatureManager();
        assertFalse(manager.isEnabled(FeatureFlag.BEGINNER_GUIDE));
        assertFalse(manager.isEnabled(FeatureFlag.CHEST_MANAGER));
        assertFalse(manager.isEnabled(FeatureFlag.SETTLEMENT_TOOLS));
        assertFalse(manager.isEnabled(FeatureFlag.MARKET_WATCH));
    }

    @Test
    @DisplayName("Should dynamically update feature flag values")
    void testUpdateFlagState() {
        FeatureManager manager = new FeatureManager();
        manager.setFeatureState(FeatureFlag.SETTLEMENT_TOOLS, true);
        assertTrue(manager.isEnabled(FeatureFlag.SETTLEMENT_TOOLS));

        manager.setFeatureState("marketWatch", true);
        assertTrue(manager.isEnabled(FeatureFlag.MARKET_WATCH));
    }

    @Test
    @DisplayName("Should safely return false for null or unknown flags")
    void testUnknownFlags() {
        FeatureManager manager = new FeatureManager();
        assertFalse(manager.isEnabled((FeatureFlag) null));
        assertFalse(manager.isEnabled("unknownRandomFeatureFlag"));
    }

    @Test
    @DisplayName("Should report accurate module readiness matching Milestone 1 implementation")
    void testModuleStatusReadiness() {
        FeatureManager manager = new FeatureManager();
        assertEquals(ModuleStatus.AVAILABLE, manager.getModuleStatus(TabType.HEM));
        assertEquals(ModuleStatus.COMING_SOON, manager.getModuleStatus(TabType.GUIDE));
        assertEquals(ModuleStatus.COMING_SOON, manager.getModuleStatus(TabType.CRAFTING));
        assertEquals(ModuleStatus.COMING_SOON, manager.getModuleStatus(TabType.KISTOR));
        assertEquals(ModuleStatus.COMING_SOON, manager.getModuleStatus(TabType.SETTLEMENT));
        assertEquals(ModuleStatus.COMING_SOON, manager.getModuleStatus(TabType.BYGGPLANER));
        assertEquals(ModuleStatus.COMING_SOON, manager.getModuleStatus(TabType.MARKETWATCH));
        assertEquals(ModuleStatus.COMING_SOON, manager.getModuleStatus(TabType.KOMMANDON));
        assertEquals(ModuleStatus.COMING_SOON, manager.getModuleStatus(TabType.INSTALLNINGAR));
    }
}