package se.jimmyeliasson.gzcompanion.gamezone;

import se.jimmyeliasson.gzcompanion.gamezone.settlement.GameZoneSettlementIdentity;
import se.jimmyeliasson.gzcompanion.gamezone.settlement.GameZoneSettlementTracker;
import se.jimmyeliasson.gzcompanion.gamezone.status.GameZoneLiveStatus;
import se.jimmyeliasson.gzcompanion.gamezone.status.GameZoneLiveStatusTracker;
import se.jimmyeliasson.gzcompanion.minecraft.OnlinePlayerSnapshot;

import java.util.List;

/**
 * The ONE place any tab refreshes the shared live-GameZone trackers from the current raw
 * Minecraft-bridge data. Refreshing here (rather than each tab calling {@code tracker.update(...)}
 * independently) guarantees a tab gets correct, current live data on the very first frame it is
 * ever opened - opening Home or Online first must never be required to "prime" another tab's live
 * data (see docs/SETTLEMENT-COMPANION.md's "same data from any tab" section).
 *
 * <p>Introduces NO new parsing - {@link GameZoneLiveStatusTracker#update} and {@link
 * GameZoneSettlementTracker#update} remain the sole authorities for their respective facts; this
 * class only calls them (both, together, every time) and combines their results into one {@link
 * GameZoneLiveContext}. Pure aside from the two tracker calls - no Minecraft API type appears
 * here, so this is fully unit-testable with synthetic trackers/inputs.
 */
public final class GameZoneLiveContextBuilder {
    private GameZoneLiveContextBuilder() {}

    public static GameZoneLiveContext refresh(
            GameZoneLiveStatusTracker statusTracker,
            GameZoneSettlementTracker settlementTracker,
            boolean connected,
            String tabHeaderText,
            List<OnlinePlayerSnapshot> onlinePlayers
    ) {
        GameZoneLiveStatus status = statusTracker.update(connected, tabHeaderText);
        GameZoneSettlementIdentity identity = settlementTracker.update(connected, tabHeaderText, onlinePlayers);
        List<String> sameSettlement = connected
                ? List.copyOf(settlementTracker.sameSettlementOnlineUsernames(onlinePlayers))
                : List.of();

        return new GameZoneLiveContext(connected, status, identity, sameSettlement);
    }
}
