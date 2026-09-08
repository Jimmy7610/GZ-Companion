package se.jimmyeliasson.gzcompanion.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TypographyScaleTest {

    @Test
    @DisplayName("TypographyScale: Scale tokens must be positive, bounded within (0, 1], and preserve strict hierarchy")
    void testTypographyScaleTokens() {
        for (TypographyScale ts : TypographyScale.values()) {
            assertTrue(ts.getScale() > 0.0f, "Scale must be strictly positive: " + ts);
            assertTrue(ts.getScale() <= 1.0f, "Scale must not exceed 1.0: " + ts);
        }

        assertTrue(TypographyScale.DISPLAY.getScale() >= TypographyScale.HEADING.getScale(), "DISPLAY >= HEADING");
        assertTrue(TypographyScale.HEADING.getScale() >= TypographyScale.BODY.getScale(), "HEADING >= BODY");
        assertTrue(TypographyScale.BODY.getScale() >= TypographyScale.SMALL.getScale(), "BODY >= SMALL");
        assertTrue(TypographyScale.SMALL.getScale() >= TypographyScale.META.getScale(), "SMALL >= META");
    }

    @Test
    @DisplayName("TypographyScale: Verified specific scale values for density pass")
    void testSpecificScaleValues() {
        assertEquals(1.0f, TypographyScale.DISPLAY.getScale(), 0.001f);
        assertTrue(TypographyScale.HEADING.getScale() >= 0.95f);
        assertTrue(TypographyScale.BODY.getScale() >= 0.85f && TypographyScale.BODY.getScale() <= 0.92f);
        assertTrue(TypographyScale.SMALL.getScale() >= 0.80f && TypographyScale.SMALL.getScale() <= 0.87f);
        assertTrue(TypographyScale.META.getScale() >= 0.75f && TypographyScale.META.getScale() <= 0.85f);
    }
}