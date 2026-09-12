package se.jimmyeliasson.gzcompanion.online;

/**
 * One row of the Online tab's grouped/sorted player list - the output of
 * {@link OnlinePlayersGrouping#build}. Never carries coordinates, distance, inventory, or
 * history - only what's needed to render one clean row and its detail card.
 *
 * @param displayName     the real Minecraft username, in its authoritative current casing when
 *                        online, or the stored favorite casing when not.
 * @param localPlayer     true if this row is the player using this client right now ("DU").
 * @param favorite        true if this exact player is in the local favorites list.
 * @param settlementMember true if this player's name matches a locally-known settlement member.
 * @param presence        see {@link OnlinePresence} - never claims more than the current GameZone
 *                        player list (or its absence) actually supports.
 * @param latencyMs       Minecraft's own already-known PlayerInfo latency, or null when unknown
 *                        (not currently online, or the connection didn't report one).
 * @param section         which section of the list this row belongs in.
 */
public record OnlinePlayerRow(
    String displayName,
    boolean localPlayer,
    boolean favorite,
    boolean settlementMember,
    OnlinePresence presence,
    Integer latencyMs,
    OnlineSection section
) {}
