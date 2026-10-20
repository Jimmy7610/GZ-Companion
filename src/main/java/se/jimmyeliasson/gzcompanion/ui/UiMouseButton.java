package se.jimmyeliasson.gzcompanion.ui;

import com.mojang.blaze3d.platform.InputConstants;

/**
 * Companion's own, version-independent mouse button numbering used by every tab's
 * {@code mouseClicked(..., int button, ...)}: {@link #LEFT} = 0, {@link #RIGHT} = 1, {@link #MIDDLE} = 2.
 *
 * <p>Minecraft's own numbering is NOT stable across versions: up to 26.1.2 (GLFW) left was 0, while
 * 26.3 (SDL) numbers buttons from 1 ({@link InputConstants#MOUSE_BUTTON_LEFT} = 1, right = 3). Every
 * click is therefore translated exactly once, at the Minecraft boundary
 * ({@code GZCompanionMainScreen#mouseClicked}), so no tab ever compares against a raw Minecraft value.
 */
public final class UiMouseButton {
    public static final int LEFT = 0;
    public static final int RIGHT = 1;
    public static final int MIDDLE = 2;
    /** Any other (extra/side) button - never treated as a click by the tabs. */
    public static final int OTHER = -1;

    private UiMouseButton() {}

    /** Translates a Minecraft {@code MouseButtonEvent#button()} value into Companion's numbering. */
    public static int fromMinecraft(int minecraftButton) {
        return switch (minecraftButton) {
            case InputConstants.MOUSE_BUTTON_LEFT -> LEFT;
            case InputConstants.MOUSE_BUTTON_RIGHT -> RIGHT;
            case InputConstants.MOUSE_BUTTON_MIDDLE -> MIDDLE;
            default -> OTHER;
        };
    }
}
