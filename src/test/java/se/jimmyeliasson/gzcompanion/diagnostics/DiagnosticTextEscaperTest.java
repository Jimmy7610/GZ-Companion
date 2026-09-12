package se.jimmyeliasson.gzcompanion.diagnostics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure escaping logic - no Minecraft dependency, so this is fully unit-testable. Exists to verify
 * the helper backing the Online tab's TEMPORARY "Diagnostik" panel makes every invisible/unusual
 * character in a raw TAB string visible, without disturbing ordinary readable text.
 */
class DiagnosticTextEscaperTest {

    @Test
    @DisplayName("Newline is escaped as \\n")
    void escapesNewline() {
        assertEquals("a\\nb", DiagnosticTextEscaper.escape("a\nb"));
    }

    @Test
    @DisplayName("Tab is escaped as \\t")
    void escapesTab() {
        assertEquals("a\\tb", DiagnosticTextEscaper.escape("a\tb"));
    }

    @Test
    @DisplayName("Non-breaking space (U+00A0) is escaped as \\u00A0")
    void escapesNonBreakingSpace() {
        assertEquals("a\\u00A0b", DiagnosticTextEscaper.escape("a b"));
    }

    @Test
    @DisplayName("Zero-width space (U+200B) is escaped as \\u200B")
    void escapesZeroWidthSpace() {
        assertEquals("a\\u200Bb", DiagnosticTextEscaper.escape("a​b"));
    }

    @Test
    @DisplayName("Normal Swedish characters (å/ä/ö) remain readable, never escaped")
    void swedishCharactersRemainReadable() {
        assertEquals("Trälskärsbukten", DiagnosticTextEscaper.escape("Trälskärsbukten"));
    }

    @Test
    @DisplayName("Ordinary visible symbols/glyphs remain readable, never escaped")
    void symbolsRemainReadable() {
        assertEquals("[TRA] ⚜ jbl76 [2]", DiagnosticTextEscaper.escape("[TRA] ⚜ jbl76 [2]"));
    }

    @Test
    @DisplayName("A plain ASCII space is never escaped")
    void plainSpaceRemainsReadable() {
        assertEquals("a b", DiagnosticTextEscaper.escape("a b"));
    }

    @Test
    @DisplayName("Null input is safely represented, never throws")
    void nullIsSafelyRepresented() {
        assertDoesNotThrow(() -> DiagnosticTextEscaper.escape(null));
        assertEquals("null", DiagnosticTextEscaper.escape(null));
    }

    @Test
    @DisplayName("A carriage return is escaped as \\r")
    void escapesCarriageReturn() {
        assertEquals("a\\rb", DiagnosticTextEscaper.escape("a\rb"));
    }

    @Test
    @DisplayName("A realistic mixed string with hidden characters is fully readable once escaped")
    void realisticMixedStringIsFullyReadable() {
        String raw = "[TRA] ⚜ jbl76 [2]";
        assertEquals("[TRA]\\u00A0⚜\\u00A0jbl76 [2]", DiagnosticTextEscaper.escape(raw));
    }
}
