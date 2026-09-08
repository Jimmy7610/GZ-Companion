package se.jimmyeliasson.gzcompanion.gamezone.model;

import se.jimmyeliasson.gzcompanion.diagnostics.CompatibilityStatus;

public record CommandDefinition(
    String command,
    String description,
    String permission,
    CompatibilityStatus status
) {}
