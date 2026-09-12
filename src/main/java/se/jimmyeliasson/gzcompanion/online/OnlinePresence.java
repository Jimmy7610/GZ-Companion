package se.jimmyeliasson.gzcompanion.online;

/**
 * How confidently GZ Companion can state a specific player's current online status - deliberately
 * three-valued so a favorite who simply isn't visible right now is never confused with "we have no
 * idea" (disconnected from GameZone entirely). Never a "last seen" - see docs/ONLINE-PLAYERS.md.
 */
public enum OnlinePresence {
    /** Present in the current GameZone player list right now. */
    ONLINE,
    /** Connected to GameZone, but this player is not in the current list. */
    NOT_ONLINE,
    /** Not connected to GameZone at all - no current list exists to check against. */
    UNKNOWN
}
