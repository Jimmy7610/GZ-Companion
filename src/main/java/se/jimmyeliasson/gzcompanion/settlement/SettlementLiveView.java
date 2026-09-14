package se.jimmyeliasson.gzcompanion.settlement;

import se.jimmyeliasson.gzcompanion.gamezone.GameZoneLiveContext;
import se.jimmyeliasson.gzcompanion.gamezone.settlement.GameZoneSettlementIdentity;
import se.jimmyeliasson.gzcompanion.gamezone.status.GameZoneLiveStatus;
import se.jimmyeliasson.gzcompanion.knowledge.settlement.SettlementCatalog;

import java.util.List;

/**
 * Settlement's own combined live view for one render - built ONCE from the shared {@link
 * GameZoneLiveContext} (never re-parsed) plus Rule Pack alignment via {@link LiveSettlementLevel}.
 * This is the ONE object {@code SettlementTabComponent} reads to decide LIVE vs PLANERING, which
 * level to plan against (together with {@link EffectiveCurrentLevel}), and what to show for
 * role/treasury/bonus/online members. Introduces no new parsing and holds no mutable state.
 *
 * @param connected                     mirrors {@link GameZoneLiveContext#connected()}.
 * @param settlementRecognized          true only when connected AND a settlement name was
 *                                      actually recognized from the current TAB header.
 * @param settlementName                the recognized settlement name, or {@code null}.
 * @param role                          the local player's recognized role (KING/LORD/MEMBER), or
 *                                      {@code null}.
 * @param bonusPercent                  the settlement's current bonus percentage, or {@code null}.
 * @param treasury                      the city treasury balance, or {@code null}.
 * @param liveLevel                     the raw live level/name plus its catalog-alignment
 *                                      verdict - see {@link LiveSettlementLevel}.
 * @param sameSettlementOnlineUsernames currently-online players (original casing, including the
 *                                      local player) sharing the local player's settlement
 *                                      prefix - NEVER a full roster, only who is visible in the
 *                                      current TAB/player-list data right now.
 */
public record SettlementLiveView(
        boolean connected,
        boolean settlementRecognized,
        String settlementName,
        String role,
        Double bonusPercent,
        Long treasury,
        LiveSettlementLevel liveLevel,
        List<String> sameSettlementOnlineUsernames
) {
    public static final SettlementLiveView NONE =
            new SettlementLiveView(false, false, null, null, null, null, LiveSettlementLevel.NONE, List.of());

    public SettlementLiveView {
        sameSettlementOnlineUsernames = sameSettlementOnlineUsernames != null
                ? List.copyOf(sameSettlementOnlineUsernames) : List.of();
    }

    public static SettlementLiveView from(GameZoneLiveContext context, SettlementCatalog catalog) {
        if (context == null || !context.connected()) {
            return NONE;
        }
        GameZoneSettlementIdentity identity = context.identity();
        GameZoneLiveStatus status = context.status();
        LiveSettlementLevel liveLevel = LiveSettlementLevel.evaluate(status.cityLevel(), status.cityName(), catalog);

        return new SettlementLiveView(
                true,
                identity.known(),
                identity.settlementName(),
                identity.role(),
                status.settlementBonusPercent(),
                status.treasury(),
                liveLevel,
                context.sameSettlementOnlineUsernames()
        );
    }
}
