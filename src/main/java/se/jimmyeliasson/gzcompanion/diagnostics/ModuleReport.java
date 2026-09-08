package se.jimmyeliasson.gzcompanion.diagnostics;

/**
 * Compatibility status for an individual module or feature area.
 */
public record ModuleReport(String moduleId, String moduleName, CompatibilityStatus status, String details) {
    public static ModuleReport of(String moduleId, String moduleName, CompatibilityStatus status, String details) {
        return new ModuleReport(moduleId, moduleName, status, details != null ? details : "");
    }
}