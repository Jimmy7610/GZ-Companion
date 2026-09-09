package se.jimmyeliasson.gzcompanion.ui;

import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;

/**
 * Contract for a tab component that owns a focusable text input (a search field, a rename
 * editor, etc). Lets {@link GZCompanionMainScreen} route the G-close shortcut, key presses and
 * typed characters to whichever tab is active without hardcoding a single tab type - this is
 * the exact isTextInputFocused/keyPressed/charTyped contract KistorTabComponent already proved
 * out for the M3 "G shouldn't close Companion while typing" fix.
 */
public interface TextInputHandler {
    boolean isTextInputFocused();

    boolean keyPressed(KeyEvent event);

    boolean charTyped(CharacterEvent event);
}
