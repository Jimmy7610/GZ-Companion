# Settlement Companion (Milestone 6 + Live Settlement Dashboard)

Status: **Implementation + human gameplay QA: COMPLETE.** Prepared for release
`v0.1.0-alpha.6` (release preparation done by this pass; GitHub publication is a
separate, not-yet-done step).

### Human QA

Manually tested in the real Minecraft Launcher against a live GameZone server. Verified:

- Settlement opens directly (cold) without first opening Home or Online.
- LIVE settlement identity, level + level name, role, bonus, and Stadskassa all display
  correctly and match vanilla TAB.
- A live level change during play is picked up and reflected correctly.
- Översikt scrolls fully to its actual bottom with no clipped content.
- Progression's derived state and its detail view (including scrolling, with the fixed
  action buttons staying pinned and never hiding content) work correctly, in both
  normal/large and compact layouts.
- Material mode starts at the live level and its own scrolling and +/- owned-amount
  controls work.
- Medlemmar's online-same-settlement section displays correctly, and local member notes
  stay independent of live online data.
- No clipping/overflow issues remain from this QA pass.

## What this is

A LIVE GameZone settlement dashboard when connected to GameZoneMC and a settlement is safely
recognized, backed by a local reference/planner/calculator/organizer for everything live data
doesn't (or can't) cover. It works fully offline, including in singleplayer - when disconnected,
or when no settlement can be safely recognized from live data, every local/offline planning
feature keeps working exactly as before this pass.

"Java understands Minecraft. Data understands GameZone": every static settlement fact (level
names, coin costs, material requirements, foundation rules, production categories) lives in the
Rule Pack (`gamezone-pack/settlement-levels.json`, `gamezone-pack/settlements.json`), not
hardcoded in Java. Every LIVE fact (settlement name/role/bonus/treasury/level, and which
same-settlement players are currently online) comes from the exact same vanilla-visible TAB header
text Home and Online already legitimately parse - never a second parser, never a new network
source, never entity/world/chunk scanning.

## Live settlement dashboard

When connected to GameZoneMC and a settlement is recognized from the current TAB header, the
Settlement tab's header shows a **LIVE** badge (green dot) instead of **PLANERING** (grey dot),
and:

- **Översikt** shows a "MITT SETTLEMENT · LIVE" card: settlement name, level · level name, the
  local player's recognized role (KING/LORD/MEMBER), settlement bonus, city treasury, and how many
  currently-online players share the local player's settlement.
- **Progression** automatically marks the live level "DU ÄR HÄR · LIVE", the level after it
  "NÄSTA", and the locally chosen target "MÅL" - independently, so a level can show more than one
  badge at once (e.g. NÄSTA and MÅL together).
- **Material** automatically starts the required-material range at the live level - the player
  only chooses a target, exactly like requirement 6 of this pass intends.
- **Medlemmar** shows an "ONLINE FRÅN DITT SETTLEMENT · N" section above the local notes, listing
  ONLY players currently visible in live TAB data who share the local player's settlement - never
  a full roster, never an inferred "offline" state for someone merely absent from the list.

If no settlement can be safely recognized from live data (connected, but the TAB header doesn't
match the expected settlement-line shape), Översikt shows: *"Inget settlement kunde identifieras
från GameZones live-data."* - never silently falling back to some other guess. If disconnected
entirely, the LIVE badge and every live-only section simply disappear, and the tab behaves exactly
as the original offline-only version did.

### Raw live facts vs. verified Rule Pack alignment

GameZone's live level/level-name (e.g. `level=10`, `levelName="Småstad"`) is checked against the
bundled `SettlementCatalog` (`LiveSettlementLevel.evaluate`) before it is ever allowed to drive
automatic Progression/Material planning:

- **ALIGNED** (the catalog has that level, under that same name, comparing only case and
  surrounding whitespace - never a fuzzy match): the live level becomes the **effective current
  level** and safely drives Progression/Material.
- **MISMATCH** (the catalog doesn't have that level number, or has it under a different name): the
  raw live level/name are still shown honestly - GameZone's own reported values are NEVER hidden
  or "corrected" - but a restrained warning appears ("Live-data och Companion-datan skiljer sig.")
  and automatic planning fails closed to the manual planner instead.
- **UNKNOWN** (no live level at all, or a level number with no accompanying name to compare):
  nothing is guessed either way; planning also falls back to the manual planner.

### Effective current level - never mutates your manual plan

`EffectiveCurrentLevel.resolve` decides, purely at READ time, which level Progression/Material
should actually use: the trusted live level when available, else the player's own manually chosen
"Planerad nuvarande nivå". This is a pure function over a plain `Integer` - it has no access to
`SettlementPlannerManager` at all, so it is structurally impossible for reading the effective level
to ever write/overwrite the manually saved value. A live level appearing (or disappearing on
disconnect) can therefore never corrupt local planning state. While a trusted live level is active,
the existing "Sätt som nuvarande" planning button is clearly relabeled ("Sätt som OFFLINE-nuvarande")
so it never looks like it changes the player's real GameZone level - it still only ever writes to
the same manual/offline fallback field.

### Same data from any tab

Settlement, Home, and Online all refresh their shared live trackers (`GameZoneLiveStatusTracker`,
`GameZoneSettlementTracker`) through the exact same `GameZoneLiveContextBuilder.refresh(...)` call
- opening Home or Online is never required to "prime" Settlement's live data, and vice versa.
Connecting to GameZone, pressing G, and going straight to Settlement (without ever opening Home or
Online) resolves correct live data on the very first frame.

## Source policy and the 15-level vs 50-level conflict

The GameZone Wiki underwent a system migration. Some older, individual settlement articles still
describe an obsolete 15-level progression. This pass used ONLY the current canonical pages:

- Settlement progression (PRIMARY): `https://www.gamezonemc.se/wiki/settlements/settlement-upgrades`
  — self-declares `ENGINE-STATUS: Settlement Levels 1.0`, `NIVÅER: 50`, `UPPGRADERINGAR: 49`,
  `MAXNIVÅ: Imperium`.
- Settlement foundation: `https://www.gamezonemc.se/wiki/settlements/skapa-ett-settlement`
- Production categories: the categories page linked from the Wiki root.

`gamezone-pack/settlement-levels.json` therefore models **exactly 50 levels** (level 1 through
level 50), never the old 15-level mapping. `SettlementKnowledgeLoader`/`SettlementCatalog` have no
code path that can produce anything but the current engine's data, and
`SettlementKnowledgeLoaderTest.bundledRulePackIsCurrentFiftyLevelEngine` is a permanent
anti-regression guard against this ever silently reverting.

The `maxMembersInitial = 5` placeholder that existed in an earlier scaffold has been removed
entirely — no current official source backs that exact number, and inventing one would violate
this project's verification policy.

## Architecture

- `knowledge.settlement` — pure, Minecraft-API-free Rule Pack knowledge: `SettlementLevel`,
  `ItemRequirement` (nullable `itemId` for genuinely ambiguous categories like Wool color or
  Armor Trim Template, with an optional `distinctVariantsRequired` count), `SettlementFoundation`,
  `ProductionCategory`, `SettlementCatalog` (immutable, loaded once), `SettlementKnowledgeLoader`
  (loads `settlement-levels.json` and `settlements.json` independently — a broken foundation file
  never hides a valid level progression and vice versa), `LevelRangeSummary` (the result of
  `SettlementCatalog.levelRange(from, to)`, which aggregates coin cost, merges identical/ambiguous
  material requirements, and lists distinct building prerequisites along the path).
- `settlement` / `settlement.storage` — local, mutable planning state, isolated per
  GameZone-server-vs-singleplayer context exactly like Chest Manager: `SettlementPlannerProfile`
  (chosen current/target level, manually entered owned-material counts, local member notes),
  `SettlementPlannerData` (versioned root, schema v1), `JsonSettlementPlannerStore`
  (`config/gzcompanion/settlement-planner.json`, atomic writes, corrupt-file backup and recovery,
  fail-closed on a future schema), `SettlementPlannerManager` (the runtime coordinator, mirroring
  `ChestManager`'s `requireLoaded()` fail-closed pattern). This pass adds three small, pure,
  Minecraft-API-free view types to the SAME package: `LiveLevelAlignment`/`LiveSettlementLevel`
  (raw live level vs. Rule Pack alignment - see above), `EffectiveCurrentLevel` (the pure,
  non-mutating live-or-manual level decision), and `SettlementLiveView` (the one combined object
  `SettlementTabComponent` reads for name/role/bonus/treasury/level/online-members).
- `gamezone.GameZoneLiveContext` / `GameZoneLiveContextBuilder` — the shared live-data refresh
  point every tab (Home, Online, Settlement) calls. Introduces NO new parsing: it only calls the
  existing `GameZoneLiveStatusTracker`/`GameZoneSettlementTracker` (which in turn call
  `GameZoneTabStatusParser`/`GameZoneTabIdentityParser`, the sole authorities for their respective
  facts) and combines their results into one `GameZoneLiveContext` record.
- `ui.tabs.SettlementTabComponent` / `ui.layout.SettlementLayout` — the Settlement tab, with four
  modes cycled via one button: **Översikt**, **Progression**, **Material**, **Medlemmar**. Layout
  geometry (`SettlementLayout`) is unchanged by this pass - the live sections render as additional
  content inside the existing panel/list/detail rects, never a new layout region.

## The four modes

- **Översikt** — the LIVE settlement card (see above) when recognized, then: total levels/
  upgrades, the effective current level (worded "Nuvarande nivå (LIVE)" when live-trusted, else
  "Planerad nuvarande nivå" / "Välj din nuvarande nivå" exactly as before), a NÄSTA NIVÅ card
  (level number/name, coin cost, building requirement - never an invented completion percentage),
  and the foundation facts with their verification trail. The Rule Pack's own load status ("Rule
  Pack: Laddad") is shown as a small, unobtrusive line here rather than crowding the tab's
  single-line header, which now prioritizes the LIVE/PLANERING indicator.
- **Progression** — a searchless list of all 50 levels (NORMAL/LARGE split pane, COMPACT
  list→detail→back, mirroring `CraftingTabComponent`) with a detail pane showing the selected
  level's coin cost, material requirements (translated names, real item icons where a concrete
  item id exists), building prerequisite/unlock, and verification trail. Rows/detail show
  PAST/DU ÄR HÄR/NÄSTA/MÅL state derived from the effective current level (never persisted - purely
  a display decision recomputed every frame). Two buttons let the user set the selected level as
  their locally planned current or target level; while a trusted live level is active, the
  current-level button is relabeled as an explicit OFFLINE fallback action (see above).
- **Material** — the range from the effective current level to the chosen target is aggregated via
  `SettlementCatalog.levelRange(...)` into one merged checklist: required amount, manually tracked
  owned amount (+/- buttons, persisted locally), and the derived missing amount. Shows its start
  level's source ("Startnivå: 10 · Småstad (LIVE)" or "Startnivå: 10 (lokal planering)"), and a
  clear "Målnivån måste vara högre än nuvarande nivå." message instead of a nonsense range if the
  chosen target isn't actually above the current level. An optional **"Beräkna från sparade
  kistor"** action lets the user pick which of their own already-indexed Chest Manager containers
  to sum into the owned amounts — never automatic, never assuming every cached chest belongs to
  the settlement. The UI always displays, next to that action, the two required disclaimers:
  *"Lokalt estimat från senast känt innehåll."* and *"Detta är inte serverns registrerade
  settlement inventory."*
- **Medlemmar** — an "ONLINE FRÅN DITT SETTLEMENT" live section (see above) above a purely local
  member organizer (name + a free-text note the player writes themselves, e.g. a role or
  responsibility). Clicking an online player opens the same local-note edit form pre-filled with
  their name - a pure UX convenience that creates no persisted record until the player explicitly
  clicks "Spara". If an existing local note's name case-insensitively matches a currently online
  player, that note is shown inline under their row. The local notes section itself is always
  labeled "Lokala anteckningar" — this is never the live server roster, there is no player
  scraping beyond the same TAB data Online already reads, and nothing here is chat history.

## Fair play and privacy

- Live data is read-only and comes ONLY from vanilla-visible TAB header/player-list text the
  client has already legitimately received - the exact same source Home and Online already use.
  No entity scanning, no world/chunk/block scanning, no coordinates, no packet inspection, no
  private/authenticated API, and no new network client of any kind (`SettlementFairPlayTest`
  structurally enforces this by scanning the source itself).
- "Online from your settlement" is never described as a full roster, a total member count, or
  "all settlement members" — only who is currently visible in live TAB data right now. No offline
  inference, no presence history is stored.
- No world/container scanning: the Chest Manager estimate only ever reads data the player already
  legitimately captured through M3's existing open-a-real-chest capture flow.
- No automatic commands: the "Kopiera /settlement ..." style actions (once wired) are clipboard-only.
- No cloud, no telemetry: `settlement-planner.json` is local-only, like every other Companion file.
- A live/catalog mismatch is shown honestly (GameZone's own reported value first) rather than
  hidden or silently "corrected" to match this Companion's bundled data.

## Known limitations

- Per-building minimum footprint dimensions are not part of this milestone (see the Building
  Planner scope in M7) — Settlement Companion only models what the settlement-upgrades page itself
  publishes per level.
- The member organizer intentionally combines "role/responsibility/free note" into a single free
  text field rather than three separate structured fields, to keep the local editor simple; the
  player can write whatever structure they like into that one note.
- No role-specific automated actions exist yet (e.g. KING/LORD-only tooling) - this pass is about
  displaying trustworthy live settlement state and connecting it to planning, not role-based
  automation, which may be a later, separate pass.
- Pixel-level rendering checks (long names, digit-count boundaries, overflow/clipping at small
  window sizes) are covered by this project's usual pure-logic unit tests wherever the underlying
  data derivation is testable, but the actual on-screen rendering still requires human Minecraft
  QA - this environment has no tool that can drive the real game window.
