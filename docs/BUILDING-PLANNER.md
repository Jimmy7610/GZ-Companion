# Building Planner (Milestone 7)

Status: **Implemented, pending human gameplay QA.**

## What this is

A local reference/planner/calculator for GameZoneMC's physical building system. It works fully
offline and never claims a local calculation guarantees GameZone's actual server-side building
approval — GameZone validates the real, built structure; this tab only helps the player plan for
it.

## Source and scope

PRIMARY source: `https://www.gamezonemc.se/wiki/buildings/fysiska-byggnader` (Building System
1.0). `gamezone-pack/buildings.json` models all 19 buildings currently in the active progression
(Stadskärna through Myntverk). Underverk (the level 50 "Wonder") is explicitly excluded, per the
source's own disclaimer that it is being redone separately and is not yet part of the active
building progression.

**Known, explicitly documented gap**: the summary source used for this pass does not publish
per-building minimum width/depth/height footprints — only the global 40% wall-coverage / 75%
roof-coverage / full-territory-containment rules, plus each building's own special block/entity
requirements. Rather than fetch all 19 individual building detail pages (out of scope for this
pass) or invent plausible dimensions, `minWidth`/`minDepth`/`minHeight` are left `null` for every
building today. `SettlementBuilding.hasPublishedMinimumFootprint()` reflects this honestly, and
the Structure Calculator's "below minimum" warning simply never fires until real per-building
dimensions are added as verified data later.

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
estimate, not a guarantee of GameZone's actual approval. If a selected building does have a
published minimum footprint and the current dimensions fall below it, a warning is shown.

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
