package se.jimmyeliasson.gzcompanion.gamezone.status;

/**
 * The local player's current live GameZone server status, parsed from vanilla-visible TAB header
 * text (see {@link GameZoneTabStatusParser}) - never from a hidden API, a command, or a menu.
 * Every field is independently nullable: one field failing to parse (or simply not being present
 * in this server build's header) must never invalidate the others - a partial status (e.g. player
 * count known, coins unknown) is still useful and must still be shown. There is deliberately no
 * "confidence"/"valid" flag on the whole record; {@link #hasAnyData()} only reports whether ANY
 * field is known, for the Home tab's fallback-message decision.
 *
 * @param onlinePlayers  current players online on this server, or {@code null} if unknown.
 * @param maxPlayers     the server's player slot cap, or {@code null} if unknown.
 * @param tps             the server's current ticks-per-second, or {@code null} if unknown.
 * @param cityLevel      the local player's city/town level, or {@code null} if unknown.
 * @param cityName       the local player's city/town name, or {@code null} if unknown.
 * @param coins          the local player's personal coin balance, or {@code null} if unknown.
 * @param treasury       the local player's city treasury balance, or {@code null} if unknown.
 * @param settlementName the local player's current settlement name (reused from
 *                       {@link se.jimmyeliasson.gzcompanion.gamezone.settlement.GameZoneTabIdentityParser}
 *                       - see that class's javadoc), or {@code null} if unknown.
 * @param settlementRole the local player's current settlement role (KING/LORD/MEMBER), or
 *                       {@code null} if unknown.
 * @param settlementBonusPercent the settlement's current bonus percentage, or {@code null} if
 *                       unknown/absent.
 */
public record GameZoneLiveStatus(
        Integer onlinePlayers,
        Integer maxPlayers,
        Double tps,
        Integer cityLevel,
        String cityName,
        Long coins,
        Long treasury,
        String settlementName,
        String settlementRole,
        Double settlementBonusPercent
) {
    public static final GameZoneLiveStatus UNKNOWN =
            new GameZoneLiveStatus(null, null, null, null, null, null, null, null, null, null);

    /** True if at least one field was successfully parsed - used to pick the Home tab's fallback message. */
    public boolean hasAnyData() {
        return onlinePlayers != null || maxPlayers != null || tps != null || cityLevel != null
                || cityName != null || coins != null || treasury != null
                || settlementName != null || settlementRole != null || settlementBonusPercent != null;
    }

    public boolean hasSettlement() {
        return settlementName != null;
    }

    public boolean hasCity() {
        return cityLevel != null || cityName != null;
    }

    public boolean hasEconomy() {
        return coins != null || treasury != null;
    }

    public boolean hasServerInfo() {
        return onlinePlayers != null || maxPlayers != null || tps != null;
    }
}
