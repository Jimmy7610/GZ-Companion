package se.jimmyeliasson.gzcompanion.gamezone.status;

import se.jimmyeliasson.gzcompanion.gamezone.settlement.GameZoneTabIdentityParser;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure, stateless parsing of GameZone's live server/city/economy status out of the vanilla-visible
 * TAB header text - no Minecraft types, no I/O, no side effects, so this is fully unit-testable
 * without a live client. Every field is parsed and reported INDEPENDENTLY: a missing or malformed
 * line never invalidates fields found on other lines - see {@link GameZoneLiveStatus}.
 *
 * <p>Human QA captured this real header shape:
 * <pre>
 *           GAMEZONE MC
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 23/100 • TPS 20,0 • 10 - Småstad
 * Coins 65 068 • Stadskassa 11 487 272
 * Trälskärsbukten • MEMBER • +44.3%
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * </pre>
 * (the spaces inside the money line are real {@code U+00A0 NBSP}, not ordinary spaces). Every line
 * is scanned structurally rather than assumed to sit at a fixed index, exactly like
 * {@link GameZoneTabIdentityParser}.
 *
 * <p><b>Settlement name/role/bonus.</b> This parser does NOT re-implement settlement-line parsing -
 * it reuses {@link GameZoneTabIdentityParser#parseHeader(String)}, the single, human-QA-verified
 * definition of that line's shape (including both observed separators, {@code •} and {@code ·}),
 * so there is exactly one place that decides what counts as a valid settlement line.
 */
public final class GameZoneTabStatusParser {
    private static final String SEP = "[\\u00B7\\u2022]";
    private static final Pattern PLAYER_TPS_CITY_PATTERN = Pattern.compile(
            "^(\\d+)\\s*/\\s*(\\d+)\\s*" + SEP + "\\s*TPS\\s*([0-9]+(?:[.,][0-9]+)?)\\s*" + SEP + "\\s*(\\d+)\\s*-\\s*(.+)$");
    private static final Pattern MONEY_LINE_PATTERN = Pattern.compile(
            "^Coins\\s+([\\d\\u00A0 ]+?)\\s*" + SEP + "\\s*Stadskassa\\s+([\\d\\u00A0 ]+)$");

    private GameZoneTabStatusParser() {}

    public static GameZoneLiveStatus parse(String headerText) {
        if (headerText == null || headerText.isBlank()) return GameZoneLiveStatus.UNKNOWN;

        Integer onlinePlayers = null;
        Integer maxPlayers = null;
        Double tps = null;
        Integer cityLevel = null;
        String cityName = null;
        Long coins = null;
        Long treasury = null;

        for (String rawLine : headerText.split("\\n", -1)) {
            String line = rawLine.trim();
            if (line.isEmpty()) continue;

            if (onlinePlayers == null) {
                Matcher pm = PLAYER_TPS_CITY_PATTERN.matcher(line);
                if (pm.matches()) {
                    onlinePlayers = parseInt(pm.group(1));
                    maxPlayers = parseInt(pm.group(2));
                    tps = parseDecimal(pm.group(3));
                    cityLevel = parseInt(pm.group(4));
                    String name = pm.group(5).trim();
                    cityName = name.isEmpty() ? null : name;
                    continue;
                }
            }

            if (coins == null) {
                Matcher mm = MONEY_LINE_PATTERN.matcher(line);
                if (mm.matches()) {
                    coins = parseMoney(mm.group(1));
                    treasury = parseMoney(mm.group(2));
                }
            }
        }

        GameZoneTabIdentityParser.ParsedHeader settlement = GameZoneTabIdentityParser.parseHeader(headerText);
        String settlementName = settlement != null ? settlement.name() : null;
        String settlementRole = settlement != null ? settlement.role() : null;
        Double bonus = settlement != null ? parseDecimal(settlement.bonusText()) : null;

        return new GameZoneLiveStatus(onlinePlayers, maxPlayers, tps, cityLevel, cityName, coins, treasury,
                settlementName, settlementRole, bonus);
    }

    private static Integer parseInt(String raw) {
        if (raw == null) return null;
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Locale-independent: only ever normalizes a comma decimal separator to a dot, nothing else. */
    private static Double parseDecimal(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return Double.parseDouble(raw.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Strips ordinary spaces and U+00A0 NBSP grouping separators before parsing - never locale-dependent. */
    private static Long parseMoney(String raw) {
        if (raw == null) return null;
        String digitsOnly = raw.replace(" ", "").replace(" ", "").trim();
        if (digitsOnly.isEmpty()) return null;
        try {
            return Long.parseLong(digitsOnly);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
