package se.jimmyeliasson.gzcompanion.gamezone.model;

import se.jimmyeliasson.gzcompanion.diagnostics.CompatibilityStatus;

public record ParserRule(
    String id,
    String pattern,
    String target,
    CompatibilityStatus status
) {}
