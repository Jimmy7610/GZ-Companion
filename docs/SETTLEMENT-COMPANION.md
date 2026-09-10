# Settlement Companion (Milestone 6)

Status: **Implemented, pending human gameplay QA.**

## What this is

A local reference/planner/calculator/organizer for GameZoneMC settlements. It works fully
offline, including in singleplayer, and it never claims to know the player's actual,
server-observed settlement state unless that has been legitimately observed by some other part
of the Companion (it currently has not — Settlement Companion is planning-only in this pass).

"Java understands Minecraft. Data understands GameZone": every settlement fact (level names,
coin costs, material requirements, foundation rules, production categories) lives in the Rule
Pack (`gamezone-pack/settlement-levels.json`, `gamezone-pack/settlements.json`), not hardcoded in
Java.

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
  `ChestManager`'s `requireLoaded()` fail-closed pattern).
- `ui.tabs.SettlementTabComponent` / `ui.layout.SettlementLayout` — the Settlement tab, with four
  modes cycled via one button: **Översikt**, **Progression**, **Material**, **Medlemmar**.

## The four modes

- **Översikt** — a short summary: total levels/upgrades, the locally chosen current/target level
  (worded "Planerad nuvarande nivå" / "Välj din nuvarande nivå" — never "Your settlement is level
  X", since that is never actually observed), the next level's name and cost if a current level is
  chosen, and the foundation facts with their verification trail.
- **Progression** — a searchless list of all 50 levels (NORMAL/LARGE split pane, COMPACT
  list→detail→back, mirroring `CraftingTabComponent`) with a detail pane showing the selected
  level's coin cost, material requirements (translated names, real item icons where a concrete
  item id exists), building prerequisite/unlock, and verification trail. Two buttons let the user
  set the selected level as their locally planned current or target level.
- **Material** — the level/target range chosen in Progression is aggregated via
  `SettlementCatalog.levelRange(...)` into one merged checklist: required amount, manually tracked
  owned amount (+/- buttons, persisted locally), and the derived missing amount. An optional
  **"Beräkna från sparade kistor"** action lets the user pick which of their own already-indexed
  Chest Manager containers to sum into the owned amounts — never automatic, never assuming every
  cached chest belongs to the settlement. The UI always displays, next to that action, the two
  required disclaimers: *"Lokalt estimat från senast känt innehåll."* and *"Detta är inte serverns
  registrerade settlement inventory."*
- **Medlemmar** — a purely local member organizer (name + a free-text note the player writes
  themselves, e.g. a role or responsibility). Always labeled "Lokala anteckningar" — this is never
  the live server roster, there is no player scraping, and nothing here is chat history.

## Fair play and privacy

- No world/container scanning: the Chest Manager estimate only ever reads data the player already
  legitimately captured through M3's existing open-a-real-chest capture flow.
- No automatic commands: the "Kopiera /settlement ..." style actions (once wired) are clipboard-only.
- No cloud, no telemetry: `settlement-planner.json` is local-only, like every other Companion file.
- No claim of live server truth anywhere in this feature — every planning fact is either sourced
  from the verified Rule Pack or explicitly labeled as the player's own local note/estimate.

## Known limitations

- Per-building minimum footprint dimensions are not part of this milestone (see the Building
  Planner scope in M7) — Settlement Companion only models what the settlement-upgrades page itself
  publishes per level.
- The member organizer intentionally combines "role/responsibility/free note" into a single free
  text field rather than three separate structured fields, to keep the local editor simple; the
  player can write whatever structure they like into that one note.
