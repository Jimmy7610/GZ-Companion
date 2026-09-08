package se.jimmyeliasson.gzcompanion.diagnostics;

/**
 * Compatibility and verification status representations.
 */
public enum CompatibilityStatus {
    COMPATIBLE("Kompatibel", 0x22C55E, 0xFF22C55E),
    VERIFIED("Verifierad", 0x22C55E, 0xFF22C55E),
    WARNING("Varning", 0xF59E0B, 0xFFF59E0B),
    UNVERIFIED("Overifierad", 0xF59E0B, 0xFFF59E0B),
    STALE("Inaktuell", 0xF59E0B, 0xFFF59E0B),
    INCOMPATIBLE("Inkompatibel", 0xEF4444, 0xFFEF4444),
    UNAVAILABLE("Ej tillg\u00E4nglig", 0x64748B, 0xFF64748B),
    UNKNOWN("Ok\u00E4nd", 0x94A3B8, 0xFF94A3B8);

    private final String displayName;
    private final int rgbColor;
    private final int argbColor;

    CompatibilityStatus(String displayName, int rgbColor, int argbColor) {
        this.displayName = displayName;
        this.rgbColor = rgbColor;
        this.argbColor = argbColor;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getRgbColor() {
        return rgbColor;
    }

    public int getArgbColor() {
        return argbColor;
    }

    public boolean isGood() {
        return this == COMPATIBLE || this == VERIFIED;
    }

    public boolean isWarning() {
        return this == WARNING || this == UNVERIFIED || this == STALE;
    }

    public boolean isError() {
        return this == INCOMPATIBLE;
    }
}