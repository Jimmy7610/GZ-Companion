package se.jimmyeliasson.gzcompanion.gamezone.parsing;

import se.jimmyeliasson.gzcompanion.gamezone.events.GameZoneObservedEvent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure, stateless matching logic - no Minecraft types, no I/O, no side effects. Given a message
 * string the client already legitimately received and a list of active parsers, returns at most
 * one {@link GameZoneObservedEvent} (the first active parser that matches, in list order).
 *
 * <p>Never mutates the input message. Never used to cancel, rewrite, or hide anything - callers
 * only ever read the match result to optionally show a local toast.
 */
public final class GameZoneParserEngine {

    private GameZoneParserEngine() {}

    public static Optional<GameZoneObservedEvent> match(List<GameZoneParserDefinition> activeParsers, String message, long nowMs) {
        if (activeParsers == null || activeParsers.isEmpty() || message == null) {
            return Optional.empty();
        }
        for (GameZoneParserDefinition parser : activeParsers) {
            Map<String, String> captured = tryMatch(parser, message);
            if (captured != null) {
                return Optional.of(new GameZoneObservedEvent(parser.eventType(), parser.id(), captured, nowMs));
            }
        }
        return Optional.empty();
    }

    /** Returns the captured values map (possibly empty) if {@code parser} matches, or {@code null} if it doesn't. */
    private static Map<String, String> tryMatch(GameZoneParserDefinition parser, String message) {
        return switch (parser.matchType()) {
            case EXACT -> message.trim().equals(parser.pattern()) ? Map.of() : null;
            case CONTAINS -> message.contains(parser.pattern()) ? Map.of() : null;
            case REGEX -> matchRegex(parser, message);
        };
    }

    private static Map<String, String> matchRegex(GameZoneParserDefinition parser, String message) {
        try {
            Pattern compiled = Pattern.compile(parser.pattern());
            Matcher matcher = compiled.matcher(message);
            if (!matcher.find()) return null;

            Map<String, String> captured = new LinkedHashMap<>();
            List<String> names = parser.captureGroupNames();
            for (int i = 0; i < names.size(); i++) {
                int groupIndex = i + 1;
                if (groupIndex <= matcher.groupCount()) {
                    String value = matcher.group(groupIndex);
                    captured.put(names.get(i), value != null ? value : "");
                }
            }
            return captured;
        } catch (Exception ignored) {
            // A regex that fails at match time (should already be pre-validated at load time,
            // but defensive here too) must never break the observer.
            return null;
        }
    }
}
