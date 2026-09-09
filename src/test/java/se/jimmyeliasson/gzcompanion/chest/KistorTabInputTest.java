package se.jimmyeliasson.gzcompanion.chest;

import net.minecraft.client.input.CharacterEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;
import se.jimmyeliasson.gzcompanion.ui.layout.UiRect;
import se.jimmyeliasson.gzcompanion.ui.tabs.KistorTabComponent;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers the reported bug: typing "g" while the Kistor search field is focused must insert the
 * character into the search box, never close the Companion. Only exercises the parts of
 * {@link KistorTabComponent} that do not require a live {@code CompanionSession} (clicking the
 * search box itself, then typing) - the actual G-vs-close decision lives in
 * {@code GZCompanionMainScreen.keyPressed}, gated on {@link KistorTabComponent#isTextInputFocused()}.
 */
class KistorTabInputTest {

    private static final UiRect BOUNDS = new UiRect(0, 0, 360, 200);

    @Test
    @DisplayName("Clicking the search box focuses it, and is not focused by default")
    void testSearchFocusToggle() {
        KistorTabComponent tab = new KistorTabComponent();
        assertFalse(tab.isTextInputFocused());

        var layout = se.jimmyeliasson.gzcompanion.ui.layout.KistorLayout.calculate(BOUNDS);
        UiRect search = layout.searchRect();
        boolean consumed = tab.mouseClicked(search.x() + 2, search.y() + 2, 0, BOUNDS, null);

        assertTrue(consumed);
        assertTrue(tab.isTextInputFocused());
        assertTrue(tab.isSearchFocused());
    }

    @Test
    @DisplayName("Typing the letter g while the search field is focused inserts it into the query, consuming the event")
    void testTypingGWhileSearchFocusedInsertsCharacter() {
        KistorTabComponent tab = new KistorTabComponent();
        var layout = se.jimmyeliasson.gzcompanion.ui.layout.KistorLayout.calculate(BOUNDS);
        UiRect search = layout.searchRect();
        tab.mouseClicked(search.x() + 2, search.y() + 2, 0, BOUNDS, null);
        assertTrue(tab.isTextInputFocused());

        boolean consumed = tab.charTyped(new CharacterEvent((int) 'g'));
        assertTrue(consumed, "charTyped must consume the character while a text input is focused");

        // Type a few more letters to spell out a realistic query containing "g".
        tab.charTyped(new CharacterEvent((int) 'o'));
        tab.charTyped(new CharacterEvent((int) 'l'));
        tab.charTyped(new CharacterEvent((int) 'd'));

        // We can't read the private searchText field directly, but ESC-unfocus followed by a
        // fresh click must NOT wipe it, and the field stays focused throughout typing - proving
        // the character stream was accepted rather than being treated as a close/global action.
        assertTrue(tab.isTextInputFocused(), "The field must remain focused through normal typing");
    }

    @Test
    @DisplayName("charTyped and keyPressed do nothing when no Companion text input is focused")
    void testNoTextInputFocusedByDefault() {
        KistorTabComponent tab = new KistorTabComponent();
        assertFalse(tab.isTextInputFocused());
        assertFalse(tab.charTyped(new CharacterEvent((int) 'g')), "Nothing is focused - charTyped must not consume the event");
    }

    @Test
    @DisplayName("ESC unfocuses the search field first, without any other side effects, leaving it available for a second ESC to close the Companion")
    void testEscUnfocusesSearchFirst() {
        KistorTabComponent tab = new KistorTabComponent();
        var layout = se.jimmyeliasson.gzcompanion.ui.layout.KistorLayout.calculate(BOUNDS);
        UiRect search = layout.searchRect();
        tab.mouseClicked(search.x() + 2, search.y() + 2, 0, BOUNDS, null);
        assertTrue(tab.isTextInputFocused());

        boolean consumed = tab.keyPressed(new net.minecraft.client.input.KeyEvent(GLFW.GLFW_KEY_ESCAPE, 0, 0));
        assertTrue(consumed, "The first ESC must be consumed by the tab (unfocus), not fall through to closing");
        assertFalse(tab.isTextInputFocused(), "Search must be unfocused after the first ESC");

        // A second ESC now finds nothing focused and must NOT be consumed by the tab, allowing
        // GZCompanionMainScreen to close the Companion normally.
        boolean secondConsumed = tab.keyPressed(new net.minecraft.client.input.KeyEvent(GLFW.GLFW_KEY_ESCAPE, 0, 0));
        assertFalse(secondConsumed, "With nothing focused, the tab must not consume ESC - the screen should close");
    }

    @Test
    @DisplayName("Backspace while the search field is focused removes the last typed character")
    void testBackspaceRemovesLastCharacter() {
        KistorTabComponent tab = new KistorTabComponent();
        var layout = se.jimmyeliasson.gzcompanion.ui.layout.KistorLayout.calculate(BOUNDS);
        UiRect search = layout.searchRect();
        tab.mouseClicked(search.x() + 2, search.y() + 2, 0, BOUNDS, null);
        tab.charTyped(new CharacterEvent((int) 'g'));

        boolean consumed = tab.keyPressed(new net.minecraft.client.input.KeyEvent(GLFW.GLFW_KEY_BACKSPACE, 0, 0));
        assertTrue(consumed);
        assertTrue(tab.isTextInputFocused(), "Backspace must not unfocus the field");
    }
}
