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
- Reading the vanilla TAB list's header text and each listed player's exact TAB display text -
  the same `Component` data vanilla's own player-list overlay (`PlayerTabOverlay`) already
  received from the server and already renders on screen whenever a player opens the player list
  (see section 6 below for how this determines the current settlement).
- Locally favoriting a player by name, purely as a client-side preference.
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
- Automating GameZone's own `/settlement menu` (or any other GUI) to discover membership - that
  menu visibly exposes a full member roster with online/offline status, which would be far more
  than vanilla's TAB list alone reveals; Companion never opens it, never sends the command, and
  never clicks it on the player's behalf. Only what vanilla's player list already shows is used.

If Minecraft's normal client player list does not expose a player, GZ Companion does not know
about that player either.

## 2. What this is

A new **Online** tab (directly below **Hem**) giving a clean, beginner-friendly overview of
currently-online GameZone players, grouped into:

1. **FAVORITER** - every locally-favorited player, online ones first, then not-online/unknown
   ones, alphabetical within each group.
2. **MIN SETTLEMENT · &lt;name&gt;** - currently-online players GZ Companion can see share the
   local player's CURRENT live GameZone settlement (see section 6), excluding anyone already
   shown as a favorite. Hidden entirely when no current settlement can be safely recognized.
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
  / `MinecraftBridge.getTabHeaderText()` / `VanillaMinecraftBridge` - the real Minecraft-touching
  seam. Exposes only raw, GameZone-agnostic vanilla data: each listed player's exact TAB display
  text (`PlayerTabOverlay.getNameForDisplay(PlayerInfo)`) and the TAB header text (read via
  `se.jimmyeliasson.gzcompanion.mixin.PlayerTabOverlayAccessor`, a minimal Mixin `@Accessor` that
  exposes vanilla's private `header` field, which vanilla itself only offers a setter for). No
  GameZone-specific parsing rules live here.
- `se.jimmyeliasson.gzcompanion.gamezone.settlement` (`GameZoneTabIdentityParser`,
  `GameZoneSettlementIdentity`, `GameZoneSettlementTracker`) - the GameZone-aware layer that
  interprets that raw TAB text into a settlement name/role/prefix, and tracks it live across
  frames with fair, safe-fallback invalidation (see section 6). Fully unit-testable with synthetic
  TAB text, no live Minecraft client needed.
- `se.jimmyeliasson.gzcompanion.online` (`OnlinePlayersGrouping`, `OnlinePlayerRow`,
  `OnlinePlayersView`, `OnlinePresence`, `OnlineSection`, `PingQuality`) - pure grouping/sorting/
  search/ping-threshold logic, fully unit-testable without a live Minecraft client. Takes the
  live same-settlement usernames as plain input; has no GameZone-specific knowledge itself.
- `se.jimmyeliasson.gzcompanion.ui.tabs.OnlineTabComponent` / `OnlineLayout` - rendering and input,
  following the same search-box + scrollable-list + detail-panel pattern already used by the
  Crafting and Settlement tabs.

## 6. Automatic settlement detection (MIN SETTLEMENT)

**MIN SETTLEMENT no longer comes from the manually-entered Settlement tab member list.** It is now
detected automatically from the live GameZone data that vanilla's own TAB/player-list already
delivers to this client - the exact same header text and per-player name text the player already
sees whenever they press the player-list key. GZ Companion does not use any hidden roster API,
does not scan entities, does not send any command, and does not automatically open or click
GameZone's own `/settlement menu → Medlemmar` GUI to discover membership.

- **Settlement name + role**: `GameZoneTabIdentityParser` scans the TAB header's text line-by-line
  for one shaped like `<settlement name> · <ROLE> · <bonus>` (the format human QA observed on the
  real server, e.g. `Trälskärsbukten · MEMBER · +44.3%`). It never assumes a fixed line index -
  unrelated header lines (server MOTD, tips, etc.) are simply skipped - and only recognizes the
  known role words `KING`, `LORD`, `MEMBER`. Anything it cannot confidently match is UNKNOWN; it
  never guesses a settlement name.
- **Settlement prefix**: the SAME parser looks at the local player's own current TAB display text
  (vanilla's `PlayerTabOverlay.getNameForDisplay`) for a short bracketed code immediately before
  their name (the shape GameZone's real TAB list was observed to use, e.g. `[BUS]`). This is never
  a hardcoded table of known prefixes - only the local player's own currently-rendered prefix is
  ever used as the reference, and it is deliberately narrow enough to avoid mistaking a culture
  sigil, a King/Lord role symbol, or a level indicator for a settlement prefix.
- **Same-settlement classification**: every other currently-listed player's own TAB display text is
  compared against that same local prefix. A match means "GameZone is currently rendering this
  player with my same settlement tag" - a live, current-session signal only. This prefix is **not**
  treated or stored as a globally unique or permanent settlement identifier, since GameZone does
  not document it as one.
- **Live invalidation**: `GameZoneSettlementTracker` holds only the CURRENT identity, never a
  history. Disconnecting from GameZone immediately clears it (no stale settlement stays active);
  reconnecting always re-parses from scratch; and if the local player's settlement changes
  mid-session, the very next TAB update replaces the old identity outright, so old settlement
  members stop being grouped the moment the new one is recognized. No previous settlement name is
  kept around "just in case."
- **No settlement recognized**: if the header can't be safely parsed, or the local player has no
  recognizable prefix, MIN SETTLEMENT simply does not appear - never a guess, never an empty
  section with a misleading title.
- **If GameZone changes its TAB format**: because this parser matches by structure/content instead
  of a fixed position, most format changes just mean it stops matching - and per the point above,
  that safely falls back to "no settlement recognized" rather than showing wrong data.

The Settlement tab's own local "Medlemmar" list (`SettlementPlannerManager`) is completely
untouched by this feature, remains available for the player's own manual notes, and continues to
be labeled "Lokala anteckningar - inte serverns riktiga medlemslista" - it is simply no longer read
by the Online tab.

## 7. Human QA sequence

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
6. While connected and a member of a GameZone settlement, confirm a **MIN SETTLEMENT · &lt;name&gt;**
   section appears listing your currently-online settlement-mates (never duplicated with FAVORITER),
   and that your own row shows `DU` if you're not also a favorite.
7. Favorite a player who is also in your settlement; confirm they appear ONLY under FAVORITER (not
   duplicated into MIN SETTLEMENT), and that their detail card still shows "Settlementmedlem ·
   &lt;name&gt;".
8. If possible, move to a different GameZone settlement mid-session (or ask another tester to);
   confirm MIN SETTLEMENT updates to the new settlement's name/members without restarting the game,
   and that old settlement-mates are no longer grouped under it.
9. Leave your settlement entirely (or connect as a player with none); confirm MIN SETTLEMENT
   disappears rather than showing an empty or stale section.
