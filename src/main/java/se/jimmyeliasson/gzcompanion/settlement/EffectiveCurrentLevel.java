package se.jimmyeliasson.gzcompanion.settlement;

/**
 * The settlement level Progression/Material planning should actually use for THIS render - a
 * trusted live GameZone level when available, else the player's manually chosen "Planerad
 * nuvarande nivå" ({@code SettlementPlannerProfile.currentLevel()}) as an offline/reference
 * fallback.
 *
 * <p><b>Never mutates the manually stored profile value.</b> This is a pure, read-time decision -
 * {@link #resolve} takes the manual level as a plain {@code Integer} (never a mutable {@code
 * SettlementPlannerManager}), so it is structurally impossible for resolving the effective level
 * to also persist/overwrite anything. A temporary live level appearing (or disappearing, e.g. on
 * disconnect) can therefore never corrupt the player's own locally-planned value.
 */
public record EffectiveCurrentLevel(Integer level, boolean live) {
    public static final EffectiveCurrentLevel NONE = new EffectiveCurrentLevel(null, false);

    public boolean known() {
        return level != null;
    }

    public static EffectiveCurrentLevel resolve(LiveSettlementLevel liveLevel, Integer manualLevel) {
        if (liveLevel != null && liveLevel.trusted()) {
            return new EffectiveCurrentLevel(liveLevel.level(), true);
        }
        return manualLevel != null ? new EffectiveCurrentLevel(manualLevel, false) : NONE;
    }

    /** Whether {@code targetLevel} is a valid, meaningful planning target given this effective
     * current level - never true when either is unknown, or when the target does not strictly
     * exceed the current level (see docs/SETTLEMENT-COMPANION.md's Material-mode section). */
    public boolean isValidTarget(Integer targetLevel) {
        return level != null && targetLevel != null && targetLevel > level;
    }
}
