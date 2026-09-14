package se.jimmyeliasson.gzcompanion.gamezone;

import se.jimmyeliasson.gzcompanion.gamezone.settlement.GameZoneSettlementIdentity;
import se.jimmyeliasson.gzcompanion.gamezone.status.GameZoneLiveStatus;

import java.util.List;

/**
 * One render's combined live GameZone facts - the local player's current connection state,
 * status (server/city/economy/settlement), settlement identity (name/role/prefix), and the
 * currently-online players who share the local player's settlement prefix. Produced ONLY by
 * {@link GameZoneLiveContextBuilder#refresh}, which is the ONE place any tab should call to
 * guarantee fresh data regardless of which other tabs have or haven't rendered this session - see
 * that class's own doc comment.
 *
 * <p>This is a pure combination of already-parsed facts - it introduces no new parsing. Every
 * field ultimately comes from {@link GameZoneLiveStatus} (via {@link
 * se.jimmyeliasson.gzcompanion.gamezone.status.GameZoneTabStatusParser}) and {@link
 * GameZoneSettlementIdentity} (via {@link
 * se.jimmyeliasson.gzcompanion.gamezone.settlement.GameZoneTabIdentityParser}), both of which
 * already parse the exact same vanilla-visible TAB header text.
 */
public record GameZoneLiveContext(
        boolean connected,
        GameZoneLiveStatus status,
        GameZoneSettlementIdentity identity,
        List<String> sameSettlementOnlineUsernames
) {
    public static final GameZoneLiveContext DISCONNECTED =
            new GameZoneLiveContext(false, GameZoneLiveStatus.UNKNOWN, GameZoneSettlementIdentity.UNKNOWN, List.of());

    public GameZoneLiveContext {
        sameSettlementOnlineUsernames = sameSettlementOnlineUsernames != null
                ? List.copyOf(sameSettlementOnlineUsernames) : List.of();
    }

    /** True only when connected AND a settlement name was actually recognized from the current TAB header. */
    public boolean settlementRecognized() {
        return connected && identity.known();
    }
}
