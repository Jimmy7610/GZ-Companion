package se.jimmyeliasson.gzcompanion.settlement;

import se.jimmyeliasson.gzcompanion.knowledge.settlement.SettlementCatalog;
import se.jimmyeliasson.gzcompanion.knowledge.settlement.SettlementLevel;

import java.util.Locale;
import java.util.Optional;

/**
 * The raw live GameZone settlement level/level-name (as parsed by {@link
 * se.jimmyeliasson.gzcompanion.gamezone.status.GameZoneTabStatusParser} - never re-parsed here),
 * plus whether it is confirmed {@link LiveLevelAlignment#ALIGNED} with the bundled {@link
 * SettlementCatalog}.
 *
 * <p><b>The raw live value is ALWAYS preserved</b>, even on a {@link LiveLevelAlignment#MISMATCH}
 * - GameZone's own reported level/name must never be hidden or overwritten just because it
 * disagrees with this Companion's bundled Rule Pack data. Only {@link #trusted()} controls whether
 * automatic progression/material planning may use this value; a mismatched or unknown level must
 * fail closed to the manual planner instead of confidently calculating against stale/unverified
 * data.
 */
public record LiveSettlementLevel(Integer level, String levelName, LiveLevelAlignment alignment) {
    public static final LiveSettlementLevel NONE = new LiveSettlementLevel(null, null, LiveLevelAlignment.UNKNOWN);

    /** True only when this level is safely aligned with the bundled catalog - the ONLY case where
     * automatic progression/material planning may use {@link #level()}. */
    public boolean trusted() {
        return alignment == LiveLevelAlignment.ALIGNED;
    }

    /**
     * Evaluates a raw live level/name pair against {@code catalog}. Name comparison normalizes
     * only case and surrounding whitespace - never fuzzy/partial matching. Never invents a value:
     * a missing level, or a level with no accompanying name to compare, is reported {@code
     * UNKNOWN} rather than guessed as aligned OR mismatched.
     */
    public static LiveSettlementLevel evaluate(Integer liveLevel, String liveLevelName, SettlementCatalog catalog) {
        if (liveLevel == null) {
            return new LiveSettlementLevel(null, liveLevelName, LiveLevelAlignment.UNKNOWN);
        }
        if (liveLevelName == null || catalog == null) {
            return new LiveSettlementLevel(liveLevel, liveLevelName, LiveLevelAlignment.UNKNOWN);
        }
        Optional<SettlementLevel> catalogLevel = catalog.byLevel(liveLevel);
        if (catalogLevel.isEmpty()) {
            return new LiveSettlementLevel(liveLevel, liveLevelName, LiveLevelAlignment.MISMATCH);
        }
        boolean nameMatches = normalize(catalogLevel.get().name()).equals(normalize(liveLevelName));
        return new LiveSettlementLevel(liveLevel, liveLevelName,
                nameMatches ? LiveLevelAlignment.ALIGNED : LiveLevelAlignment.MISMATCH);
    }

    private static String normalize(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }
}
