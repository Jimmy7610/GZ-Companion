package se.jimmyeliasson.gzcompanion.gamezone;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import se.jimmyeliasson.gzcompanion.diagnostics.CompatibilityStatus;

import static org.junit.jupiter.api.Assertions.*;

class RulePackLoaderTest {
    private RulePackLoader loader;

    @BeforeEach
    void setUp() {
        loader = new RulePackLoader();
    }

    @Test
    @DisplayName("Should successfully load bundled GameZone Rule Pack")
    void testLoadBundled() {
        RulePack pack = loader.loadBundled();
        assertNotNull(pack, "Loaded RulePack must not be null");
        assertNotNull(pack.manifest(), "Manifest must not be null");

        RulePackManifest manifest = pack.manifest();
        assertEquals("1.0.0", manifest.schemaVersion());
        assertEquals("gamezone", manifest.profile());
        assertEquals("play.gamezonemc.se", manifest.targetHost());
        assertTrue(manifest.testedMinecraftVersions().contains("26.1.2"), "Must support 26.1.2");
        assertNotNull(manifest.verification());
        assertEquals(CompatibilityStatus.VERIFIED, manifest.verification().status());
    }

    @Test
    @DisplayName("Should parse feature flags from bundled pack")
    void testFeatureFlags() {
        RulePack pack = loader.loadBundled();
        assertNotNull(pack.featureFlags());
        assertTrue(pack.featureFlags().containsKey("beginnerGuide"));
        assertTrue(pack.featureFlags().get("beginnerGuide"));
        assertTrue(pack.featureFlags().containsKey("settlementTools"));
        assertFalse(pack.featureFlags().get("settlementTools"));
    }

    @Test
    @DisplayName("Should parse commands from bundled pack")
    void testCommands() {
        RulePack pack = loader.loadBundled();
        assertNotNull(pack.commands());
        assertFalse(pack.commands().isEmpty());
        assertTrue(pack.commands().stream().anyMatch(c -> c.command().equals("/spawn")));
    }

    @Test
    @DisplayName("Should parse guides from bundled pack")
    void testGuides() {
        RulePack pack = loader.loadBundled();
        assertNotNull(pack.guides());
        assertFalse(pack.guides().isEmpty());
        assertEquals("beginner_welcome", pack.guides().getFirst().id());
    }
}
