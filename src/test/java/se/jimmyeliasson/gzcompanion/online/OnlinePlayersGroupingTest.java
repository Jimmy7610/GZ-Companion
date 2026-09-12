package se.jimmyeliasson.gzcompanion.online;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import se.jimmyeliasson.gzcompanion.minecraft.OnlinePlayerSnapshot;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure grouping/sorting/search logic - no live Minecraft client needed, matching this whole
 * codebase's established pattern of testing extracted pure logic rather than MC-touching glue.
 */
class OnlinePlayersGroupingTest {

    private static OnlinePlayerSnapshot player(String name) {
        return new OnlinePlayerSnapshot(name, false, 50);
    }

    private static OnlinePlayerSnapshot local(String name) {
        return new OnlinePlayerSnapshot(name, true, 20);
    }

    @Test
    @DisplayName("The current visible player list maps correctly into ÖVRIGA ONLINE when nobody is favorited or a settlement member")
    void currentPlayerListMapsCorrectly() {
        List<OnlinePlayerSnapshot> online = List.of(player("AlexMiner"), player("Miner123"));
        OnlinePlayersView view = OnlinePlayersGrouping.build(online, true, List.of(), List.of(), null);

        assertEquals(2, view.others().size());
        assertEquals(2, view.onlineCount());
        assertTrue(view.favorites().isEmpty());
        assertTrue(view.settlementMembers().isEmpty());
    }

    @Test
    @DisplayName("The local player's row is marked localPlayer (DU), using the real profile name - never hardcoded")
    void localPlayerIsMarked() {
        OnlinePlayersView view = OnlinePlayersGrouping.build(List.of(local("jbl76"), player("AlexMiner")), true, List.of(), List.of(), null);

        OnlinePlayerRow selfRow = view.others().stream().filter(r -> r.displayName().equals("jbl76")).findFirst().orElseThrow();
        assertTrue(selfRow.localPlayer());
        OnlinePlayerRow otherRow = view.others().stream().filter(r -> r.displayName().equals("AlexMiner")).findFirst().orElseThrow();
        assertFalse(otherRow.localPlayer());
    }

    @Test
    @DisplayName("Search is case-insensitive")
    void searchIsCaseInsensitive() {
        OnlinePlayersView view = OnlinePlayersGrouping.build(List.of(player("AlexMiner")), true, List.of(), List.of(), "ALEXMINER");
        assertEquals(1, view.others().size());

        OnlinePlayersView viewLower = OnlinePlayersGrouping.build(List.of(player("AlexMiner")), true, List.of(), List.of(), "alexminer");
        assertEquals(1, viewLower.others().size());
    }

    @Test
    @DisplayName("Partial search matches a substring anywhere in the name")
    void partialSearchWorks() {
        OnlinePlayersView view = OnlinePlayersGrouping.build(List.of(player("AlexMiner"), player("SteveMaster")), true, List.of(), List.of(), "Min");
        assertEquals(1, view.others().size());
        assertEquals("AlexMiner", view.others().get(0).displayName());
    }

    @Test
    @DisplayName("A favorite present in the current online list is reported ONLINE with real latency")
    void favoriteOnlineWhenPresent() {
        OnlinePlayersView view = OnlinePlayersGrouping.build(List.of(new OnlinePlayerSnapshot("Kalle92", false, 88)), true, List.of("Kalle92"), List.of(), null);

        OnlinePlayerRow row = view.favorites().get(0);
        assertEquals(OnlinePresence.ONLINE, row.presence());
        assertEquals(88, row.latencyMs());
    }

    @Test
    @DisplayName("A favorite absent from the current list, while connected, is NOT_ONLINE - never a fabricated 'offline' claim beyond that")
    void favoriteNotOnlineWhenAbsentButConnected() {
        OnlinePlayersView view = OnlinePlayersGrouping.build(List.of(), true, List.of("Kalle92"), List.of(), null);

        OnlinePlayerRow row = view.favorites().get(0);
        assertEquals(OnlinePresence.NOT_ONLINE, row.presence());
        assertNull(row.latencyMs());
    }

    @Test
    @DisplayName("A favorite's status is UNKNOWN (never NOT_ONLINE) when disconnected from GameZone entirely")
    void favoriteUnknownWhenDisconnected() {
        OnlinePlayersView view = OnlinePlayersGrouping.build(List.of(), false, List.of("Kalle92"), List.of(), null);

        OnlinePlayerRow row = view.favorites().get(0);
        assertEquals(OnlinePresence.UNKNOWN, row.presence());
        assertEquals("Kalle92", row.displayName(), "Original stored casing must be preserved when we have no live snapshot to refresh it from.");
    }

    @Test
    @DisplayName("Settlement member matching is case-insensitive")
    void settlementMatchingIsCaseInsensitive() {
        OnlinePlayersView view = OnlinePlayersGrouping.build(List.of(player("Builder77")), true, List.of(), List.of("builder77"), null);

        assertEquals(1, view.settlementMembers().size());
        assertEquals("Builder77", view.settlementMembers().get(0).displayName(), "Display casing must come from the live online snapshot, not the stored member name.");
    }

    @Test
    @DisplayName("A player who is both a favorite and a settlement member appears ONLY in FAVORITER, never duplicated into MIN SETTLEMENT")
    void favoriteWinsOverSettlementSection() {
        OnlinePlayersView view = OnlinePlayersGrouping.build(List.of(player("Kalle92")), true, List.of("Kalle92"), List.of("Kalle92"), null);

        assertEquals(1, view.favorites().size());
        assertTrue(view.settlementMembers().isEmpty());
        assertTrue(view.favorites().get(0).settlementMember(), "The row itself must still know it's a settlement member, for badge purposes.");
    }

    @Test
    @DisplayName("No player appears twice across all three sections, even with overlapping favorite/settlement/online membership")
    void noPlayerAppearsTwice() {
        List<OnlinePlayerSnapshot> online = List.of(player("Kalle92"), player("Builder77"), player("AlexMiner"));
        OnlinePlayersView view = OnlinePlayersGrouping.build(online, true, List.of("Kalle92"), List.of("Builder77"), null);

        List<String> allNames = view.allRows().stream().map(OnlinePlayerRow::displayName).toList();
        assertEquals(3, allNames.size());
        assertEquals(3, new java.util.HashSet<>(allNames).size(), "Every name must be unique across the combined row list.");
    }

    @Test
    @DisplayName("Each section is sorted alphabetically (case-insensitive)")
    void alphabeticalOrdering() {
        List<OnlinePlayerSnapshot> online = List.of(player("zeta"), player("alpha"), player("Mango"));
        OnlinePlayersView view = OnlinePlayersGrouping.build(online, true, List.of(), List.of(), null);

        List<String> names = view.others().stream().map(OnlinePlayerRow::displayName).toList();
        assertEquals(List.of("alpha", "Mango", "zeta"), names);
    }

    @Test
    @DisplayName("Within FAVORITER, online favorites are listed before not-online/unknown favorites")
    void onlineFavoritesBeforeOfflineFavorites() {
        List<OnlinePlayerSnapshot> online = List.of(player("ZOnlineFavorite"));
        OnlinePlayersView view = OnlinePlayersGrouping.build(online, true, List.of("AOfflineFavorite", "ZOnlineFavorite"), List.of(), null);

        List<String> names = view.favorites().stream().map(OnlinePlayerRow::displayName).toList();
        assertEquals(List.of("ZOnlineFavorite", "AOfflineFavorite"), names,
                "Even though 'AOfflineFavorite' sorts first alphabetically, online status must be the primary sort key.");
    }

    @Test
    @DisplayName("An empty player list produces empty sections with zero online count, never an error")
    void emptyPlayerList() {
        OnlinePlayersView view = OnlinePlayersGrouping.build(List.of(), true, List.of(), List.of(), null);

        assertEquals(0, view.onlineCount());
        assertTrue(view.favorites().isEmpty());
        assertTrue(view.settlementMembers().isEmpty());
        assertTrue(view.others().isEmpty());
    }

    @Test
    @DisplayName("50+ online players group and sort correctly with no duplicates and no crash")
    void manyPlayersGroupCorrectly() {
        List<OnlinePlayerSnapshot> online = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            online.add(player("Player" + String.format("%02d", i)));
        }
        OnlinePlayersView view = OnlinePlayersGrouping.build(online, true, List.of("Player05", "Player50"), List.of("Player10"), null);

        assertEquals(60, view.onlineCount());
        assertEquals(2, view.favorites().size());
        assertEquals(1, view.settlementMembers().size());
        assertEquals(57, view.others().size());
        assertEquals(60, view.allRows().size());
        assertEquals(60, new java.util.HashSet<>(view.allRows().stream().map(OnlinePlayerRow::displayName).toList()).size());
    }

    @Test
    @DisplayName("Null favorite/settlement lists and a null search query never throw - safe defaults throughout")
    void nullInputsHandledSafely() {
        assertDoesNotThrow(() -> OnlinePlayersGrouping.build(null, true, null, null, null));
        OnlinePlayersView view = OnlinePlayersGrouping.build(null, true, null, null, null);
        assertEquals(0, view.onlineCount());
    }

    @Test
    @DisplayName("Blank/duplicate favorite entries are deduplicated case-insensitively and never crash")
    void blankAndDuplicateFavoritesHandledSafely() {
        OnlinePlayersView view = OnlinePlayersGrouping.build(List.of(), true, List.of("Kalle92", "kalle92", "", "  ", "KALLE92"), List.of(), null);
        assertEquals(1, view.favorites().size(), "Case-insensitive duplicates and blanks must collapse to exactly one row.");
    }
}
