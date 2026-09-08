package se.jimmyeliasson.gzcompanion.gamezone;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MalformedRulePackTest {

    @Test
    @DisplayName("Should gracefully handle non-existent Rule Pack path without throwing exceptions")
    void testNonExistentPrefix() {
        RulePackLoader loader = new RulePackLoader();
        RulePack pack = loader.loadFromPrefix("/invalid-path-that-does-not-exist/");

        assertNotNull(pack, "RulePack must not be null even when path is invalid");
        assertNotNull(pack.manifest(), "Manifest fallback must exist");
        assertEquals("0.0.0-fallback", pack.manifest().packVersion());
        assertFalse(pack.loadWarnings().isEmpty(), "Should record warning message");
    }
}
