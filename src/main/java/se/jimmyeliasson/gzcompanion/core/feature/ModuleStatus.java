package se.jimmyeliasson.gzcompanion.core.feature;

/**
 * Representation of individual mod module implementation and availability status.
 */
public enum ModuleStatus {
    AVAILABLE("Tillgänglig", 0x22C55E),
    COMING_SOON("Kommer snart", 0x64748B),
    DISABLED("Avaktiverad", 0xEF4444);

    private final String displayName;
    private final int rgbColor;

    ModuleStatus(String displayName, int rgbColor) {
        this.displayName = displayName;
        this.rgbColor = rgbColor;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getRgbColor() {
        return rgbColor;
    }
}