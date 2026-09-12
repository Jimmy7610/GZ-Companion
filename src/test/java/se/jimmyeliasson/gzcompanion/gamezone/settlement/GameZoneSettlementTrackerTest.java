package se.jimmyeliasson.gzcompanion.gamezone.settlement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import se.jimmyeliasson.gzcompanion.minecraft.OnlinePlayerSnapshot;
import se.jimmyeliasson.gzcompanion.online.OnlinePlayerRow;
import se.jimmyeliasson.gzcompanion.online.OnlinePlayersGrouping;
import se.jimmyeliasson.gzcompanion.online.OnlinePlayersView;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Live invalidation behavior - no live Minecraft client needed, only synthetic raw TAB data
 * (exactly what {@link se.jimmyeliasson.gzcompanion.minecraft.VanillaMinecraftBridge} would have
 * supplied), matching this codebase's established pure-logic-testing convention.
 */
class GameZoneSettlementTrackerTest {

    private static OnlinePlayerSnapshot local(String name, String display) {
        return new OnlinePlayerSnapshot(name, true, 20, display);
    }

    private static OnlinePlayerSnapshot other(String name, String display) {
        return new OnlinePlayerSnapshot(name, false, 50, display);
    }

    @Test
    @DisplayName("A settlement name change (A -> B) is picked up as soon as the header changes")
    void settlementNameChangesAcrossUpdates() {
        GameZoneSettlementTracker tracker = new GameZoneSettlementTracker();
        List<OnlinePlayerSnapshot> players = List.of(local("jbl76", "[BUS] jbl76"));

        GameZoneSettlementIdentity first = tracker.update(true, "Trälskärsbukten · MEMBER · +44.3%", players);
        assertEquals("Trälskärsbukten", first.settlementName());

        List<OnlinePlayerSnapshot> playersAfterMove = List.of(local("jbl76", "[UDD] jbl76"));
        GameZoneSettlementIdentity second = tracker.update(true, "Uddevalla · MEMBER · +2.0%", playersAfterMove);
        assertEquals("Uddevalla", second.settlementName());
    }

    @Test
    @DisplayName("Same-settlement membership grouping changes (A -> B) the moment the settlement changes")
    void membershipGroupingChangesAcrossUpdates() {
        GameZoneSettlementTracker tracker = new GameZoneSettlementTracker();

        List<OnlinePlayerSnapshot> playersInA = List.of(local("jbl76", "[BUS] jbl76"), other("Olivre", "[BUS] Olivre"));
        tracker.update(true, "Trälskärsbukten · MEMBER · +44.3%", playersInA);
        assertEquals(Set.of("jbl76", "Olivre"), tracker.sameSettlementOnlineUsernames(playersInA));

        List<OnlinePlayerSnapshot> playersInB = List.of(local("jbl76", "[UDD] jbl76"), other("AnotherMember", "[UDD] AnotherMember"));
        tracker.update(true, "Uddevalla · MEMBER · +2.0%", playersInB);
        assertEquals(Set.of("jbl76", "AnotherMember"), tracker.sameSettlementOnlineUsernames(playersInB));
    }

    @Test
    @DisplayName("Members of the OLD settlement are no longer classified as same-settlement after the change")
    void oldSettlementMembersNoLongerClassified() {
        GameZoneSettlementTracker tracker = new GameZoneSettlementTracker();

        List<OnlinePlayerSnapshot> playersInA = List.of(local("jbl76", "[BUS] jbl76"), other("Olivre", "[BUS] Olivre"));
        tracker.update(true, "Trälskärsbukten · MEMBER · +44.3%", playersInA);

        tracker.update(true, "Uddevalla · MEMBER · +2.0%", List.of(local("jbl76", "[UDD] jbl76")));
        List<OnlinePlayerSnapshot> stillOnlineFromA = List.of(local("jbl76", "[UDD] jbl76"), other("Olivre", "[BUS] Olivre"));
        assertEquals(Set.of("jbl76"), tracker.sameSettlementOnlineUsernames(stillOnlineFromA),
                "Olivre still carries the OLD [BUS] prefix and must not be grouped under the new settlement.");
    }

    @Test
    @DisplayName("Leaving a settlement entirely (no header line, no local prefix) clears the group")
    void leavingSettlementClearsGroup() {
        GameZoneSettlementTracker tracker = new GameZoneSettlementTracker();
        List<OnlinePlayerSnapshot> playersInA = List.of(local("jbl76", "[BUS] jbl76"), other("Olivre", "[BUS] Olivre"));
        tracker.update(true, "Trälskärsbukten · MEMBER · +44.3%", playersInA);
        assertFalse(tracker.sameSettlementOnlineUsernames(playersInA).isEmpty());

        List<OnlinePlayerSnapshot> playersNoSettlement = List.of(local("jbl76", "jbl76"), other("Olivre", "[BUS] Olivre"));
        GameZoneSettlementIdentity afterLeaving = tracker.update(true, "Welcome to GameZoneMC!", playersNoSettlement);
        assertFalse(afterLeaving.known());
        assertTrue(tracker.sameSettlementOnlineUsernames(playersNoSettlement).isEmpty());
    }

    @Test
    @DisplayName("Disconnecting from GameZone immediately clears the LIVE identity - never a stale settlement")
    void disconnectClearsLiveIdentity() {
        GameZoneSettlementTracker tracker = new GameZoneSettlementTracker();
        List<OnlinePlayerSnapshot> players = List.of(local("jbl76", "[BUS] jbl76"));
        tracker.update(true, "Trälskärsbukten · MEMBER · +44.3%", players);
        assertTrue(tracker.current().known());

        GameZoneSettlementIdentity afterDisconnect = tracker.update(false, null, List.of());
        assertEquals(GameZoneSettlementIdentity.UNKNOWN, afterDisconnect);
        assertTrue(tracker.sameSettlementOnlineUsernames(players).isEmpty(),
                "A previous settlement's prefix must never keep grouping players after disconnect.");
    }

    @Test
    @DisplayName("Reconnecting reparses fresh state rather than reusing anything from before the disconnect")
    void reconnectReparsesFreshState() {
        GameZoneSettlementTracker tracker = new GameZoneSettlementTracker();
        tracker.update(true, "Trälskärsbukten · MEMBER · +44.3%", List.of(local("jbl76", "[BUS] jbl76")));
        tracker.update(false, null, List.of());
        assertFalse(tracker.current().known());

        GameZoneSettlementIdentity afterReconnect = tracker.update(true, "Uddevalla · KING · +8.0%", List.of(local("jbl76", "[UDD] jbl76")));
        assertTrue(afterReconnect.known());
        assertEquals("Uddevalla", afterReconnect.settlementName());
        assertEquals("KING", afterReconnect.role());
    }

    // ------------------------------------------------------------------
    // Integration with OnlinePlayersGrouping: favorites precedence + DU
    // ------------------------------------------------------------------

    @Test
    @DisplayName("A favorite who is also a same-settlement member appears ONLY in FAVORITER")
    void favoriteWinsOverLiveSettlementGrouping() {
        GameZoneSettlementTracker tracker = new GameZoneSettlementTracker();
        List<OnlinePlayerSnapshot> players = List.of(local("jbl76", "[BUS] jbl76"), other("DennisSanden", "[BUS] DennisSanden"));
        tracker.update(true, "Trälskärsbukten · MEMBER · +44.3%", players);
        List<String> settlementMembers = List.copyOf(tracker.sameSettlementOnlineUsernames(players));

        OnlinePlayersView view = OnlinePlayersGrouping.build(players, true, List.of("DennisSanden"), settlementMembers, null);

        assertEquals(1, view.favorites().size());
        assertTrue(view.settlementMembers().stream().noneMatch(r -> r.displayName().equals("DennisSanden")));
        assertTrue(view.favorites().get(0).settlementMember());
    }

    @Test
    @DisplayName("The local player is correctly marked DU when grouped under MIN SETTLEMENT")
    void localPlayerMarkedDuUnderSettlement() {
        GameZoneSettlementTracker tracker = new GameZoneSettlementTracker();
        List<OnlinePlayerSnapshot> players = List.of(local("jbl76", "[BUS] jbl76"), other("Olivre", "[BUS] Olivre"));
        tracker.update(true, "Trälskärsbukten · MEMBER · +44.3%", players);
        List<String> settlementMembers = List.copyOf(tracker.sameSettlementOnlineUsernames(players));

        OnlinePlayersView view = OnlinePlayersGrouping.build(players, true, List.of(), settlementMembers, null);

        OnlinePlayerRow selfRow = view.settlementMembers().stream().filter(r -> r.displayName().equals("jbl76")).findFirst().orElseThrow();
        assertTrue(selfRow.localPlayer());
    }

    @Test
    @DisplayName("50+ players in the same live settlement group deterministically with no duplicates")
    void manyPlayersInSameSettlementGroupDeterministically() {
        GameZoneSettlementTracker tracker = new GameZoneSettlementTracker();
        java.util.List<OnlinePlayerSnapshot> players = new java.util.ArrayList<>();
        players.add(local("jbl76", "[BUS] jbl76"));
        for (int i = 0; i < 59; i++) {
            players.add(other("Player" + String.format("%02d", i), "[BUS] Player" + String.format("%02d", i)));
        }
        tracker.update(true, "Trälskärsbukten · MEMBER · +44.3%", players);
        Set<String> members = tracker.sameSettlementOnlineUsernames(players);

        assertEquals(60, members.size());

        List<String> settlementMembers = List.copyOf(members);
        OnlinePlayersView view = OnlinePlayersGrouping.build(players, true, List.of(), settlementMembers, null);
        assertEquals(60, view.settlementMembers().size());
        assertEquals(60, new java.util.HashSet<>(view.allRows().stream().map(OnlinePlayerRow::displayName).toList()).size());
    }

    // ------------------------------------------------------------------
    // REAL DATA end-to-end: the exact captured runtime header/prefix format from the real
    // GameZoneMC server (bullet separator, Unicode [TRÄ] prefix), through the tracker AND
    // OnlinePlayersGrouping, proving MIN SETTLEMENT · Trälskärsbukten would actually populate.
    // ------------------------------------------------------------------

    @Test
    @DisplayName("REAL DATA end-to-end: real header + real Unicode prefixes produce the correct identity and MIN SETTLEMENT grouping")
    void realDataEndToEndProducesCorrectIdentityAndGrouping() {
        GameZoneSettlementTracker tracker = new GameZoneSettlementTracker();

        List<OnlinePlayerSnapshot> players = List.of(
                local("jbl76", "[TRÄ] ⚜ jbl76 [2]"),
                other("Olivre", "[TRÄ] ⚜ Olivre [62]"),
                other("Jeizo", "[TRÄ] ⚜ Jeizo [22]"),
                other("SomeoneElse", "[BUS] ⚜ SomeoneElse [11]")
        );

        GameZoneSettlementIdentity identity = tracker.update(true, "Trälskärsbukten • MEMBER • +44.3%", players);

        assertEquals("Trälskärsbukten", identity.settlementName());
        assertEquals("MEMBER", identity.role());
        assertEquals("TRÄ", identity.settlementPrefix());

        Set<String> sameSettlement = tracker.sameSettlementOnlineUsernames(players);
        assertEquals(Set.of("jbl76", "Olivre", "Jeizo"), sameSettlement);
        assertFalse(sameSettlement.contains("SomeoneElse"));

        List<String> settlementMembers = List.copyOf(sameSettlement);
        OnlinePlayersView view = OnlinePlayersGrouping.build(players, true, List.of(), settlementMembers, null);

        List<String> minSettlementNames = view.settlementMembers().stream().map(OnlinePlayerRow::displayName).sorted().toList();
        assertEquals(List.of("Jeizo", "Olivre", "jbl76"), minSettlementNames);
        assertTrue(view.others().stream().anyMatch(r -> r.displayName().equals("SomeoneElse")));
    }
}
