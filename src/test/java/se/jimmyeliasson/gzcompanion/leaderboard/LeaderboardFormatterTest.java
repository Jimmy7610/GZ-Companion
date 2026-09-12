package se.jimmyeliasson.gzcompanion.leaderboard;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LeaderboardFormatterTest {
    private static final LeaderboardDefinition DEF = GameZoneLeaderboardRegistry.byId("player_coins").orElseThrow();
    private static final Instant NOW = Instant.parse("2026-09-13T12:00:00Z");

    private static LeaderboardSnapshot snapshotWith(LeaderboardStatus status, Instant fetchedAt) {
        List<LeaderboardEntry> entries = fetchedAt != null ? List.of(LeaderboardEntry.of(1, "Alfa", "1 coins", "Coins")) : List.of();
        return new LeaderboardSnapshot(DEF, status, entries, fetchedAt, null);
    }

    @Test
    @DisplayName("LOADED is labeled LIVE")
    void loadedIsLive() {
        assertEquals("LIVE", LeaderboardFormatter.freshnessLabel(snapshotWith(LeaderboardStatus.LOADED, NOW)));
    }

    @Test
    @DisplayName("STALE is never labeled LIVE")
    void staleIsNeverLive() {
        String label = LeaderboardFormatter.freshnessLabel(snapshotWith(LeaderboardStatus.STALE, NOW.minusSeconds(300)));
        assertNotEquals("LIVE", label);
        assertEquals("SENAST HÄMTADE", label);
    }

    @Test
    @DisplayName("UNAVAILABLE/INCOMPATIBLE/ERROR are never labeled LIVE")
    void failureStatusesAreNeverLive() {
        for (LeaderboardStatus status : List.of(LeaderboardStatus.UNAVAILABLE, LeaderboardStatus.INCOMPATIBLE, LeaderboardStatus.ERROR)) {
            assertNotEquals("LIVE", LeaderboardFormatter.freshnessLabel(snapshotWith(status, null)));
        }
    }

    @Test
    @DisplayName("timeAgo boundaries: just nu / sek / min / h / dagar")
    void timeAgoBoundaries() {
        assertEquals("just nu", LeaderboardFormatter.timeAgo(NOW, NOW));
        assertEquals("just nu", LeaderboardFormatter.timeAgo(NOW.minusSeconds(4), NOW));
        assertEquals("12 sek sedan", LeaderboardFormatter.timeAgo(NOW.minusSeconds(12), NOW));
        assertEquals("5 min sedan", LeaderboardFormatter.timeAgo(NOW.minusSeconds(300), NOW));
        assertEquals("2 h sedan", LeaderboardFormatter.timeAgo(NOW.minusSeconds(7200), NOW));
        assertEquals("3 dagar sedan", LeaderboardFormatter.timeAgo(NOW.minusSeconds(3L * 86400), NOW));
    }

    @Test
    @DisplayName("timeAgo never goes negative even if fetchedAt is slightly after now (clock skew)")
    void timeAgoNeverNegative() {
        assertEquals("just nu", LeaderboardFormatter.timeAgo(NOW.plusSeconds(5), NOW));
    }

    @Test
    @DisplayName("A LOADED snapshot's detail line says 'Uppdaterad', a STALE one says 'Senast hämtad'")
    void detailLineWordingDiffersByFreshness() {
        String liveDetail = LeaderboardFormatter.freshnessDetail(snapshotWith(LeaderboardStatus.LOADED, NOW.minusSeconds(10)), NOW);
        String staleDetail = LeaderboardFormatter.freshnessDetail(snapshotWith(LeaderboardStatus.STALE, NOW.minusSeconds(240)), NOW);
        assertTrue(liveDetail.startsWith("Uppdaterad"));
        assertTrue(staleDetail.startsWith("Senast hämtad"));
    }
}
