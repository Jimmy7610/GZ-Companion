package se.jimmyeliasson.gzcompanion.gamezone.settlement;

import se.jimmyeliasson.gzcompanion.minecraft.OnlinePlayerSnapshot;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Holds the local player's LIVE GameZone settlement identity across frames - the "modest live
 * snapshot/cache" this feature needs so the (cheap, but non-zero) header/prefix parse only reruns
 * when the underlying TAB data actually changed, while same-settlement membership is always
 * recomputed fresh against the current online player list so a mid-session change is reflected the
 * moment it's observed.
 *
 * <p>Deliberately holds NO settlement history - disconnecting immediately discards the current
 * identity ({@link GameZoneSettlementIdentity#UNKNOWN}) rather than keeping a "last known"
 * settlement around, and a fresh connection always reparses from scratch. This class is pure
 * (no Minecraft dependency) and fully unit-testable with synthetic snapshots.
 */
public final class GameZoneSettlementTracker {
    private boolean lastConnected = false;
    private String lastHeaderText;
    private String lastLocalDisplayText;
    private String lastLocalUsername;
    private GameZoneSettlementIdentity identity = GameZoneSettlementIdentity.UNKNOWN;

    /**
     * Refreshes the tracked identity from the current raw TAB data. Must be called with
     * {@code connected = false} the moment GameZone disconnects - this immediately clears the
     * tracked identity rather than leaving a stale settlement active.
     */
    public GameZoneSettlementIdentity update(boolean connected, String headerText, List<OnlinePlayerSnapshot> onlinePlayers) {
        if (!connected) {
            reset();
            return identity;
        }

        OnlinePlayerSnapshot local = findLocalPlayer(onlinePlayers);
        String localDisplayText = local != null ? local.tabDisplayText() : null;
        String localUsername = local != null ? local.username() : null;

        boolean unchanged = lastConnected
                && Objects.equals(lastHeaderText, headerText)
                && Objects.equals(lastLocalDisplayText, localDisplayText)
                && Objects.equals(lastLocalUsername, localUsername);
        if (!unchanged) {
            identity = GameZoneTabIdentityParser.identityFor(headerText, localDisplayText, localUsername);
            lastConnected = true;
            lastHeaderText = headerText;
            lastLocalDisplayText = localDisplayText;
            lastLocalUsername = localUsername;
        }
        return identity;
    }

    /** The identity as of the most recent {@link #update}. */
    public GameZoneSettlementIdentity current() {
        return identity;
    }

    /**
     * Usernames (original casing) of currently-online players who share the local player's current
     * settlement prefix - including the local player themself when they have one, so they can be
     * grouped under MIN SETTLEMENT with a DU marker. Always recomputed against the given live list,
     * never cached, so a player joining/leaving/changing settlement is reflected immediately.
     * Empty whenever the local prefix itself is unknown - never a guess.
     */
    public Set<String> sameSettlementOnlineUsernames(List<OnlinePlayerSnapshot> onlinePlayers) {
        String prefix = identity.settlementPrefix();
        if (prefix == null || onlinePlayers == null) return Set.of();

        Set<String> result = new HashSet<>();
        for (OnlinePlayerSnapshot player : onlinePlayers) {
            if (player == null || player.username() == null || player.username().isBlank()) continue;
            if (GameZoneTabIdentityParser.isSameSettlement(player.tabDisplayText(), player.username(), prefix)) {
                result.add(player.username());
            }
        }
        return result;
    }

    private void reset() {
        identity = GameZoneSettlementIdentity.UNKNOWN;
        lastConnected = false;
        lastHeaderText = null;
        lastLocalDisplayText = null;
        lastLocalUsername = null;
    }

    private static OnlinePlayerSnapshot findLocalPlayer(List<OnlinePlayerSnapshot> players) {
        if (players == null) return null;
        for (OnlinePlayerSnapshot player : players) {
            if (player != null && player.localPlayer()) return player;
        }
        return null;
    }
}
