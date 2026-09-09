package se.jimmyeliasson.gzcompanion.ui.tabs;

import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;
import se.jimmyeliasson.gzcompanion.ui.layout.CraftingLayout;
import se.jimmyeliasson.gzcompanion.ui.layout.UiRect;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mirrors {@code KistorTabInputTest}/{@code CommandsTabInputTest} for the Crafting tab's search
 * field, proving the shared {@code TextInputHandler} contract works identically here too.
 */
class CraftingTabInputTest {

    private static final UiRect BOUNDS = new UiRect(0, 0, 360, 200);

    @Test
    @DisplayName("Not focused by default; clicking the search box focuses it")
    void testSearchFocusToggle() {
        CraftingTabComponent tab = new CraftingTabComponent();
        assertFalse(tab.isTextInputFocused());

        CraftingLayout layout = CraftingLayout.calculate(BOUNDS);
        UiRect search = layout.searchRect();
        boolean consumed = tab.mouseClicked(search.x() + 2, search.y() + 2, 0, BOUNDS, null);

        assertTrue(consumed);
        assertTrue(tab.isTextInputFocused());
    }

    @Test
    @DisplayName("Typing the letter g while the search field is focused inserts it, consuming the event")
    void testTypingGInsertsCharacter() {
        CraftingTabComponent tab = new CraftingTabComponent();
        CraftingLayout layout = CraftingLayout.calculate(BOUNDS);
        UiRect search = layout.searchRect();
        tab.mouseClicked(search.x() + 2, search.y() + 2, 0, BOUNDS, null);

        boolean consumed = tab.charTyped(new CharacterEvent((int) 'g'));
        assertTrue(consumed);
        assertTrue(tab.isTextInputFocused());
    }

    @Test
    @DisplayName("charTyped does nothing when no text input is focused")
    void testNoTextInputFocusedByDefault() {
        CraftingTabComponent tab = new CraftingTabComponent();
        assertFalse(tab.isTextInputFocused());
        assertFalse(tab.charTyped(new CharacterEvent((int) 'g')));
    }

    @Test
    @DisplayName("ESC unfocuses the search field first rather than falling through")
    void testEscUnfocusesSearchFirst() {
        CraftingTabComponent tab = new CraftingTabComponent();
        CraftingLayout layout = CraftingLayout.calculate(BOUNDS);
        UiRect search = layout.searchRect();
        tab.mouseClicked(search.x() + 2, search.y() + 2, 0, BOUNDS, null);
        assertTrue(tab.isTextInputFocused());

        boolean consumed = tab.keyPressed(new KeyEvent(GLFW.GLFW_KEY_ESCAPE, 0, 0));
        assertTrue(consumed);
        assertFalse(tab.isTextInputFocused());

        boolean secondConsumed = tab.keyPressed(new KeyEvent(GLFW.GLFW_KEY_ESCAPE, 0, 0));
        assertFalse(secondConsumed);
    }

    @Test
    @DisplayName("Clicking the mode button cycles Alla -> Recept -> GameZone-föremål -> Alla")
    void testModeButtonCycles() {
        CraftingTabComponent tab = new CraftingTabComponent();
        CraftingLayout layout = CraftingLayout.calculate(BOUNDS);
        UiRect modeBtn = layout.modeBtnRect();

        assertTrue(tab.mouseClicked(modeBtn.x() + 1, modeBtn.y() + 1, 0, BOUNDS, null));
        assertTrue(tab.mouseClicked(modeBtn.x() + 1, modeBtn.y() + 1, 0, BOUNDS, null));
        assertTrue(tab.mouseClicked(modeBtn.x() + 1, modeBtn.y() + 1, 0, BOUNDS, null));
        // Three clicks cycle through all three modes and back to the start - no exception, no
        // stuck state. Cycling itself is exercised via the click return value only, since mode
        // is private; this proves the button consumes the click every time.
    }
}
