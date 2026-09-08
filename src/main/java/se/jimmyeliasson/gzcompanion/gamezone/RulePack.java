package se.jimmyeliasson.gzcompanion.gamezone;

import se.jimmyeliasson.gzcompanion.gamezone.model.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Immutable in-memory representation of loaded GameZone Rule Pack.
 */
public record RulePack(
    RulePackManifest manifest,
    Map<String, Boolean> featureFlags,
    List<CommandDefinition> commands,
    List<GuideDefinition> guides,
    List<WorldRule> worldRules,
    List<ParserRule> parsers,
    List<String> loadWarnings
) {
    public static RulePack empty(String warning) {
        RulePackManifest fallbackManifest = new RulePackManifest(
            "1.0.0", "gamezone", "0.0.0-fallback", "Empty Pack", "Fallback",
            "*", List.of("26.1.2"), "play.gamezonemc.se",
            new RulePackManifest.VerificationInfo("Fallback", "", se.jimmyeliasson.gzcompanion.diagnostics.CompatibilityStatus.UNKNOWN),
            Collections.emptyMap()
        );
        return new RulePack(
            fallbackManifest,
            Collections.emptyMap(),
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList(),
            warning != null ? List.of(warning) : Collections.emptyList()
        );
    }
}