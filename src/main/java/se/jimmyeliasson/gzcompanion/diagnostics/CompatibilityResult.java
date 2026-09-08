package se.jimmyeliasson.gzcompanion.diagnostics;

import java.util.Collections;
import java.util.List;

/**
 * Overall system compatibility evaluation.
 */
public record CompatibilityResult(
    CompatibilityStatus overallStatus,
    String summaryMessage,
    List<ModuleReport> moduleReports
) {
    public List<ModuleReport> moduleReports() {
        return moduleReports != null ? Collections.unmodifiableList(moduleReports) : Collections.emptyList();
    }
}