# Live GameZone Status (Home dashboard)

Status: **Implemented, automated tests passing, awaiting human QA on the real GameZoneMC server.**

> **GZ Companion is an unofficial community project for GameZoneMC. It is not affiliated with or
> endorsed by GameZoneMC.**

## 1. Purpose

The Home ("Hem") tab now includes a **LIVE GAMEZONE** card showing the local player's current
server population, tick rate, city, and economy - alongside the settlement name/role/bonus
automatic detection already ships on the Online tab. It turns the previously mostly-static Home
dashboard into a genuinely live GameZone status view, without requiring the player to open the
vanilla player list themselves or leave the Companion window.

## 2. Data source - fair play

**GZ Companion reads ONLY the vanilla TAB header `Component` that GameZone already sends to this
client and that vanilla's own player-list overlay already renders on screen** - the exact same
seam (`MinecraftBridge.getTabHeaderText()`, backed by the `PlayerTabOverlayAccessor` Mixin) already
used and human-QA-verified for the Online tab's automatic settlement detection. No new Minecraft
access was added for this feature.

**This feature does NOT:**
- Send any command.
- Open or click any GameZone menu or GUI element.
- Scan entities, or reveal hidden/vanished players.
- Inspect any player's inventory or coordinates.
- Access a private server API.
- Use any external API, or poll GameZone's website.
- Add telemetry, a cloud component, or a server component of any kind.
- Infer or guess a value GameZone did not actually render.

If the vanilla TAB header does not contain a recognizable line for a given piece of information,
GZ Companion does not know that value either - see section 4.

## 3. Real header structure

Human QA captured this real GameZoneMC TAB header:

```
          GAMEZONE MC
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
23/100 • TPS 20,0 • 10 - Småstad
Coins 65 068 • Stadskassa 11 487 272
Trälskärsbukten • MEMBER • +44.3%
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

(the spaces inside the money line are real `U+00A0 NBSP` characters, not ordinary spaces - see
`docs/ONLINE-PLAYERS.md` section 6 for the same discovery on the settlement line's separator).
Every line is scanned structurally, never assumed to sit at a fixed index - unrelated lines (the
server name banner, box-drawing dividers, MOTD/tips) are simply ignored, and reordering the
meaningful lines relative to each other has no effect on parsing.

From that header, GZ Companion parses:

| Field | Example value | Source line |
|---|---|---|
| Online players / max players | `23` / `100` | `23/100 • TPS 20,0 • 10 - Småstad` |
| TPS | `20.0` | same line (accepts `,` or `.` as the decimal separator) |
| City level | `10` | same line |
| City name | `Småstad` | same line (everything after `<level> - `) |
| Coins | `65068` | `Coins 65 068 • Stadskassa 11 487 272` (NBSP/space-tolerant) |
| City treasury (Stadskassa) | `11487272` | same line |
| Settlement name | `Trälskärsbukten` | `Trälskärsbukten • MEMBER • +44.3%` (reused from the Online tab's settlement parser) |
| Settlement role | `MEMBER` | same line |
| Settlement bonus | `44.3` | same line |

`Trälskärsbukten`/`MEMBER`/`44.3`/`[TRÄ]`-shaped values are captured real-server EXAMPLES only -
none of them are hardcoded anywhere in the parser; every player's own real values are read fresh
from their own current TAB header.

Both observed header separators are accepted - `•` (U+2022 BULLET, confirmed on the real server)
and `·` (U+00B7 MIDDLE DOT, from an earlier screenshot) - never arbitrary punctuation.

## 4. Architecture

Following the same "Java understands Minecraft, GameZone-specific code understands GameZone"
split already established for the Online tab:

- `se.jimmyeliasson.gzcompanion.minecraft.MinecraftBridge#getTabHeaderText()` /
  `VanillaMinecraftBridge` - unchanged, GameZone-agnostic raw data source, shared with the Online
  tab's settlement detection.
- `se.jimmyeliasson.gzcompanion.gamezone.status`:
  - `GameZoneLiveStatus` - a pure record holding every field independently nullable, plus
    `hasAnyData()`/`hasSettlement()`/`hasCity()`/`hasEconomy()`/`hasServerInfo()` helpers the UI
    uses to decide what to show.
  - `GameZoneTabStatusParser` - pure, stateless parsing of the header text into a
    `GameZoneLiveStatus`. **Reuses**
    `se.jimmyeliasson.gzcompanion.gamezone.settlement.GameZoneTabIdentityParser#parseHeader(String)`
    for the settlement name/role/bonus-text fields, rather than re-implementing that
    already-human-QA-verified definition a second time - there is exactly one place in this
    codebase that decides what a valid settlement line looks like.
  - `GameZoneLiveStatusTracker` - a modest per-frame cache keyed on the raw header text
    (identical strategy to `GameZoneSettlementTracker`): re-parses only when the header text
    actually changed, clears immediately on disconnect, and always reparses fresh on reconnect.
    Holds no history.
  - `GameZoneStatusFormatter` - small, pure, locale-independent display formatting (grouped money,
    one-decimal TPS, signed bonus percentage).
- `se.jimmyeliasson.gzcompanion.ui.tabs.HomeTabComponent` / `se.jimmyeliasson.gzcompanion.ui.layout.LiveGameZoneCardLayout` -
  rendering. The existing dense 5-card Home grid (`HomeTabLayout`) is completely unchanged; the
  live card is appended directly below it, and the Home tab gained whole-page scrolling (mirroring
  the Online tab's scissor+scroll convention) so the new card is reachable even when it doesn't
  fit in the visible window height. When the window is tall enough to show everything without
  scrolling, or the scroll offset is at its default of zero, the existing grid renders
  pixel-identically to before this feature.

## 5. Parsing/fallback behavior

Every field is parsed and reported **independently** - a missing or malformed line never
invalidates fields found on other lines:

- **Player count + TPS + city** all come from one structural line (`<players>/<max> <sep> TPS
  <tps> <sep> <level> - <city name>`); if that whole line doesn't match the expected shape (e.g. a
  non-numeric player count), none of its four fields are guessed - but the money and settlement
  lines, if present, still parse normally.
- **Coins + Stadskassa** come from their own line and are independent of everything else.
- **Settlement name/role/bonus** come from the shared settlement parser and are independent of
  everything else. A settlement line with no bonus segment leaves the bonus `null` - never
  inferred.
- If NONE of the above lines are recognized, `GameZoneLiveStatus.UNKNOWN` is returned
  (`hasAnyData()` is `false`).

The Home card's own display logic then chooses one of four states:

1. **Disconnected** - `GameZoneMC · Inte ansluten`. No values shown, no stale numbers.
2. **Connected, no recognized header data** - `GameZoneMC · Ansluten` / `Live-status kunde inte
   läsas just nu.` - restrained wording that never implies the server itself is broken.
3. **Connected, partial data** - only the sections/values that actually parsed are shown; a
   missing individual value renders as `—`, never a guess, and a whole missing section (e.g. no
   settlement line at all) is replaced with a short muted note rather than hidden entirely or
   padded with fake data.
4. **Connected, full data** - all sections shown: settlement name/role/bonus, city name/level,
   coins/treasury, and player count/TPS.

## 6. Live invalidation - no persistence of authoritative data

`GameZoneLiveStatusTracker` holds only the CURRENT status, exactly like
`GameZoneSettlementTracker`:

- Disconnecting from GameZone immediately clears it - the next render shows the disconnected
  state, never a stale previous session's numbers.
- Reconnecting always re-parses from scratch.
- Any change to the raw header text (player joins/leaves, coins earned, TPS drift, a new
  settlement) is picked up on the very next frame - the player never needs to press the vanilla
  TAB key themselves for this to update, since GZ Companion already reads the same underlying data
  Minecraft already received.
- Nothing here is written to a settings file, a log, or any other persistent store by default.

## 7. Human QA checklist

1. Connect to GameZoneMC. Open the Companion (`G`) on **Hem**. Scroll down (mouse wheel) past the
   existing dashboard cards if needed; confirm a **LIVE GAMEZONE** card appears, badged
   `ANSLUTEN`.
2. Confirm the settlement name/role/bonus shown match what the vanilla TAB list itself shows.
3. Confirm the STAD section's city name/level, EKONOMI section's coins/stadskassa, and SERVER
   section's player count/TPS all match the vanilla TAB header.
4. Wait for something to change server-side (another player joining/leaving, coins earned, TPS
   drifting) - confirm the card updates within a moment, without pressing TAB or reopening the
   Companion.
5. Disconnect from GameZoneMC (or open a singleplayer world). Confirm the card immediately shows
   `GameZoneMC · Inte ansluten` with no leftover numbers from the previous session.
6. Reconnect. Confirm fresh, correct values appear again.
7. Resize the Companion window to a small/compact size. Confirm the card's three sub-cards stack
   vertically, nothing overlaps or clips, and text stays readable.
8. Confirm the existing Home cards (Welcome, Serverstatus, version strip, Nästa uppgift,
   Modulstatus) look and behave exactly as before this feature.
