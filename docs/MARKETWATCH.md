# MarketWatch (Milestone 9)

Status: **Implemented, pending human gameplay QA.**

## What this is

An always-available offline reference to GameZone's real MarketWatch system, plus a purely local
"Mina anteckningar" watchlist. MarketWatch is **not** an auction price list — it represents
resource **demand** for upcoming settlement upgrades, compared against each settlement's
registered inventory. The Companion never runs `/marketwatch` automatically, and every local note
is explicitly labeled as the player's own observation, never GameZone's live server truth.

## Verified facts (source: `https://www.gamezonemc.se/wiki/economy/marketwatch`, WIKI-VERSION 1.1)

- Command: `/marketwatch`.
- 7 categories (the same "sju inriktningar" production categories already verified for Settlement
  Companion from the Wiki's Produktion section — gruvdrift, jordbruk, boskap, skogsbruk,
  byggmaterial, fiske, alkemi).
- Purpose: "MarketWatch hjälper spelare och företag att se vilka resurser som behövs för kommande
  settlementuppgraderingar. Systemet jämför settlementens sammanlagda behov med material som
  redan finns i registrerade settlement inventories."
- Usage: run `/marketwatch`, then choose a category to see that category's resource demand.

**Important distinction**: the Wiki's MarketWatch page also documents a separate, related command
family — `/market <item>` (shop price lookup across up to 3 active shops), `/market köp ...`
(company buy listings), and `/market annonser` (active listings). These are a genuinely different
GameZone system (a player-shop price/order book) and are intentionally **not** modeled as part of
MarketWatch here, to avoid conflating "settlement resource demand" with "shop price lookup."

## Architecture

- `knowledge.economy.MarketWatchInfo` / `MarketWatchKnowledgeLoader` — loads the verified command/
  purpose/usage facts from `gamezone-pack/marketwatch.json` (schema v1). The 7 categories
  themselves are deliberately **not** duplicated in this file — the tab reads them from the
  already-loaded `SettlementCatalog.productionCategories()` (same Wiki source) so the two lists
  can never silently drift apart.
- `marketwatch` / `marketwatch.storage` — the local watchlist: `MarketWatchNote` (item, category,
  a combined free-text note, last-observed timestamp, favorite flag), `MarketWatchNotesData`
  (versioned root, schema v1, isolated per GameZone-server-vs-singleplayer context exactly like
  Settlement/Building planner state), `JsonMarketWatchNotesStore`
  (`config/gzcompanion/marketwatch-notes.json`, atomic writes, corrupt-file backup and recovery,
  fail-closed on a future schema), `MarketWatchNotesManager` (the runtime coordinator, with local
  search/sort — favorites first, then most recently observed).
- `ui.tabs.MarketWatchTabComponent` / `ui.layout.MarketWatchLayout` — a fixed reference card
  (purpose, command, category count, a "Kopiera /marketwatch" clipboard button) above a searchable
  local-notes list with inline add/edit/delete (delete requires a second confirming click,
  mirroring Settlement's member organizer) and a favorite star toggle.

## Saved location on a note ("Spara plats" / "Hitta")

MarketWatch does **not** know any shop coordinates - the Companion never reads `/market` or the
`/marketwatch` GUI. What a note can carry instead is a place the player saved **themselves**:

1. Stand at the shop/place, open the note's edit form ("+ Ny" or "Ändra").
2. Press **Spara plats** - the player's own current block position and dimension are recorded
   (`MarketWatchNavigation.locationFromPose`). Without a live position nothing is recorded; the
   form says so. **Uppdatera plats** / **Ta bort plats** change it; **Spara** persists it,
   **Avbryt** discards it.
3. The note row gets one extra line - "Din sparade plats • 847 block fågelvägen" - and **Hitta**.
   Notes without a saved location look and behave exactly as before.
4. **Hitta** hands the place to the shared Kistor navigation (`ChestNavigationManager.startPoint`,
   see `docs/CHEST-MANAGER.md` §20): same HUD arrow, distance, vertical and wrong-dimension states,
   same NAVIGERAR banner/Stoppa in Kistor. The row shows "● NAVIGERAR HIT" and **Stoppa**.
   Moving or removing the place, or deleting the note, while it is the target restarts or stops
   navigation accordingly.

Storage: an optional, additive `"location": {"dimension", "x", "y", "z", "savedAtMs"}` object on a
note, still schema 1 (bumping it would make older builds refuse the whole file). The world/server
context is the note's own context bucket. An incomplete or out-of-world location is dropped on
load (the note itself is kept) and never repaired into a guessed position. An older build that
re-saves the file drops the field.

A future, verified `/market` shop-coordinate source would build its own `NavigationPoint` and call
the same `startPoint` - no navigation redesign needed.

## Why price/demand/quantity notes are one combined field

Per the same simplification already applied and documented for Settlement Companion's member
organizer, `MarketWatchNote.note` combines what the milestone brief listed as separate
"price note / demand note / quantity note / free note" fields into one free-text field the player
can structure however they like, rather than four separate structured inputs.

## The optional visible-GUI capture question — deferred, and why

The milestone brief allowed an OPTIONAL, strictly-scoped feature: safely reading the
manually-opened `/marketwatch` GUI's visible item stacks (screen title, display names, lore,
counts) if — and only if — that GUI's exact identity/layout could be verified without guessing.

The Wiki page describes the `/marketwatch` category-selection flow in prose ("Välj en kategori för
att se efterfrågan...") but does not publish the exact inventory screen title, slot layout, or
item-stack structure GameZone's client actually renders. Guessing that structure risks either
silently reading nothing (a harmless but confusing false negative) or, worse, misreading an
unrelated menu that happens to share a title. Per this project's explicit "better inactive than
false-positive" policy (already applied identically to M5's chat parsers), this feature is
**deferred, not implemented**. The offline reference and local notes/watchlist above are the
complete, honest MarketWatch experience for this pass. A clean adapter seam for a future
human-verified GUI capture is the natural next step once that exact screen structure is confirmed
by a real player opening `/marketwatch` and reporting back what GameZone's client actually shows.

## Fair play and privacy

- `/marketwatch` (and the copy-only `/market` reference, if ever added) is never run automatically.
- No claim of live server truth: every note is explicitly local, favorited/observed by the player
  themselves, never scraped from chat or any menu.
- No cloud, no telemetry: `marketwatch-notes.json` is local-only, like every other Companion file.
