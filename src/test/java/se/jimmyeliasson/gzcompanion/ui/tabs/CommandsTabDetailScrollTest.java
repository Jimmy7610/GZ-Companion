package se.jimmyeliasson.gzcompanion.ui.tabs;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import se.jimmyeliasson.gzcompanion.ui.layout.UiRect;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers the null-safety guards of {@code CommandsTabComponent.calculateMaxDetailScroll} (the
 * Font-dependent pixel-height math itself is not unit tested here, consistent with this
 * codebase's existing boundary - no test anywhere touches a real {@code Font} instance, since
 * Font metrics require a running Minecraft client).
 */
class CommandsTabDetailScrollTest {

    @Test
    @DisplayName("calculateMaxDetailScroll returns 0 when the font is unavailable")
    void testReturnsZeroWithNullFont() {
        CommandsTabComponent tab = new CommandsTabComponent();
        int result = tab.calculateMaxDetailScroll(null, new UiRect(0, 0, 100, 100), null);
        assertEquals(0, result);
    }

    @Test
    @DisplayName("calculateMaxDetailScroll returns 0 when the content area is null")
    void testReturnsZeroWithNullContentArea() {
        CommandsTabComponent tab = new CommandsTabComponent();
        int result = tab.calculateMaxDetailScroll(null, null, null);
        assertEquals(0, result);
    }

    @Test
    @DisplayName("calculateMaxDetailScroll returns 0 when the command is null")
    void testReturnsZeroWithNullCommand() {
        CommandsTabComponent tab = new CommandsTabComponent();
        int result = tab.calculateMaxDetailScroll(null, new UiRect(0, 0, 100, 100), null);
        assertEquals(0, result);
    }
}
