package se.jimmyeliasson.gzcompanion.gamezone;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import se.jimmyeliasson.gzcompanion.core.CompanionConstants;
import se.jimmyeliasson.gzcompanion.core.feature.FeatureFlag;
import se.jimmyeliasson.gzcompanion.core.feature.FeatureManager;
import se.jimmyeliasson.gzcompanion.diagnostics.CompatibilityResult;
import se.jimmyeliasson.gzcompanion.diagnostics.CompatibilityService;
import se.jimmyeliasson.gzcompanion.diagnostics.CompatibilityStatus;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RulePackRegressionTest {

    @Test
    @DisplayName("Regression: Unverified data remains UNVERIFIED and is not falsely promoted")
    void testUnverifiedDataStaysUnverified() {
        RulePackLoader loader = new RulePackLoader();
        RulePack pack = loader.loadBundled();

        CompatibilityService service = new CompatibilityService();
        CompatibilityResult result = service.evaluate(pack, CompanionConstants.TARGET_MINECRAFT_VERSION);

        assertEquals(CompatibilityStatus.UNVERIFIED, result.overallStatus());
        assertNotEquals(CompatibilityStatus.VERIFIED, result.overallStatus(), "Unverified server facts must not report VERIFIED");
    }

    @Test
    @DisplayName("Regression: Optional features default safely when Rule Pack is missing or corrupt")
    void testFeatureManagerSafeDefaultsOnMissingPack() {
        FeatureManager featureManager = new FeatureManager();
        // Reset or corrupt state
        featureManager.resetToDefaults();

        for (FeatureFlag flag : FeatureFlag.values()) {
            assertFalse(featureManager.isEnabled(flag), "Flag " + flag.name() + " should default safely to false");
        }
    }

    @Test
    @DisplayName("Regression: App can load minimal/bootstrap pack without crashing")
    void testMinimalPackLoad() {
        RulePack minimalPack = RulePack.empty("Minimal bootstrap test");
        assertNotNull(minimalPack);
        assertNotNull(minimalPack.manifest());
        assertTrue(minimalPack.commands().isEmpty());
        assertTrue(minimalPack.guides().isEmpty());
        assertTrue(minimalPack.worldRules().isEmpty());
        assertTrue(minimalPack.parsers().isEmpty());

        CompatibilityService service = new CompatibilityService();
        CompatibilityResult result = service.evaluate(minimalPack, se.jimmyeliasson.gzcompanion.core.CompanionConstants.TARGET_MINECRAFT_VERSION);
        assertNotNull(result);
    }

    @Test
    @DisplayName("Regression: Mod version helper resolves valid non-empty string")
    void testModVersionResolution() {
        String version = CompanionConstants.getModVersion();
        assertNotNull(version);
        assertFalse(version.isBlank());
        assertTrue(version.startsWith("0.1.0-alpha"), "Mod version must match 0.1.0-alpha series");
    }

    @Test
    @DisplayName("Regression: Running Minecraft version must match tested versions in manifest")
    void testTestedMinecraftVersionMatching() {
        RulePackLoader loader = new RulePackLoader();
        RulePack pack = loader.loadBundled();

        assertTrue(pack.manifest().testedMinecraftVersions().contains(se.jimmyeliasson.gzcompanion.core.CompanionConstants.TARGET_MINECRAFT_VERSION), "Manifest must list the target Minecraft version");
        
        CompatibilityService service = new CompatibilityService();
        CompatibilityResult result = service.evaluate(pack, se.jimmyeliasson.gzcompanion.core.CompanionConstants.TARGET_MINECRAFT_VERSION);
        
        boolean hasMinecraftWarning = result.moduleReports().stream()
                .anyMatch(r -> "minecraft".equals(r.moduleId()) && r.status() == CompatibilityStatus.WARNING);
        assertFalse(hasMinecraftWarning, "The target Minecraft version should not have version warnings");
    }
}