package se.jimmyeliasson.gzcompanion.diagnostics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import se.jimmyeliasson.gzcompanion.gamezone.RulePack;
import se.jimmyeliasson.gzcompanion.gamezone.RulePackLoader;

import static org.junit.jupiter.api.Assertions.*;

class CompatibilityServiceTest {

    @Test
    @DisplayName("Should evaluate bundled pack as unverified/compatible pending live confirmation")
    void testBundledCompatibility() {
        RulePackLoader loader = new RulePackLoader();
        RulePack pack = loader.loadBundled();

        CompatibilityService service = new CompatibilityService();
        CompatibilityResult result = service.evaluate(pack, "26.1.2");

        assertNotNull(result);
        assertEquals(CompatibilityStatus.UNVERIFIED, result.overallStatus());
        assertFalse(result.moduleReports().isEmpty());
    }

    @Test
    @DisplayName("Should report warning when running on untracked Minecraft version")
    void testUntrackedMinecraftVersion() {
        RulePackLoader loader = new RulePackLoader();
        RulePack pack = loader.loadBundled();

        CompatibilityService service = new CompatibilityService();
        CompatibilityResult result = service.evaluate(pack, "99.0.0");

        assertNotNull(result);
        assertEquals(CompatibilityStatus.WARNING, result.overallStatus());
    }

    @Test
    @DisplayName("Should report incompatible for null rule pack")
    void testNullRulePack() {
        CompatibilityService service = new CompatibilityService();
        CompatibilityResult result = service.evaluate(null, "26.1.2");

        assertNotNull(result);
        assertEquals(CompatibilityStatus.INCOMPATIBLE, result.overallStatus());
    }
}