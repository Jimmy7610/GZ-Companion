package se.jimmyeliasson.gzcompanion.guide.model;

/**
 * Explicit semantic progression state for a guide step.
 */
public enum GuideStepState {
    LOCKED("Låst", false),
    AVAILABLE("Tillgänglig", false),
    ACTIVE("Aktiv", false),
    COMPLETED_MANUAL("Klar (Manuell)", true),
    COMPLETED_AUTO("Klar (Automatisk)", true),
    SATISFIED_BY_LATER_PROGRESS("Klar (Senare framsteg)", true);

    private final String displayName;
    private final boolean completed;

    GuideStepState(String displayName, boolean completed) {
        this.displayName = displayName;
        this.completed = completed;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isCompleted() {
        return completed;
    }
}