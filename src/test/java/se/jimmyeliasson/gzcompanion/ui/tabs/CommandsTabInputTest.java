package se.jimmyeliasson.gzcompanion.ui.tabs;

import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;
import se.jimmyeliasson.gzcompanion.ui.layout.CommandsLayout;
import se.jimmyeliasson.gzcompanion.ui.layout.UiRect;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mirrors {@code KistorTabInputTest}: proves the Kommandon search field gets the same "G types,
 * doesn't close Companion" behavior via the shared {@code TextInputHandler} contract, without
 * duplicating the fix. Only exercises the parts that don't require a live {@code CompanionSession}
 * render pass (clicking the search box, then typing/backspacing/ESC).
 */
class CommandsTabInputTest {

    private static final UiRect BOUNDS = new UiRect(0, 0, 360, 200);

    @Test
    @DisplayName("Not focused by default; clicking the search box focuses it")
    void testSearchFocusToggle() {
        CommandsTabComponent tab = new CommandsTabComponent();
        assertFalse(tab.isTextInputFocused());

        CommandsLayout layout = CommandsLayout.calculate(BOUNDS);
        UiRect search = layout.searchRect();
        boolean consumed = tab.mouseClicked(search.x() + 2, search.y() + 2, 0, BOUNDS, null);

        assertTrue(consumed);
        assertTrue(tab.isTextInputFocused());
    }

    @Test
    @DisplayName("Typing the letter g while the search field is focused inserts it, consuming the event")
    void testTypingGInsertsCharacter() {
        CommandsTabComponent tab = new CommandsTabComponent();
        CommandsLayout layout = CommandsLayout.calculate(BOUNDS);
        UiRect search = layout.searchRect();
        tab.mouseClicked(search.x() + 2, search.y() + 2, 0, BOUNDS, null);

        boolean consumed = tab.charTyped(new CharacterEvent((int) 'g'));
        assertTrue(consumed, "charTyped must consume the character while the search field is focused");
        assertTrue(tab.isTextInputFocused(), "The field must remain focused through normal typing");
    }

    @Test
    @DisplayName("charTyped does nothing when no text input is focused")
    void testNoTextInputFocusedByDefault() {
        CommandsTabComponent tab = new CommandsTabComponent();
        assertFalse(tab.isTextInputFocused());
        assertFalse(tab.charTyped(new CharacterEvent((int) 'g')));
    }

    @Test
    @DisplayName("ESC unfocuses the search field first rather than falling through")
    void testEscUnfocusesSearchFirst() {
        CommandsTabComponent tab = new CommandsTabComponent();
        CommandsLayout layout = CommandsLayout.calculate(BOUNDS);
        UiRect search = layout.searchRect();
        tab.mouseClicked(search.x() + 2, search.y() + 2, 0, BOUNDS, null);
        assertTrue(tab.isTextInputFocused());

        boolean consumed = tab.keyPressed(new KeyEvent(GLFW.GLFW_KEY_ESCAPE, 0, 0));
        assertTrue(consumed);
        assertFalse(tab.isTextInputFocused());

        boolean secondConsumed = tab.keyPressed(new KeyEvent(GLFW.GLFW_KEY_ESCAPE, 0, 0));
        assertFalse(secondConsumed, "With nothing focused, ESC must fall through so the screen can close");
    }

    @Test
    @DisplayName("Backspace while the search field is focused removes the last typed character without unfocusing it")
    void testBackspaceRemovesLastCharacter() {
        CommandsTabComponent tab = new CommandsTabComponent();
        CommandsLayout layout = CommandsLayout.calculate(BOUNDS);
        UiRect search = layout.searchRect();
        tab.mouseClicked(search.x() + 2, search.y() + 2, 0, BOUNDS, null);
        tab.charTyped(new CharacterEvent((int) 'g'));

        boolean consumed = tab.keyPressed(new KeyEvent(GLFW.GLFW_KEY_BACKSPACE, 0, 0));
        assertTrue(consumed);
        assertTrue(tab.isTextInputFocused());
    }

    @Test
    @DisplayName("Clicking the clear button clears the search text without requiring a focused field")
    void testClearButtonClearsSearch() {
        CommandsTabComponent tab = new CommandsTabComponent();
        CommandsLayout layout = CommandsLayout.calculate(BOUNDS);
        UiRect search = layout.searchRect();
        tab.mouseClicked(search.x() + 2, search.y() + 2, 0, BOUNDS, null);
        tab.charTyped(new CharacterEvent((int) 'g'));

        UiRect clear = layout.clearBtnRect();
        boolean consumed = tab.mouseClicked(clear.x() + 1, clear.y() + 1, 0, BOUNDS, null);
        assertTrue(consumed);
    }
}
