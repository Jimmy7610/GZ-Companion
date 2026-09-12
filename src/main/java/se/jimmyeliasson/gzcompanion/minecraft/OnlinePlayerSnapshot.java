package se.jimmyeliasson.gzcompanion.minecraft;

/**
 * One player currently known to the vanilla Minecraft client's own player list - the exact same
 * list that backs the normal tab/player list (F3 player list), nothing beyond what Minecraft
 * itself already delivered to this client. No GameZone-specific assumptions live here; see
 * {@code se.jimmyeliasson.gzcompanion.online} for anything GameZone-aware built on top of this.
 *
 * <p>Fair-play boundary: if Minecraft's normal client player list does not expose a player, GZ
 * Companion does not know about that player either. This record never carries coordinates,
 * inventory contents, or movement/history data - only identity, latency, and "is this me".
 */
public record OnlinePlayerSnapshot(String username, boolean localPlayer, int latencyMs) {}
