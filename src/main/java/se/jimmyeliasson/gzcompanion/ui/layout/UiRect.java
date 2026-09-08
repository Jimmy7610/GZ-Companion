package se.jimmyeliasson.gzcompanion.ui.layout;

/**
 * Immutable 2D rectangle primitive for layout calculation, rendering bounds, and hit testing.
 */
public record UiRect(int x, int y, int width, int height) {
    public int right() {
        return x + width;
    }

    public int bottom() {
        return y + height;
    }

    public boolean contains(double px, double py) {
        return px >= x && px < x + width && py >= y && py < y + height;
    }

    public UiRect inset(int dx, int dy) {
        return new UiRect(x + dx, y + dy, Math.max(0, width - (dx * 2)), Math.max(0, height - (dy * 2)));
    }

    public UiRect withX(int newX) {
        return new UiRect(newX, y, width, height);
    }

    public UiRect withY(int newY) {
        return new UiRect(x, newY, width, height);
    }

    public UiRect withWidth(int newWidth) {
        return new UiRect(x, y, newWidth, height);
    }

    public UiRect withHeight(int newHeight) {
        return new UiRect(x, y, width, newHeight);
    }
}