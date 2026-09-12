package se.jimmyeliasson.gzcompanion.leaderboard;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Validates {@link GameZoneLeaderboardRegistry} against the exact counts GameZone's public site
 * advertised on 2026-09-13 (see docs/LEADERBOARDS.md) - a CURRENT snapshot, not eternal truth (the
 * task that added this feature was explicit: "use that as current validation, NOT eternal truth").
 * If GameZone adds/removes a board, update the registry AND this test together.
 */
class GameZoneLeaderboardRegistryTest {
    private static final Pattern VALID_ID = Pattern.compile("^[a-z][a-z0-9_]*$");

    @Test
    @DisplayName("Exactly 27 boards total, matching GameZone's own published counts per group")
    void exactCountsAsDiscovered() {
        assertEquals(27, GameZoneLeaderboardRegistry.all().size());
        assertEquals(10, GameZoneLeaderboardRegistry.byGroup(LeaderboardGroup.SPELARE).size());
        assertEquals(7, GameZoneLeaderboardRegistry.byGroup(LeaderboardGroup.SETTLEMENTS).size());
        assertEquals(5, GameZoneLeaderboardRegistry.byGroup(LeaderboardGroup.FORETAG).size());
        assertEquals(5, GameZoneLeaderboardRegistry.byGroup(LeaderboardGroup.SERVERN).size());
    }

    @Test
    @DisplayName("Every board id is unique")
    void everyIdIsUnique() {
        List<LeaderboardDefinition> all = GameZoneLeaderboardRegistry.all();
        Set<String> ids = all.stream().map(LeaderboardDefinition::id).collect(java.util.stream.Collectors.toSet());
        assertEquals(all.size(), ids.size(), "duplicate board id found");
    }

    @Test
    @DisplayName("Every board id looks like a valid URL path segment (lowercase, digits, underscores)")
    void everyIdIsUrlSafe() {
        for (LeaderboardDefinition def : GameZoneLeaderboardRegistry.all()) {
            assertTrue(VALID_ID.matcher(def.id()).matches(), "id not URL-safe: " + def.id());
        }
    }

    @Test
    @DisplayName("byId resolves every registered board")
    void byIdResolvesEveryBoard() {
        for (LeaderboardDefinition def : GameZoneLeaderboardRegistry.all()) {
            assertEquals(def, GameZoneLeaderboardRegistry.byId(def.id()).orElseThrow());
        }
    }

    @Test
    @DisplayName("byId returns empty for an unknown id, never guesses")
    void byIdUnknownReturnsEmpty() {
        assertTrue(GameZoneLeaderboardRegistry.byId("does_not_exist").isEmpty());
    }

    @Test
    @DisplayName("Every group has at least one board, and firstInGroup resolves it")
    void everyGroupHasAFirstBoard() {
        for (LeaderboardGroup group : LeaderboardGroup.values()) {
            assertFalse(GameZoneLeaderboardRegistry.byGroup(group).isEmpty(), "group has no boards: " + group);
            assertTrue(GameZoneLeaderboardRegistry.firstInGroup(group).isPresent());
        }
    }

    @Test
    @DisplayName("Only SERVERN boards are marked isServerStat")
    void onlyServernBoardsAreServerStats() {
        for (LeaderboardDefinition def : GameZoneLeaderboardRegistry.all()) {
            assertEquals(def.group() == LeaderboardGroup.SERVERN, def.isServerStat(),
                    "isServerStat mismatch for " + def.id());
        }
    }

    @Test
    @DisplayName("No production board definition contains a real current ranking value - titles/labels only, never a name or coin figure")
    void noHardcodedCurrentRankingData() {
        // A structural safeguard for "DO NOT place actual current names/coin values from today's
        // website into the Rule Pack or Java source as production data" - every definition's fields
        // are metadata (title/description/label), never anything shaped like a leaderboard ENTRY.
        for (LeaderboardDefinition def : GameZoneLeaderboardRegistry.all()) {
            assertFalse(def.title().matches(".*\\d{4,}.*"), "title looks like it might contain a numeric value: " + def.title());
        }
    }
}
