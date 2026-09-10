# Building Planner (Milestone 7)

Status: **Implemented, pending human gameplay QA.**

## What this is

A local reference/planner/calculator for GameZoneMC's physical building system. It works fully
offline and never claims a local calculation guarantees GameZone's actual server-side building
approval — GameZone validates the real, built structure; this tab only helps the player plan for
it.

## Source and scope

PRIMARY source: `https://www.gamezonemc.se/wiki/buildings/fysiska-byggnader` (Building System
1.0), with all 19 individual building detail pages (`https://www.gamezonemc.se/wiki/buildings/<id>`)
fetched and inspected in a dedicated follow-up pass. `gamezone-pack/buildings.json` models all 19
buildings currently in the active progression (Stadskärna through Myntverk). Underverk (the level
50 "Wonder") is explicitly excluded, per the source's own disclaimer that it is being redone
separately and is not yet part of the active building progression.

**Per-building minimum footprint — now fully populated.** Every one of the 19 buildings publishes
its own minimum width/depth on its individual detail page (e.g. Stall: 19×19), and 4 of the 19
(Vindhamn, Kyrka, Rådhus, Slott) separately publish a minimum height as well - the other 15 have
no documented height minimum and correctly leave `minHeight` `null` rather than inventing one.
`SettlementBuilding.hasPublishedMinimumFootprint()`/`fitsFootprint(width, depth, height)` reflect
this: every building now has a real, checkable minimum, and the Structure Calculator's warning
fires against it honestly (naming the building, e.g. "För litet för Stall").

Every building's `verification.sourceReference` points at its own individual page (not a shared
summary page) for every footprint/cost/special-requirement field verified there.

## A real, honestly-represented level-requirement conflict (Stadskärna, Handelscentrum)

The **Settlement Upgrade** progression page (`https://www.gamezonemc.se/wiki/settlements/settlement-upgrades`)
presents every building twice as the settlement progresses: once as "**BYGGNAD PÅ NIVÅ N**" ("once
the settlement reaches level N, this building can be licensed and built") and again, one level
later, as "**KRÄVS FÖR NIVÅ N+1**" ("this building must already be physically completed and active
before the settlement can upgrade to level N+1"). Cross-checking all 19 buildings against their own
individual pages confirms this pattern holds exactly for 17 of them (e.g. Bank: "byggnad på nivå
6" → "krävs för nivå 7", and its own page's `NIVÅKRAV` correctly says 6).

**For exactly 2 of the 19 - Stadskärna and Handelscentrum - the building's own individual page
contradicts this pattern:**

- **Stadskärna**: its own page states `NIVÅKRAV: Settlementnivå 2`. But the Settlement Upgrade
  page shows Stadskärna only under "**KRÄVS FÖR NIVÅ 2**" ("this building must already be
  physically completed and active before the settlement can perform the upgrade [to level 2]") -
  meaning it must be built while the settlement is still at level 1, not after reaching level 2.
  There is no "byggnad på nivå 1" card for it (level 1/Enstöring has no upgrade-unlock section at
  all, being the starting level) - but requiring it to precede level 2 while also claiming it
  needs level 2 to exist is circular.
- **Handelscentrum**: its own page states `NIVÅKRAV: Settlementnivå 4`. The Settlement Upgrade
  page explicitly shows Handelscentrum as "**BYGGNAD PÅ NIVÅ 3**" (licensable once the settlement
  reaches level 3, license 20 000 Coins) and separately as "**KRÄVS FÖR NIVÅ 4**" (must be
  complete before upgrading to level 4, same license cost, same special requirements). The
  individual page's own stated availability level (4) is one higher than the progression page's
  explicit availability level (3).

**This is not silently resolved by picking one number.** Per this project's source-conflict
policy, neither page is treated as unquestionably correct. Instead:

- `SettlementBuilding.levelRequirement` keeps the individual page's own stated value (2 and 4,
  unchanged) - it is never deleted or overwritten.
- A new `progressionRequiredForUpgradeToLevel` field carries the Settlement Upgrade page's own
  "krävs för nivå N" value for every building that has one (`null` for buildings, like
  Laboratorium, that are never presented as a general upgrade gate - it only applies to Alkemi
  settlements).
- `hasLevelRequirementConflict()` is a general, data-driven rule - `levelRequirement >=
  progressionRequiredForUpgradeToLevel` - not a hardcoded id check. Running it across all 19
  bundled buildings finds exactly these two and no others (see
  `BuildingKnowledgeLoaderTest.exactlyTwoBundledBuildingsHaveALevelConflict`).
- Each building now carries **two independent verification trails**: `verification` (footprint,
  cost, special requirements, bonus - confirmed consistent between both sources for all 19
  buildings, including these two, and left fully `VERIFIED`) and a separate
  `levelRequirementVerification`, which is a new `VerificationStatus.CONFLICT` value for these two
  buildings specifically - added because none of the existing statuses (`VERIFIED`/`UNVERIFIED`/
  `STALE`/`UNKNOWN`) honestly describe "two current canonical sources actively disagree."
- The Byggplaner UI shows both raw numbers plus the natural-Swedish warning "GameZones Wiki
  innehåller motstridiga nivåuppgifter för denna byggnad." instead of a single confident
  "Nivåkrav: Settlementnivå X" line, for exactly these two buildings.

## Architecture

- `knowledge.building` — `SettlementBuilding`, `BuildingRequirement` (nullable `itemId` for
  ambiguous categories, mirroring `knowledge.settlement.ItemRequirement`), `GlobalBuildingRules`,
  `BuildingKnowledgeBase` (immutable, loaded once, with `search(query)`), `BuildingKnowledgeLoader`
  (`buildings.json`, schema v1).
- `building` / `building.storage` — local, mutable plan state, isolated per
  GameZone-server-vs-singleplayer context exactly like Chest Manager and Settlement Companion:
  `BuildingPlan` (a chosen building, a user-given plan name, planning dimensions, and a fixed
  local checklist), `BuildingRequirementKey` (LICENSE / LEVEL / SIZE / WALLS / ROOF / SPECIAL —
  local planning checkboxes, never a claim of server-side completion), `BuildingPlanData`
  (versioned root, schema v1), `JsonBuildingPlanStore`
  (`config/gzcompanion/building-plans.json`, atomic writes, corrupt-file backup and recovery,
  fail-closed on a future schema), `BuildingPlanManager` (the runtime coordinator).
- `ui.tabs.BuildingsTabComponent` / `ui.layout.BuildingLayout` — the Byggplaner tab: a searchable
  building list + detail pane (NORMAL/LARGE split, COMPACT list→detail→back, mirroring
  `CraftingTabComponent`), hosting the Structure Calculator and the local plan list for whichever
  building is selected.

## Structure Calculator ("Planeringsestimat")

A simple rectangular-footprint local helper: width/depth/height steppers compute floor/roof area
(`width × depth`), wall surface area (`2 × (width + depth) × height`), and the published minimum
roof/wall block counts implied by the global coverage percentages. Every result is labeled
"Planeringsestimat" and the calculator always shows an explicit reminder that this is a local
estimate, not a guarantee of GameZone's actual approval.

Selecting a building now shows its real "Minsta storlek" prominently in the detail pane (before
Väggkrav/Takkrav/Specialkrav/Bonus), and seeds the calculator's width/depth/height at exactly that
building's published minimum, so opening a building starts in a passing state. Shrinking a
dimension below the minimum shows a named warning ("För litet för Stall - minsta storlek är
19 × 19."); meeting or exceeding it shows a positive status ("Måtten uppfyller Stalls publicerade
minimikrav."). Neither message is ever shown as a guarantee of GameZone's actual approval.

## Local plans

For the selected building, the player can create a new local plan from the calculator's current
dimensions, rename it, delete it (with a two-click "Säker?" confirmation, mirroring the pattern
used for Settlement's member organizer), and toggle each of the six fixed checklist items
(License/Nivå/Storlek/Väggar/Tak/Specialkrav) as a purely local planning note. Plans persist per
GameZone-server-vs-singleplayer context, never leaking between different servers or worlds.

## Fair play and privacy

- No automation: nothing here issues `/building` commands automatically (a clipboard-only
  "Kopiera kommando" action, if wired, would use only already-VERIFIED M4 catalog commands).
- No claim of live server truth: every plan and checklist item is explicitly local; the tab never
  says a building has actually been approved by GameZone.
- No cloud, no telemetry: `building-plans.json` is local-only, like every other Companion file.
