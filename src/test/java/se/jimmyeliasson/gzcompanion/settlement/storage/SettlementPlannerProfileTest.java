package se.jimmyeliasson.gzcompanion.settlement.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SettlementPlannerProfileTest {

    @Test
    @DisplayName("empty() profile has no level selections, no owned amounts, and no members")
    void emptyProfileIsSafe() {
        SettlementPlannerProfile profile = SettlementPlannerProfile.empty();
        assertNull(profile.currentLevel());
        assertNull(profile.targetLevel());
        assertEquals(0, profile.ownedAmount("minecraft:oak_log"));
        assertTrue(profile.members().isEmpty());
    }

    @Test
    @DisplayName("withCurrentLevel/withTargetLevel are copy-on-write and don't affect the original")
    void levelMutationsAreCopyOnWrite() {
        SettlementPlannerProfile original = SettlementPlannerProfile.empty();
        SettlementPlannerProfile updated = original.withCurrentLevel(5).withTargetLevel(10);

        assertNull(original.currentLevel());
        assertEquals(5, updated.currentLevel());
        assertEquals(10, updated.targetLevel());
    }

    @Test
    @DisplayName("withOwnedAmount stores a positive amount and removes the entry when set to zero")
    void ownedAmountSetAndClear() {
        SettlementPlannerProfile profile = SettlementPlannerProfile.empty().withOwnedAmount("minecraft:oak_log", 12);
        assertEquals(12, profile.ownedAmount("minecraft:oak_log"));

        SettlementPlannerProfile cleared = profile.withOwnedAmount("minecraft:oak_log", 0);
        assertEquals(0, cleared.ownedAmount("minecraft:oak_log"));
        assertFalse(cleared.ownedItemAmounts().containsKey("minecraft:oak_log"));
    }

    @Test
    @DisplayName("withMember adds a new member; a second call with the same id replaces it in place")
    void memberAddAndUpdate() {
        SettlementPlannerProfile profile = SettlementPlannerProfile.empty()
                .withMember(new MemberNote("m1", "Alice", "Byggare"));
        assertEquals(1, profile.members().size());

        SettlementPlannerProfile updated = profile.withMember(new MemberNote("m1", "Alice", "Kassör"));
        assertEquals(1, updated.members().size());
        assertEquals("Kassör", updated.members().get(0).note());
    }

    @Test
    @DisplayName("withoutMember removes exactly the matching member and leaves the rest untouched")
    void memberRemoval() {
        SettlementPlannerProfile profile = SettlementPlannerProfile.empty()
                .withMember(new MemberNote("m1", "Alice", "Byggare"))
                .withMember(new MemberNote("m2", "Bob", "Gruvdrift"));

        SettlementPlannerProfile updated = profile.withoutMember("m1");
        assertEquals(1, updated.members().size());
        assertEquals("Bob", updated.members().get(0).playerName());
    }

    @Test
    @DisplayName("A blank player name falls back to a placeholder rather than storing an empty string")
    void blankPlayerNameFallsBack() {
        MemberNote note = new MemberNote("m1", "   ", "note");
        assertEquals("Okänd spelare", note.playerName());
    }
}
