package se.jimmyeliasson.gzcompanion.minecraft;

/**
 * One player currently known to the vanilla Minecraft client's own player list - the exact same
 * list that backs the normal tab/player list (F3 player list), nothing beyond what Minecraft
 * itself already delivered to this client. No GameZone-specific assumptions live here; see
 * {@code se.jimmyeliasson.gzcompanion.online} and {@code se.jimmyeliasson.gzcompanion.gamezone.settlement}
 * for anything GameZone-aware built on top of this.
 *
 * <p>Fair-play boundary: if Minecraft's normal client player list does not expose a player, GZ
 * Companion does not know about that player either. This record never carries coordinates,
 * inventory contents, or movement/history data - only identity, latency, and "is this me".
 *
 * @param tabDisplayText this player's exact TAB list display text, as vanilla's own
 *                        {@code PlayerTabOverlay.getNameForDisplay(PlayerInfo)} renders it
 *                        (flattened to plain text - no GameZone parsing happens here), or
 *                        {@code null} when unavailable. This is raw vanilla-visible data only;
 *                        interpreting it into a settlement identity is GameZone-aware logic and
 *                        does not belong in this package.
 */
public record OnlinePlayerSnapshot(String username, boolean localPlayer, int latencyMs, String tabDisplayText) {
    public OnlinePlayerSnapshot(String username, boolean localPlayer, int latencyMs) {
        this(username, localPlayer, latencyMs, null);
    }
}
