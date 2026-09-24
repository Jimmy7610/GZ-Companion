# GZ Companion — Chest Manager (Kistor 2.0)

> **GZ Companion is an unofficial community project for GameZoneMC. It is not affiliated with or
> endorsed by GameZoneMC.**

Status: **Kistor 2.0 implemented, pending human gameplay QA.** The Milestone 3 capture system is
unchanged; Kistor 2.0 is a domain/UI/navigation layer on top of it. Do not treat Kistor 2.0 as
complete until the manual QA checklist in §24 has been run in real Minecraft gameplay.

Kistor 2.0 answers four questions, all from storage the player has legitimately opened before:

1. **Where are my things?** — SAKER (§15)
2. **Which storage contains them?** — SAKER item detail, FÖRVARING (§15–§16)
3. **How do I get back to that storage?** — HITTA navigation (§20)
4. **Do I already have the materials for my Settlement/building goal?** — Hitta material i kistor
   + Hämtningslista (§21)

## 1. Fair-Play Boundary

GZ Companion knows ONLY what the Minecraft client legitimately showed the player.

**Allowed:**
- The player physically interacts with a supported storage block.
- Minecraft legitimately opens its storage screen.
- GZ Companion reads the slots visible in that opened menu.
- GZ Companion updates its local snapshot while that screen remains open.
- GZ Companion saves the final visible state when the screen closes.
- The user later browses/searches that local cached snapshot.
- The user sees coordinates/dimension of storage they personally opened.
- **Kistor 2.0:** the player explicitly selects ONE of those already-known storage locations and
  the HUD compares the player's OWN current position and camera yaw with that saved coordinate
  (§20).

**Forbidden — and not implemented anywhere in this module:**
- World scanning for containers, chunk scanning, or searching block entities to discover
  unopened storage.
- Reading unopened container inventories.
- X-ray, packet tricks/sniffing, or any server-internal data extraction; GameZone private APIs.
- Hidden chest detection, chest/container radar, or plugin data extraction.
- ESP, outlines/boxes through walls, or any world-space rendering; raytracing for the chest.
- Automatic chest discovery — only storage in the local index can ever be a navigation target.
- Pathfinding, A* routes, auto-walking, auto-turning, route generation.
- Automatic GUI clicks, automatic item transfers, automatic chat, commands or teleports.
- Claiming cached contents are current/live.
- Recursively opening/reading nested storage the player did not open themselves (shulker box
  contents inside an item stack, bundle contents, etc.).

These guarantees are enforced structurally by `KistorFairPlayTest`, which scans every Kistor 2.0
source file for world/chunk/block-entity access, raytracing, world rendering, player
automation, chat/command/packet/network use.

Every UI surface uses **last-known** language, never "live" or "current" language:
"Senast känt innehåll", "Senast känt totalt", "Senast öppnad", "Saknas enligt estimat" and
"Kan ha ändrats sedan du öppnade förvaringen." Cached chest contents are never labeled LIVE.

## 2. Capture Lifecycle

The Chest Manager never snapshots a chest once on open — it runs a full opened-storage capture
session:

1. **Physical interaction.** The player right-clicks a block. Fabric's `UseBlockCallback` fires
   client-side (this hooks the client's own interaction handling and works identically in
   singleplayer and on any remote server, including GameZoneMC — no server cooperation needed,
   and nothing about vanilla interaction behavior is altered; the listener always returns
   `InteractionResult.PASS`). If the block is on the explicit storage allow-list, a short-lived
   *pending interaction* is recorded (position, dimension, context, storage kind, timestamp).
2. **Menu open correlation.** When a screen opens (`ScreenEvents.AFTER_INIT`) and it is an
   `AbstractContainerScreen`, the Minecraft-specific adapter determines every storage kind whose
   block(s) legitimately open that exact menu Java class. `ChestManager.tryBeginCapture` then
   verifies the pending interaction is recent, in the same context and dimension, AND one of
   those compatible kinds. If any check fails, the pending interaction is discarded and **nothing
   is indexed.** On success, an immediate snapshot is taken right away — the screen is already
   legitimately open, so this is a legitimate read — instead of waiting for the first tick. This
   closes a race where a player who closes the screen before the first tick would otherwise leave
   no legitimate snapshot at all.
3. **Live capture while open.** `ScreenEvents.afterTick` re-reads only the storage portion of the
   menu once per client tick. A lightweight fingerprint of the visible slots is computed; if
   unchanged since the last read, nothing happens. If changed, the in-memory snapshot is updated.
   **No disk write happens while the screen is open.**
4. **Finalize on close.** `ScreenEvents.remove` fires when the screen closes. One true final read
   of the menu is taken while it is still valid and forwarded the same way, so the persisted
   contents reflect the very last state the player actually saw — never a state from a stale
   earlier tick. The finalized snapshot is then persisted **exactly once**, updating "senast
   öppnad" (last opened) even when the contents are unchanged from the existing record. If a
   capture session somehow never received a single legitimate snapshot (it should not, given the
   immediate open-time read above), GZ Companion fails closed: no empty record is created, and an
   existing non-empty record is never overwritten with empty data. Either way, the capture session
   is then cleared.

## 3. Physical Interaction Correlation

`UseBlockCallback` and a screen opening are not intrinsically linked events, so a defensive
correlation window is used. A storage screen is only attached to a pending physical interaction
if **all** of the following hold:

- The interaction happened within `ChestManager.PENDING_INTERACTION_WINDOW_MS` (2000ms) of the
  menu opening.
- The context (player/world/server) matches.
- The dimension matches.
- The block that was clicked is one whose storage kind is structurally compatible with the menu
  class that opened (e.g. a `ChestMenu` structurally supports Chest, Trapped Chest, and Barrel;
  it does NOT satisfy a pending Hopper interaction).

If correlation cannot be proven, GZ Companion does not guess a position and does not index
anything. This is also the concrete defense against **GameZone plugin/virtual GUIs**: a server or
plugin can present a vanilla-looking inventory GUI (reusing e.g. `ChestMenu`) without any real
block interaction behind it — such a GUI never has a matching pending interaction, so it is
never indexed as a physical chest.

## 4. Supported Storage — Milestone 3

Explicit allow-list, verified against Minecraft 26.1.2's actual block and menu classes:

| Storage kind | Block class | Menu class actually opened |
| :--- | :--- | :--- |
| Chest | `ChestBlock` | `ChestMenu` |
| Trapped Chest | `TrappedChestBlock` (extends `ChestBlock`) | `ChestMenu` |
| Barrel | `BarrelBlock` | `ChestMenu` (`ChestMenu.threeRows`) |
| Shulker Box (placed) | `ShulkerBoxBlock` | `ShulkerBoxMenu` |
| Hopper | `HopperBlock` | `HopperMenu` |
| Dispenser | `DispenserBlock` | `DispenserMenu` |
| Dropper | `DropperBlock` (extends `DispenserBlock`) | `DispenserMenu` |

Note several vanilla block kinds legitimately share the same menu class (Barrel opens the same
`ChestMenu` a Chest does). Correlation therefore checks "is the clicked block's kind one of the
kinds this menu class structurally supports," not a 1:1 class mapping.

**Explicitly excluded, and never treated as storage:**
- Crafting table, furnace, blast furnace, smoker, anvil, enchanting table, merchant/villager
  trading, beacon, loom, grindstone, stonecutter, brewing stand, cartography table, lectern.
- Ender Chest — contents are player-global, not tied to one physical block, and are out of scope
  for this milestone.
- Minecart-with-chest and other container *entities* — this milestone only handles block storage.
- Horse/mount inventories.
- Any plugin/virtual GUI without a proven matching physical block interaction (see §3).

These may become separate, deliberately-modeled extensions in a later milestone; none are
silently treated as generic storage today.

## 5. Player Inventory Exclusion

The capture adapter reads `menu.slots` (every slot in the opened menu, which vanilla always
mixes container slots and the player's own inventory/hotbar slots together) and explicitly skips
every slot whose backing `Container` is the player's own inventory object. Only the remaining
slots — the physical storage itself — are ever recorded. The player's personal inventory is never
part of a stored snapshot.

## 6. Double Chests & `StorageShape`

Double-chest identity is derived **only** from the already client-visible `BlockState` of the
block the player clicked — specifically the vanilla `ChestBlock.TYPE` property
(`SINGLE`/`LEFT`/`RIGHT`) and `ChestBlock.getConnectedBlockPos(...)`. No surrounding block
entities are ever scanned.

Physical shape is modeled as an explicit `StorageShape` enum, not a boolean, because a boolean
cannot distinguish "Minecraft proved this is a single chest" from "this is a double-chest half
whose partner could not be resolved" — collapsing those two into one flag was a real bug found in
human gameplay QA (a normal single chest was shown as "kan vara dubbel").

| `StorageShape` | Meaning | UI text |
| :--- | :--- | :--- |
| `SINGLE` | `ChestType.SINGLE` confirmed by the block state. No partner. | nothing extra |
| `DOUBLE` | `ChestType.LEFT`/`RIGHT` with a resolved partner position. | "Dubbel kista" |
| `UNKNOWN` | A chest-family block whose double/single state could not be safely resolved. | "(kan vara dubbel)" |
| `NOT_APPLICABLE` | Barrel, Shulker Box, Hopper, Dispenser, or Dropper — shape doesn't apply. | nothing |

- `SINGLE` is recorded as a normal single-position entry with no partner and no double-chest text
  anywhere in the UI.
- `DOUBLE` has its partner position read directly from the block state. The anchor position is
  canonicalized (the numerically lower of the two X/Y/Z positions) so that clicking either half of
  the same double chest resolves to the **same** logical entry — it is never duplicated depending
  on which side was opened.
- If the partner cannot be determined from a `LEFT`/`RIGHT` state (an unexpected shape), GZ
  Companion does **not** guess `SINGLE` or `DOUBLE`. It records `UNKNOWN`, shown as "(kan vara
  dubbel)" — the ambiguity is surfaced honestly rather than silently asserting a wrong shape.
- Non-chest-family kinds are always `NOT_APPLICABLE` and never show any chest-shape text.

## 7. Local Persistence (schema v2)

Path: `config/gzcompanion/chest-index.json`.

```json
{
  "schemaVersion": 2,
  "contexts": {
    "<profileId>@@<contextKey>": {
      "containers": {
        "<stableContainerKey>": {
          "kind": "CHEST",
          "label": "Materiallager",
          "dimension": "minecraft:overworld",
          "anchor": { "x": 120, "y": 64, "z": -32 },
          "partner": { "x": 121, "y": 64, "z": -32 },
          "shape": "DOUBLE",
          "lastOpenedAtMs": 1234567890,
          "slots": [ { "slot": 0, "itemId": "minecraft:iron_ingot", "count": 32 } ],
          "favorite": true,
          "group": "Min bas",
          "locationNote": "Källaren bakom smedjan",
          "previous": {
            "lastOpenedAtMs": 1234000000,
            "slots": [ { "slot": 0, "itemId": "minecraft:iron_ingot", "count": 12 } ]
          }
        }
      }
    }
  }
}
```

The four Kistor 2.0 fields (`favorite`, `group`, `locationNote`, `previous`) are optional and are
only written when set.

- Atomic writes: data is written to a `.tmp` file and moved into place with
  `StandardCopyOption.ATOMIC_MOVE`, exactly like `JsonGuideProgressStore`.
- Corruption recovery: a file that fails to parse (or fails to load at all) is preserved as
  `chest-index.json.corrupt.<timestamp>`, the mod continues with an empty index, and
  `ChestManager` still reports `LOADED` — a malformed file is treated the same as a fresh start,
  never as a permanent error state.
- **Incompatible future schema fails closed, without any overwrite risk.** `JsonChestIndexStore`
  returns a typed `ChestIndexLoadResult` distinguishing `NOT_FOUND` / `LOADED` /
  `CORRUPT_RECOVERED` (all safe, all result in `ChestManagerStatus.LOADED`) from
  `INCOMPATIBLE_SCHEMA` (a `schemaVersion` higher than this build understands — now anything
  above 2). On `INCOMPATIBLE_SCHEMA` the file on disk is **never moved, deleted, or overwritten**,
  and `ChestManager` reports `ChestManagerStatus.INCOMPATIBLE`. Every capture and mutation entry
  point (`recordPendingInteraction`, `tryBeginCapture`, `updateCaptureSlots`, `endCapture`,
  `forgetContainer`, `clearContext`, `setLabel`, `setFavorite`, `setGroup`, `setLocationNote`)
  requires `LOADED` status and is a safe no-op otherwise. The Kistor tab shows a controlled
  "kistindexet är sparat av en nyare version" message; active navigation stops.
- Unknown/future JSON fields are tolerated and ignored on read. A present-but-malformed Kistor 2.0
  field (e.g. `"favorite": {"x":1}`) falls back to its default instead of discarding the storage
  entry.
- Container identity is a **stable key** (context + dimension + canonical anchor position +
  storage kind), never an array index — re-opening the same physical storage updates the existing
  entry, keeping its label and Kistor 2.0 metadata.
- 100% local. No telemetry, no cloud account, no external API calls.
- **Growth policy:** nothing is automatically deleted. The index grows only as large as the set of
  storage locations the player has actually opened; each record keeps at most ONE previous
  snapshot (§18), so history is bounded.

### 7a. Schema migration v1 → v2

Real players already have schema-v1 files. Migration is lossless and needs no manual editing:

- A v1 file loads with every existing field intact — ids/stable keys, labels, dimension, anchor,
  partner, shape (including the legacy `partnerUnknown` mapping below), `lastOpenedAtMs`, slots.
- Missing Kistor 2.0 fields get safe defaults: not a favorite, no group, no location note, no
  previous snapshot.
- Migration happens **in memory only**. Merely loading a v1 file never rewrites it; the loaded
  data is tagged schema v2 and the next legitimate save (a capture finalization or a local edit)
  writes v2 atomically.
- The serializer always writes the current schema version.
- Consequence to be aware of: once written as v2, an older GZ Companion build (v1-only) reads the
  file as `INCOMPATIBLE` and — by design — fails closed without touching it. Nothing is lost; it
  simply can't be edited by the older build.

Covered by `ChestIndexSchemaMigrationTest` with a realistic alpha.6 v1 fixture.

**Backward-compatible `StorageShape` migration (unchanged).** The very first M3 release shipped a
container schema with `partner` + a boolean `partnerUnknown` instead of an explicit `shape` field.
Those records are still read losslessly:

- A record that already has an explicit `"shape"` value uses it directly.
- A legacy record without `"shape"` is mapped: `partner != null` → `DOUBLE`;
  `partner == null && partnerUnknown == true` → `UNKNOWN`; otherwise `SINGLE` for a chest-family
  kind, `NOT_APPLICABLE` for everything else.
- An explicit `"shape"` value this build doesn't recognize falls back to the same legacy mapping.

## 8. Context Isolation

Reuses the exact same profile/world/server identity semantics already established by the Guide
Engine (`GuideContext` / `GuideContextResolver`) rather than inventing new identity rules — no
duplicate world-detection logic exists. On top of that context key, dimension is also part of a
container's identity, so:

- The same X/Y/Z under a different world/server context is a different entry.
- The same X/Y/Z in a different dimension (Overworld vs. Nether vs. End) is a different entry.
- Restarting Minecraft or switching worlds and back preserves each world's own index unchanged.

## 9. Search, Filter, and Sort

One prominent local search field at the top of Kistor: **"Vad letar du efter?"**. It never
triggers any world lookup and never returns a result from a different context.
`ChestSearchMatcher` is the single source of truth for matching (case-insensitive):

- Translated item display name (the real vanilla name via
  `MinecraftChestCaptureAdapter.resolveItemDisplayName`, memoized per id) and raw item id.
- Custom label, **group**, **location note**.
- Storage type (`"Tunna"`, `"barrel"`, `"dubbel kista"`).
- Dimension key and short name (`"nether"`, `"minecraft:the_nether"`).
- Coordinates, typed with spaces or commas (`"120 64 -32"`, `"120, 64, -32"`).

**FÖRVARING** searches storage locations by all of the above. **SAKER** searches aggregated items:
if any item name/id matches, those items are shown; otherwise, if the query matches storage
metadata (e.g. the group "Min bas"), the result is every item stored in those matching storage
locations (`ChestItemSearch`, scope `STORAGE_MATCH`).

Controls row (local UI state, never persisted):
- **Typ** (`ChestTypeFilter`): Alla / Kista (+ Fällkista) / Tunna / Shulker / Hopper /
  Dispenser+Dropper.
- **Sortering**: FÖRVARING uses `ChestSortMode` (senast öppnad / namn / typ); SAKER uses
  `ChestItemSortMode` (antal / namn / förvaringar). All orders are total and deterministic.
- **Grupp** (`ChestGroupFilter`): Alla → each existing group → Utan grupp.

Technical ids: the item detail shows the raw id only when the existing "Visa tekniska Minecraft-ID"
setting is on (item tooltips follow the same setting); raw-id search always works.

## 10. Local Custom Labels

`ChestManager.setLabel` stores a purely local label **only** inside `chest-index.json`. It never
writes a sign, edits a block, sends chat, or issues a command. Blank clears it; control characters
become spaces; capped at 32 characters. Edited inline from the FÖRVARING detail ("Namnge" /
"Byt namn" → "Spara"; Enter saves, Esc cancels, clicking elsewhere cancels).

## 10a. Copy Coordinates

The detail pane's action row includes "Kopiera koord.", which copies the anchor position as plain
text (e.g. `"120 64 -32"`, matching the format already used for coordinate search) to the local OS
clipboard via the real, current Minecraft 26.1.2 API — `Minecraft.getInstance().keyboardHandler
.setClipboard(String)` — no reflection, no hacks. A temporary "Kopierat!" confirmation replaces the
button label for two seconds. This is a pure local convenience; nothing is sent anywhere, and
chat is never used for the confirmation.

## 10b. Real Item Icons

Kistor 2.0 renders real vanilla item icons everywhere items appear (SAKER rows and detail,
storage cards, storage contents, "sedan förra öppningen", material view, Hämtningslista).
Stored snapshots contain item ids, not ItemStacks, so `chest.bridge.ChestItemIcons`:

- resolves the id against the running client's own item registry with
  `BuiltInRegistries.ITEM.getOptional(...)` (never `getValue`, which would silently turn an
  unknown/future id into Air), memoized with a bounded cache;
- renders with the real 26.1.2 `GuiGraphicsExtractor.fakeItem(ItemStack, x, y)` — the same call
  Crafting/Settlement/Byggplaner already use. Icons smaller than 16 px (10 px in dense rows) are
  drawn by scaling the extractor's own 2D pose (`pose().pushMatrix()/translate/scale`), so text
  next to them stays on the normal pixel grid;
- draws a neutral placeholder for an unknown id, keeping row alignment;
- keeps Minecraft types out of every pure domain class. No reflection.

Hovering an icon shows the shared item tooltip (vanilla name, plus the raw id when technical ids
are enabled). Final icon sizing must be confirmed visually (§24).

## 11. Corruption Behavior

Identical philosophy to the rest of GZ Companion: a malformed or unreadable `chest-index.json`
never crashes the mod. It is backed up with a timestamp suffix and the Chest Manager continues
with an empty index; the rest of the mod (Guide, Home, etc.) is entirely unaffected. If the store
cannot initialize at all, `ChestManagerStatus.ERROR` is reported and the Kistor tab shows a
controlled error state instead of pretending to work.

## 12. Privacy & Diagnostics

All Kistor data — labels, favorites, groups, location notes, previous snapshots, contents,
coordinates — is local-first and never leaves the player's machine.

Generic diagnostics ("Kopiera diagnostik") expose only:
- `Kistor-status` (manager status),
- `Kistor-index: ... (schema vN, M indexerade)` for the current context,
- `Kistor-navigering: aktiv|inaktiv`.

They never include coordinates, labels, location notes, groups, item contents, or the navigation
target or its position (`ChestNavigationLifecycleTest.diagnosticsAreRedacted`).

**Reset.** Settings → "Rensa Kistor-index" (and "Rensa ALL lokal data") clears every container
record of the current context — which removes its favorites, groups, location notes and previous
snapshots too, since they live on those records — and immediately stops any navigation toward
storage in that context (`KistorRuntime.onChestIndexCleared`). Navigation also stops lazily if
the target ever disappears from the index for any other reason.

## 12a. Capture Session Cleanup

Leaving a world/server — disconnecting, quitting to the title screen, or joining a different
world — always tears down the client's play connection first. `ChestCaptureController` registers
`ClientPlayConnectionEvents.DISCONNECT` (a verified, current Fabric hook) to call
`ChestManager.clearTransientCaptureState()` at that exact moment, which clears any in-memory
pending interaction and/or active capture session **without persisting anything** — no guessed or
fake final snapshot is ever written. This guarantees a pending interaction or an in-progress
capture from one world/server can never carry over into another. In normal operation the existing
`ScreenEvents.remove` hook already finalizes a capture cleanly before this would ever be needed;
this is defense in depth for the pathological case where a screen closes without that firing.

## 12b. Pending-Interaction Hygiene

A pending interaction (recorded on block right-click, before any menu has opened) is consumed
exactly once — every call to `tryBeginCapture` clears it immediately, whether or not it
correlates. So the only way one can linger in memory is if the player right-clicks a supported
block and no menu ever opens afterward at all; the next real interaction (or the world-leave cleanup
in §12a) always clears it. No timer thread or background worker is used — correctness relies
entirely on the strict 2-second recency check in §3, which would already reject a stale pending
interaction even if one were somehow still sitting in memory.

## 13. Input Behavior (G, Escape, and Companion Text Fields)

`G` closes the Companion globally — **except** while a Kistor text input is focused (the search
field or an inline label/group/note editor), in which case `G` types into it. Implemented through
the shared `TextInputHandler` contract (`KistorTabComponent.isTextInputFocused()`).

`Escape` priority:
1. An open inline editor: the first `Escape` cancels it without saving.
2. Otherwise a focused search field: `Escape` (or `Enter`) unfocuses it and keeps the query.
3. Otherwise `Escape` closes the Companion normally.

No new global keybind was added for Kistor 2.0.

## 14. Kistor 2.0 Layout

```
Kistor  12 sparade                     [SAKER][FÖRVARING][MATERIAL*]
[ NAVIGERAR: Materiallager                                  [ Stoppa ] ]   (only while navigating)
[ 284 block fågelvägen • 12 block lägre                               ]
[ Vad letar du efter? ...                                           ][x]
[ Typ: Alla ] [ Sortering: ... ] [ Grupp: Alla ]
+-------------- list --------------+ +------------- detail -------------+
|                                  | |                                  |
+----------------------------------+ +----------------------------------+
                                     [ Hitta ][ Byt namn ][ ★ Fäst     ]   FÖRVARING: 2 pinned rows
                                     [Koord.][Notering][ Grupp ][ Glöm ]   SAKER: 1 row (Hitta närmaste)
```

`*` MATERIAL appears only while a planner material request is active. No new top-level Companion
tab was added. `KistorLayout` is pure geometry:

- **Normal/large** (≥ 300 px content width): list (40%) beside detail (60%).
- **Compact** (< 300 px): ONE pane at a time (list and detail are the same rectangle) with a
  "< Saker" / "< Förvaring" back button; a compact list that isn't showing a detail reserves no
  action zone and uses the full height.
- Pinned action rows are reserved below the detail pane, outside every scrollable area, so no
  action button can overlap scrolled content (`Kistor2LayoutTest` checks every
  mode/banner/detail combination at several sizes).
- Scroll ranges are recorded from the content height actually drawn each frame
  (`ScrollState`), and routing in compact mode follows the visible pane.
- Rows never overlap icons/counts: counts are right-aligned and names ellipsize before them.
- While navigating, the banner is deliberately two lines: line 1 is **NAVIGERAR: <namn>** with
  **Stoppa** on the right; line 2 gets the full banner width for **<avstånd> block fågelvägen •
  <höjdskillnad>**. The 23 px banner reserves its own vertical space so search/filters/content
  never overlap it.

## 15. SAKER — aggregated items (default mode)

`ChestItemIndex.build(context, containers, displayName)` aggregates the already-stored snapshots
of the CURRENT context (other contexts are ignored even if passed in):

- item id, display name, **total last-known count**, number of storage locations, and every
  contributing location with its count (duplicate slots in one container are summed; zero
  counts, blank ids and air are ignored);
- locations ordered largest amount first, then most recently opened, then stable key;
- items ordered by total (default), name, or spread — always deterministic.

List rows: real icon · name · total (right-aligned) · "Finns i N förvaringar".

Item detail: icon + name, "Senast känt totalt: 438", "Finns i 4 förvaringar", raw id (setting),
then **SENAST KÄNDA PLATSER** — one row per storage: label, amount, relative time/freshness,
distance (only when the player is in the same dimension) or the storage's dimension, a
**NÄRMAST** tag, and a **Hitta** button. Clicking the row itself opens that storage's full
FÖRVARING detail. Wording always says the amounts are last known, not the server's current
stock.

**Hitta närmaste** (pinned) selects the nearest known storage holding the item, considering only
the current context and the player's current dimension (`ChestItemIndex.nearestSameDimension`).
It never discovers anything new.

Caching: the index is cached per (context, `ChestManager.revision()`, type filter, group filter)
by `ChestItemIndexCache`, and SAKER search results are memoized per (index, query, sort) — so a
normal render frame does no rebuilding at all.

## 16. FÖRVARING — known storage

Cards show `★` for favorites, the title (label, or storage type), a freshness badge, the most
meaningful items (largest counts first, as many 10 px icon+count chips as fit, then "+N andra"),
and "Overworld · Öppnad 18 min sedan · Min bas". Favorites are listed first under **FÄSTA**.

Detail: title, storage type + shape ("Dubbel kista", or "(kan vara dubbel)" for UNKNOWN),
dimension, coordinates, group, location note, freshness + last opened, "● NAVIGERAR HIT" when it
is the navigation target, **SENAST KÄNT INNEHÅLL** with icons and counts, **SEDAN FÖRRA
ÖPPNINGEN** (§18), and the last-known warning.

Actions: **Hitta** (or **Stoppa** when this is the target), **Namnge/Byt namn**, **★ Fäst / Ta
bort favorit**; secondary row **Koord.** (copy), **Notering**, **Grupp**, and a deliberately quiet
**Glöm** that needs a second click ("Bekräfta!") within 4 seconds.

## 17. Favorites, groups and location notes

Local metadata per storage (`StorageMetadata`), only ever stored in `chest-index.json`, never
touching signs, chat, commands, the world, or any GameZone API:

- **Favorite/pinned** — shown first under FÄSTA.
- **Group** — one simple level (no nested folders). The group editor lets the player type a new
  group or click an existing group chip, or "Ingen grupp" to remove it. Assigning a name that
  matches an existing group ignoring case reuses that spelling. Filter via the Grupp control;
  search matches group names.
- **Location note** — free text, e.g. "Källaren bakom smedjan".

Sanitization (shared `StorageMetadata.sanitizeText`): control characters/newlines become spaces,
whitespace collapses, trimmed; blank means "not set". Caps: label 32, group 24, note 80
characters.

## 18. Freshness and "sedan förra öppningen"

`ChestFreshness.classify(lastOpenedAtMs, now)`: **FÄRSK** (< 30 min), **SENASTE DYGNET**
(< 24 h), **TIDIGARE** (< 3 days), **ÄLDRE SNAPSHOT** (≥ 3 days), **OKÄND TID** (no timestamp),
plus relative text ("just nu", "18 min sedan", "2 h sedan", "3 dagar sedan"). An old snapshot is
presented as older, never as wrong; the last-known warning is always shown.

Previous snapshot: when a known storage is legitimately reopened and its final snapshot persisted,
the old current snapshot becomes the ONE retained `previous` snapshot (the older one is dropped —
bounded, no history). `ChestSnapshotDiff` shows only real per-item differences (appeared,
disappeared, increased, decreased; slot moves with equal totals are not changes), ordered by delta
descending (largest gain first, largest loss last), capped at 12 lines plus "+N fler ändringar".
Nothing extra is written while the storage screen is open — still exactly one persist per
finalized capture.

## 19. Capture feedback

After (and only after) a legitimate finalization + persist, `ChestManager.endCapture` returns a
`ChestCaptureEvent`; `ChestCaptureFeedback` decides the local toast:

- New storage: **"Ny förvaring sparad"** — "Dubbel kista • 17 olika föremål".
- Known storage whose contents changed: **"Materiallager uppdaterad"** (or "Förvaring
  uppdaterad") — "Senast känt innehåll sparat".
- Known storage reopened unchanged: **no toast** (anti-spam).
- The exact navigation target: **"✓ Materiallager hittad"** (§20).

Toasts go through the existing `GameZoneToastManager` via `offerCompanion`: respects the
Companion notifications setting (the separate GameZone-event toggle does not silence local Kistor
feedback), dedupes per storage within the existing 8 s window, and keeps the existing bounded
3-toast queue. No toast is ever produced per tick.

## 20. HITTA — navigation to ONE known storage

**What it is:** the player selects one storage location they previously legitimately opened and
presses **Hitta**. The HUD then compares the player's own current position and camera yaw with that
saved coordinate and shows direction and distance.

**What it is not:** chest radar, world/chunk/block-entity scanning, hidden storage detection, ESP
or outlines through walls, raytracing, pathfinding, route generation, auto-walking or
auto-turning. No nearby unknown storage can ever appear — only a container that already exists in
the local index can be a target (`ChestNavigationManager.start` refuses anything else).

**State.** `ChestNavigationManager` (inside the session-only `KistorRuntime`) holds at most one
target by its stable `StoredContainerId`. It is memory-only and never persisted, so it cannot
survive a restart. Starting navigation shows "Navigering startad • stäng med G för att se pilen". At the top of
Kistor, the active target uses a two-line banner: **NAVIGERAR: Materiallager** with **Stoppa** on
line 1, then distance + height difference across the full width on line 2 (clicking the banner
outside Stoppa opens the target's detail).

**HUD** (`KistorNavigationHudElement`, registered with `HudElementRegistry` exactly like
`GameZoneToastHudElement`):

- Inactive: one boolean check, draws nothing.
- Top-center card, placed below any boss bars the client is already drawing (counted via the
  read-only `BossHealthOverlayAccessor` mixin, capped at a third of the screen height) and pushed
  below the Companion toast card if they would overlap on a narrow screen. Its dark-navy fill is
  intentionally semi-transparent (~45% alpha, `0x730D141C`) so the world remains visible behind
  navigation; the border stays clear and the HUD text uses shadows for readability.
- Hidden while a screen (Companion, a storage menu, inventory) is open — except chat.
- **A real, smoothly rotating arrow**: an up-pointing arrow drawn from fills in a half-pixel local
  grid under the extractor's 2D pose rotated by the relative bearing, using the interpolated
  (partial-tick) camera yaw and position, so it rotates smoothly while the player turns the camera
  standing still. Arrow up = the camera faces the target; clockwise = target to the right; down =
  behind. Direction is relative to the CAMERA/LOOK yaw, not movement.
- Title, **"284 block fågelvägen"** (straight-line horizontal distance — the wording can't be
  confused with a route distance), and **"↓ 12"** / **"↑ 17"** vertical difference (hidden when
  under 2 blocks).
- **Nära** (< 15 blocks 3D): emphasized card, "Nära · 11 block fågelvägen".
- **DU ÄR FRAMME** (≤ 4.5 blocks 3D, roughly reach distance): arrow removed. Coordinate proximity
  only — nothing is highlighted, outlined or raytraced.
- **Wrong dimension**: no arrow at all; "⚠ Finns i Nether" / "Du är i Overworld". Saved
  coordinates stay visible in Kistor. When the player later enters the right dimension the arrow
  resumes automatically. No cross-dimension or portal routing is ever invented.

**Math** (`ChestNavigationMath`, pure). Minecraft yaw: 0 = facing +Z (south), 90 = −X (west),
180 = −Z (north), −90 = +X (east), increasing as the camera turns right; a player with yaw θ looks
along (−sin θ, cos θ). Target yaw = `atan2(−dx, dz)`; relative bearing =
`wrap(targetYaw − playerYaw)` into [−180, 180). The target point is the block center, or the
midpoint of both halves of a proven double chest. Tested without a live client
(`ChestNavigationMathTest`): ahead/right/left/behind, wraparound at ±180 and unnormalized yaw,
identical position, different Y levels, vertical deltas, thresholds, wrong dimension.

**Ending navigation.** It stays active until the player presses **Stoppa**, or the capture
controller proves the player legitimately opened the EXACT target (same stable identity rules as
the Chest Manager: context + dimension + canonical anchor + kind — opening either half of the
target double chest counts, a different chest or the same coordinates in another dimension do
not). Then it stops and shows "✓ Materiallager hittad".

**Fail-safe stop** (prefers STOP over any risk of cross-context navigation):
- the target no longer exists in the local index (forgotten, Kistor reset),
- the Chest Manager is not LOADED (error/incompatible schema),
- the world/server context changes (checked on join, once per second on the HUD, and every Kistor
  frame),
- the client disconnects / leaves the world (`ClientPlayConnectionEvents.DISCONNECT`).
While the player/level is unavailable (e.g. loading), the HUD simply draws nothing.

## 21. Planner integration: "Hitta material i kistor" and Hämtningslista

**Settlement → Material** and **Byggplaner → building detail** get a **Hitta material i kistor**
button. It hands a `ChestMaterialRequest` (built by `PlannerMaterialRequests` from the planner's own
requirement data) to Kistor, which opens its MATERIAL view. The planners themselves never read
chest data for this.

Gate: offered only while the existing setting **"Använd senast kända kistodata i planerare"** is
on. When it is off, Settlement doesn't show the button, Byggplaner shows "Kistdata i planerare är
avstängd i Inställningar.", and Kistor's material view refuses to compute. Settlement's
live-vs-local truth semantics (LIVE settlement data, "Planerad nuvarande nivå", owned-amount
checklist) are untouched.

**Tillgång** (`ChestMaterialAvailability`), per required material: needed amount, "Senast känt i
kistor" total, and **"SAKNAS ENLIGT ESTIMAT"** (needed − last known, never negative), plus the top
three storage locations with amount, distance/dimension and **Hitta**. Category requirements with
no concrete item ("any Wool") are listed as "kan inte matchas mot kistdata" and never guessed.

**Hämtningslista** (`ChestPickupPlanner`): pickup suggestions grouped by storage, each group with
its own **Hitta**, each line a local checkbox. **Allocation rule:** for each material, in the
planner's order, take from the storage locations that last held it — *largest last-known amount
first* (fewest stops), ties broken by *most recently opened* (freshest data), then stable key —
until the need is covered or the known stock runs out; any remainder is listed under **SAKNAS
ENLIGT ESTIMAT**. The player's position is deliberately not an input, so the list never reshuffles
while walking. Groups are ordered by total pickup amount. Checkmarks are session-only state (kept
while the Companion is closed to go fetch things, cleared on disconnect, a new request, or a
Kistor reset) — no long-term state is created.

Planning assistance only: nothing withdraws items, clicks storage slots, moves the player, runs
commands or completes objectives. Everything is labeled as a local estimate from last-known
snapshots, never GameZone's live server inventory.

## 22. Performance

- No disk write per frame; still exactly one persist per finalized capture, plus one per explicit
  local edit (label, favorite, group, note, forget, reset).
- No world, chunk or block-entity scanning; no networking; no new executors or threads.
- `ChestManager.revision()` is bumped on every index mutation; the item index, storage search
  results, SAKER search results and material plans are memoized against it (and their query/filter
  keys), so steady-state frames rebuild nothing. Item display names are memoized (bounded).
- Inactive navigation HUD: one boolean check. Active: one O(1) hash lookup for the target, the
  player's own pose, a few trig operations, and a throttled (1 s) context-key check.
- Bounded everywhere: one previous snapshot per storage, the existing 3-entry toast queue with its
  pruned dedupe map, a 512-entry cap on pickup checkmarks, 4096-entry caps on the icon and
  display-name memos.

## 23. Known Limitations

- **Client cannot distinguish a real chest's real contents from a real chest whose contents a
  server-side plugin has deliberately customized.** Inherent client-side limitation.
- **Ender Chests, container entities (minecart with chest), and mount inventories are not
  indexed** (see §4).
- **Double-chest partner detection relies on `ChestBlock.getConnectedBlockPos`**; an unexpected
  shape is recorded as `UNKNOWN` rather than guessed (§6).
- **No automated retention/cleanup** of old storage records yet.
- **Navigation is straight-line only** by design — no route, no portal awareness. A target behind
  a wall or under a mountain still points straight at it.
- **Downgrade:** once saved as schema v2, an older v1-only build treats the file as incompatible
  (fails closed, never overwrites).
- Icon scaling, HUD placement and arrow appearance could not be verified visually in this
  environment — see §24.

## 24. Testing and Human QA

Automated JUnit coverage (all Minecraft-free except the tab input tests, which only use
Minecraft's plain input records): `ChestIndexSchemaMigrationTest` (v1→v2, defaults, round-trip,
malformed fields, future schema fails closed), `ChestManagerKistor2Test` (metadata persistence,
sanitization, groups, previous-snapshot rollover, capture events, revision), `ChestItemIndexTest`,
`ChestItemIndexCacheTest`, `ChestSearchTest`, `ChestSnapshotDiffTest`, `ChestMaterialTest`
(availability, allocation, shortages, planner gate), `ChestNavigationMathTest`,
`ChestNavigationLifecycleTest`, `ChestCaptureFeedbackTest`, `Kistor2LayoutTest` (responsive
geometry, HUD placement), `KistorFairPlayTest`, plus the pre-existing `ChestManagerTest`,
`JsonChestIndexStoreTest`, `KistorLayoutTest`, `KistorTabInputTest`, `KistorTabComponentTest`.

The Minecraft-specific adapters (`MinecraftChestCaptureAdapter`, `ChestCaptureController`,
`ChestItemIcons`, `MinecraftPlayerPoseReader`, `KistorNavigationHudElement`) need live client
state and are verified by the manual checklist below. Automated tests alone are not sign-off.

### Manual gameplay QA checklist

Run with `.\gradlew.bat runClient` (singleplayer first, then GameZone):

1. Open a new normal single chest containing items, close it.
2. Verify the toast "Ny förvaring sparad" — "Kista • N olika föremål".
3. Open Companion (G) → Kistor. SAKER is the default mode.
4. Confirm the storage appears under FÖRVARING with NO double-chest text.
5. Confirm real item icons render at a sensible size in SAKER rows, storage cards (10 px chips),
   detail contents, and that text/counts don't overlap icons.
6. Rename the storage to "Materiallager" (Byt namn → type → Enter).
7. Add group "Min bas" (Grupp → type → Enter), then check the group chip appears for a second
   storage.
8. Add a location note (Notering → type → Enter).
9. Favorite it (★ Fäst) — it moves under FÄSTA.
10. Search an item in SAKER (e.g. "järn" / "iron").
11. Verify the aggregate total and "Finns i N förvaringar".
12. Put the same item into a second chest, open and close it.
13. Verify the item detail lists both storage locations with counts, distance and Hitta.
14. Reopen Materiallager and change its contents; verify the "Materiallager uppdaterad" toast.
15. Verify "SEDAN FÖRRA ÖPPNINGEN" shows only the real +/− differences. Reopen without changes:
    no toast, and "Inga ändringar sedan förra öppningen."
16. Start HITTA navigation from the storage detail; see "Navigering startad" and the two-line
    NAVIGERAR banner: target name + Stoppa on line 1, full distance/height text on line 2 with no
    truncation.
17. Close Companion with G.
18. Verify the navigation HUD card is top-center, below any boss bar, not covering the crosshair
    or hotbar, and that the semi-transparent background lets the world show through while all text
    remains easy to read.
19. Stand still and turn the camera left/right.
20. Verify the arrow rotates smoothly, and arrow UP means the target is straight ahead (target on
    your right → arrow points right; behind → down).
21. Walk toward the target.
22. Verify "N block fågelvägen" decreases.
23. Move above/below the target (dig down / build up).
24. Verify "↓ N" / "↑ N" and that it hides within 1 block.
25. Travel to the Nether with the target active: "⚠ Finns i Overworld" / "Du är i Nether", no arrow.
26. Return to the Overworld.
27. Verify the arrow resumes automatically.
28. Walk up to the target: "Nära ..." within 15 blocks, then "DU ÄR FRAMME". Nothing is highlighted
    through walls.
29. Open a different chest — navigation must remain active.
30. Open the exact target — navigation ends and "✓ Materiallager hittad" appears (try clicking the
    other half of a double-chest target too).
31. Settlement → Material with a target level: "Hitta material i kistor" → Kistor MATERIAL view with
    needed / senast känt / saknas enligt estimat. Turn the planner setting off: the button
    disappears and the view refuses to compute.
32. Byggplaner → a building with special requirements → "Hitta material i kistor".
33. Hämtningslista: groups by storage, check lines, Hitta per group, shortages listed; close and
    reopen Companion — checkmarks remain; Stäng materiallista removes the view.
34. Compact GUI scale / small window: one pane at a time, back buttons, pinned buttons never over
    scrolled content, all panes scroll correctly.
35. Restart Minecraft: labels, favorites, groups, notes and the previous-snapshot diff remain; no
    navigation is active after restart.
36. With navigation active, disconnect and join another world/server: navigation must be stopped
    and never appear there.
37. With an alpha.6 (schema v1) `chest-index.json` copied in beforehand: all old storage, labels,
    positions and contents are still present after the upgrade.
38. Also re-check the original M3 items: plugin/virtual GUIs are never indexed; "g" in a Kistor
    text field doesn't close the Companion; Rensa Kistor-index clears everything and stops
    navigation.

Do not consider Kistor 2.0 complete until this checklist has been run in real gameplay.
