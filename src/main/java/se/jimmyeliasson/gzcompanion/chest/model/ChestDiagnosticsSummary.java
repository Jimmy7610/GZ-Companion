package se.jimmyeliasson.gzcompanion.chest.model;

/**
 * Safe, non-sensitive Chest Manager diagnostics: status, schema version, and a container count
 * for the current context ONLY. Deliberately excludes item contents, coordinates, and labels —
 * those are private local player data and must never appear in generic diagnostics output.
 */
public record ChestDiagnosticsSummary(ChestManagerStatus status, int schemaVersion, int indexedContainerCount) {
    public String toSafeString() {
        return "Kistor: " + status.getDisplayName() + " (schema v" + schemaVersion + ", " + indexedContainerCount + " indexerade)";
    }
}
