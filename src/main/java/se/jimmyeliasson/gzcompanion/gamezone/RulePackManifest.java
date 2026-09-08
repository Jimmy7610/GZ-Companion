package se.jimmyeliasson.gzcompanion.gamezone;

import se.jimmyeliasson.gzcompanion.diagnostics.CompatibilityStatus;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Parsed manifest from manifest.json.
 */
public record RulePackManifest(
    String schemaVersion,
    String profile,
    String packVersion,
    String name,
    String author,
    String compatibleCompanionVersions,
    List<String> testedMinecraftVersions,
    String targetHost,
    VerificationInfo verification,
    Map<String, ModuleInfo> modules
) {
    public record VerificationInfo(String source, String lastVerified, CompatibilityStatus status) {}
    public record ModuleInfo(String file, CompatibilityStatus status) {}

    public List<String> testedMinecraftVersions() {
        return testedMinecraftVersions != null ? Collections.unmodifiableList(testedMinecraftVersions) : Collections.emptyList();
    }

    public Map<String, ModuleInfo> modules() {
        return modules != null ? Collections.unmodifiableMap(modules) : Collections.emptyMap();
    }
}