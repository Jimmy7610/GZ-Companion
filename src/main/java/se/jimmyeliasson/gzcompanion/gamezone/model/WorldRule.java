package se.jimmyeliasson.gzcompanion.gamezone.model;

import se.jimmyeliasson.gzcompanion.diagnostics.CompatibilityStatus;

public record WorldRule(
    String id,
    String name,
    String description,
    CompatibilityStatus status
) {}
