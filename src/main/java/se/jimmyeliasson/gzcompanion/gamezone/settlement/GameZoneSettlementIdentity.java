package se.jimmyeliasson.gzcompanion.gamezone.settlement;

/**
 * The local player's current GameZone settlement identity, as parsed from vanilla-visible TAB data
 * (see {@link GameZoneTabIdentityParser}) - never from a hidden roster API, a command, or manually
 * entered data. Valid ONLY for the current connected GameZone session; a stale identity from a
 * previous connection or a previous settlement must never be reused (see
 * {@link GameZoneSettlementTracker}).
 *
 * @param settlementName the settlement name parsed from the TAB header, or {@code null} if it
 *                        could not be safely recognized.
 * @param role            the parsed role (currently one of {@code KING}, {@code LORD},
 *                        {@code MEMBER}), or {@code null} if unknown.
 * @param settlementPrefix the local player's own current TAB-list prefix token (e.g. the bracketed
 *                        code GameZone renders before a player's name), used ONLY as a
 *                        current-session "same settlement" grouping signal - see that field's use
 *                        in {@link GameZoneTabIdentityParser#isSameSettlement}. This is NOT a
 *                        globally unique or persistent settlement identifier and must never be
 *                        treated as one.
 */
public record GameZoneSettlementIdentity(String settlementName, String role, String settlementPrefix) {
    public static final GameZoneSettlementIdentity UNKNOWN = new GameZoneSettlementIdentity(null, null, null);

    /** True only when a settlement name was actually recognized from the current TAB header. */
    public boolean known() {
        return settlementName != null;
    }
}
