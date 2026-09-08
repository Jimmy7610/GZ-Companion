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
    @DisplayName("Should successfully load bundled GameZone Rule Pack with unofficial unverified status")
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
        assertEquals(CompatibilityStatus.UNVERIFIED, manifest.verification().status());
        assertFalse(manifest.name().contains("Official"), "Manifest name must not claim official status");
    }

    @Test
    @DisplayName("Should parse feature flags from bundled pack with safe defaults")
    void testFeatureFlags() {
        RulePack pack = loader.loadBundled();
        assertNotNull(pack.featureFlags());
        assertTrue(pack.featureFlags().containsKey("beginnerGuide"));
        assertFalse(pack.featureFlags().get("beginnerGuide"), "Unimplemented guide flag must default to false");
        assertTrue(pack.featureFlags().containsKey("settlementTools"));
        assertFalse(pack.featureFlags().get("settlementTools"));
    }

    @Test
    @DisplayName("Should parse empty unverified collections without errors")
    void testEmptyCollections() {
        RulePack pack = loader.loadBundled();
        assertNotNull(pack.commands(), "Commands list must not be null");
        assertTrue(pack.commands().isEmpty(), "Commands list should be empty pending live server verification");

        assertNotNull(pack.guides(), "Guides list must not be null");
        assertTrue(pack.guides().isEmpty(), "Guides list should be empty pending live server verification");

        assertNotNull(pack.worldRules(), "World rules list must not be null");
        assertTrue(pack.worldRules().isEmpty(), "World rules list should be empty pending live server verification");

        assertNotNull(pack.parsers(), "Parsers list must not be null");
        assertTrue(pack.parsers().isEmpty(), "Parsers list should be empty pending live server verification");
    }
}