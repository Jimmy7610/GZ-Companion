package se.jimmyeliasson.gzcompanion.guide.model;

/**
 * Semantic runtime readiness status of the Guide Engine.
 */
public enum GuideLoadStatus {
    LOADED("Laddad", true),
    UNAVAILABLE("Ej tillgänglig", false),
    ERROR("Fel vid inläsning", false),
    INCOMPATIBLE("Inkompatibel version", false);

    private final String displayName;
    private final boolean available;

    GuideLoadStatus(String displayName, boolean available) {
        this.displayName = displayName;
        this.available = available;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isAvailable() {
        return available;
    }
}
