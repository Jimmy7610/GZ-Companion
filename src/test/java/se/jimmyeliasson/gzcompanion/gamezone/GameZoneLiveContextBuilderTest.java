package se.jimmyeliasson.gzcompanion.gamezone;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import se.jimmyeliasson.gzcompanion.gamezone.settlement.GameZoneSettlementTracker;
import se.jimmyeliasson.gzcompanion.gamezone.status.GameZoneLiveStatusTracker;
import se.jimmyeliasson.gzcompanion.minecraft.OnlinePlayerSnapshot;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link GameZoneLiveContextBuilder} - the single shared entry point every tab (Home, Online,
 * Settlement) must call to refresh live GameZone data, so opening any ONE of them first can never
 * be required to "prime" another's live data. No live Minecraft client needed - only synthetic
 * raw TAB data, exactly like {@link GameZoneSettlementTracker}'s own tests.
 */
class GameZoneLiveContextBuilderTest {

    private static OnlinePlayerSnapshot local(String name, String display) {
        return new OnlinePlayerSnapshot(name, true, 20, display);
    }

    private static OnlinePlayerSnapshot other(String name, String display) {
        return new OnlinePlayerSnapshot(name, false, 50, display);
    }

    private static final String REAL_HEADER =
            "23/100 • TPS 20,0 • 10 - Småstad\n"
            + "Coins 65 068 • Stadskassa 11 487 272\n"
            + "Trälskärsbukten • MEMBER • +44.3%";

    @Test
    @DisplayName("A: recognized live settlement - name/role/bonus/level/level-name/treasury all resolve from ONE refresh call")
    void recognizedLiveSettlementResolvesEveryFact() {
        GameZoneLiveStatusTracker statusTracker = new GameZoneLiveStatusTracker();
        GameZoneSettlementTracker settlementTracker = new GameZoneSettlementTracker();
        List<OnlinePlayerSnapshot> players = List.of(local("jbl76", "[TRÄ] jbl76"));

        GameZoneLiveContext ctx = GameZoneLiveContextBuilder.refresh(statusTracker, settlementTracker, true, REAL_HEADER, players);

        assertTrue(ctx.connected());
        assertTrue(ctx.settlementRecognized());
        assertEquals("Trälskärsbukten", ctx.identity().settlementName());
        assertEquals("MEMBER", ctx.identity().role());
        assertEquals(10, ctx.status().cityLevel());
        assertEquals("Småstad", ctx.status().cityName());
        assertEquals(11_487_272L, ctx.status().treasury());
        assertEquals(44.3, ctx.status().settlementBonusPercent(), 0.001);
    }

    @Test
    @DisplayName("K: opening Settlement WITHOUT any other tab having run first still resolves live context correctly - "
            + "fresh tracker instances, never touched before, prove there is no tab-priming dependency")
    void freshTrackersResolveLiveContextWithNoPriorPriming() {
        // Deliberately brand-new trackers, exactly as CompanionSession constructs them once at
        // startup - nothing here simulates Home or Online having rendered even once.
        GameZoneLiveStatusTracker neverTouchedStatusTracker = new GameZoneLiveStatusTracker();
        GameZoneSettlementTracker neverTouchedSettlementTracker = new GameZoneSettlementTracker();
        List<OnlinePlayerSnapshot> players = List.of(local("jbl76", "[TRÄ] jbl76"));

        GameZoneLiveContext ctx = GameZoneLiveContextBuilder.refresh(
                neverTouchedStatusTracker, neverTouchedSettlementTracker, true, REAL_HEADER, players);

        assertTrue(ctx.settlementRecognized(), "the very first call must already resolve the live settlement - no priming needed");
        assertEquals("Trälskärsbukten", ctx.identity().settlementName());
    }

    @Test
    @DisplayName("C/L: disconnecting clears live state immediately, and reconnecting resolves fresh data with no stale carryover")
    void disconnectThenReconnectNeverLeavesStaleData() {
        GameZoneLiveStatusTracker statusTracker = new GameZoneLiveStatusTracker();
        GameZoneSettlementTracker settlementTracker = new GameZoneSettlementTracker();
        List<OnlinePlayerSnapshot> players = List.of(local("jbl76", "[TRÄ] jbl76"));

        GameZoneLiveContext connected = GameZoneLiveContextBuilder.refresh(statusTracker, settlementTracker, true, REAL_HEADER, players);
        assertTrue(connected.settlementRecognized());

        GameZoneLiveContext disconnected = GameZoneLiveContextBuilder.refresh(statusTracker, settlementTracker, false, null, List.of());
        assertFalse(disconnected.connected());
        assertFalse(disconnected.settlementRecognized());
        assertEquals(GameZoneLiveContext.DISCONNECTED, disconnected);
        assertTrue(disconnected.sameSettlementOnlineUsernames().isEmpty());

        String differentHeader = "40/100 • TPS 19,8 • 15 - Stad\nCoins 100 • Stadskassa 200\nUddevalla • KING • +2.0%";
        GameZoneLiveContext reconnected = GameZoneLiveContextBuilder.refresh(statusTracker, settlementTracker, true, differentHeader, players);
        assertEquals("Uddevalla", reconnected.identity().settlementName(), "must reparse fresh, never reuse the pre-disconnect settlement");
        assertEquals(15, reconnected.status().cityLevel());
    }

    @Test
    @DisplayName("J: same-settlement online usernames contain no duplicates and correctly include the local player")
    void sameSettlementOnlineUsernamesHasNoDuplicatesAndIncludesLocalPlayer() {
        GameZoneLiveStatusTracker statusTracker = new GameZoneLiveStatusTracker();
        GameZoneSettlementTracker settlementTracker = new GameZoneSettlementTracker();
        List<OnlinePlayerSnapshot> players = List.of(
                local("jbl76", "[TRÄ] jbl76"),
                other("Olivre", "[TRÄ] Olivre"),
                other("SomeoneElse", "[BUS] SomeoneElse")
        );

        GameZoneLiveContext ctx = GameZoneLiveContextBuilder.refresh(statusTracker, settlementTracker, true, REAL_HEADER, players);

        assertEquals(2, ctx.sameSettlementOnlineUsernames().size());
        assertTrue(ctx.sameSettlementOnlineUsernames().contains("jbl76"));
        assertTrue(ctx.sameSettlementOnlineUsernames().contains("Olivre"));
        assertFalse(ctx.sameSettlementOnlineUsernames().contains("SomeoneElse"));
        assertEquals(ctx.sameSettlementOnlineUsernames().size(),
                java.util.Set.copyOf(ctx.sameSettlementOnlineUsernames()).size(), "no duplicates");
    }

    @Test
    @DisplayName("ARCHITECTURE: Home, Online, and Settlement all call the SAME shared GameZoneLiveContextBuilder.refresh - "
            + "no tab independently calls tracker.update(...) itself, so opening any ONE of them first can never be "
            + "required to \"prime\" another's live data")
    void everyTabCallsTheSharedBuilderNotTheTrackersDirectly() throws java.io.IOException {
        java.nio.file.Path homeTab = java.nio.file.Path.of("src", "main", "java", "se", "jimmyeliasson", "gzcompanion", "ui", "tabs", "HomeTabComponent.java");
        java.nio.file.Path onlineTab = java.nio.file.Path.of("src", "main", "java", "se", "jimmyeliasson", "gzcompanion", "ui", "tabs", "OnlineTabComponent.java");
        java.nio.file.Path settlementTab = java.nio.file.Path.of("src", "main", "java", "se", "jimmyeliasson", "gzcompanion", "ui", "tabs", "SettlementTabComponent.java");

        for (java.nio.file.Path tab : List.of(homeTab, onlineTab, settlementTab)) {
            assertTrue(java.nio.file.Files.isRegularFile(tab), "expected " + tab + " to exist");
            String source = java.nio.file.Files.readString(tab);
            assertTrue(source.contains("GameZoneLiveContextBuilder.refresh"),
                    tab + " must refresh live data via the shared GameZoneLiveContextBuilder, not independently");
            assertFalse(source.contains(".getSettlementTracker().update(") || source.contains(".getLiveStatusTracker().update("),
                    tab + " must never call tracker.update(...) directly - that would reintroduce a tab-to-tab priming dependency");
        }
    }

    @Test
    @DisplayName("Zero same-settlement players besides self - the local player alone still resolves correctly, no crash, no phantom members")
    void onlySelfInSettlementResolvesCorrectly() {
        GameZoneLiveStatusTracker statusTracker = new GameZoneLiveStatusTracker();
        GameZoneSettlementTracker settlementTracker = new GameZoneSettlementTracker();
        List<OnlinePlayerSnapshot> players = List.of(local("jbl76", "[TRÄ] jbl76"));

        GameZoneLiveContext ctx = GameZoneLiveContextBuilder.refresh(statusTracker, settlementTracker, true, REAL_HEADER, players);

        assertEquals(1, ctx.sameSettlementOnlineUsernames().size());
        assertEquals(List.of("jbl76"), ctx.sameSettlementOnlineUsernames());
    }

    @Test
    @DisplayName("F: a partial header (no settlement line at all) leaves settlement fields unknown without inventing values")
    void partialHeaderLeavesSettlementFieldsUnknown() {
        GameZoneLiveStatusTracker statusTracker = new GameZoneLiveStatusTracker();
        GameZoneSettlementTracker settlementTracker = new GameZoneSettlementTracker();
        String headerWithNoSettlementLine = "23/100 • TPS 20,0 • 10 - Småstad\nCoins 65 068 • Stadskassa 11 487 272";

        GameZoneLiveContext ctx = GameZoneLiveContextBuilder.refresh(statusTracker, settlementTracker, true, headerWithNoSettlementLine, List.of());

        assertFalse(ctx.settlementRecognized());
        assertNull(ctx.status().settlementName());
        assertNull(ctx.status().settlementBonusPercent());
        assertEquals(10, ctx.status().cityLevel(), "the city/level line parsed independently must still be known");
    }
}
