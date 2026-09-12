package se.jimmyeliasson.gzcompanion.leaderboard;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LeaderboardEntryTest {
    @Test
    @DisplayName("rank must be >= 1")
    void rankMustBePositive() {
        assertThrows(IllegalArgumentException.class, () -> LeaderboardEntry.of(0, "Alfa", "1 coins", "Coins"));
        assertThrows(IllegalArgumentException.class, () -> LeaderboardEntry.of(-1, "Alfa", "1 coins", "Coins"));
    }

    @Test
    @DisplayName("displayName must not be blank")
    void displayNameMustNotBeBlank() {
        assertThrows(IllegalArgumentException.class, () -> LeaderboardEntry.of(1, "", "1 coins", "Coins"));
        assertThrows(IllegalArgumentException.class, () -> LeaderboardEntry.of(1, "   ", "1 coins", "Coins"));
        assertThrows(IllegalArgumentException.class, () -> LeaderboardEntry.of(1, null, "1 coins", "Coins"));
    }

    @Test
    @DisplayName("primaryValue must not be blank")
    void primaryValueMustNotBeBlank() {
        assertThrows(IllegalArgumentException.class, () -> LeaderboardEntry.of(1, "Alfa", "", "Coins"));
    }

    @Test
    @DisplayName("An empty (but non-null) primaryLabel is allowed")
    void blankLabelIsAllowed() {
        LeaderboardEntry entry = LeaderboardEntry.of(1, "Alfa", "1", "");
        assertEquals("", entry.primaryLabel());
    }

    @Test
    @DisplayName("secondaryValue and secondaryLabel must both be present or both absent")
    void secondaryFieldsMustBePaired() {
        assertThrows(IllegalArgumentException.class,
                () -> new LeaderboardEntry(1, "Alfa", "1", "Coins", "extra", null));
        assertThrows(IllegalArgumentException.class,
                () -> new LeaderboardEntry(1, "Alfa", "1", "Coins", null, "extra-label"));
    }

    @Test
    @DisplayName("hasSecondaryValue reflects whether a secondary value is present")
    void hasSecondaryValueReflectsPresence() {
        assertFalse(LeaderboardEntry.of(1, "Alfa", "1", "Coins").hasSecondaryValue());
        assertTrue(new LeaderboardEntry(1, "Alfa", "1", "Coins", "extra", "").hasSecondaryValue());
    }
}
