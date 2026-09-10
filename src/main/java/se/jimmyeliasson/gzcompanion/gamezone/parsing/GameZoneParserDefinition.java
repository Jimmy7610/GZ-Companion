package se.jimmyeliasson.gzcompanion.gamezone.parsing;

import se.jimmyeliasson.gzcompanion.gamezone.events.GameZoneEventType;
import se.jimmyeliasson.gzcompanion.knowledge.common.VerificationMetadata;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * One Rule Pack-declared chat/event parser rule. Reuses M4's {@link VerificationMetadata} - a
 * parser is only ever activated ({@link #isActive()}) when it is both explicitly {@code enabled}
 * AND carries {@link se.jimmyeliasson.gzcompanion.knowledge.common.VerificationStatus#VERIFIED}.
 * This double gate means a data-entry mistake (enabling an unverified guess) can never silently
 * activate a real pattern - "better inactive than false-positive."
 *
 * <p>For {@code REGEX} match type, {@code pattern} is compiled once at load time
 * ({@link GameZoneParserLoader}) - a malformed regex is rejected there, never at match time.
 */
public record GameZoneParserDefinition(
    String id,
    GameZoneEventType eventType,
    ParserMatchType matchType,
    String pattern,
    List<String> captureGroupNames,
    boolean enabled,
    VerificationMetadata verification
) {
    public GameZoneParserDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(matchType, "matchType");
        Objects.requireNonNull(pattern, "pattern");
        captureGroupNames = captureGroupNames != null ? List.copyOf(captureGroupNames) : List.of();
        verification = verification != null ? verification : VerificationMetadata.UNVERIFIED_DEFAULT;
    }

    public boolean isActive() {
        return enabled && verification.status() == se.jimmyeliasson.gzcompanion.knowledge.common.VerificationStatus.VERIFIED;
    }

    /** Only meaningful for {@code REGEX} - validated once here so callers never re-validate per message. */
    public boolean hasValidRegexSyntax() {
        if (matchType != ParserMatchType.REGEX) return true;
        try {
            Pattern.compile(pattern);
            return true;
        } catch (PatternSyntaxException e) {
            return false;
        }
    }
}
