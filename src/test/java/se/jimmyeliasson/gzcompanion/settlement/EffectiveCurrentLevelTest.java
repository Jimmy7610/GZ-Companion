package se.jimmyeliasson.gzcompanion.settlement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** {@link EffectiveCurrentLevel#resolve} - trusted live level vs. manual planner fallback. */
class EffectiveCurrentLevelTest {

    private static LiveSettlementLevel aligned(int level, String name) {
        return new LiveSettlementLevel(level, name, LiveLevelAlignment.ALIGNED);
    }

    private static LiveSettlementLevel mismatch(int level, String name) {
        return new LiveSettlementLevel(level, name, LiveLevelAlignment.MISMATCH);
    }

    @Test
    @DisplayName("B: a trusted (ALIGNED) live level overrides the manual planner level")
    void trustedLiveLevelOverridesManual() {
        EffectiveCurrentLevel effective = EffectiveCurrentLevel.resolve(aligned(10, "Småstad"), 6);

        assertEquals(10, effective.level());
        assertTrue(effective.live());
    }

    @Test
    @DisplayName("E: a MISMATCH live level does NOT override the manual planner level - fails closed to manual")
    void mismatchedLiveLevelFallsBackToManual() {
        EffectiveCurrentLevel effective = EffectiveCurrentLevel.resolve(mismatch(99, "Påhittad"), 6);

        assertEquals(6, effective.level());
        assertFalse(effective.live());
    }

    @Test
    @DisplayName("F: an UNKNOWN live level (no data) falls back to manual")
    void unknownLiveLevelFallsBackToManual() {
        EffectiveCurrentLevel effective = EffectiveCurrentLevel.resolve(LiveSettlementLevel.NONE, 6);

        assertEquals(6, effective.level());
        assertFalse(effective.live());
    }

    @Test
    @DisplayName("Neither live nor manual level known - effective level is unknown")
    void neitherKnownIsUnknown() {
        EffectiveCurrentLevel effective = EffectiveCurrentLevel.resolve(LiveSettlementLevel.NONE, null);

        assertFalse(effective.known());
        assertNull(effective.level());
        assertFalse(effective.live());
    }

    @Test
    @DisplayName("M: resolving the effective level is a pure read - it takes the manual level as a plain "
            + "value, never a SettlementPlannerManager, so it is structurally impossible for this to mutate/persist anything")
    void resolvingIsAPureReadWithNoMutationCapability() {
        // The method signature itself is the proof: EffectiveCurrentLevel.resolve(LiveSettlementLevel, Integer)
        // has no reference to SettlementPlannerManager/SettlementPlannerProfile at all - there is nothing
        // it could call to persist a value even if it wanted to.
        EffectiveCurrentLevel a = EffectiveCurrentLevel.resolve(aligned(10, "Småstad"), 6);
        EffectiveCurrentLevel b = EffectiveCurrentLevel.resolve(aligned(10, "Småstad"), 6);
        assertEquals(a, b, "calling resolve repeatedly with the same inputs must be perfectly idempotent");
    }

    @Test
    @DisplayName("H: a target level must be strictly greater than the effective current level to be valid")
    void targetMustBeStrictlyGreaterThanEffectiveCurrent() {
        EffectiveCurrentLevel effective = new EffectiveCurrentLevel(10, true);

        assertFalse(effective.isValidTarget(10), "target equal to current is not valid");
        assertFalse(effective.isValidTarget(9), "target below current is not valid");
        assertFalse(effective.isValidTarget(null));
        assertTrue(effective.isValidTarget(11));
        assertTrue(effective.isValidTarget(50));
    }

    @Test
    @DisplayName("H: an unknown effective level never validates any target")
    void unknownEffectiveLevelNeverValidatesTarget() {
        assertFalse(EffectiveCurrentLevel.NONE.isValidTarget(10));
    }
}
