package se.jimmyeliasson.gzcompanion.guide.model;

/**
 * Tracks why and how a guide step was completed.
 */
public enum GuideCompletionSource {
    MANUAL("Markerad som klar"),
    INVENTORY_EVIDENCE("Automatiskt klar"),
    PROGRESSION_INFERENCE("Redan uppfyllt av senare framsteg");

    private final String displayName;

    GuideCompletionSource(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}