# GZ Companion — Chest Manager (Kistor)

> **GZ Companion is an unofficial community project for GameZoneMC. It is not affiliated with or
> endorsed by GameZoneMC.**

Milestone 3 status: **implemented, pending human gameplay QA.** Do not treat this milestone as
complete until the manual QA sequence in this document has been run in real Minecraft gameplay.

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

**Forbidden — and not implemented anywhere in this module:**
- World scanning for containers, or searching block entities to discover unopened storage.
- Reading unopened container inventories.
- X-ray, packet tricks, or any server-internal data extraction.
- Hidden chest detection, container radar, or plugin data extraction.
- Claiming cached contents are current/live.
- Recursively opening/reading nested storage the player did not open themselves (shulker box
  contents inside an item stack, bundle contents, etc.).
- Any automation that violates GameZone rules.

Every UI surface uses **last-known** language, never "live" or "current" language:
"Senast känt innehåll", "Senast öppnad", and "Kan ha ändrats sedan du öppnade förvaringen."
("May have changed since you opened this storage.")

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

## 7. Local Persistence

Path: `config/gzcompanion/chest-index.json`.

```json
{
  "schemaVersion": 1,
  "contexts": {
    "<profileId>@@<contextKey>": {
      "containers": {
        "<stableContainerKey>": {
          "kind": "CHEST",
          "label": "Gruvbas",
          "dimension": "minecraft:overworld",
          "anchor": { "x": 120, "y": 64, "z": -32 },
          "partner": { "x": 121, "y": 64, "z": -32 },
          "shape": "DOUBLE",
          "lastOpenedAtMs": 1234567890,
          "slots": [ { "slot": 0, "itemId": "minecraft:iron_ingot", "count": 32 } ]
        }
      }
    }
  }
}
```

- Atomic writes: data is written to a `.tmp` file and moved into place with
  `StandardCopyOption.ATOMIC_MOVE`, exactly like `JsonGuideProgressStore`.
- Corruption recovery: a file that fails to parse (or fails to load at all) is preserved as
  `chest-index.json.corrupt.<timestamp>`, the mod continues with an empty index, and
  `ChestManager` still reports `LOADED` — a malformed file is treated the same as a fresh start,
  never as a permanent error state.
- **Incompatible future schema fails closed, without any overwrite risk.** `JsonChestIndexStore`
  returns a typed `ChestIndexLoadResult` distinguishing `NOT_FOUND` / `LOADED` /
  `CORRUPT_RECOVERED` (all safe, all result in `ChestManagerStatus.LOADED`) from
  `INCOMPATIBLE_SCHEMA` (a `schemaVersion` higher than this build understands). On
  `INCOMPATIBLE_SCHEMA` the file on disk is **never moved, deleted, or overwritten**, and
  `ChestManager` reports `ChestManagerStatus.INCOMPATIBLE` instead of `LOADED`. Every capture and
  mutation entry point (`recordPendingInteraction`, `tryBeginCapture`, `updateCaptureSlots`,
  `endCapture`, `forgetContainer`, `setLabel`) requires `LOADED` status and is a safe no-op
  otherwise — so an older client can never silently replace or partially overwrite an index file
  written by a newer version. Only in-memory queries (`getContainers`, `search`, etc.) remain
  available while incompatible; the Kistor tab shows a controlled "kistindexet är sparat av en
  nyare version" message, and the rest of GZ Companion (Guide, Home, etc.) is unaffected.
- Unknown/future JSON fields are tolerated and ignored on read, so a newer version of the file
  written by a future GZ Companion release doesn't break an older one that reads it.
- Container identity is a **stable key** (context + dimension + canonical anchor position +
  storage kind), never an array index — re-opening the same physical storage updates the existing
  entry.
- 100% local. No telemetry, no cloud account, no external API calls.
- **Growth policy:** nothing is automatically deleted in Milestone 3. The index grows only as
  large as the set of storage locations the player has actually opened. See §9.

**Backward-compatible `StorageShape` migration.** The very first M3 release shipped a container
schema with `partner` + a boolean `partnerUnknown` instead of an explicit `shape` field. Real
gameplay data already exists under that shape, so schema stays at v1 and the old fields are read
losslessly rather than the whole index being rewritten or discarded:

- A record that already has an explicit `"shape"` value uses it directly.
- A legacy record without `"shape"` is migrated in-memory (never rewritten on disk merely because
  it was loaded) using this conservative mapping:
  - `partner != null` → `DOUBLE`
  - `partner == null && partnerUnknown == true` → `UNKNOWN`
  - `partner == null && partnerUnknown == false` → `SINGLE` for a chest-family kind, otherwise
    `NOT_APPLICABLE`
- An explicit `"shape"` value this build doesn't recognize (e.g. written by a future version)
  falls back to the same legacy mapping rather than crashing or guessing.
- Every record written by this version onward always includes the explicit `"shape"` field.
- The future-schema fail-closed behavior (§7, `ChestIndexLoadResult`) is completely unaffected by
  this migration — it only concerns individual container records under the *same* schema version.

## 8. Context Isolation

Reuses the exact same profile/world/server identity semantics already established by the Guide
Engine (`GuideContext` / `GuideContextResolver`) rather than inventing new identity rules — no
duplicate world-detection logic exists. On top of that context key, dimension is also part of a
container's identity, so:

- The same X/Y/Z under a different world/server context is a different entry.
- The same X/Y/Z in a different dimension (Overworld vs. Nether vs. End) is a different entry.
- Restarting Minecraft or switching worlds and back preserves each world's own index unchanged.

## 9. Search, Filter, and Sort

`ChestManager.search(contextKey, query, typeFilter, sortMode)` operates purely on already-indexed
local data — it never triggers any world lookup, and never returns a result from a different
context. Matching (case-insensitive) is checked against:

- The raw Minecraft item ID (`minecraft:iron_ingot`).
- A resolved item display name (the real vanilla translated name when running in-game via
  `MinecraftChestCaptureAdapter.resolveItemDisplayName`, or a readable fallback transform of the
  ID — e.g. `iron_ingot` → `Iron Ingot` — when no live resolver is wired, such as in unit tests).
- Coordinate text (`"120 64 -32"`).
- Storage kind name/display name (`"barrel"`, `"Tunna"`).
- The dimension key (`"overworld"` matches `minecraft:overworld`).
- The player's local custom label, if set.

An empty query returns every indexed container for the current context (subject to the type
filter). The Kistor search bar shows a live "X av Y" result count once a query or filter is
active, and a small clear button next to the field. Tag-based search was intentionally left out
of Milestone 3 per the roadmap scope — it did not block this milestone.

**Storage type filter (`ChestTypeFilter`).** A compact cycle button ("Typ: ...") restricts results
to one storage grouping: `ALL`, `CHEST` (Chest + Trapped Chest), `BARREL`, `SHULKER_BOX`,
`HOPPER`, `DISPENSER_DROPPER` (Dispenser + Dropper). It combines with the search query (both must
match) and is local UI state, not persisted.

**Sort mode (`ChestSortMode`).** A second compact cycle button ("Sortering: ...") controls result
order: `RECENT` (default, most recently opened first), `NAME` (local label if present, otherwise
the storage type's display name, case-insensitive), `TYPE` (grouped by storage kind, then by
label/coordinates). Also local UI state, not persisted.

## 10. Local Custom Labels

`ChestManager.setLabel(contextKey, id, label)` stores a purely local, player-chosen label (e.g.
"Gruvbas", "Mat") **only** inside `chest-index.json`. It never writes a sign, places or edits a
block, sends a chat message, or issues a server command. Saving an empty or whitespace-only label
clears it; any real label is trimmed and capped at 32 characters.

**Kistor UI.** The detail pane's pinned action row has a "Namnge"/"Byt namn" button (its label
changes to "Spara" while editing). Clicking it opens an inline local text field pre-filled with
the current label. While editing: `Enter` commits, `Escape` cancels without saving, and clicking
anywhere else (other than the field itself or the commit button) also cancels without saving —
a single, consistently-documented "click away cancels" behavior. Labeled storage shows its label
as the primary line in both the list row and the detail pane, with the storage type demoted to
secondary information; unlabeled storage shows the storage type as the primary line, exactly as
before labels existed.

## 10a. Copy Coordinates

The detail pane's action row includes "Kopiera koord.", which copies the anchor position as plain
text (e.g. `"120 64 -32"`, matching the format already used for coordinate search) to the local OS
clipboard via the real, current Minecraft 26.1.2 API — `Minecraft.getInstance().keyboardHandler
.setClipboard(String)` — no reflection, no hacks. A temporary "Kopierat!" confirmation replaces the
button label for two seconds. This is a pure local convenience; nothing is sent anywhere, and
chat is never used for the confirmation.

## 10b. Item Icons — Deliberately Not Implemented This Pass

Minecraft 26.1.2's render abstraction (`GuiGraphicsExtractor.item(ItemStack, x, y)`) *is* a clean,
non-reflective API for rendering a real vanilla item icon, and was verified to exist before this
decision was made. It was deliberately not wired into the aggregated item list this pass: vanilla
item rendering is built around a fixed 16×16 icon, while the Kistor item rows are a dense ~10px
line height, so fitting a real icon in would require scaling the render pipeline down (via pose
transforms) in a way that cannot be visually verified without a running display in this
environment — and a visually broken icon would be worse than no icon. Functionality was
prioritized over this polish item, per instruction. A future pass can revisit this once it can be
checked against a real screenshot.

## 11. Corruption Behavior

Identical philosophy to the rest of GZ Companion: a malformed or unreadable `chest-index.json`
never crashes the mod. It is backed up with a timestamp suffix and the Chest Manager continues
with an empty index; the rest of the mod (Guide, Home, etc.) is entirely unaffected. If the store
cannot initialize at all, `ChestManagerStatus.ERROR` is reported and the Kistor tab shows a
controlled error state instead of pretending to work.

## 12. Privacy

Chest Manager diagnostics intentionally expose only status, schema version, and an indexed-count
number for the current context — never coordinates, labels, or item contents — in any generic
diagnostic output. All chest data is local-first: it never leaves the player's machine.

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

`G` closes the Companion globally, exactly as established in Milestone 1 — **except** while a
legitimate Companion text input owned by the Kistor tab (the search field or the local label
editor) is focused, in which case `G` types into that field instead. This is implemented as a
single condition in `GZCompanionMainScreen.keyPressed`: the global close action for `G` is
skipped whenever `KistorTabComponent.isTextInputFocused()` is true; Minecraft's own `charTyped`
callback still inserts the character normally, since it is a separate event from `keyPressed`.
This fixed a real usability bug where searching for "gold", "glass", "gravel", or "gruvbas" (or
naming a label starting with "g") was impossible.

`Escape` priority, active-tab-aware:
1. If the label editor is focused, the first `Escape` cancels the edit without saving.
2. Otherwise, if the search field is focused, the first `Escape` unfocuses it and preserves the
   typed query.
3. Otherwise (nothing left focused), `Escape` closes the Companion normally, exactly as before.

Every other tab (Guide, Home, etc.) is completely unaffected — `isTextInputFocused()` is only ever
true while the Kistor tab is active, so `G` and `Escape` behave exactly as they did in M1/M2
everywhere else.

## 14. Known Limitations

- **Client cannot distinguish a real chest's real contents from a real chest whose contents a
  server-side plugin has deliberately customized.** If GameZone (or any plugin) intercepts a
  right-click on a genuine, previously-placed Chest block and serves a modified inventory through
  the same vanilla `ChestMenu`, GZ Companion has no way to know that server-side substitution
  happened — the interaction and the menu are both completely legitimate from the client's point
  of view. This is an inherent client-side limitation, not a bug, and is not specific to GZ
  Companion.
- **Ender Chests, container entities (minecart with chest), and mount inventories are not
  indexed** in Milestone 3 (see §4). These are candidates for a future, separately-designed
  extension.
- **The double-chest partner detection relies on `ChestBlock.getConnectedBlockPos`.** If that
  ever returns an unexpected shape, GZ Companion deliberately records `StorageShape.UNKNOWN`
  rather than guessing (see §6).
- **No automated retention/cleanup exists yet.** The index can only grow as large as the storage
  locations a player has actually opened; a future milestone may add player-facing retention
  controls (e.g. "forget storage not opened in 90 days").
- **Real item icons are not rendered in the item list this pass** — see §10b for why, and what a
  future pass would need to verify first.

## 15. Testing

Automated JUnit tests cover the storage model (stable identity, context/dimension isolation,
double-chest canonicalization, `StorageShape` resolution and legacy migration), snapshot/
fingerprint behavior, correlation rejection paths (stale/mismatched/unsupported/virtual-GUI),
capture cleanup and pending-interaction hygiene, persistence (round-trip, atomic write, corruption
recovery, schema rejection, unknown-field/unknown-shape tolerance, malformed-entry skipping,
context/dimension isolation across a full reload), search/filter/sort, labels (set/clear/reload/
search), forget, layout geometry, Kistor text-input focus routing, and the M2 Home objective
fallback fix (`ChestManagerTest`, `JsonChestIndexStoreTest`, `KistorLayoutTest`,
`KistorTabInputTest`, `HomeTabComponentTest`).

Consistent with the Guide Engine's own testing tiers, the Minecraft-specific capture adapter and
controller (`MinecraftChestCaptureAdapter`, `ChestCaptureController`) are **not** unit tested —
they require live Minecraft block/menu/screen state that only exists in real gameplay. They are
verified instead through the manual human gameplay QA sequence below. Automated tests alone are
not sufficient sign-off for this milestone.

### Manual gameplay QA sequence

Run with `.\gradlew.bat runClient`:

1. Open a new single chest containing items, close it, open GZ Companion → Kistor. The chest
   should appear with the correct last-known contents, and must show NO double-chest text.
2. Reopen the same chest, move items between the chest and your own inventory, close it. GZ
   Companion should update the **same** record, not create a duplicate.
3. Open a genuine double chest. It should show "Dubbel kista" and resolve to one entry regardless
   of which half is clicked.
4. Open another chest at a different position. Both should appear as separate entries.
5. Search for an item, a label, a storage type, and a dimension name. Only already-saved,
   previously-opened storage should match. Try the type filter and each sort mode.
6. Rename a storage entry, confirm the label appears in the list, search for it, then clear it.
7. Use "Kopiera koord." and paste the clipboard contents somewhere to confirm the format.
8. While the search field is focused, type a query containing the letter "g" (e.g. "gold" or
   "gruvbas") — it must NOT close the Companion.
9. Open a normal plugin/virtual GUI, if one is available on the server. It must **not** be
   indexed as a physical chest.
10. Restart Minecraft. The same world's indexed storage should still be there.
11. Create or join another world. World A's chest index must not appear in World B.
12. Return to World A. Its storage index should reappear unchanged.

Do not consider Milestone 3 complete until this sequence has been run and confirmed in real
gameplay.
