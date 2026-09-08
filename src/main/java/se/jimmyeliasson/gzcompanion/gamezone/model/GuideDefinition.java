package se.jimmyeliasson.gzcompanion.gamezone.model;

import se.jimmyeliasson.gzcompanion.diagnostics.CompatibilityStatus;
import java.util.List;

public record GuideDefinition(
    String id,
    String title,
    String category,
    List<GuideStep> steps
) {
    public record GuideStep(int stepNumber, String title, String body, CompatibilityStatus status) {}
}
