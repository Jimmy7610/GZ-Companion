package se.jimmyeliasson.gzcompanion.advisor;

/**
 * One ranked, explainable Advisor suggestion. {@code optionalCommand}, when present, is always
 * an already-VERIFIED command string meant for a copy-only clipboard action - the Advisor never
 * executes anything automatically.
 */
public record AdvisorSuggestion(String id, String title, String reason, String nextStep, String optionalCommand) {
    public AdvisorSuggestion(String id, String title, String reason, String nextStep) {
        this(id, title, reason, nextStep, null);
    }
}
