package se.jimmyeliasson.gzcompanion.gamezone.settlement;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure, stateless parsing of GameZone's settlement identity out of vanilla-visible TAB text - no
 * Minecraft types, no I/O, no side effects, so this is fully unit-testable without a live client.
 *
 * <p><b>Settlement name/role.</b> Human QA on the real GameZoneMC server found the TAB header
 * contains a line shaped like {@code "<settlement name> · <role> · <bonus>"} (e.g.
 * {@code "Trälskärsbukten · MEMBER · +44.3%"}). Rather than assuming a fixed line index (fragile -
 * GameZone could add/remove header lines above or below it), every header line is checked against
 * this structure and the first line that matches wins; unrelated lines are simply ignored. If no
 * line matches, or the format is ambiguous, the settlement is UNKNOWN - this parser never guesses.
 *
 * <p><b>Settlement prefix.</b> GameZone's wiki documents a "settlementprefix" as a distinct TAB
 * element (separate from culture sigil, King/Lord role indicator, and level), and real TAB lists
 * show it as a short bracketed code somewhere before a player's name (e.g. {@code [BUS]},
 * {@code [DDD]}, {@code [GRH]} - evidence of the FORMAT only, never hardcoded as meaningful values).
 * Real rows were observed to also carry one or more non-bracketed culture/role glyphs BETWEEN the
 * prefix and the name (e.g. {@code "[BUS] ✦ ♔ Jimmy"}), and a trailing bracketed level AFTER the
 * name (e.g. {@code "Jimmy [38]"}) - so this parser does not require the prefix to be immediately
 * adjacent to the username. Instead, it looks at everything BEFORE the player's known username
 * (never after - that's where a level lives) and searches that portion for bracketed alphanumeric
 * tokens shaped like {@code [A-Z0-9]{2,6}}. Exactly one such candidate is accepted as the prefix;
 * zero candidates, or more than one (an ambiguous read), both fail safely to UNKNOWN (null) rather
 * than guessing. Bare glyphs (culture sigils, role symbols) never match this bracketed shape, so
 * they're naturally never mistaken for a prefix.
 *
 * <p><b>Prefix uniqueness caveat.</b> A matching prefix between two players is only a same-moment,
 * same-session "GameZone rendered them with the same tag" signal - GameZone does not document this
 * prefix as a globally unique or permanent settlement identifier, so it is used here ONLY as a
 * live TAB grouping signal, never persisted as an eternal identity (see
 * {@link GameZoneSettlementTracker}).
 */
public final class GameZoneTabIdentityParser {
    private static final Pattern HEADER_LINE_PATTERN =
            Pattern.compile("^(.+?)\\s*\\u00B7\\s*(KING|LORD|MEMBER)\\s*(?:\\u00B7\\s*[+-]?\\d+(?:[.,]\\d+)?%)?$");
    private static final Pattern PREFIX_PATTERN = Pattern.compile("\\[([A-Za-z0-9]{2,6})\\]");

    private GameZoneTabIdentityParser() {}

    /**
     * Combines a header parse and a local-prefix extraction into one identity. Either half can
     * independently succeed or fail - a recognized settlement name with no recognizable prefix (or
     * vice versa) is possible and safe; {@link GameZoneSettlementIdentity#UNKNOWN} is returned only
     * when neither half yields anything.
     */
    public static GameZoneSettlementIdentity identityFor(String headerText, String localDisplayText, String localUsername) {
        ParsedHeader header = parseHeader(headerText);
        String prefix = extractPrefix(localDisplayText, localUsername);
        if (header == null && prefix == null) {
            return GameZoneSettlementIdentity.UNKNOWN;
        }
        return new GameZoneSettlementIdentity(
                header != null ? header.name() : null,
                header != null ? header.role() : null,
                prefix
        );
    }

    /**
     * True if {@code candidateDisplayText} carries the same settlement prefix token as
     * {@code localPrefix}. Never true when {@code localPrefix} is unknown - a missing local prefix
     * must never produce same-settlement guesses.
     */
    public static boolean isSameSettlement(String candidateDisplayText, String candidateUsername, String localPrefix) {
        if (localPrefix == null || localPrefix.isBlank()) return false;
        String candidatePrefix = extractPrefix(candidateDisplayText, candidateUsername);
        return candidatePrefix != null && candidatePrefix.equalsIgnoreCase(localPrefix);
    }

    /** Package-visible for direct unit testing of the header-only parse behavior. */
    static ParsedHeader parseHeader(String headerText) {
        if (headerText == null || headerText.isBlank()) return null;
        for (String rawLine : headerText.split("\\n", -1)) {
            String line = rawLine.trim();
            if (line.isEmpty()) continue;
            Matcher m = HEADER_LINE_PATTERN.matcher(line);
            if (!m.matches()) continue;
            String name = m.group(1).trim();
            String role = m.group(2);
            if (name.isEmpty()) continue;
            return new ParsedHeader(name, role);
        }
        return null;
    }

    /**
     * Package-visible for direct unit testing of the prefix-extraction behavior. Looks ONLY at the
     * text before {@code username} - a trailing {@code [LEVEL]} after the name is never considered.
     * Requires exactly one bracketed alphanumeric candidate in that leading portion; an ambiguous
     * (multiple candidates) or absent (zero candidates) read both fail safely to {@code null}.
     */
    static String extractPrefix(String displayText, String username) {
        if (displayText == null || username == null || username.isBlank()) return null;
        int idx = displayText.indexOf(username);
        if (idx < 0) {
            idx = displayText.toLowerCase(Locale.ROOT).indexOf(username.toLowerCase(Locale.ROOT));
        }
        if (idx <= 0) return null;

        String before = displayText.substring(0, idx);

        Matcher m = PREFIX_PATTERN.matcher(before);
        String candidate = null;
        while (m.find()) {
            if (candidate != null) {
                return null; // more than one bracketed candidate before the name - ambiguous, never guess
            }
            candidate = m.group(1);
        }
        return candidate != null ? candidate.toUpperCase(Locale.ROOT) : null;
    }

    record ParsedHeader(String name, String role) {}
}
