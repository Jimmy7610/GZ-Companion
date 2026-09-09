package se.jimmyeliasson.gzcompanion.guide.model;

/**
 * Manifest entry for an individual guide file.
 */
public record GuideHeader(
    String id,
    String file,
    String title,
    String description
) {}