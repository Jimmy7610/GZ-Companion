package se.jimmyeliasson.gzcompanion.gamezone.status;

import java.util.Locale;

/**
 * Small, pure, deterministic formatting helpers for {@link GameZoneLiveStatus} values on the Home
 * tab - deliberately independent of the runtime's default locale (a Swedish-style space-grouped
 * number must look the same regardless of the machine's OS locale settings), and never throws.
 */
public final class GameZoneStatusFormatter {
    private static final String UNKNOWN_PLACEHOLDER = "—";

    private GameZoneStatusFormatter() {}

    /** Groups a whole number with ordinary spaces every 3 digits, e.g. {@code 11487272 -> "11 487 272"}. */
    public static String formatMoney(Long value) {
        if (value == null) return UNKNOWN_PLACEHOLDER;
        boolean negative = value < 0;
        String digits = Long.toString(Math.abs(value));

        StringBuilder grouped = new StringBuilder();
        int count = 0;
        for (int i = digits.length() - 1; i >= 0; i--) {
            grouped.append(digits.charAt(i));
            count++;
            if (count % 3 == 0 && i != 0) {
                grouped.append(' ');
            }
        }
        return (negative ? "-" : "") + grouped.reverse();
    }

    /** One decimal place, dot separator, e.g. {@code 20.0 -> "20.0"}. */
    public static String formatTps(Double tps) {
        if (tps == null) return UNKNOWN_PLACEHOLDER;
        return String.format(Locale.ROOT, "%.1f", tps);
    }

    /** One decimal place with an explicit sign for positive values, e.g. {@code 44.3 -> "+44.3%"}. */
    public static String formatBonusPercent(Double bonusPercent) {
        if (bonusPercent == null) return UNKNOWN_PLACEHOLDER;
        String sign = bonusPercent > 0 ? "+" : "";
        return sign + String.format(Locale.ROOT, "%.1f", bonusPercent) + "%";
    }
}
