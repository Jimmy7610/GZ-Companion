package se.jimmyeliasson.gzcompanion.guide.model;

import java.util.List;

/**
 * Versioned manifest cataloging available guide content.
 */
public record GuideManifest(
    int schemaVersion,
    String contentVersion,
    String locale,
    List<String> testedMinecraftVersions,
    List<GuideHeader> guides
) {
    public GuideManifest {
        if (testedMinecraftVersions == null) testedMinecraftVersions = List.of();
        if (guides == null) guides = List.of();
    }
}