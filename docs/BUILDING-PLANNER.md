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

**A real data conflict found and fixed during this pass**: the overview page's "Byggnadsprogression"
table numbers its rows 1, 2, 3, 5, 6, 7, 8, 10, 12... which an earlier pass had mistakenly read as
each building's settlement level requirement. Cross-checking every individual page revealed that
row number is NOT the level requirement for two buildings - Stadskärna's own page states
`NIVÅKRAV: Settlementnivå 2` (not 1) and Handelscentrum's states `Settlementnivå 4` (not 3). Per
this project's source-conflict policy, the individual page is authoritative; `levelRequirement`
for both was corrected, and every building's `verification.sourceReference` now points at its own
individual page (not the shared overview page) for every field verified there.

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
