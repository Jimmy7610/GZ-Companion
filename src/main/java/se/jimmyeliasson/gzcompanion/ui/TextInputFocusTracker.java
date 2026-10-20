package se.jimmyeliasson.gzcompanion.ui;

import java.util.function.Consumer;

/**
 * Keeps Minecraft's text-input system in step with Companion's own text fields.
 *
 * <p>Since Minecraft 26.3 (SDL instead of GLFW) the window only delivers typed characters while text
 * input is explicitly started; vanilla widgets do that through
 * {@code Minecraft#onTextInputFocusChange(listener, focused)}. Companion's search fields, label
 * editors and coordinate fields are custom, so without this every {@code charTyped} would silently
 * stop arriving. The tracker remembers what it last reported and forwards only real changes, so
 * {@link #sync(boolean)} is cheap enough to call after every input event and once per frame.
 */
public final class TextInputFocusTracker {
    private final Consumer<Boolean> notifier;
    private boolean reported;

    /** @param notifier receives {@code true}/{@code false} exactly when the focused state changes. */
    public TextInputFocusTracker(Consumer<Boolean> notifier) {
        this.notifier = notifier;
    }

    /** Reports {@code focused} to Minecraft if it differs from the last reported state. */
    public void sync(boolean focused) {
        if (focused == reported) return;
        reported = focused;
        try {
            notifier.accept(focused);
        } catch (RuntimeException ignored) {
            // Never let the text-input bookkeeping break the screen; the next change retries.
        }
    }

    /** Releases text input (e.g. when the Companion screen closes). */
    public void release() {
        sync(false);
    }

    public boolean isReportedFocused() {
        return reported;
    }
}
