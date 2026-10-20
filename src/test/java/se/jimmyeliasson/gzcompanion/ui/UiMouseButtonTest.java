package se.jimmyeliasson.gzcompanion.ui;

import com.mojang.blaze3d.platform.InputConstants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UiMouseButtonTest {

    @Test
    @DisplayName("Minecraft 26.3 numbers mouse buttons from 1 (SDL) - the reason clicks must be translated")
    void minecraftNumberingIsOneBased() {
        assertEquals(1, InputConstants.MOUSE_BUTTON_LEFT);
        assertEquals(2, InputConstants.MOUSE_BUTTON_MIDDLE);
        assertEquals(3, InputConstants.MOUSE_BUTTON_RIGHT);
    }

    @Test
    @DisplayName("Minecraft buttons translate to Companion's left = 0, right = 1, middle = 2")
    void translatesMinecraftButtons() {
        assertEquals(UiMouseButton.LEFT, UiMouseButton.fromMinecraft(InputConstants.MOUSE_BUTTON_LEFT));
        assertEquals(UiMouseButton.RIGHT, UiMouseButton.fromMinecraft(InputConstants.MOUSE_BUTTON_RIGHT));
        assertEquals(UiMouseButton.MIDDLE, UiMouseButton.fromMinecraft(InputConstants.MOUSE_BUTTON_MIDDLE));
        assertEquals(0, UiMouseButton.LEFT, "every tab treats 0 as a left click");
    }

    @Test
    @DisplayName("The old GLFW left-button value 0 and extra buttons are never treated as a left click")
    void unknownButtonsAreNotClicks() {
        assertEquals(UiMouseButton.OTHER, UiMouseButton.fromMinecraft(0));
        assertEquals(UiMouseButton.OTHER, UiMouseButton.fromMinecraft(4));
        assertEquals(UiMouseButton.OTHER, UiMouseButton.fromMinecraft(-1));
    }
}
