# Online Players & Favorites

Status: **Implemented, pending human gameplay QA.**

> **GZ Companion is an unofficial community project for GameZoneMC. It is not affiliated with or
> endorsed by GameZoneMC.**

## 1. Fair-Play Boundary

GZ Companion knows ONLY who the vanilla Minecraft client's own player list already showed the
player - the exact same data that backs the normal in-game player list.

**Allowed:**
- Reading `ClientPacketListener.getListedOnlinePlayers()` - the same collection the vanilla
  player list (tab list) itself renders from, so it can never contain a player the server marked
  unlisted/hidden from that list.
- Showing a player's username, whether they are the local player, and Minecraft's own
  already-known `PlayerInfo` latency value.
- Locally favoriting a player by name, purely as a client-side preference.
- Locally matching an online player's name against GZ Companion's own local settlement member
  list (already-existing, player-entered data) for a badge.
- Copying a player's exact username to the OS clipboard on request.

**Forbidden — and not implemented anywhere in this module:**
- Querying hidden/server-internal player data, or detecting vanished/hidden players.
- Scanning entities to discover players.
- Showing coordinates, distance, dimension, or inventory contents of any player.
- Tracking movement.
- Recording "last seen", join times, or leave times - a favorite who isn't currently visible is
  either `NOT_ONLINE` (connected, but absent from the current list) or `UNKNOWN` (not connected to
  GameZone at all), never a remembered history.
- Polling an external API, or probing the network for latency ourselves - `latencyMs` is always
  read directly from Minecraft's own `PlayerInfo`, never measured independently.
- Inferring or guessing at a hidden player's existence.
- Sending chat messages or running commands on the player's behalf (the "Kopiera namn" button only
  ever writes to the OS clipboard).

If Minecraft's normal client player list does not expose a player, GZ Companion does not know
about that player either.

## 2. What this is

A new **Online** tab (directly below **Hem**) giving a clean, beginner-friendly overview of
currently-online GameZone players, grouped into:

1. **FAVORITER** - every locally-favorited player, online ones first, then not-online/unknown
   ones, alphabetical within each group.
2. **MIN SETTLEMENT** - currently-online players who match a locally-known settlement member,
   excluding anyone already shown as a favorite.
3. **ÖVRIGA ONLINE** - everyone else currently online.

No player is ever shown in more than one section. Search filters all three sections at once,
case-insensitively, by partial username match.

When not connected to GameZone, the player list itself is unavailable (`Online-listan är
tillgänglig när du är ansluten till GameZoneMC.`), but locally-stored favorites still show, with
their status reported honestly as `Status okänd` rather than a fabricated "offline".

## 3. Favorites persistence

Favorite player names are stored as a plain string list (`favoritePlayers`) inside the existing
global `config/gzcompanion/settings.json` (see `docs/SETTINGS.md`) - nothing else about a player
is ever persisted. Lookups are case-insensitive; the originally-stored casing is preserved for
display when a favorite isn't currently visible. No schema bump was needed to add this field: a
settings file saved before it existed simply defaults to an empty favorites list on load, and any
malformed entry in the array (wrong type, blank, null) is skipped individually rather than failing
the whole load.

## 4. Architecture

- `se.jimmyeliasson.gzcompanion.minecraft.OnlinePlayerSnapshot` / `MinecraftBridge.getOnlinePlayers()`
  / `VanillaMinecraftBridge` - the real Minecraft-touching seam. No GameZone-specific assumptions
  live here.
- `se.jimmyeliasson.gzcompanion.online` (`OnlinePlayersGrouping`, `OnlinePlayerRow`,
  `OnlinePlayersView`, `OnlinePresence`, `OnlineSection`, `PingQuality`) - pure grouping/sorting/
  search/ping-threshold logic, fully unit-testable without a live Minecraft client.
- `se.jimmyeliasson.gzcompanion.ui.tabs.OnlineTabComponent` / `OnlineLayout` - rendering and input,
  following the same search-box + scrollable-list + detail-panel pattern already used by the
  Crafting and Settlement tabs.

## 5. Human QA sequence

1. Connect to GameZoneMC. Open the Companion (`G`) → **Online**. Confirm the player count in the
   header matches what the vanilla player list (tab key) shows, and that your own name is marked
   `DU`.
2. Star a player to favorite them; confirm they move into **FAVORITER**. Un-star them from both the
   row and the detail panel; confirm they move back to their original section.
3. Fully restart Minecraft. Confirm favorites are still there.
4. Disconnect from GameZoneMC (or open a singleplayer world). Open **Online** again; confirm the
   unavailable message shows, and any favorites still show with `Status okänd` - never "Inte online".
5. On Hem, confirm the connected server status row's player count is clickable and jumps straight
   to the Online tab.
